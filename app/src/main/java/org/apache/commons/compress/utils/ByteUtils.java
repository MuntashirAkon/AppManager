// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.utils;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Utility methods for reading and writing bytes.
 * @since 1.14
 */
// Copyright 2017 Stefan Bodewig
public final class ByteUtils {

    /**
     * Empty array.
     *
     * @since 1.21
     */
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private ByteUtils() { /* no instances */ }

    /**
     * Used to supply bytes.
     * @since 1.14
     */
    public interface ByteSupplier {
        /**
         * The contract is similar to {@link InputStream#read()}, return
         * the byte as an unsigned int, -1 if there are no more bytes.
         * @return the supplied byte or -1 if there are no more bytes
         * @throws IOException if supplying fails
         */
        int getAsByte() throws IOException;
    }

    /**
     * Used to consume bytes.
     * @since 1.14
     */
    public interface ByteConsumer {
        /**
         * The contract is similar to {@link OutputStream#write(int)},
         * consume the lower eight bytes of the int as a byte.
         * @param b the byte to consume
         * @throws IOException if consuming fails
         */
        void accept(int b) throws IOException;
    }

    /**
     * Reads the given byte array as a little endian long.
     * @param bytes the byte array to convert
     * @return the number read
     */
    public static long fromLittleEndian(final byte[] bytes) {
        return fromLittleEndian(bytes, 0, bytes.length);
    }

    /**
     * Reads the given byte array as a little endian long.
     * @param bytes the byte array to convert
     * @param off the offset into the array that starts the value
     * @param length the number of bytes representing the value
     * @return the number read
     * @throws IllegalArgumentException if len is bigger than eight
     */
    public static long fromLittleEndian(final byte[] bytes, final int off, final int length) {
        checkReadLength(length);
        long l = 0;
        for (int i = 0; i < length; i++) {
            l |= (bytes[off + i] & 0xffL) << (8 * i);
        }
        return l;
    }

    /**
     * Reads the given number of bytes from the given stream as a little endian long.
     * @param in the stream to read from
     * @param length the number of bytes representing the value
     * @return the number read
     * @throws IllegalArgumentException if len is bigger than eight
     * @throws IOException if reading fails or the stream doesn't
     * contain the given number of bytes anymore
     */
    public static long fromLittleEndian(final InputStream in, final int length) throws IOException {
        // somewhat duplicates the ByteSupplier version in order to save the creation of a wrapper object
        checkReadLength(length);
        long l = 0;
        for (int i = 0; i < length; i++) {
            final long b = in.read();
            if (b == -1) {
                throw new IOException("Premature end of data");
            }
            l |= (b << (i * 8));
        }
        return l;
    }

    /**
     * Reads the given number of bytes from the given supplier as a little endian long.
     *
     * <p>Typically used by our InputStreams that need to count the
     * bytes read as well.</p>
     *
     * @param supplier the supplier for bytes
     * @param length the number of bytes representing the value
     * @return the number read
     * @throws IllegalArgumentException if len is bigger than eight
     * @throws IOException if the supplier fails or doesn't supply the
     * given number of bytes anymore
     */
    public static long fromLittleEndian(final ByteSupplier supplier, final int length) throws IOException {
        checkReadLength(length);
        long l = 0;
        for (int i = 0; i < length; i++) {
            final long b = supplier.getAsByte();
            if (b == -1) {
                throw new IOException("Premature end of data");
            }
            l |= (b << (i * 8));
        }
        return l;
    }

    /**
     * Reads the given number of bytes from the given input as little endian long.
     * @param in the input to read from
     * @param length the number of bytes representing the value
     * @return the number read
     * @throws IllegalArgumentException if len is bigger than eight
     * @throws IOException if reading fails or the stream doesn't
     * contain the given number of bytes anymore
     */
    public static long fromLittleEndian(final DataInput in, final int length) throws IOException {
        // somewhat duplicates the ByteSupplier version in order to save the creation of a wrapper object
        checkReadLength(length);
        long l = 0;
        for (int i = 0; i < length; i++) {
            final long b = in.readUnsignedByte();
            l |= (b << (i * 8));
        }
        return l;
    }

    /**
     * Inserts the given value into the array as a little endian
     * sequence of the given length starting at the given offset.
     * @param b the array to write into
     * @param value the value to insert
     * @param off the offset into the array that receives the first byte
     * @param length the number of bytes to use to represent the value
     */
    public static void toLittleEndian(final byte[] b, final long value, final int off, final int length) {
        long num = value;
        for (int i = 0; i < length; i++) {
            b[off + i] = (byte) (num & 0xff);
            num >>= 8;
        }
    }

    /**
     * Writes the given value to the given stream as a little endian
     * array of the given length.
     * @param out the stream to write to
     * @param value the value to write
     * @param length the number of bytes to use to represent the value
     * @throws IOException if writing fails
     */
    public static void toLittleEndian(final OutputStream out, final long value, final int length)
            throws IOException {
        // somewhat duplicates the ByteConsumer version in order to save the creation of a wrapper object
        long num = value;
        for (int i = 0; i < length; i++) {
            out.write((int) (num & 0xff));
            num >>= 8;
        }
    }

    /**
     * Provides the given value to the given consumer as a little endian
     * sequence of the given length.
     * @param consumer the consumer to provide the bytes to
     * @param value the value to provide
     * @param length the number of bytes to use to represent the value
     * @throws IOException if writing fails
     */
    public static void toLittleEndian(final ByteConsumer consumer, final long value, final int length)
            throws IOException {
        long num = value;
        for (int i = 0; i < length; i++) {
            consumer.accept((int) (num & 0xff));
            num >>= 8;
        }
    }

    /**
     * Writes the given value to the given stream as a little endian
     * array of the given length.
     * @param out the output to write to
     * @param value the value to write
     * @param length the number of bytes to use to represent the value
     * @throws IOException if writing fails
     */
    public static void toLittleEndian(final DataOutput out, final long value, final int length)
            throws IOException {
        // somewhat duplicates the ByteConsumer version in order to save the creation of a wrapper object
        long num = value;
        for (int i = 0; i < length; i++) {
            out.write((int) (num & 0xff));
            num >>= 8;
        }
    }

    /**
     * {@link ByteSupplier} based on {@link InputStream}.
     * @since 1.14
     */
    public static class InputStreamByteSupplier implements ByteSupplier {
        private final InputStream is;
        public InputStreamByteSupplier(final InputStream is) {
            this.is = is;
        }
        @Override
        public int getAsByte() throws IOException {
            return is.read();
        }
    }

    /**
     * {@link ByteConsumer} based on {@link OutputStream}.
     * @since 1.14
     */
    public static class OutputStreamByteConsumer implements ByteConsumer {
        private final OutputStream os;
        public OutputStreamByteConsumer(final OutputStream os) {
            this.os = os;
        }
        @Override
        public void accept(final int b) throws IOException {
            os.write(b);
        }
    }

    private static void checkReadLength(final int length) {
        if (length > 8) {
            throw new IllegalArgumentException("Can't read more than eight bytes into a long value");
        }
    }
}