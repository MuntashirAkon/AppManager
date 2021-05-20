// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.compressors.bzip2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.utils.BitInputStream;
import org.apache.commons.compress.utils.CloseShieldFilterInputStream;
import org.apache.commons.compress.utils.InputStreamStatistics;

/**
 * An input stream that decompresses from the BZip2 format to be read as any other stream.
 *
 * @NotThreadSafe
 */
// Copyright 2008 Torsten Curdt
public class BZip2CompressorInputStream extends CompressorInputStream
        implements BZip2Constants, InputStreamStatistics {

    /**
     * Index of the last char in the block, so the block size == last + 1.
     */
    private int last;

    /**
     * Index in zptr[] of original string after sorting.
     */
    private int origPtr;

    /**
     * always: in the range 0 .. 9. The current block size is 100000 * this
     * number.
     */
    private int blockSize100k;

    private boolean blockRandomised;

    private final CRC crc = new CRC();

    private int nInUse;

    private BitInputStream bin;
    private final boolean decompressConcatenated;

    private static final int EOF = 0;
    private static final int START_BLOCK_STATE = 1;
    private static final int RAND_PART_A_STATE = 2;
    private static final int RAND_PART_B_STATE = 3;
    private static final int RAND_PART_C_STATE = 4;
    private static final int NO_RAND_PART_A_STATE = 5;
    private static final int NO_RAND_PART_B_STATE = 6;
    private static final int NO_RAND_PART_C_STATE = 7;

    private int currentState = START_BLOCK_STATE;

    private int storedBlockCRC, storedCombinedCRC;
    private int computedBlockCRC, computedCombinedCRC;

    // Variables used by setup* methods exclusively

    private int su_count;
    private int su_ch2;
    private int su_chPrev;
    private int su_i2;
    private int su_j2;
    private int su_rNToGo;
    private int su_rTPos;
    private int su_tPos;
    private char su_z;

    /**
     * All memory intensive stuff. This field is initialized by initBlock().
     */
    private BZip2CompressorInputStream.Data data;

    /**
     * Constructs a new BZip2CompressorInputStream which decompresses bytes
     * read from the specified stream. This doesn't support decompressing
     * concatenated .bz2 files.
     *
     * @param in the InputStream from which this object should be created
     * @throws IOException
     *             if the stream content is malformed or an I/O error occurs.
     * @throws NullPointerException
     *             if {@code in == null}
     */
    public BZip2CompressorInputStream(final InputStream in) throws IOException {
        this(in, false);
    }

    /**
     * Constructs a new BZip2CompressorInputStream which decompresses bytes
     * read from the specified stream.
     *
     * @param in the InputStream from which this object should be created
     * @param decompressConcatenated
     *                     if true, decompress until the end of the input;
     *                     if false, stop after the first .bz2 stream and
     *                     leave the input position to point to the next
     *                     byte after the .bz2 stream
     *
     * @throws IOException
     *             if {@code in == null}, the stream content is malformed, or an I/O error occurs.
     */
    public BZip2CompressorInputStream(final InputStream in, final boolean decompressConcatenated) throws IOException {
        this.bin = new BitInputStream(in == System.in ? new CloseShieldFilterInputStream(in) : in,
                ByteOrder.BIG_ENDIAN);
        this.decompressConcatenated = decompressConcatenated;

        init(true);
        initBlock();
    }

    @Override
    public int read() throws IOException {
        if (this.bin != null) {
            final int r = read0();
            count(r < 0 ? -1 : 1);
            return r;
        }
        throw new IOException("Stream closed");
    }

    /*
     * (non-Javadoc)
     *
     * @see java.io.InputStream#read(byte[], int, int)
     */
    @Override
    public int read(final byte[] dest, final int offs, final int len)
            throws IOException {
        if (offs < 0) {
            throw new IndexOutOfBoundsException("offs(" + offs + ") < 0.");
        }
        if (len < 0) {
            throw new IndexOutOfBoundsException("len(" + len + ") < 0.");
        }
        if (offs + len > dest.length) {
            throw new IndexOutOfBoundsException("offs(" + offs + ") + len("
                    + len + ") > dest.length(" + dest.length + ").");
        }
        if (this.bin == null) {
            throw new IOException("Stream closed");
        }
        if (len == 0) {
            return 0;
        }

        final int hi = offs + len;
        int destOffs = offs;
        int b;
        while (destOffs < hi && ((b = read0()) >= 0)) {
            dest[destOffs++] = (byte) b;
            count(1);
        }

        return (destOffs == offs) ? -1 : (destOffs - offs);
    }

    /**
     * @since 1.17
     */
    @Override
    public long getCompressedCount() {
        return bin.getBytesRead();
    }

    private void makeMaps() {
        final boolean[] inUse = this.data.inUse;
        final byte[] seqToUnseq = this.data.seqToUnseq;

        int nInUseShadow = 0;

        for (int i = 0; i < 256; i++) {
            if (inUse[i]) {
                seqToUnseq[nInUseShadow++] = (byte) i;
            }
        }

        this.nInUse = nInUseShadow;
    }

    private int read0() throws IOException {
        switch (currentState) {
            case EOF:
                return -1;

            case START_BLOCK_STATE:
                return setupBlock();

            case RAND_PART_A_STATE:
                throw new IllegalStateException();

            case RAND_PART_B_STATE:
                return setupRandPartB();

            case RAND_PART_C_STATE:
                return setupRandPartC();

            case NO_RAND_PART_A_STATE:
                throw new IllegalStateException();

            case NO_RAND_PART_B_STATE:
                return setupNoRandPartB();

            case NO_RAND_PART_C_STATE:
                return setupNoRandPartC();

            default:
                throw new IllegalStateException();
        }
    }

    private int readNextByte(final BitInputStream in) throws IOException {
        final long b = in.readBits(8);
        return (int) b;
    }

    private boolean init(final boolean isFirstStream) throws IOException {
        if (null == bin) {
            throw new IOException("No InputStream");
        }

        if (!isFirstStream) {
            bin.clearBitCache();
        }

        final int magic0 = readNextByte(this.bin);
        if (magic0 == -1 && !isFirstStream) {
            return false;
        }
        final int magic1 = readNextByte(this.bin);
        final int magic2 = readNextByte(this.bin);

        if (magic0 != 'B' || magic1 != 'Z' || magic2 != 'h') {
            throw new IOException(isFirstStream
                    ? "Stream is not in the BZip2 format"
                    : "Garbage after a valid BZip2 stream");
        }

        final int blockSize = readNextByte(this.bin);
        if ((blockSize < '1') || (blockSize > '9')) {
            throw new IOException("BZip2 block size is invalid");
        }

        this.blockSize100k = blockSize - '0';

        this.computedCombinedCRC = 0;

        return true;
    }

    private void initBlock() throws IOException {
        final BitInputStream bin = this.bin;
        char magic0;
        char magic1;
        char magic2;
        char magic3;
        char magic4;
        char magic5;

        while (true) {
            // Get the block magic bytes.
            magic0 = bsGetUByte(bin);
            magic1 = bsGetUByte(bin);
            magic2 = bsGetUByte(bin);
            magic3 = bsGetUByte(bin);
            magic4 = bsGetUByte(bin);
            magic5 = bsGetUByte(bin);

            // If isn't end of stream magic, break out of the loop.
            if (magic0 != 0x17 || magic1 != 0x72 || magic2 != 0x45
                    || magic3 != 0x38 || magic4 != 0x50 || magic5 != 0x90) {
                break;
            }

            // End of stream was reached. Check the combined CRC and
            // advance to the next .bz2 stream if decoding concatenated
            // streams.
            if (complete()) {
                return;
            }
        }

        if (magic0 != 0x31 || // '1'
                magic1 != 0x41 || // ')'
                magic2 != 0x59 || // 'Y'
                magic3 != 0x26 || // '&'
                magic4 != 0x53 || // 'S'
                magic5 != 0x59 // 'Y'
        ) {
            this.currentState = EOF;
            throw new IOException("Bad block header");
        }
        this.storedBlockCRC = bsGetInt(bin);
        this.blockRandomised = bsR(bin, 1) == 1;

        /*
         * Allocate data here instead in constructor, so we do not allocate
         * it if the input file is empty.
         */
        if (this.data == null) {
            this.data = new Data(this.blockSize100k);
        }

        // currBlockNo++;
        getAndMoveToFrontDecode();

        this.crc.initializeCRC();
        this.currentState = START_BLOCK_STATE;
    }

    private void endBlock() throws IOException {
        this.computedBlockCRC = this.crc.getFinalCRC();

        // A bad CRC is considered a fatal error.
        if (this.storedBlockCRC != this.computedBlockCRC) {
            // make next blocks readable without error
            // (repair feature, not yet documented, not tested)
            this.computedCombinedCRC = (this.storedCombinedCRC << 1)
                    | (this.storedCombinedCRC >>> 31);
            this.computedCombinedCRC ^= this.storedBlockCRC;

            throw new IOException("BZip2 CRC error");
        }

        this.computedCombinedCRC = (this.computedCombinedCRC << 1)
                | (this.computedCombinedCRC >>> 31);
        this.computedCombinedCRC ^= this.computedBlockCRC;
    }

    private boolean complete() throws IOException {
        this.storedCombinedCRC = bsGetInt(bin);
        this.currentState = EOF;
        this.data = null;

        if (this.storedCombinedCRC != this.computedCombinedCRC) {
            throw new IOException("BZip2 CRC error");
        }

        // Look for the next .bz2 stream if decompressing
        // concatenated files.
        return !decompressConcatenated || !init(false);
    }

    @Override
    public void close() throws IOException {
        final BitInputStream inShadow = this.bin;
        if (inShadow != null) {
            try {
                inShadow.close();
            } finally {
                this.data = null;
                this.bin = null;
            }
        }
    }

    /**
     * read bits from the input stream
     * @param n the number of bits to read, must not exceed 32?
     * @return the requested bits combined into an int
     * @throws IOException
     */
    private static int bsR(final BitInputStream bin, final int n) throws IOException {
        final long thech = bin.readBits(n);
        if (thech < 0) {
            throw new IOException("Unexpected end of stream");
        }
        return (int) thech;
    }

    private static boolean bsGetBit(final BitInputStream bin) throws IOException {
        return bsR(bin, 1) != 0;
    }

    private static char bsGetUByte(final BitInputStream bin) throws IOException {
        return (char) bsR(bin, 8);
    }

    private static int bsGetInt(final BitInputStream bin) throws IOException {
        return bsR(bin, 32);
    }

    private static void checkBounds(final int checkVal, final int limitExclusive, final String name)
            throws IOException {
        if (checkVal < 0) {
            throw new IOException("Corrupted input, " + name + " value negative");
        }
        if (checkVal >= limitExclusive) {
            throw new IOException("Corrupted input, " + name + " value too big");
        }
    }

    /**
     * Called by createHuffmanDecodingTables() exclusively.
     */
    private static void hbCreateDecodeTables(final int[] limit,
                                             final int[] base, final int[] perm, final char[] length,
                                             final int minLen, final int maxLen, final int alphaSize)
            throws IOException {
        for (int i = minLen, pp = 0; i <= maxLen; i++) {
            for (int j = 0; j < alphaSize; j++) {
                if (length[j] == i) {
                    perm[pp++] = j;
                }
            }
        }

        for (int i = MAX_CODE_LEN; --i > 0;) {
            base[i] = 0;
            limit[i] = 0;
        }

        for (int i = 0; i < alphaSize; i++) {
            final int l = length[i];
            checkBounds(l, MAX_ALPHA_SIZE, "length");
            base[l + 1]++;
        }

        for (int i = 1, b = base[0]; i < MAX_CODE_LEN; i++) {
            b += base[i];
            base[i] = b;
        }

        for (int i = minLen, vec = 0, b = base[i]; i <= maxLen; i++) {
            final int nb = base[i + 1];
            vec += nb - b;
            b = nb;
            limit[i] = vec - 1;
            vec <<= 1;
        }

        for (int i = minLen + 1; i <= maxLen; i++) {
            base[i] = ((limit[i - 1] + 1) << 1) - base[i];
        }
    }

    private void recvDecodingTables() throws IOException {
        final BitInputStream bin = this.bin;
        final Data dataShadow = this.data;
        final boolean[] inUse = dataShadow.inUse;
        final byte[] pos = dataShadow.recvDecodingTables_pos;
        final byte[] selector = dataShadow.selector;
        final byte[] selectorMtf = dataShadow.selectorMtf;

        int inUse16 = 0;

        /* Receive the mapping table */
        for (int i = 0; i < 16; i++) {
            if (bsGetBit(bin)) {
                inUse16 |= 1 << i;
            }
        }

        Arrays.fill(inUse, false);
        for (int i = 0; i < 16; i++) {
            if ((inUse16 & (1 << i)) != 0) {
                final int i16 = i << 4;
                for (int j = 0; j < 16; j++) {
                    if (bsGetBit(bin)) {
                        inUse[i16 + j] = true;
                    }
                }
            }
        }

        makeMaps();
        final int alphaSize = this.nInUse + 2;
        /* Now the selectors */
        final int nGroups = bsR(bin, 3);
        final int selectors = bsR(bin, 15);
        if (selectors < 0) {
            throw new IOException("Corrupted input, nSelectors value negative");
        }
        checkBounds(alphaSize, MAX_ALPHA_SIZE + 1, "alphaSize");
        checkBounds(nGroups, N_GROUPS + 1, "nGroups");

        // Don't fail on nSelectors overflowing boundaries but discard the values in overflow
        // See https://gnu.wildebeest.org/blog/mjw/2019/08/02/bzip2-and-the-cve-that-wasnt/
        // and https://sourceware.org/ml/bzip2-devel/2019-q3/msg00007.html

        for (int i = 0; i < selectors; i++) {
            int j = 0;
            while (bsGetBit(bin)) {
                j++;
            }
            if (i < MAX_SELECTORS) {
                selectorMtf[i] = (byte) j;
            }
        }
        final int nSelectors = selectors > MAX_SELECTORS ? MAX_SELECTORS : selectors;

        /* Undo the MTF values for the selectors. */
        for (int v = nGroups; --v >= 0;) {
            pos[v] = (byte) v;
        }

        for (int i = 0; i < nSelectors; i++) {
            int v = selectorMtf[i] & 0xff;
            checkBounds(v, N_GROUPS, "selectorMtf");
            final byte tmp = pos[v];
            while (v > 0) {
                // nearly all times v is zero, 4 in most other cases
                pos[v] = pos[v - 1];
                v--;
            }
            pos[0] = tmp;
            selector[i] = tmp;
        }

        final char[][] len = dataShadow.temp_charArray2d;

        /* Now the coding tables */
        for (int t = 0; t < nGroups; t++) {
            int curr = bsR(bin, 5);
            final char[] len_t = len[t];
            for (int i = 0; i < alphaSize; i++) {
                while (bsGetBit(bin)) {
                    curr += bsGetBit(bin) ? -1 : 1;
                }
                len_t[i] = (char) curr;
            }
        }

        // finally create the Huffman tables
        createHuffmanDecodingTables(alphaSize, nGroups);
    }

    /**
     * Called by recvDecodingTables() exclusively.
     */
    private void createHuffmanDecodingTables(final int alphaSize,
                                             final int nGroups) throws IOException {
        final Data dataShadow = this.data;
        final char[][] len = dataShadow.temp_charArray2d;
        final int[] minLens = dataShadow.minLens;
        final int[][] limit = dataShadow.limit;
        final int[][] base = dataShadow.base;
        final int[][] perm = dataShadow.perm;

        for (int t = 0; t < nGroups; t++) {
            int minLen = 32;
            int maxLen = 0;
            final char[] len_t = len[t];
            for (int i = alphaSize; --i >= 0;) {
                final char lent = len_t[i];
                if (lent > maxLen) {
                    maxLen = lent;
                }
                if (lent < minLen) {
                    minLen = lent;
                }
            }
            hbCreateDecodeTables(limit[t], base[t], perm[t], len[t], minLen,
                    maxLen, alphaSize);
            minLens[t] = minLen;
        }
    }

    private void getAndMoveToFrontDecode() throws IOException {
        final BitInputStream bin = this.bin;
        this.origPtr = bsR(bin, 24);
        recvDecodingTables();

        final Data dataShadow = this.data;
        final byte[] ll8 = dataShadow.ll8;
        final int[] unzftab = dataShadow.unzftab;
        final byte[] selector = dataShadow.selector;
        final byte[] seqToUnseq = dataShadow.seqToUnseq;
        final char[] yy = dataShadow.getAndMoveToFrontDecode_yy;
        final int[] minLens = dataShadow.minLens;
        final int[][] limit = dataShadow.limit;
        final int[][] base = dataShadow.base;
        final int[][] perm = dataShadow.perm;
        final int limitLast = this.blockSize100k * 100000;

        /*
         * Setting up the unzftab entries here is not strictly necessary, but it
         * does save having to do it later in a separate pass, and so saves a
         * block's worth of cache misses.
         */
        for (int i = 256; --i >= 0;) {
            yy[i] = (char) i;
            unzftab[i] = 0;
        }

        int groupNo = 0;
        int groupPos = G_SIZE - 1;
        final int eob = this.nInUse + 1;
        int nextSym = getAndMoveToFrontDecode0();
        int lastShadow = -1;
        int zt = selector[groupNo] & 0xff;
        checkBounds(zt, N_GROUPS, "zt");
        int[] base_zt = base[zt];
        int[] limit_zt = limit[zt];
        int[] perm_zt = perm[zt];
        int minLens_zt = minLens[zt];

        while (nextSym != eob) {
            if ((nextSym == RUNA) || (nextSym == RUNB)) {
                int s = -1;

                for (int n = 1; true; n <<= 1) {
                    if (nextSym == RUNA) {
                        s += n;
                    } else if (nextSym == RUNB) {
                        s += n << 1;
                    } else {
                        break;
                    }

                    if (groupPos == 0) {
                        groupPos = G_SIZE - 1;
                        checkBounds(++groupNo, MAX_SELECTORS, "groupNo");
                        zt = selector[groupNo] & 0xff;
                        checkBounds(zt, N_GROUPS, "zt");
                        base_zt = base[zt];
                        limit_zt = limit[zt];
                        perm_zt = perm[zt];
                        minLens_zt = minLens[zt];
                    } else {
                        groupPos--;
                    }

                    int zn = minLens_zt;
                    checkBounds(zn, MAX_ALPHA_SIZE, "zn");
                    int zvec = bsR(bin, zn);
                    while(zvec > limit_zt[zn]) {
                        checkBounds(++zn, MAX_ALPHA_SIZE, "zn");
                        zvec = (zvec << 1) | bsR(bin, 1);
                    }
                    final int tmp = zvec - base_zt[zn];
                    checkBounds(tmp, MAX_ALPHA_SIZE, "zvec");
                    nextSym = perm_zt[tmp];
                }
                checkBounds(s, this.data.ll8.length, "s");

                final int yy0 = yy[0];
                checkBounds(yy0, 256, "yy");
                final byte ch = seqToUnseq[yy0];
                unzftab[ch & 0xff] += s + 1;

                final int from = ++lastShadow;
                lastShadow += s;
                checkBounds(lastShadow, this.data.ll8.length, "lastShadow");
                Arrays.fill(ll8, from, lastShadow + 1, ch);

                if (lastShadow >= limitLast) {
                    throw new IOException("Block overrun while expanding RLE in MTF, "
                            + lastShadow + " exceeds " + limitLast);
                }
            } else {
                if (++lastShadow >= limitLast) {
                    throw new IOException("Block overrun in MTF, "
                            + lastShadow + " exceeds " + limitLast);
                }
                checkBounds(nextSym, 256 + 1, "nextSym");

                final char tmp = yy[nextSym - 1];
                checkBounds(tmp, 256, "yy");
                unzftab[seqToUnseq[tmp] & 0xff]++;
                ll8[lastShadow] = seqToUnseq[tmp];

                /*
                 * This loop is hammered during decompression, hence avoid
                 * native method call overhead of System.arraycopy for very
                 * small ranges to copy.
                 */
                if (nextSym <= 16) {
                    for (int j = nextSym - 1; j > 0;) {
                        yy[j] = yy[--j];
                    }
                } else {
                    System.arraycopy(yy, 0, yy, 1, nextSym - 1);
                }

                yy[0] = tmp;

                if (groupPos == 0) {
                    groupPos = G_SIZE - 1;
                    checkBounds(++groupNo, MAX_SELECTORS, "groupNo");
                    zt = selector[groupNo] & 0xff;
                    checkBounds(zt, N_GROUPS, "zt");
                    base_zt = base[zt];
                    limit_zt = limit[zt];
                    perm_zt = perm[zt];
                    minLens_zt = minLens[zt];
                } else {
                    groupPos--;
                }

                int zn = minLens_zt;
                checkBounds(zn, MAX_ALPHA_SIZE, "zn");
                int zvec = bsR(bin, zn);
                while(zvec > limit_zt[zn]) {
                    checkBounds(++zn, MAX_ALPHA_SIZE, "zn");
                    zvec = (zvec << 1) | bsR(bin, 1);
                }
                final int idx = zvec - base_zt[zn];
                checkBounds(idx, MAX_ALPHA_SIZE, "zvec");
                nextSym = perm_zt[idx];
            }
        }

        this.last = lastShadow;
    }

    private int getAndMoveToFrontDecode0() throws IOException {
        final Data dataShadow = this.data;
        final int zt = dataShadow.selector[0] & 0xff;
        checkBounds(zt, N_GROUPS, "zt");
        final int[] limit_zt = dataShadow.limit[zt];
        int zn = dataShadow.minLens[zt];
        checkBounds(zn, MAX_ALPHA_SIZE, "zn");
        int zvec = bsR(bin, zn);
        while (zvec > limit_zt[zn]) {
            checkBounds(++zn, MAX_ALPHA_SIZE, "zn");
            zvec = (zvec << 1) | bsR(bin, 1);
        }
        final int tmp = zvec - dataShadow.base[zt][zn];
        checkBounds(tmp, MAX_ALPHA_SIZE, "zvec");

        return dataShadow.perm[zt][tmp];
    }

    private int setupBlock() throws IOException {
        if (currentState == EOF || this.data == null) {
            return -1;
        }

        final int[] cftab = this.data.cftab;
        final int ttLen = this.last + 1;
        final int[] tt = this.data.initTT(ttLen);
        final byte[] ll8 = this.data.ll8;
        cftab[0] = 0;
        System.arraycopy(this.data.unzftab, 0, cftab, 1, 256);

        for (int i = 1, c = cftab[0]; i <= 256; i++) {
            c += cftab[i];
            cftab[i] = c;
        }

        for (int i = 0, lastShadow = this.last; i <= lastShadow; i++) {
            final int tmp = cftab[ll8[i] & 0xff]++;
            checkBounds(tmp, ttLen, "tt index");
            tt[tmp] = i;
        }

        if ((this.origPtr < 0) || (this.origPtr >= tt.length)) {
            throw new IOException("Stream corrupted");
        }

        this.su_tPos = tt[this.origPtr];
        this.su_count = 0;
        this.su_i2 = 0;
        this.su_ch2 = 256; /* not a char and not EOF */

        if (this.blockRandomised) {
            this.su_rNToGo = 0;
            this.su_rTPos = 0;
            return setupRandPartA();
        }
        return setupNoRandPartA();
    }

    private int setupRandPartA() throws IOException {
        if (this.su_i2 <= this.last) {
            this.su_chPrev = this.su_ch2;
            int su_ch2Shadow = this.data.ll8[this.su_tPos] & 0xff;
            checkBounds(this.su_tPos, this.data.tt.length, "su_tPos");
            this.su_tPos = this.data.tt[this.su_tPos];
            if (this.su_rNToGo == 0) {
                this.su_rNToGo = Rand.rNums(this.su_rTPos) - 1;
                if (++this.su_rTPos == 512) {
                    this.su_rTPos = 0;
                }
            } else {
                this.su_rNToGo--;
            }
            this.su_ch2 = su_ch2Shadow ^= (this.su_rNToGo == 1) ? 1 : 0;
            this.su_i2++;
            this.currentState = RAND_PART_B_STATE;
            this.crc.updateCRC(su_ch2Shadow);
            return su_ch2Shadow;
        }
        endBlock();
        initBlock();
        return setupBlock();
    }

    private int setupNoRandPartA() throws IOException {
        if (this.su_i2 <= this.last) {
            this.su_chPrev = this.su_ch2;
            final int su_ch2Shadow = this.data.ll8[this.su_tPos] & 0xff;
            this.su_ch2 = su_ch2Shadow;
            checkBounds(this.su_tPos, this.data.tt.length, "su_tPos");
            this.su_tPos = this.data.tt[this.su_tPos];
            this.su_i2++;
            this.currentState = NO_RAND_PART_B_STATE;
            this.crc.updateCRC(su_ch2Shadow);
            return su_ch2Shadow;
        }
        this.currentState = NO_RAND_PART_A_STATE;
        endBlock();
        initBlock();
        return setupBlock();
    }

    private int setupRandPartB() throws IOException {
        if (this.su_ch2 != this.su_chPrev) {
            this.currentState = RAND_PART_A_STATE;
            this.su_count = 1;
            return setupRandPartA();
        }
        if (++this.su_count < 4) {
            this.currentState = RAND_PART_A_STATE;
            return setupRandPartA();
        }
        this.su_z = (char) (this.data.ll8[this.su_tPos] & 0xff);
        checkBounds(this.su_tPos, this.data.tt.length, "su_tPos");
        this.su_tPos = this.data.tt[this.su_tPos];
        if (this.su_rNToGo == 0) {
            this.su_rNToGo = Rand.rNums(this.su_rTPos) - 1;
            if (++this.su_rTPos == 512) {
                this.su_rTPos = 0;
            }
        } else {
            this.su_rNToGo--;
        }
        this.su_j2 = 0;
        this.currentState = RAND_PART_C_STATE;
        if (this.su_rNToGo == 1) {
            this.su_z ^= 1;
        }
        return setupRandPartC();
    }

    private int setupRandPartC() throws IOException {
        if (this.su_j2 < this.su_z) {
            this.crc.updateCRC(this.su_ch2);
            this.su_j2++;
            return this.su_ch2;
        }
        this.currentState = RAND_PART_A_STATE;
        this.su_i2++;
        this.su_count = 0;
        return setupRandPartA();
    }

    private int setupNoRandPartB() throws IOException {
        if (this.su_ch2 != this.su_chPrev) {
            this.su_count = 1;
            return setupNoRandPartA();
        }
        if (++this.su_count >= 4) {
            checkBounds(this.su_tPos, this.data.ll8.length, "su_tPos");
            this.su_z = (char) (this.data.ll8[this.su_tPos] & 0xff);
            this.su_tPos = this.data.tt[this.su_tPos];
            this.su_j2 = 0;
            return setupNoRandPartC();
        }
        return setupNoRandPartA();
    }

    private int setupNoRandPartC() throws IOException {
        if (this.su_j2 < this.su_z) {
            final int su_ch2Shadow = this.su_ch2;
            this.crc.updateCRC(su_ch2Shadow);
            this.su_j2++;
            this.currentState = NO_RAND_PART_C_STATE;
            return su_ch2Shadow;
        }
        this.su_i2++;
        this.su_count = 0;
        return setupNoRandPartA();
    }

    private static final class Data {

        // (with blockSize 900k)
        final boolean[] inUse = new boolean[256]; // 256 byte

        final byte[] seqToUnseq = new byte[256]; // 256 byte
        final byte[] selector = new byte[MAX_SELECTORS]; // 18002 byte
        final byte[] selectorMtf = new byte[MAX_SELECTORS]; // 18002 byte

        /**
         * Freq table collected to save a pass over the data during
         * decompression.
         */
        final int[] unzftab = new int[256]; // 1024 byte

        final int[][] limit = new int[N_GROUPS][MAX_ALPHA_SIZE]; // 6192 byte
        final int[][] base = new int[N_GROUPS][MAX_ALPHA_SIZE]; // 6192 byte
        final int[][] perm = new int[N_GROUPS][MAX_ALPHA_SIZE]; // 6192 byte
        final int[] minLens = new int[N_GROUPS]; // 24 byte

        final int[] cftab = new int[257]; // 1028 byte
        final char[] getAndMoveToFrontDecode_yy = new char[256]; // 512 byte
        final char[][] temp_charArray2d = new char[N_GROUPS][MAX_ALPHA_SIZE]; // 3096
        // byte
        final byte[] recvDecodingTables_pos = new byte[N_GROUPS]; // 6 byte
        // ---------------
        // 60798 byte

        int[] tt; // 3600000 byte
        final byte[] ll8; // 900000 byte

        // ---------------
        // 4560782 byte
        // ===============

        Data(final int blockSize100k) {
            this.ll8 = new byte[blockSize100k * BZip2Constants.BASEBLOCKSIZE];
        }

        /**
         * Initializes the {@link #tt} array.
         *
         * This method is called when the required length of the array is known.
         * I don't initialize it at construction time to avoid unnecessary
         * memory allocation when compressing small files.
         */
        int[] initTT(final int length) {
            int[] ttShadow = this.tt;

            // tt.length should always be >= length, but theoretically
            // it can happen, if the compressor mixed small and large
            // blocks. Normally only the last block will be smaller
            // than others.
            if ((ttShadow == null) || (ttShadow.length < length)) {
                this.tt = ttShadow = new int[length];
            }

            return ttShadow;
        }

    }

    /**
     * Checks if the signature matches what is expected for a bzip2 file.
     *
     * @param signature
     *            the bytes to check
     * @param length
     *            the number of bytes to check
     * @return true, if this stream is a bzip2 compressed stream, false otherwise
     *
     * @since 1.1
     */
    public static boolean matches(final byte[] signature, final int length) {
        return length >= 3 && signature[0] == 'B' &&
                signature[1] == 'Z' && signature[2] == 'h';
    }
}