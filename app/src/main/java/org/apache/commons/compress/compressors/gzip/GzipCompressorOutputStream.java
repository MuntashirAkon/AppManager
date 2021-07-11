// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.compressors.gzip;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.compressors.CompressorOutputStream;

/**
 * Compressed output stream using the gzip format. This implementation improves
 * over the standard {@link GZIPOutputStream} class by allowing
 * the configuration of the compression level and the header metadata (file name,
 * comment, modification time, operating system and extra flags).
 *
 * @see <a href="https://tools.ietf.org/html/rfc1952">GZIP File Format Specification</a>
 */
// Copyright 2008 Torsten Curdt
public class GzipCompressorOutputStream extends CompressorOutputStream {

    /** Header flag indicating a file name follows the header */
    private static final int FNAME = 1 << 3;

    /** Header flag indicating a comment follows the header */
    private static final int FCOMMENT = 1 << 4;

    /** The underlying stream */
    private final OutputStream out;

    /** Deflater used to compress the data */
    private final Deflater deflater;

    /** The buffer receiving the compressed data from the deflater */
    private final byte[] deflateBuffer = new byte[512];

    /** Indicates if the stream has been closed */
    private boolean closed;

    /** The checksum of the uncompressed data */
    private final CRC32 crc = new CRC32();

    /**
     * Creates a gzip compressed output stream with the default parameters.
     * @param out the stream to compress to
     * @throws IOException if writing fails
     */
    public GzipCompressorOutputStream(final OutputStream out) throws IOException {
        this(out, new GzipParameters());
    }

    /**
     * Creates a gzip compressed output stream with the specified parameters.
     * @param out the stream to compress to
     * @param parameters the parameters to use
     * @throws IOException if writing fails
     *
     * @since 1.7
     */
    public GzipCompressorOutputStream(final OutputStream out, final GzipParameters parameters) throws IOException {
        this.out = out;
        this.deflater = new Deflater(parameters.getCompressionLevel(), true);

        writeHeader(parameters);
    }

    private void writeHeader(final GzipParameters parameters) throws IOException {
        final String filename = parameters.getFilename();
        final String comment = parameters.getComment();

        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) GZIPInputStream.GZIP_MAGIC);
        buffer.put((byte) Deflater.DEFLATED); // compression method (8: deflate)
        buffer.put((byte) ((filename != null ? FNAME : 0) | (comment != null ? FCOMMENT : 0))); // flags
        buffer.putInt((int) (parameters.getModificationTime() / 1000));

        // extra flags
        final int compressionLevel = parameters.getCompressionLevel();
        if (compressionLevel == Deflater.BEST_COMPRESSION) {
            buffer.put((byte) 2);
        } else if (compressionLevel == Deflater.BEST_SPEED) {
            buffer.put((byte) 4);
        } else {
            buffer.put((byte) 0);
        }

        buffer.put((byte) parameters.getOperatingSystem());

        out.write(buffer.array());

        if (filename != null) {
            out.write(filename.getBytes(StandardCharsets.ISO_8859_1));
            out.write(0);
        }

        if (comment != null) {
            out.write(comment.getBytes(StandardCharsets.ISO_8859_1));
            out.write(0);
        }
    }

    private void writeTrailer() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt((int) crc.getValue());
        buffer.putInt(deflater.getTotalIn());

        out.write(buffer.array());
    }

    @Override
    public void write(final int b) throws IOException {
        write(new byte[]{(byte) (b & 0xff)}, 0, 1);
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.1
     */
    @Override
    public void write(final byte[] buffer) throws IOException {
        write(buffer, 0, buffer.length);
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.1
     */
    @Override
    public void write(final byte[] buffer, final int offset, final int length) throws IOException {
        if (deflater.finished()) {
            throw new IOException("Cannot write more data, the end of the compressed data stream has been reached");
        }
        if (length > 0) {
            deflater.setInput(buffer, offset, length);

            while (!deflater.needsInput()) {
                deflate();
            }

            crc.update(buffer, offset, length);
        }
    }

    private void deflate() throws IOException {
        final int length = deflater.deflate(deflateBuffer, 0, deflateBuffer.length);
        if (length > 0) {
            out.write(deflateBuffer, 0, length);
        }
    }

    /**
     * Finishes writing compressed data to the underlying stream without closing it.
     *
     * @since 1.7
     * @throws IOException on error
     */
    public void finish() throws IOException {
        if (!deflater.finished()) {
            deflater.finish();

            while (!deflater.finished()) {
                deflate();
            }

            writeTrailer();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.7
     */
    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            try {
                finish();
            } finally {
                deflater.end();
                out.close();
                closed = true;
            }
        }
    }

}