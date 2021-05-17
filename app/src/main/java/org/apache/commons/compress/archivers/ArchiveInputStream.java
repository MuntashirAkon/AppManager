// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.archivers;

import java.io.IOException;
import java.io.InputStream;

/**
 * Archive input streams <b>MUST</b> override the
 * {@link #read(byte[], int, int)} - or {@link #read()} -
 * method so that reading from the stream generates EOF for the end of
 * data in each entry as well as at the end of the file proper.
 * <p>
 * The {@link #getNextEntry()} method is used to reset the input stream
 * ready for reading the data from the next entry.
 * <p>
 * The input stream classes must also implement a method with the signature:
 * <pre>
 * public static boolean matches(byte[] signature, int length)
 * </pre>
 * which is used by the {@link ArchiveStreamFactory} to autodetect
 * the archive type from the first few bytes of a stream.
 */
// Copyright 2008 Torsten Curdt
public abstract class ArchiveInputStream extends InputStream {

    private final byte[] single = new byte[1];
    private static final int BYTE_MASK = 0xFF;

    /** holds the number of bytes read in this stream */
    private long bytesRead = 0;

    /**
     * Returns the next Archive Entry in this Stream.
     *
     * @return the next entry,
     *         or {@code null} if there are no more entries
     * @throws IOException if the next entry could not be read
     */
    public abstract ArchiveEntry getNextEntry() throws IOException;

    /*
     * Note that subclasses also implement specific get() methods which
     * return the appropriate class without need for a cast.
     * See SVN revision r743259
     * @return
     * @throws IOException
     */
    // public abstract XXXArchiveEntry getNextXXXEntry() throws IOException;

    /**
     * Reads a byte of data. This method will block until enough input is
     * available.
     *
     * Simply calls the {@link #read(byte[], int, int)} method.
     *
     * MUST be overridden if the {@link #read(byte[], int, int)} method
     * is not overridden; may be overridden otherwise.
     *
     * @return the byte read, or -1 if end of input is reached
     * @throws IOException
     *             if an I/O error has occurred
     */
    @Override
    public int read() throws IOException {
        final int num = read(single, 0, 1);
        return num == -1 ? -1 : single[0] & BYTE_MASK;
    }

    /**
     * Increments the counter of already read bytes.
     * Doesn't increment if the EOF has been hit (read == -1)
     *
     * @param read the number of bytes read
     */
    protected void count(final int read) {
        count((long) read);
    }

    /**
     * Increments the counter of already read bytes.
     * Doesn't increment if the EOF has been hit (read == -1)
     *
     * @param read the number of bytes read
     * @since 1.1
     */
    protected void count(final long read) {
        if (read != -1) {
            bytesRead = bytesRead + read;
        }
    }

    /**
     * Decrements the counter of already read bytes.
     *
     * @param pushedBack the number of bytes pushed back.
     * @since 1.1
     */
    protected void pushedBackBytes(final long pushedBack) {
        bytesRead -= pushedBack;
    }

    /**
     * Returns the current number of bytes read from this stream.
     * @return the number of read bytes
     * @deprecated this method may yield wrong results for large
     * archives, use #getBytesRead instead
     */
    @Deprecated
    public int getCount() {
        return (int) bytesRead;
    }

    /**
     * Returns the current number of bytes read from this stream.
     * @return the number of read bytes
     * @since 1.1
     */
    public long getBytesRead() {
        return bytesRead;
    }

    /**
     * Whether this stream is able to read the given entry.
     *
     * <p>
     * Some archive formats support variants or details that are not supported (yet).
     * </p>
     *
     * @param archiveEntry
     *            the entry to test
     * @return This implementation always returns true.
     *
     * @since 1.1
     */
    public boolean canReadEntryData(final ArchiveEntry archiveEntry) {
        return true;
    }

}