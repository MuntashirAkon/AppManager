// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.compressors.gzip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.EOFException;
import java.io.InputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.BufferedInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.CRC32;

import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.utils.ByteUtils;
import org.apache.commons.compress.utils.CountingInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.compress.utils.InputStreamStatistics;

/**
 * Input stream that decompresses .gz files.
 *
 * <p>This supports decompressing concatenated .gz files which is important
 * when decompressing standalone .gz files.</p>
 *
 * <p>
 * {@link java.util.zip.GZIPInputStream} doesn't decompress concatenated .gz
 * files: it stops after the first member and silently ignores the rest.
 * It doesn't leave the read position to point to the beginning of the next
 * member, which makes it difficult workaround the lack of concatenation
 * support.
 * </p>
 *
 * <p>
 * Instead of using <code>GZIPInputStream</code>, this class has its own .gz
 * container format decoder. The actual decompression is done with
 * {@link java.util.zip.Inflater}.
 * </p>
 *
 * <p>If you use the constructor {@code GzipCompressorInputStream(in)}
 * or {@code GzipCompressorInputStream(in, false)} with some {@code
 * InputStream} {@code in} then {@link #read} will return -1 as soon
 * as the first internal member has been read completely. The stream
 * {@code in} will be positioned at the start of the second gzip
 * member if there is one.</p>
 *
 * <p>If you use the constructor {@code GzipCompressorInputStream(in,
 * true)} with some {@code InputStream} {@code in} then {@link #read}
 * will return -1 once the stream {@code in} has been exhausted. The
 * data read from a stream constructed this way will consist of the
 * concatenated data of all gzip members contained inside {@code
 * in}.</p>
 *
 * @see "https://tools.ietf.org/html/rfc1952"
 */
// Copyright 2011 sebbASF
public class GzipCompressorInputStream extends CompressorInputStream
        implements InputStreamStatistics {

    // Header flags
    // private static final int FTEXT = 0x01; // Uninteresting for us
    private static final int FHCRC = 0x02;
    private static final int FEXTRA = 0x04;
    private static final int FNAME = 0x08;
    private static final int FCOMMENT = 0x10;
    private static final int FRESERVED = 0xE0;

    private final CountingInputStream countingStream;

    // Compressed input stream, possibly wrapped in a
    // BufferedInputStream, always wrapped in countingStream above
    private final InputStream in;

    // True if decompressing multi member streams.
    private final boolean decompressConcatenated;

    // Buffer to hold the input data
    private final byte[] buf = new byte[8192];

    // Amount of data in buf.
    private int bufUsed;

    // Decompressor
    private Inflater inf = new Inflater(true);

    // CRC32 from uncompressed data
    private final CRC32 crc = new CRC32();

    // True once everything has been decompressed
    private boolean endReached = false;

    // used in no-arg read method
    private final byte[] oneByte = new byte[1];

    private final GzipParameters parameters = new GzipParameters();

    /**
     * Constructs a new input stream that decompresses gzip-compressed data
     * from the specified input stream.
     * <p>
     * This is equivalent to <code>GzipCompressorInputStream(inputStream, false)</code> and thus will not decompress
     * concatenated .gz files.
     *
     * @param inputStream The InputStream from which this object should be created of
     * @throws IOException If the stream could not be created
     */
    public GzipCompressorInputStream(final InputStream inputStream)
            throws IOException {
        this(inputStream, false);
    }

    /**
     * Constructs a new input stream that decompresses gzip-compressed data
     * from the specified input stream.
     * <p>
     * If <code>decompressConcatenated</code> is {@code false}:
     * This decompressor might read more input than it will actually use.
     * If <code>inputStream</code> supports <code>mark</code> and
     * <code>reset</code>, then the input position will be adjusted
     * so that it is right after the last byte of the compressed stream.
     * If <code>mark</code> isn't supported, the input position will be
     * undefined.
     *
     * @param inputStream            the InputStream from which this object should
     *                               be created of
     * @param decompressConcatenated if true, decompress until the end of the input;
     *                               if false, stop after the first .gz member
     * @throws IOException if the stream could not be created
     */
    public GzipCompressorInputStream(final InputStream inputStream,
                                     final boolean decompressConcatenated)
            throws IOException {
        countingStream = new CountingInputStream(inputStream);
        // Mark support is strictly needed for concatenated files only,
        // but it's simpler if it is always available.
        if (countingStream.markSupported()) {
            in = countingStream;
        } else {
            in = new BufferedInputStream(countingStream);
        }

        this.decompressConcatenated = decompressConcatenated;
        init(true);
    }

    /**
     * Provides the stream's meta data - may change with each stream
     * when decompressing concatenated streams.
     *
     * @return the stream's meta data
     * @since 1.8
     */
    public GzipParameters getMetaData() {
        return parameters;
    }

    private boolean init(final boolean isFirstMember) throws IOException {
        if (!isFirstMember && !decompressConcatenated) {
            throw new IOException("Initialised without a first member nor the decompressConcatenated is enabled");
        }

        // Check the magic bytes without a possibility of EOFException.
        final int magic0 = in.read();

        // If end of input was reached after decompressing at least
        // one .gz member, we have reached the end of the file successfully.
        if (magic0 == -1 && !isFirstMember) {
            return false;
        }

        if (magic0 != 31 || in.read() != 139) {
            throw new IOException(isFirstMember ? "Input is not in the .gz format"
                    : "Garbage after a valid .gz stream");
        }

        // Parsing the rest of the header may throw EOFException.
        final DataInput inData = new DataInputStream(in);
        final int method = inData.readUnsignedByte();
        if (method != Deflater.DEFLATED) {
            throw new IOException("Unsupported compression method " + method + " in the .gz header");
        }

        final int flg = inData.readUnsignedByte();
        if ((flg & FRESERVED) != 0) {
            throw new IOException(
                    "Reserved flags are set in the .gz header");
        }

        parameters.setModificationTime(ByteUtils.fromLittleEndian(inData, 4) * 1000);
        switch (inData.readUnsignedByte()) { // extra flags
            case 2:
                parameters.setCompressionLevel(Deflater.BEST_COMPRESSION);
                break;
            case 4:
                parameters.setCompressionLevel(Deflater.BEST_SPEED);
                break;
            default:
                // ignored for now
                break;
        }
        parameters.setOperatingSystem(inData.readUnsignedByte());

        // Extra field, ignored
        if ((flg & FEXTRA) != 0) {
            int xlen = inData.readUnsignedByte();
            xlen |= inData.readUnsignedByte() << 8;

            // This isn't as efficient as calling in.skip would be,
            // but it's lazier to handle unexpected end of input this way.
            // Most files don't have an extra field anyway.
            while (xlen-- > 0) {
                inData.readUnsignedByte();
            }
        }

        // Original file name
        if ((flg & FNAME) != 0) {
            parameters.setFilename(new String(readToNull(inData),
                    StandardCharsets.ISO_8859_1));
        }

        // Comment
        if ((flg & FCOMMENT) != 0) {
            parameters.setComment(new String(readToNull(inData),
                    StandardCharsets.ISO_8859_1));
        }

        // Header "CRC16" which is actually a truncated CRC32 (which isn't
        // as good as real CRC16). I don't know if any encoder implementation
        // sets this, so it's not worth trying to verify it. GNU gzip 1.4
        // doesn't support this field, but zlib seems to be able to at least
        // skip over it.
        if ((flg & FHCRC) != 0) {
            inData.readShort();
        }

        // Reset
        inf.reset();
        crc.reset();

        return true;
    }

    private static byte[] readToNull(final DataInput inData) throws IOException {
        try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            int b;
            while ((b = inData.readUnsignedByte()) != 0x00) { // NOPMD NOSONAR
                bos.write(b);
            }
            return bos.toByteArray();
        }
    }

    @Override
    public int read() throws IOException {
        return read(oneByte, 0, 1) == -1 ? -1 : oneByte[0] & 0xFF;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.1
     */
    @Override
    public int read(final byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        if (endReached) {
            return -1;
        }

        int size = 0;

        while (len > 0) {
            if (inf.needsInput()) {
                // Remember the current position because we may need to
                // rewind after reading too much input.
                in.mark(buf.length);

                bufUsed = in.read(buf);
                if (bufUsed == -1) {
                    throw new EOFException();
                }

                inf.setInput(buf, 0, bufUsed);
            }

            final int ret;
            try {
                ret = inf.inflate(b, off, len);
            } catch (final DataFormatException e) { // NOSONAR
                throw new IOException("Gzip-compressed data is corrupt");
            }

            crc.update(b, off, ret);
            off += ret;
            len -= ret;
            size += ret;
            count(ret);

            if (inf.finished()) {
                // We may have read too many bytes. Rewind the read
                // position to match the actual amount used.
                in.reset();

                final int skipAmount = bufUsed - inf.getRemaining();
                if (IOUtils.skip(in, skipAmount) != skipAmount) {
                    throw new IOException();
                }

                bufUsed = 0;

                final DataInput inData = new DataInputStream(in);

                // CRC32
                final long crcStored = ByteUtils.fromLittleEndian(inData, 4);

                if (crcStored != crc.getValue()) {
                    throw new IOException("Gzip-compressed data is corrupt (CRC32 error)");
                }

                // Uncompressed size modulo 2^32 (ISIZE in the spec)
                final long isize = ByteUtils.fromLittleEndian(inData, 4);

                if (isize != (inf.getBytesWritten() & 0xffffffffL)) {
                    throw new IOException("Gzip-compressed data is corrupt (uncompressed size mismatch)");
                }

                // See if this is the end of the file.
                if (!decompressConcatenated || !init(false)) {
                    inf.end();
                    inf = null;
                    endReached = true;
                    return size == 0 ? -1 : size;
                }
            }
        }

        return size;
    }

    /**
     * Checks if the signature matches what is expected for a .gz file.
     *
     * @param signature the bytes to check
     * @param length    the number of bytes to check
     * @return true if this is a .gz stream, false otherwise
     * @since 1.1
     */
    public static boolean matches(final byte[] signature, final int length) {
        return length >= 2 && signature[0] == 31 && signature[1] == -117;
    }

    /**
     * Closes the input stream (unless it is System.in).
     *
     * @since 1.2
     */
    @Override
    public void close() throws IOException {
        if (inf != null) {
            inf.end();
            inf = null;
        }

        if (this.in != System.in) {
            this.in.close();
        }
    }

    /**
     * @since 1.17
     */
    @Override
    public long getCompressedCount() {
        return countingStream.getBytesRead();
    }
}