// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.archivers;

import android.system.ErrnoException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.LinkOption;

/**
 * Archive output stream implementations are expected to override the
 * {@link #write(byte[], int, int)} method to improve performance.
 * They should also override {@link #close()} to ensure that any necessary
 * trailers are added.
 *
 * <p>The normal sequence of calls when working with ArchiveOutputStreams is:</p>
 * <ul>
 *   <li>Create ArchiveOutputStream object,</li>
 *   <li>optionally write SFX header (Zip only),</li>
 *   <li>repeat as needed:
 *     <ul>
 *       <li>{@link #putArchiveEntry(ArchiveEntry)} (writes entry header),
 *       <li>{@link #write(byte[])} (writes entry data, as often as needed),
 *       <li>{@link #closeArchiveEntry()} (closes entry),
 *     </ul>
 *   </li>
 *   <li> {@link #finish()} (ends the addition of entries),</li>
 *   <li> optionally write additional data, provided format supports it,</li>
 *   <li>{@link #close()}.</li>
 * </ul>
 */
// Copyright 2008 Torsten Curdt
public abstract class ArchiveOutputStream extends OutputStream {

    /** Temporary buffer used for the {@link #write(int)} method */
    private final byte[] oneByte = new byte[1];
    static final int BYTE_MASK = 0xFF;

    /** holds the number of bytes written to this stream */
    private long bytesWritten = 0;
    // Methods specific to ArchiveOutputStream

    /**
     * Writes the headers for an archive entry to the output stream.
     * The caller must then write the content to the stream and call
     * {@link #closeArchiveEntry()} to complete the process.
     *
     * @param entry describes the entry
     * @throws IOException if an I/O error occurs
     */
    public abstract void putArchiveEntry(ArchiveEntry entry) throws IOException;

    /**
     * Closes the archive entry, writing any trailer information that may
     * be required.
     * @throws IOException if an I/O error occurs
     */
    public abstract void closeArchiveEntry() throws IOException;

    /**
     * Finishes the addition of entries to this stream, without closing it.
     * Additional data can be written, if the format supports it.
     *
     * @throws IOException if the user forgets to close the entry.
     */
    public abstract void finish() throws IOException;

    /**
     * Create an archive entry using the inputFile and entryName provided.
     *
     * @param inputFile the file to create the entry from
     * @param entryName name to use for the entry
     * @return the ArchiveEntry set up with details from the file
     *
     * @throws IOException if an I/O error occurs
     */
    public abstract ArchiveEntry createArchiveEntry(File inputFile, String entryName) throws IOException;

    /**
     * Create an archive entry using the inputPath and entryName provided.
     *
     * The default implementation calls simply delegates as:
     * <pre>return createArchiveEntry(inputFile.toFile(), entryName);</pre>
     *
     * Subclasses should override this method.
     *
     * @param inputPath the file to create the entry from
     * @param entryName name to use for the entry
     * @param options options indicating how symbolic links are handled.
     * @return the ArchiveEntry set up with details from the file
     *
     * @throws IOException if an I/O error occurs
     * @since 1.21
     */
    public ArchiveEntry createArchiveEntry(final File inputPath, final String entryName, final LinkOption... options) throws IOException, ErrnoException {
        return createArchiveEntry(inputPath, entryName);
    }

    // Generic implementations of OutputStream methods that may be useful to sub-classes

    /**
     * Writes a byte to the current archive entry.
     *
     * <p>This method simply calls {@code write( byte[], 0, 1 )}.
     *
     * <p>MUST be overridden if the {@link #write(byte[], int, int)} method
     * is not overridden; may be overridden otherwise.
     *
     * @param b The byte to be written.
     * @throws IOException on error
     */
    @Override
    public void write(final int b) throws IOException {
        oneByte[0] = (byte) (b & BYTE_MASK);
        write(oneByte, 0, 1);
    }

    /**
     * Increments the counter of already written bytes.
     * Doesn't increment if EOF has been hit ({@code written == -1}).
     *
     * @param written the number of bytes written
     */
    protected void count(final int written) {
        count((long) written);
    }

    /**
     * Increments the counter of already written bytes.
     * Doesn't increment if EOF has been hit ({@code written == -1}).
     *
     * @param written the number of bytes written
     * @since 1.1
     */
    protected void count(final long written) {
        if (written != -1) {
            bytesWritten = bytesWritten + written;
        }
    }

    /**
     * Returns the current number of bytes written to this stream.
     * @return the number of written bytes
     * @deprecated this method may yield wrong results for large
     * archives, use #getBytesWritten instead
     */
    @Deprecated
    public int getCount() {
        return (int) bytesWritten;
    }

    /**
     * Returns the current number of bytes written to this stream.
     * @return the number of written bytes
     * @since 1.1
     */
    public long getBytesWritten() {
        return bytesWritten;
    }

    /**
     * Whether this stream is able to write the given entry.
     *
     * <p>Some archive formats support variants or details that are
     * not supported (yet).</p>
     *
     * @param archiveEntry
     *            the entry to test
     * @return This implementation always returns true.
     * @since 1.1
     */
    public boolean canWriteEntryData(final ArchiveEntry archiveEntry) {
        return true;
    }
}