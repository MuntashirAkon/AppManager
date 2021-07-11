// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.archivers.tar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.zip.ZipEncoding;
import org.apache.commons.compress.archivers.zip.ZipEncodingHelper;
import org.apache.commons.compress.utils.CharsetNames;
import org.apache.commons.compress.utils.IOUtils;

import static org.apache.commons.compress.archivers.tar.TarConstants.CHKSUMLEN;
import static org.apache.commons.compress.archivers.tar.TarConstants.CHKSUM_OFFSET;
import static org.apache.commons.compress.archivers.tar.TarConstants.SPARSE_NUMBYTES_LEN;
import static org.apache.commons.compress.archivers.tar.TarConstants.SPARSE_OFFSET_LEN;

/**
 * This class provides static utility methods to work with byte streams.
 *
 * @Immutable
 */
// Copyright 2008 Torsten Curdt
public class TarUtils {

    private static final int BYTE_MASK = 255;

    static final ZipEncoding DEFAULT_ENCODING =
        ZipEncodingHelper.getZipEncoding(null);

    /**
     * Encapsulates the algorithms used up to Commons Compress 1.3 as
     * ZipEncoding.
     */
    static final ZipEncoding FALLBACK_ENCODING = new ZipEncoding() {
            @Override
            public boolean canEncode(final String name) { return true; }

            @Override
            public ByteBuffer encode(final String name) {
                final int length = name.length();
                final byte[] buf = new byte[length];

                // copy until end of input or output is reached.
                for (int i = 0; i < length; ++i) {
                    buf[i] = (byte) name.charAt(i);
                }
                return ByteBuffer.wrap(buf);
            }

            @Override
            public String decode(final byte[] buffer) {
                final int length = buffer.length;
                final StringBuilder result = new StringBuilder(length);

                for (final byte b : buffer) {
                    if (b == 0) { // Trailing null
                        break;
                    }
                    result.append((char) (b & 0xFF)); // Allow for sign-extension
                }

                return result.toString();
            }
        };

    /** Private constructor to prevent instantiation of this utility class. */
    private TarUtils(){
    }

    /**
     * Parse an octal string from a buffer.
     *
     * <p>Leading spaces are ignored.
     * The buffer must contain a trailing space or NUL,
     * and may contain an additional trailing space or NUL.</p>
     *
     * <p>The input buffer is allowed to contain all NULs,
     * in which case the method returns 0L
     * (this allows for missing fields).</p>
     *
     * <p>To work-around some tar implementations that insert a
     * leading NUL this method returns 0 if it detects a leading NUL
     * since Commons Compress 1.4.</p>
     *
     * @param buffer The buffer from which to parse.
     * @param offset The offset into the buffer from which to parse.
     * @param length The maximum number of bytes to parse - must be at least 2 bytes.
     * @return The long value of the octal string.
     * @throws IllegalArgumentException if the trailing space/NUL is missing or if a invalid byte is detected.
     */
    public static long parseOctal(final byte[] buffer, final int offset, final int length) {
        long    result = 0;
        int     end = offset + length;
        int     start = offset;

        if (length < 2){
            throw new IllegalArgumentException("Length "+length+" must be at least 2");
        }

        if (buffer[start] == 0) {
            return 0L;
        }

        // Skip leading spaces
        while (start < end){
            if (buffer[start] == ' '){
                start++;
            } else {
                break;
            }
        }

        // Trim all trailing NULs and spaces.
        // The ustar and POSIX tar specs require a trailing NUL or
        // space but some implementations use the extra digit for big
        // sizes/uids/gids ...
        byte trailer = buffer[end - 1];
        while (start < end && (trailer == 0 || trailer == ' ')) {
            end--;
            trailer = buffer[end - 1];
        }

        for ( ;start < end; start++) {
            final byte currentByte = buffer[start];
            // CheckStyle:MagicNumber OFF
            if (currentByte < '0' || currentByte > '7'){
                throw new IllegalArgumentException(
                        exceptionMessage(buffer, offset, length, start, currentByte));
            }
            result = (result << 3) + (currentByte - '0'); // convert from ASCII
            // CheckStyle:MagicNumber ON
        }

        return result;
    }

    /**
     * Compute the value contained in a byte buffer.  If the most
     * significant bit of the first byte in the buffer is set, this
     * bit is ignored and the rest of the buffer is interpreted as a
     * binary number.  Otherwise, the buffer is interpreted as an
     * octal number as per the parseOctal function above.
     *
     * @param buffer The buffer from which to parse.
     * @param offset The offset into the buffer from which to parse.
     * @param length The maximum number of bytes to parse.
     * @return The long value of the octal or binary string.
     * @throws IllegalArgumentException if the trailing space/NUL is
     * missing or an invalid byte is detected in an octal number, or
     * if a binary number would exceed the size of a signed long
     * 64-bit integer.
     * @since 1.4
     */
    public static long parseOctalOrBinary(final byte[] buffer, final int offset,
                                          final int length) {

        if ((buffer[offset] & 0x80) == 0) {
            return parseOctal(buffer, offset, length);
        }
        final boolean negative = buffer[offset] == (byte) 0xff;
        if (length < 9) {
            return parseBinaryLong(buffer, offset, length, negative);
        }
        return parseBinaryBigInteger(buffer, offset, length, negative);
    }

    private static long parseBinaryLong(final byte[] buffer, final int offset,
                                        final int length,
                                        final boolean negative) {
        if (length >= 9) {
            throw new IllegalArgumentException("At offset " + offset + ", "
                                               + length + " byte binary number"
                                               + " exceeds maximum signed long"
                                               + " value");
        }
        long val = 0;
        for (int i = 1; i < length; i++) {
            val = (val << 8) + (buffer[offset + i] & 0xff);
        }
        if (negative) {
            // 2's complement
            val--;
            val ^= (long) Math.pow(2.0, (length - 1) * 8.0) - 1;
        }
        return negative ? -val : val;
    }

    private static long parseBinaryBigInteger(final byte[] buffer,
                                              final int offset,
                                              final int length,
                                              final boolean negative) {
        final byte[] remainder = new byte[length - 1];
        System.arraycopy(buffer, offset + 1, remainder, 0, length - 1);
        BigInteger val = new BigInteger(remainder);
        if (negative) {
            // 2's complement
            val = val.add(BigInteger.valueOf(-1)).not();
        }
        if (val.bitLength() > 63) {
            throw new IllegalArgumentException("At offset " + offset + ", "
                                               + length + " byte binary number"
                                               + " exceeds maximum signed long"
                                               + " value");
        }
        return negative ? -val.longValue() : val.longValue();
    }

    /**
     * Parse a boolean byte from a buffer.
     * Leading spaces and NUL are ignored.
     * The buffer may contain trailing spaces or NULs.
     *
     * @param buffer The buffer from which to parse.
     * @param offset The offset into the buffer from which to parse.
     * @return The boolean value of the bytes.
     * @throws IllegalArgumentException if an invalid byte is detected.
     */
    public static boolean parseBoolean(final byte[] buffer, final int offset) {
        return buffer[offset] == 1;
    }

    // Helper method to generate the exception message
    private static String exceptionMessage(final byte[] buffer, final int offset,
            final int length, final int current, final byte currentByte) {
        // default charset is good enough for an exception message,
        //
        // the alternative was to modify parseOctal and
        // parseOctalOrBinary to receive the ZipEncoding of the
        // archive (deprecating the existing public methods, of
        // course) and dealing with the fact that ZipEncoding#decode
        // can throw an IOException which parseOctal* doesn't declare
        String string = new String(buffer, offset, length);

        string=string.replaceAll("\0", "{NUL}"); // Replace NULs to allow string to be printed
        return "Invalid byte "+currentByte+" at offset "+(current-offset)+" in '"+string+"' len="+length;
    }

    /**
     * Parse an entry name from a buffer.
     * Parsing stops when a NUL is found
     * or the buffer length is reached.
     *
     * @param buffer The buffer from which to parse.
     * @param offset The offset into the buffer from which to parse.
     * @param length The maximum number of bytes to parse.
     * @return The entry name.
     */
    public static String parseName(final byte[] buffer, final int offset, final int length) {
        try {
            return parseName(buffer, offset, length, DEFAULT_ENCODING);
        } catch (final IOException ex) { // NOSONAR
            try {
                return parseName(buffer, offset, length, FALLBACK_ENCODING);
            } catch (final IOException ex2) {
                // impossible
                throw new RuntimeException(ex2); //NOSONAR
            }
        }
    }

    /**
     * Parse an entry name from a buffer.
     * Parsing stops when a NUL is found
     * or the buffer length is reached.
     *
     * @param buffer The buffer from which to parse.
     * @param offset The offset into the buffer from which to parse.
     * @param length The maximum number of bytes to parse.
     * @param encoding name of the encoding to use for file names
     * @since 1.4
     * @return The entry name.
     * @throws IOException on error
     */
    public static String parseName(final byte[] buffer, final int offset,
                                   final int length,
                                   final ZipEncoding encoding)
        throws IOException {

        int len = 0;
        for (int i = offset; len < length && buffer[i] != 0; i++) {
            len++;
        }
        if (len > 0) {
            final byte[] b = new byte[len];
            System.arraycopy(buffer, offset, b, 0, len);
            return encoding.decode(b);
        }
        return "";
    }

    /**
     * Parses the content of a PAX 1.0 sparse block.
     * @since 1.20
     * @param buffer The buffer from which to parse.
     * @param offset The offset into the buffer from which to parse.
     * @return a parsed sparse struct
     */
    public static TarArchiveStructSparse parseSparse(final byte[] buffer, final int offset) {
        final long sparseOffset = parseOctalOrBinary(buffer, offset, SPARSE_OFFSET_LEN);
        final long sparseNumbytes = parseOctalOrBinary(buffer, offset + SPARSE_OFFSET_LEN, SPARSE_NUMBYTES_LEN);

        return new TarArchiveStructSparse(sparseOffset, sparseNumbytes);
    }

    /**
     * Copy a name into a buffer.
     * Copies characters from the name into the buffer
     * starting at the specified offset.
     * If the buffer is longer than the name, the buffer
     * is filled with trailing NULs.
     * If the name is longer than the buffer,
     * the output is truncated.
     *
     * @param name The header name from which to copy the characters.
     * @param buf The buffer where the name is to be stored.
     * @param offset The starting offset into the buffer
     * @param length The maximum number of header bytes to copy.
     * @return The updated offset, i.e. offset + length
     */
    public static int formatNameBytes(final String name, final byte[] buf, final int offset, final int length) {
        try {
            return formatNameBytes(name, buf, offset, length, DEFAULT_ENCODING);
        } catch (final IOException ex) { // NOSONAR
            try {
                return formatNameBytes(name, buf, offset, length,
                                       FALLBACK_ENCODING);
            } catch (final IOException ex2) {
                // impossible
                throw new RuntimeException(ex2); //NOSONAR
            }
        }
    }

    /**
     * Copy a name into a buffer.
     * Copies characters from the name into the buffer
     * starting at the specified offset.
     * If the buffer is longer than the name, the buffer
     * is filled with trailing NULs.
     * If the name is longer than the buffer,
     * the output is truncated.
     *
     * @param name The header name from which to copy the characters.
     * @param buf The buffer where the name is to be stored.
     * @param offset The starting offset into the buffer
     * @param length The maximum number of header bytes to copy.
     * @param encoding name of the encoding to use for file names
     * @since 1.4
     * @return The updated offset, i.e. offset + length
     * @throws IOException on error
     */
    public static int formatNameBytes(final String name, final byte[] buf, final int offset,
                                      final int length,
                                      final ZipEncoding encoding)
        throws IOException {
        int len = name.length();
        ByteBuffer b = encoding.encode(name);
        while (b.limit() > length && len > 0) {
            b = encoding.encode(name.substring(0, --len));
        }
        final int limit = b.limit() - b.position();
        System.arraycopy(b.array(), b.arrayOffset(), buf, offset, limit);

        // Pad any remaining output bytes with NUL
        for (int i = limit; i < length; ++i) {
            buf[offset + i] = 0;
        }

        return offset + length;
    }

    /**
     * Fill buffer with unsigned octal number, padded with leading zeroes.
     *
     * @param value number to convert to octal - treated as unsigned
     * @param buffer destination buffer
     * @param offset starting offset in buffer
     * @param length length of buffer to fill
     * @throws IllegalArgumentException if the value will not fit in the buffer
     */
    public static void formatUnsignedOctalString(final long value, final byte[] buffer,
            final int offset, final int length) {
        int remaining = length;
        remaining--;
        if (value == 0) {
            buffer[offset + remaining--] = (byte) '0';
        } else {
            long val = value;
            for (; remaining >= 0 && val != 0; --remaining) {
                // CheckStyle:MagicNumber OFF
                buffer[offset + remaining] = (byte) ((byte) '0' + (byte) (val & 7));
                val = val >>> 3;
                // CheckStyle:MagicNumber ON
            }
            if (val != 0){
                throw new IllegalArgumentException
                (value+"="+Long.toOctalString(value)+ " will not fit in octal number buffer of length "+length);
            }
        }

        for (; remaining >= 0; --remaining) { // leading zeros
            buffer[offset + remaining] = (byte) '0';
        }
    }

    /**
     * Write an octal integer into a buffer.
     *
     * Uses {@link #formatUnsignedOctalString} to format
     * the value as an octal string with leading zeros.
     * The converted number is followed by space and NUL
     *
     * @param value The value to write
     * @param buf The buffer to receive the output
     * @param offset The starting offset into the buffer
     * @param length The size of the output buffer
     * @return The updated offset, i.e offset+length
     * @throws IllegalArgumentException if the value (and trailer) will not fit in the buffer
     */
    public static int formatOctalBytes(final long value, final byte[] buf, final int offset, final int length) {

        int idx=length-2; // For space and trailing null
        formatUnsignedOctalString(value, buf, offset, idx);

        buf[offset + idx++] = (byte) ' '; // Trailing space
        buf[offset + idx]   = 0; // Trailing null

        return offset + length;
    }

    /**
     * Write an octal long integer into a buffer.
     *
     * Uses {@link #formatUnsignedOctalString} to format
     * the value as an octal string with leading zeros.
     * The converted number is followed by a space.
     *
     * @param value The value to write as octal
     * @param buf The destinationbuffer.
     * @param offset The starting offset into the buffer.
     * @param length The length of the buffer
     * @return The updated offset
     * @throws IllegalArgumentException if the value (and trailer) will not fit in the buffer
     */
    public static int formatLongOctalBytes(final long value, final byte[] buf, final int offset, final int length) {

        final int idx=length-1; // For space

        formatUnsignedOctalString(value, buf, offset, idx);
        buf[offset + idx] = (byte) ' '; // Trailing space

        return offset + length;
    }

    /**
     * Write an long integer into a buffer as an octal string if this
     * will fit, or as a binary number otherwise.
     *
     * Uses {@link #formatUnsignedOctalString} to format
     * the value as an octal string with leading zeros.
     * The converted number is followed by a space.
     *
     * @param value The value to write into the buffer.
     * @param buf The destination buffer.
     * @param offset The starting offset into the buffer.
     * @param length The length of the buffer.
     * @return The updated offset.
     * @throws IllegalArgumentException if the value (and trailer)
     * will not fit in the buffer.
     * @since 1.4
     */
    public static int formatLongOctalOrBinaryBytes(
        final long value, final byte[] buf, final int offset, final int length) {

        // Check whether we are dealing with UID/GID or SIZE field
        final long maxAsOctalChar = length == TarConstants.UIDLEN ? TarConstants.MAXID : TarConstants.MAXSIZE;

        final boolean negative = value < 0;
        if (!negative && value <= maxAsOctalChar) { // OK to store as octal chars
            return formatLongOctalBytes(value, buf, offset, length);
        }

        if (length < 9) {
            formatLongBinary(value, buf, offset, length, negative);
        } else {
            formatBigIntegerBinary(value, buf, offset, length, negative);
        }

        buf[offset] = (byte) (negative ? 0xff : 0x80);
        return offset + length;
    }

    private static void formatLongBinary(final long value, final byte[] buf,
                                         final int offset, final int length,
                                         final boolean negative) {
        final int bits = (length - 1) * 8;
        final long max = 1L << bits;
        long val = Math.abs(value); // Long.MIN_VALUE stays Long.MIN_VALUE
        if (val < 0 || val >= max) {
            throw new IllegalArgumentException("Value " + value +
                " is too large for " + length + " byte field.");
        }
        if (negative) {
            val ^= max - 1;
            val++;
            val |= 0xffL << bits;
        }
        for (int i = offset + length - 1; i >= offset; i--) {
            buf[i] = (byte) val;
            val >>= 8;
        }
    }

    private static void formatBigIntegerBinary(final long value, final byte[] buf,
                                               final int offset,
                                               final int length,
                                               final boolean negative) {
        final BigInteger val = BigInteger.valueOf(value);
        final byte[] b = val.toByteArray();
        final int len = b.length;
        if (len > length - 1) {
            throw new IllegalArgumentException("Value " + value +
                " is too large for " + length + " byte field.");
        }
        final int off = offset + length - len;
        System.arraycopy(b, 0, buf, off, len);
        final byte fill = (byte) (negative ? 0xff : 0);
        for (int i = offset + 1; i < off; i++) {
            buf[i] = fill;
        }
    }

    /**
     * Writes an octal value into a buffer.
     *
     * Uses {@link #formatUnsignedOctalString} to format
     * the value as an octal string with leading zeros.
     * The converted number is followed by NUL and then space.
     *
     * @param value The value to convert
     * @param buf The destination buffer
     * @param offset The starting offset into the buffer.
     * @param length The size of the buffer.
     * @return The updated value of offset, i.e. offset+length
     * @throws IllegalArgumentException if the value (and trailer) will not fit in the buffer
     */
    public static int formatCheckSumOctalBytes(final long value, final byte[] buf, final int offset, final int length) {

        int idx=length-2; // for NUL and space
        formatUnsignedOctalString(value, buf, offset, idx);

        buf[offset + idx++]   = 0; // Trailing null
        buf[offset + idx]     = (byte) ' '; // Trailing space

        return offset + length;
    }

    /**
     * Compute the checksum of a tar entry header.
     *
     * @param buf The tar entry's header buffer.
     * @return The computed checksum.
     */
    public static long computeCheckSum(final byte[] buf) {
        long sum = 0;

        for (final byte element : buf) {
            sum += BYTE_MASK & element;
        }

        return sum;
    }

    /**
     * Wikipedia <a href="https://en.wikipedia.org/wiki/Tar_(file_format)#File_header">says</a>:
     * <blockquote>
     * The checksum is calculated by taking the sum of the unsigned byte values
     * of the header block with the eight checksum bytes taken to be ascii
     * spaces (decimal value 32). It is stored as a six digit octal number with
     * leading zeroes followed by a NUL and then a space. Various
     * implementations do not adhere to this format. For better compatibility,
     * ignore leading and trailing whitespace, and get the first six digits. In
     * addition, some historic tar implementations treated bytes as signed.
     * Implementations typically calculate the checksum both ways, and treat it
     * as good if either the signed or unsigned sum matches the included
     * checksum.
     * </blockquote>
     * <p>
     * The return value of this method should be treated as a best-effort
     * heuristic rather than an absolute and final truth. The checksum
     * verification logic may well evolve over time as more special cases
     * are encountered.
     *
     * @param header tar header
     * @return whether the checksum is reasonably good
     * @see <a href="https://issues.apache.org/jira/browse/COMPRESS-191">COMPRESS-191</a>
     * @since 1.5
     */
    public static boolean verifyCheckSum(final byte[] header) {
        final long storedSum = parseOctal(header, CHKSUM_OFFSET, CHKSUMLEN);
        long unsignedSum = 0;
        long signedSum = 0;

        for (int i = 0; i < header.length; i++) {
            byte b = header[i];
            if (CHKSUM_OFFSET  <= i && i < CHKSUM_OFFSET + CHKSUMLEN) {
                b = ' ';
            }
            unsignedSum += 0xff & b;
            signedSum += b;
        }
        return storedSum == unsignedSum || storedSum == signedSum;
    }

    /**
     * For PAX Format 0.0, the sparse headers(GNU.sparse.offset and GNU.sparse.numbytes)
     * may appear multi times, and they look like:
     *
     * GNU.sparse.size=size
     * GNU.sparse.numblocks=numblocks
     * repeat numblocks times
     *   GNU.sparse.offset=offset
     *   GNU.sparse.numbytes=numbytes
     * end repeat
     *
     * For PAX Format 0.1, the sparse headers are stored in a single variable : GNU.sparse.map
     *
     * GNU.sparse.map
     *    Map of non-null data chunks. It is a string consisting of comma-separated values "offset,size[,offset-1,size-1...]"
     *
     * @param inputStream inputstream to read keys and values
     * @param sparseHeaders used in PAX Format 0.0 &amp; 0.1, as it may appear multi times,
     *                      the sparse headers need to be stored in an array, not a map
     * @param globalPaxHeaders global PAX headers of the tar archive
     * @return map of PAX headers values found inside of the current (local or global) PAX headers tar entry.
     * @throws IOException
     */
    protected static Map<String, String> parsePaxHeaders(final InputStream inputStream, final List<TarArchiveStructSparse> sparseHeaders, final Map<String, String> globalPaxHeaders)
            throws IOException {
        final Map<String, String> headers = new HashMap<>(globalPaxHeaders);
        Long offset = null;
        // Format is "length keyword=value\n";
        while(true) { // get length
            int ch;
            int len = 0;
            int read = 0;
            while((ch = inputStream.read()) != -1) {
                read++;
                if (ch == '\n') { // blank line in header
                    break;
                } else if (ch == ' '){ // End of length string
                    // Get keyword
                    final ByteArrayOutputStream coll = new ByteArrayOutputStream();
                    while((ch = inputStream.read()) != -1) {
                        read++;
                        if (ch == '='){ // end of keyword
                            final String keyword = coll.toString(CharsetNames.UTF_8);
                            // Get rest of entry
                            final int restLen = len - read;
                            if (restLen <= 1) { // only NL
                                headers.remove(keyword);
                            } else {
                                final byte[] rest = new byte[restLen];
                                final int got = IOUtils.readFully(inputStream, rest);
                                if (got != restLen) {
                                    throw new IOException("Failed to read "
                                            + "Paxheader. Expected "
                                            + restLen
                                            + " bytes, read "
                                            + got);
                                }
                                // Drop trailing NL
                                final String value = new String(rest, 0,
                                        restLen - 1, StandardCharsets.UTF_8);
                                headers.put(keyword, value);

                                // for 0.0 PAX Headers
                                if (keyword.equals("GNU.sparse.offset")) {
                                    if (offset != null) {
                                        // previous GNU.sparse.offset header but but no numBytes
                                        sparseHeaders.add(new TarArchiveStructSparse(offset, 0));
                                    }
                                    offset = Long.valueOf(value);
                                }

                                // for 0.0 PAX Headers
                                if (keyword.equals("GNU.sparse.numbytes")) {
                                    if (offset == null) {
                                        throw new IOException("Failed to read Paxheader." +
                                                "GNU.sparse.offset is expected before GNU.sparse.numbytes shows up.");
                                    }
                                    sparseHeaders.add(new TarArchiveStructSparse(offset, Long.parseLong(value)));
                                    offset = null;
                                }
                            }
                            break;
                        }
                        coll.write((byte) ch);
                    }
                    break; // Processed single header
                }

                // COMPRESS-530 : throw if we encounter a non-number while reading length
                if (ch < '0' || ch > '9') {
                    throw new IOException("Failed to read Paxheader. Encountered a non-number while reading length");
                }

                len *= 10;
                len += ch - '0';
            }
            if (ch == -1){ // EOF
                break;
            }
        }
        if (offset != null) {
            // offset but no numBytes
            sparseHeaders.add(new TarArchiveStructSparse(offset, 0));
        }
        return headers;
    }

    /**
     * For PAX Format 0.1, the sparse headers are stored in a single variable : GNU.sparse.map
     * GNU.sparse.map
     *    Map of non-null data chunks. It is a string consisting of comma-separated values "offset,size[,offset-1,size-1...]"
     *
     * @param sparseMap the sparse map string consisting of comma-separated values "offset,size[,offset-1,size-1...]"
     * @return sparse headers parsed from sparse map
     */
    protected static List<TarArchiveStructSparse> parsePAX01SparseHeaders(String sparseMap) {
        List<TarArchiveStructSparse> sparseHeaders = new ArrayList<>();
        String[] sparseHeaderStrings = sparseMap.split(",");

        for (int i = 0; i < sparseHeaderStrings.length; i += 2) {
            long sparseOffset = Long.parseLong(sparseHeaderStrings[i]);
            long sparseNumbytes = Long.parseLong(sparseHeaderStrings[i + 1]);
            sparseHeaders.add(new TarArchiveStructSparse(sparseOffset, sparseNumbytes));
        }

        return sparseHeaders;
    }

    /**
     * For PAX Format 1.X:
     * The sparse map itself is stored in the file data block, preceding the actual file data.
     * It consists of a series of decimal numbers delimited by newlines. The map is padded with nulls to the nearest block boundary.
     * The first number gives the number of entries in the map. Following are map entries, each one consisting of two numbers
     * giving the offset and size of the data block it describes.
     * @param inputStream
     * @param recordSize
     * @return sparse headers
     * @throws IOException
     */
    protected static List<TarArchiveStructSparse> parsePAX1XSparseHeaders(final InputStream inputStream, final int recordSize) throws IOException {
        // for 1.X PAX Headers
        List<TarArchiveStructSparse> sparseHeaders = new ArrayList<>();
        long bytesRead = 0;

        long[] readResult = readLineOfNumberForPax1X(inputStream);
        long sparseHeadersCount = readResult[0];
        bytesRead += readResult[1];
        while (sparseHeadersCount-- > 0) {
            readResult = readLineOfNumberForPax1X(inputStream);
            long sparseOffset = readResult[0];
            bytesRead += readResult[1];

            readResult = readLineOfNumberForPax1X(inputStream);
            long sparseNumbytes = readResult[0];
            bytesRead += readResult[1];
            sparseHeaders.add(new TarArchiveStructSparse(sparseOffset, sparseNumbytes));
        }

        // skip the rest of this record data
        long bytesToSkip = recordSize - bytesRead % recordSize;
        IOUtils.skip(inputStream, bytesToSkip);
        return sparseHeaders;
    }

    /**
     * For 1.X PAX Format, the sparse headers are stored in the file data block, preceding the actual file data.
     * It consists of a series of decimal numbers delimited by newlines.
     *
     * @param inputStream the input stream of the tar file
     * @return the decimal number delimited by '\n', and the bytes read from input stream
     * @throws IOException
     */
    private static long[] readLineOfNumberForPax1X(final InputStream inputStream) throws IOException {
        int number;
        long result = 0;
        long bytesRead = 0;

        while ((number = inputStream.read()) != '\n') {
            bytesRead += 1;
            if (number == -1) {
                throw new IOException("Unexpected EOF when reading parse information of 1.X PAX format");
            }
            result = result * 10 + (number - '0');
        }
        bytesRead += 1;

        return new long[]{result, bytesRead};
    }

}