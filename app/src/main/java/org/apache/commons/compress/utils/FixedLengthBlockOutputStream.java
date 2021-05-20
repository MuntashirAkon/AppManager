// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.utils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.WritableByteChannel;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class supports writing to an OutputStream or WritableByteChannel in fixed length blocks.
 * <p>It can be be used to support output to devices such as tape drives that require output in this
 * format. If the final block does not have enough content to fill an entire block, the output will
 * be padded to a full block size.</p>
 *
 * <p>This class can be used to support TAR,PAX, and CPIO blocked output to character special devices.
 * It is not recommended that this class be used unless writing to such devices, as the padding
 * serves no useful purpose in such cases.</p>
 *
 * <p>This class should normally wrap a FileOutputStream or associated WritableByteChannel directly.
 * If there is an intervening filter that modified the output, such as a CompressorOutputStream, or
 * performs its own buffering, such as BufferedOutputStream,  output to the device may
 * no longer be of the specified size.</p>
 *
 * <p>Any content written to this stream should be self-delimiting and should tolerate any padding
 * added to fill the last block.</p>
 *
 * @since 1.15
 */
// Copyright 2017 Simon Spero
public class FixedLengthBlockOutputStream extends OutputStream implements WritableByteChannel {

    private final WritableByteChannel out;
    private final int blockSize;
    private final ByteBuffer buffer;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Create a fixed length block output stream with given destination stream and block size
     * @param os   The stream to wrap.
     * @param blockSize The block size to use.
     */
    public FixedLengthBlockOutputStream(final OutputStream os, final int blockSize) {
        if (os instanceof FileOutputStream) {
            final FileOutputStream fileOutputStream = (FileOutputStream) os;
            out = fileOutputStream.getChannel();
            buffer = ByteBuffer.allocateDirect(blockSize);
        } else {
            out = new BufferAtATimeOutputChannel(os);
            buffer = ByteBuffer.allocate(blockSize);
        }
        this.blockSize = blockSize;
    }

    /**
     * Create a fixed length block output stream with given destination writable byte channel and block size
     * @param out   The writable byte channel to wrap.
     * @param blockSize The block size to use.
     */
    public FixedLengthBlockOutputStream(final WritableByteChannel out, final int blockSize) {
        this.out = out;
        this.blockSize = blockSize;
        this.buffer = ByteBuffer.allocateDirect(blockSize);
    }

    private void maybeFlush() throws IOException {
        if (!buffer.hasRemaining()) {
            writeBlock();
        }
    }

    private void writeBlock() throws IOException {
        buffer.flip();
        final int i = out.write(buffer);
        final boolean hasRemaining = buffer.hasRemaining();
        if (i != blockSize || hasRemaining) {
            final String msg = String.format(Locale.ROOT, "Failed to write %,d bytes atomically. Only wrote  %,d", blockSize, i);
            throw new IOException(msg);
        }
        buffer.clear();
    }

    @Override
    public void write(final int b) throws IOException {
        if (!isOpen()) {
            throw new ClosedChannelException();
        }
        buffer.put((byte) b);
        maybeFlush();
    }

    @Override
    public void write(final byte[] b, final int offset, final int length) throws IOException {
        if (!isOpen()) {
            throw new ClosedChannelException();
        }
        int off = offset;
        int len = length;
        while (len > 0) {
            final int n = Math.min(len, buffer.remaining());
            buffer.put(b, off, n);
            maybeFlush();
            len -= n;
            off += n;
        }
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        if (!isOpen()) {
            throw new ClosedChannelException();
        }
        final int srcRemaining = src.remaining();

        if (srcRemaining < buffer.remaining()) {
            // if don't have enough bytes in src to fill up a block we must buffer
            buffer.put(src);
        } else {
            int srcLeft = srcRemaining;
            final int savedLimit = src.limit();
            // If we're not at the start of buffer, we have some bytes already  buffered
            // fill up the reset of buffer and write the block.
            if (buffer.position() != 0) {
                final int n = buffer.remaining();
                src.limit(src.position() + n);
                buffer.put(src);
                writeBlock();
                srcLeft -= n;
            }
            // whilst we have enough bytes in src for complete blocks,
            // write them directly from src without copying them to buffer
            while (srcLeft >= blockSize) {
                src.limit(src.position() + blockSize);
                out.write(src);
                srcLeft -= blockSize;
            }
            // copy any remaining bytes into buffer
            src.limit(savedLimit);
            buffer.put(src);
        }
        return srcRemaining;
    }

    @Override
    public boolean isOpen() {
        if (!out.isOpen()) {
            closed.set(true);
        }
        return !closed.get();
    }

    /**
     * Potentially pads and then writes the current block to the underlying stream.
     * @throws IOException if writing fails
     */
    public void flushBlock() throws IOException {
        if (buffer.position() != 0) {
            padBlock();
            writeBlock();
        }
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            try {
                flushBlock();
            } finally {
                out.close();
            }
        }
    }

    private void padBlock() {
        buffer.order(ByteOrder.nativeOrder());
        int bytesToWrite = buffer.remaining();
        if (bytesToWrite > 8) {
            final int align = buffer.position() & 7;
            if (align != 0) {
                final int limit = 8 - align;
                for (int i = 0; i < limit; i++) {
                    buffer.put((byte) 0);
                }
                bytesToWrite -= limit;
            }

            while (bytesToWrite >= 8) {
                buffer.putLong(0L);
                bytesToWrite -= 8;
            }
        }
        while (buffer.hasRemaining()) {
            buffer.put((byte) 0);
        }
    }

    /**
     * Helper class to provide channel wrapper for arbitrary output stream that doesn't alter the
     * size of writes.  We can't use Channels.newChannel, because for non FileOutputStreams, it
     * breaks up writes into 8KB max chunks. Since the purpose of this class is to always write
     * complete blocks, we need to write a simple class to take care of it.
     */
    private static class BufferAtATimeOutputChannel implements WritableByteChannel {

        private final OutputStream out;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private BufferAtATimeOutputChannel(final OutputStream out) {
            this.out = out;
        }

        @Override
        public int write(final ByteBuffer buffer) throws IOException {
            if (!isOpen()) {
                throw new ClosedChannelException();
            }
            if (!buffer.hasArray()) {
                throw new IOException("Direct buffer somehow written to BufferAtATimeOutputChannel");
            }

            try {
                final int pos = buffer.position();
                final int len = buffer.limit() - pos;
                out.write(buffer.array(), buffer.arrayOffset() + pos, len);
                buffer.position(buffer.limit());
                return len;
            } catch (final IOException e) {
                try {
                    close();
                } catch (final IOException ignored) { //NOSONAR
                }
                throw e;
            }
        }

        @Override
        public boolean isOpen() {
            return !closed.get();
        }

        @Override
        public void close() throws IOException {
            if (closed.compareAndSet(false, true)) {
                out.close();
            }
        }

    }


}