// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.utils;

import java.io.IOException;
import java.io.InputStream;

/**
 * A stream that limits reading from a wrapped stream to a given number of bytes.
 * @NotThreadSafe
 * @since 1.6
 */
// Copyright 2013 Damjan Jovanovic
public class BoundedInputStream extends InputStream {
    private final InputStream in;
    private long bytesRemaining;

    /**
     * Creates the stream that will at most read the given amount of
     * bytes from the given stream.
     * @param in the stream to read from
     * @param size the maximum amount of bytes to read
     */
    public BoundedInputStream(final InputStream in, final long size) {
        this.in = in;
        bytesRemaining = size;
    }

    @Override
    public int read() throws IOException {
        if (bytesRemaining > 0) {
            --bytesRemaining;
            return in.read();
        }
        return -1;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        if (bytesRemaining == 0) {
            return -1;
        }
        int bytesToRead = len;
        if (bytesToRead > bytesRemaining) {
            bytesToRead = (int) bytesRemaining;
        }
        final int bytesRead = in.read(b, off, bytesToRead);
        if (bytesRead >= 0) {
            bytesRemaining -= bytesRead;
        }
        return bytesRead;
    }

    @Override
    public void close() {
        // there isn't anything to close in this stream and the nested
        // stream is controlled externally
    }

    /**
     * @since 1.20
     */
    @Override
    public long skip(final long n) throws IOException {
        final long bytesToSkip = Math.min(bytesRemaining, n);
        final long bytesSkipped = in.skip(bytesToSkip);
        bytesRemaining -= bytesSkipped;

        return bytesSkipped;
    }

    /**
     * @return bytes remaining to read
     * @since 1.21
     */
    public long getBytesRemaining() {
        return bytesRemaining;
    }
}