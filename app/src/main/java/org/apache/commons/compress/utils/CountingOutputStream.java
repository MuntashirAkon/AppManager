// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.utils;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Stream that tracks the number of bytes read.
 * @since 1.3
 * @NotThreadSafe
 */
// Copyright 2011 Stefan Bodewig
public class CountingOutputStream extends FilterOutputStream {
    private long bytesWritten = 0;

    public CountingOutputStream(final OutputStream out) {
        super(out);
    }

    @Override
    public void write(final int b) throws IOException {
        out.write(b);
        count(1);
    }
    @Override
    public void write(final byte[] b) throws IOException {
        write(b, 0, b.length);
    }
    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        out.write(b, off, len);
        count(len);
    }

    /**
     * Increments the counter of already written bytes.
     * Doesn't increment if the EOF has been hit (written == -1)
     *
     * @param written the number of bytes written
     */
    protected void count(final long written) {
        if (written != -1) {
            bytesWritten += written;
        }
    }

    /**
     * Returns the current number of bytes written to this stream.
     * @return the number of written bytes
     */
    public long getBytesWritten() {
        return bytesWritten;
    }
}