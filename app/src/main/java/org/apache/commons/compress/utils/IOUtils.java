// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.utils;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * Utility functions
 *
 * @Immutable (has mutable data but it is write - only)
 */
// Copyright 2008 Torsten Curdt
public final class IOUtils {

    private static final int COPY_BUF_SIZE = 8024;
    private static final int SKIP_BUF_SIZE = 4096;

    // This buffer does not need to be synchronized because it is write only; the contents are ignored
    // Does not affect Immutability
    private static final byte[] SKIP_BUF = new byte[SKIP_BUF_SIZE];

    /** Private constructor to prevent instantiation of this utility class. */
    private IOUtils(){
    }

    /**
     * Copies the content of a InputStream into an OutputStream.
     * Uses a default buffer size of 8024 bytes.
     *
     * @param input
     *            the InputStream to copy
     * @param output
     *            the target Stream
     * @return the number of bytes copied
     * @throws IOException
     *             if an error occurs
     */
    public static long copy(final InputStream input, final OutputStream output) throws IOException {
        return copy(input, output, COPY_BUF_SIZE);
    }

    /**
     * Copies the content of a InputStream into an OutputStream
     *
     * @param input
     *            the InputStream to copy
     * @param output
     *            the target Stream
     * @param buffersize
     *            the buffer size to use, must be bigger than 0
     * @return the number of bytes copied
     * @throws IOException
     *             if an error occurs
     * @throws IllegalArgumentException
     *             if buffersize is smaller than or equal to 0
     */
    public static long copy(final InputStream input, final OutputStream output, final int buffersize) throws IOException {
        if (buffersize < 1) {
            throw new IllegalArgumentException("buffersize must be bigger than 0");
        }
        final byte[] buffer = new byte[buffersize];
        int n = 0;
        long count=0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    /**
     * Skips the given number of bytes by repeatedly invoking skip on
     * the given input stream if necessary.
     *
     * <p>In a case where the stream's skip() method returns 0 before
     * the requested number of bytes has been skip this implementation
     * will fall back to using the read() method.</p>
     *
     * <p>This method will only skip less than the requested number of
     * bytes if the end of the input stream has been reached.</p>
     *
     * @param input stream to skip bytes in
     * @param numToSkip the number of bytes to skip
     * @return the number of bytes actually skipped
     * @throws IOException on error
     */
    public static long skip(final InputStream input, long numToSkip) throws IOException {
        final long available = numToSkip;
        while (numToSkip > 0) {
            final long skipped = input.skip(numToSkip);
            if (skipped == 0) {
                break;
            }
            numToSkip -= skipped;
        }

        while (numToSkip > 0) {
            final int read = readFully(input, SKIP_BUF, 0,
                                 (int) Math.min(numToSkip, SKIP_BUF_SIZE));
            if (read < 1) {
                break;
            }
            numToSkip -= read;
        }
        return available - numToSkip;
    }

    /**
     * Reads as much from input as possible to fill the given array.
     *
     * <p>This method may invoke read repeatedly to fill the array and
     * only read less bytes than the length of the array if the end of
     * the stream has been reached.</p>
     *
     * @param input stream to read from
     * @param array buffer to fill
     * @return the number of bytes actually read
     * @throws IOException on error
     */
    public static int readFully(final InputStream input, final byte[] array) throws IOException {
        return readFully(input, array, 0, array.length);
    }

    /**
     * Reads as much from input as possible to fill the given array
     * with the given amount of bytes.
     *
     * <p>This method may invoke read repeatedly to read the bytes and
     * only read less bytes than the requested length if the end of
     * the stream has been reached.</p>
     *
     * @param input stream to read from
     * @param array buffer to fill
     * @param offset offset into the buffer to start filling at
     * @param len of bytes to read
     * @return the number of bytes actually read
     * @throws IOException
     *             if an I/O error has occurred
     */
    public static int readFully(final InputStream input, final byte[] array, final int offset, final int len)
        throws IOException {
        if (len < 0 || offset < 0 || len + offset > array.length) {
            throw new IndexOutOfBoundsException();
        }
        int count = 0, x = 0;
        while (count != len) {
            x = input.read(array, offset + count, len - count);
            if (x == -1) {
                break;
            }
            count += x;
        }
        return count;
    }

    /**
     * Reads {@code b.remaining()} bytes from the given channel
     * starting at the current channel's position.
     *
     * <p>This method reads repeatedly from the channel until the
     * requested number of bytes are read. This method blocks until
     * the requested number of bytes are read, the end of the channel
     * is detected, or an exception is thrown.</p>
     *
     * @param channel the channel to read from
     * @param b the buffer into which the data is read.
     * @throws IOException - if an I/O error occurs.
     * @throws EOFException - if the channel reaches the end before reading all the bytes.
     */
    public static void readFully(final ReadableByteChannel channel, final ByteBuffer b) throws IOException {
        final int expectedLength = b.remaining();
        int read = 0;
        while (read < expectedLength) {
            final int readNow = channel.read(b);
            if (readNow <= 0) {
                break;
            }
            read += readNow;
        }
        if (read < expectedLength) {
            throw new EOFException();
        }
    }

    // toByteArray(InputStream) copied from:
    // commons/proper/io/trunk/src/main/java/org/apache/commons/io/IOUtils.java?revision=1428941
    // January 8th, 2013
    //
    // Assuming our copy() works just as well as theirs!  :-)

    /**
     * Gets the contents of an <code>InputStream</code> as a <code>byte[]</code>.
     * <p>
     * This method buffers the input internally, so there is no need to use a
     * <code>BufferedInputStream</code>.
     *
     * @param input  the <code>InputStream</code> to read from
     * @return the requested byte array
     * @throws NullPointerException if the input is null
     * @throws IOException if an I/O error occurs
     * @since 1.5
     */
    public static byte[] toByteArray(final InputStream input) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }

    /**
     * Closes the given Closeable and swallows any IOException that may occur.
     * @param c Closeable to close, can be null
     * @since 1.7
     */
    public static void closeQuietly(final Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (final IOException ignored) { // NOPMD NOSONAR
            }
        }
    }
}