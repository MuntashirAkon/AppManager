// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.utils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;

/**
 * Reads bits from an InputStream.
 * @since 1.10
 * @NotThreadSafe
 */
// Copyright 2014 Damjan Jovanovic
public class BitInputStream implements Closeable {
    private static final int MAXIMUM_CACHE_SIZE = 63; // bits in long minus sign bit
    private static final long[] MASKS = new long[MAXIMUM_CACHE_SIZE + 1];

    static {
        for (int i = 1; i <= MAXIMUM_CACHE_SIZE; i++) {
            MASKS[i] = (MASKS[i - 1] << 1) + 1;
        }
    }

    private final CountingInputStream in;
    private final ByteOrder byteOrder;
    private long bitsCached = 0;
    private int bitsCachedSize = 0;

    /**
     * Constructor taking an InputStream and its bit arrangement.
     * @param in the InputStream
     * @param byteOrder the bit arrangement across byte boundaries,
     *      either BIG_ENDIAN (aaaaabbb bb000000) or LITTLE_ENDIAN (bbbaaaaa 000000bb)
     */
    public BitInputStream(final InputStream in, final ByteOrder byteOrder) {
        this.in = new CountingInputStream(in);
        this.byteOrder = byteOrder;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    /**
     * Clears the cache of bits that have been read from the
     * underlying stream but not yet provided via {@link #readBits}.
     */
    public void clearBitCache() {
        bitsCached = 0;
        bitsCachedSize = 0;
    }

    /**
     * Returns at most 63 bits read from the underlying stream.
     *
     * @param count the number of bits to read, must be a positive
     * number not bigger than 63.
     * @return the bits concatenated as a long using the stream's byte order.
     *         -1 if the end of the underlying stream has been reached before reading
     *         the requested number of bits
     * @throws IOException on error
     */
    public long readBits(final int count) throws IOException {
        if (count < 0 || count > MAXIMUM_CACHE_SIZE) {
            throw new IOException("count must not be negative or greater than " + MAXIMUM_CACHE_SIZE);
        }
        if (ensureCache(count)) {
            return -1;
        }

        if (bitsCachedSize < count) {
            return processBitsGreater57(count);
        }
        return readCachedBits(count);
    }

    /**
     * Returns the number of bits that can be read from this input
     * stream without reading from the underlying input stream at all.
     * @return estimate of the number of bits that can be read without reading from the underlying stream
     * @since 1.16
     */
    public int bitsCached() {
        return bitsCachedSize;
    }

    /**
     * Returns an estimate of the number of bits that can be read from
     * this input stream without blocking by the next invocation of a
     * method for this input stream.
     * @throws IOException if the underlying stream throws one when calling available
     * @return estimate of the number of bits that can be read without blocking
     * @since 1.16
     */
    public long bitsAvailable() throws IOException {
        return bitsCachedSize + ((long) Byte.SIZE) * in.available();
    }

    /**
     * Drops bits until the next bits will be read from a byte boundary.
     * @since 1.16
     */
    public void alignWithByteBoundary() {
        final int toSkip = bitsCachedSize % Byte.SIZE;
        if (toSkip > 0) {
            readCachedBits(toSkip);
        }
    }

    /**
     * Returns the number of bytes read from the underlying stream.
     *
     * <p>This includes the bytes read to fill the current cache and
     * not read as bits so far.</p>
     * @return the number of bytes read from the underlying stream
     * @since 1.17
     */
    public long getBytesRead() {
        return in.getBytesRead();
    }

    private long processBitsGreater57(final int count) throws IOException {
        final long bitsOut;
        final int overflowBits;
        long overflow = 0L;

        // bitsCachedSize >= 57 and left-shifting it 8 bits would cause an overflow
        final int bitsToAddCount = count - bitsCachedSize;
        overflowBits = Byte.SIZE - bitsToAddCount;
        final long nextByte = in.read();
        if (nextByte < 0) {
            return nextByte;
        }
        if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
            final long bitsToAdd = nextByte & MASKS[bitsToAddCount];
            bitsCached |= (bitsToAdd << bitsCachedSize);
            overflow = (nextByte >>> bitsToAddCount) & MASKS[overflowBits];
        } else {
            bitsCached <<= bitsToAddCount;
            final long bitsToAdd = (nextByte >>> (overflowBits)) & MASKS[bitsToAddCount];
            bitsCached |= bitsToAdd;
            overflow = nextByte & MASKS[overflowBits];
        }
        bitsOut = bitsCached & MASKS[count];
        bitsCached = overflow;
        bitsCachedSize = overflowBits;
        return bitsOut;
    }

    private long readCachedBits(final int count) {
        final long bitsOut;
        if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
            bitsOut = (bitsCached & MASKS[count]);
            bitsCached >>>= count;
        } else {
            bitsOut = (bitsCached >> (bitsCachedSize - count)) & MASKS[count];
        }
        bitsCachedSize -= count;
        return bitsOut;
    }

    /**
     * Fills the cache up to 56 bits
     * @param count
     * @return return true, when EOF
     * @throws IOException
     */
    private boolean ensureCache(final int count) throws IOException {
        while (bitsCachedSize < count && bitsCachedSize < 57) {
            final long nextByte = in.read();
            if (nextByte < 0) {
                return true;
            }
            if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                bitsCached |= (nextByte << bitsCachedSize);
            } else {
                bitsCached <<= Byte.SIZE;
                bitsCached |= nextByte;
            }
            bitsCachedSize += Byte.SIZE;
        }
        return false;
    }

}