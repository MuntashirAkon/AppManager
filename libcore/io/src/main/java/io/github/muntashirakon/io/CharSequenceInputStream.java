// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.Objects;

/**
 * Implements an {@link InputStream} to read from String, StringBuffer, StringBuilder or CharBuffer.
 * <p>
 * <strong>Note:</strong> Supports {@link #mark(int)} and {@link #reset()}.
 */
// Copyright 2012 Apache Software Foundation
public class CharSequenceInputStream extends InputStream {
    private static final int NO_MARK = -1;
    private static final int EOF = -1;

    private final ByteBuffer mByteBuffer;
    private int mByteBufferMark; // position in mByteBuffer
    private final CharBuffer mCharBuffer;
    private int mCharBufferMark; // position in mCharBuffer
    private final CharsetEncoder mCharsetEncoder;

    /**
     * Constructs a new instance with a buffer size of {@link IoUtils#DEFAULT_BUFFER_SIZE}.
     *
     * @param cs      the input character sequence.
     * @param charset the character set name to use.
     * @throws IllegalArgumentException if the buffer is not large enough to hold a complete character.
     */
    public CharSequenceInputStream(final CharSequence cs, final Charset charset) {
        this(cs, charset, IoUtils.DEFAULT_BUFFER_SIZE);
    }

    /**
     * Constructs a new instance.
     *
     * @param cs         the input character sequence.
     * @param charset    the character set name to use, null maps to the default Charset.
     * @param bufferSize the buffer size to use.
     * @throws IllegalArgumentException if the buffer is not large enough to hold a complete character.
     */
    public CharSequenceInputStream(final CharSequence cs, final Charset charset, final int bufferSize) {
        this(cs, bufferSize, charset.newEncoder());
    }

    private CharSequenceInputStream(final CharSequence cs, final int bufferSize, final CharsetEncoder charsetEncoder) {
        mCharsetEncoder = charsetEncoder;
        // Ensure that buffer is long enough to hold a complete character
        mByteBuffer = ByteBuffer.allocate(checkMinBufferSize(charsetEncoder, bufferSize));
        mByteBuffer.flip();
        mCharBuffer = CharBuffer.wrap(cs);
        mCharBufferMark = NO_MARK;
        mByteBufferMark = NO_MARK;
    }

    /**
     * Constructs a new instance with a buffer size of {@link IoUtils#DEFAULT_BUFFER_SIZE}.
     *
     * @param cs      the input character sequence.
     * @param charset the character set name to use.
     * @throws IllegalArgumentException if the buffer is not large enough to hold a complete character.
     */
    public CharSequenceInputStream(final CharSequence cs, final String charset) {
        this(cs, charset, IoUtils.DEFAULT_BUFFER_SIZE);
    }

    /**
     * Constructs a new instance.
     *
     * @param cs         the input character sequence.
     * @param charset    the character set name to use, null maps to the default Charset.
     * @param bufferSize the buffer size to use.
     * @throws IllegalArgumentException if the buffer is not large enough to hold a complete character.
     */
    public CharSequenceInputStream(final CharSequence cs, final String charset, final int bufferSize) {
        this(cs, Charset.forName(charset), bufferSize);
    }

    /**
     * Return an estimate of the number of bytes remaining in the byte stream.
     *
     * @return the count of bytes that can be read without blocking (or returning EOF).
     */
    @Override
    public int available() {
        // The cached entries are in bBuf; since encoding always creates at least one byte
        // per character, we can add the two to get a better estimate (e.g. if bBuf is empty)
        // Note that the implementation in 2.4 could return zero even though there were
        // encoded bytes still available.
        return mByteBuffer.remaining() + mCharBuffer.remaining();
    }

    @Override
    public void close() {
        // noop
    }

    /**
     * Fills the byte output buffer from the input char buffer.
     *
     * @throws CharacterCodingException an error encoding data.
     */
    private void fillBuffer() throws CharacterCodingException {
        mByteBuffer.compact();
        final CoderResult result = mCharsetEncoder.encode(mCharBuffer, mByteBuffer, true);
        if (result.isError()) {
            result.throwException();
        }
        mByteBuffer.flip();
    }

    /**
     * Gets the CharsetEncoder.
     *
     * @return the CharsetEncoder.
     */
    CharsetEncoder getCharsetEncoder() {
        return mCharsetEncoder;
    }

    /**
     * {@inheritDoc}
     *
     * @param readlimit max read limit (ignored).
     */
    @Override
    public synchronized void mark(final int readlimit) {
        mCharBufferMark = mCharBuffer.position();
        mByteBufferMark = mByteBuffer.position();
        mCharBuffer.mark();
        mByteBuffer.mark();
        // It would be nice to be able to use mark & reset on the cBuf and bBuf;
        // however the bBuf is re-used so that won't work
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public int read() throws IOException {
        for (; ; ) {
            if (mByteBuffer.hasRemaining()) {
                return mByteBuffer.get() & 0xFF;
            }
            fillBuffer();
            if (!mByteBuffer.hasRemaining() && !mCharBuffer.hasRemaining()) {
                return EOF;
            }
        }
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(final byte[] array, int off, int len) throws IOException {
        Objects.requireNonNull(array, "array");
        if (len < 0 || off + len > array.length) {
            throw new IndexOutOfBoundsException("Array Size=" + array.length + ", offset=" + off + ", length=" + len);
        }
        if (len == 0) {
            return 0; // must return 0 for zero length read
        }
        if (!mByteBuffer.hasRemaining() && !mCharBuffer.hasRemaining()) {
            return EOF;
        }
        int bytesRead = 0;
        while (len > 0) {
            if (mByteBuffer.hasRemaining()) {
                final int chunk = Math.min(mByteBuffer.remaining(), len);
                mByteBuffer.get(array, off, chunk);
                off += chunk;
                len -= chunk;
                bytesRead += chunk;
            } else {
                fillBuffer();
                if (!mByteBuffer.hasRemaining() && !mCharBuffer.hasRemaining()) {
                    break;
                }
            }
        }
        return bytesRead == 0 && !mCharBuffer.hasRemaining() ? EOF : bytesRead;
    }

    @Override
    public synchronized void reset() throws IOException {
        //
        // This is not the most efficient implementation, as it re-encodes from the beginning.
        //
        // Since the bBuf is re-used, in general it's necessary to re-encode the data.
        //
        // It should be possible to apply some optimizations however:
        // + use mark/reset on the cBuf and bBuf. This would only work if the buffer had not been (re)filled since
        // the mark. The code would have to catch InvalidMarkException - does not seem possible to check if mark is
        // valid otherwise. + Try saving the state of the cBuf before each fillBuffer; it might be possible to
        // restart from there.
        //
        if (mCharBufferMark != NO_MARK) {
            // if cBuf is at 0, we have not started reading anything, so skip re-encoding
            if (mCharBuffer.position() != 0) {
                mCharsetEncoder.reset();
                mCharBuffer.rewind();
                mByteBuffer.rewind();
                mByteBuffer.limit(0); // rewind does not clear the buffer
                while (mCharBuffer.position() < mCharBufferMark) {
                    mByteBuffer.rewind(); // empty the buffer (we only refill when empty during normal processing)
                    mByteBuffer.limit(0);
                    fillBuffer();
                }
            }
            if (mCharBuffer.position() != mCharBufferMark) {
                throw new IllegalStateException("Unexpected CharBuffer position: actual=" + mCharBuffer.position() + " " +
                        "expected=" + mCharBufferMark);
            }
            mByteBuffer.position(mByteBufferMark);
            mCharBufferMark = NO_MARK;
            mByteBufferMark = NO_MARK;
        }
    }

    @Override
    public long skip(long n) throws IOException {
        //
        // This could be made more efficient by using position to skip within the current buffer.
        //
        long skipped = 0;
        while (n > 0 && available() > 0) {
            read();
            n--;
            skipped++;
        }
        return skipped;
    }

    private static int checkMinBufferSize(final CharsetEncoder charsetEncoder, final int bufferSize) {
        final float minRequired = minBufferSize(charsetEncoder);
        if (bufferSize < minRequired) {
            throw new IllegalArgumentException(String.format("Buffer size %,d must be at least %s for a CharsetEncoder %s.", bufferSize, minRequired,
                    charsetEncoder.charset().displayName()));
        }
        return bufferSize;
    }

    private static float minBufferSize(final CharsetEncoder charsetEncoder) {
        return charsetEncoder.maxBytesPerChar() * 2;
    }
}
