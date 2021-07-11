// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.compressors.bzip2;

import java.util.BitSet;

/**
 * Encapsulates the Burrows-Wheeler sorting algorithm needed by {@link
 * BZip2CompressorOutputStream}.
 *
 * <p>This class is based on a Java port of Julian Seward's
 * blocksort.c in his libbzip2</p>
 *
 * <p>The Burrows-Wheeler transform is a reversible transform of the
 * original data that is supposed to group similar bytes close to
 * each other.  The idea is to sort all permutations of the input and
 * only keep the last byte of each permutation.  E.g. for "Commons
 * Compress" you'd get:</p>
 *
 * <pre>
 *  CompressCommons
 * Commons Compress
 * CompressCommons
 * essCommons Compr
 * mmons CompressCo
 * mons CompressCom
 * mpressCommons Co
 * ns CompressCommo
 * ommons CompressC
 * ompressCommons C
 * ons CompressComm
 * pressCommons Com
 * ressCommons Comp
 * s CompressCommon
 * sCommons Compres
 * ssCommons Compre
 * </pre>
 *
 * <p>Which results in a new text "ss romooCCmmpnse", in adition the
 * index of the first line that contained the original text is kept -
 * in this case it is 1.  The idea is that in a long English text all
 * permutations that start with "he" are likely suffixes of a "the" and
 * thus they end in "t" leading to a larger block of "t"s that can
 * better be compressed by the subsequent Move-to-Front, run-length
 * und Huffman encoding steps.</p>
 *
 * <p>For more information see for example:</p>
 * <ul>
 *   <li><a
 *   href="http://www.hpl.hp.com/techreports/Compaq-DEC/SRC-RR-124.pdf">Burrows,
 *   M. and Wheeler, D.: A Block-sorting Lossless Data Compression
 *   Algorithm</a></li>
 *   <li><a href="http://webglimpse.net/pubs/suffix.pdf">Manber, U. and
 *   Myers, G.: Suffix arrays: A new method for on-line string
 *   searches</a></li>
 *   <li><a
 *   href="http://www.cs.tufts.edu/~nr/comp150fp/archive/bob-sedgewick/fast-strings.pdf">Bentley,
 *   J.L. and Sedgewick, R.: Fast Algorithms for Sorting and Searching
 *   Strings</a></li>
 * </ul>
 *
 * @NotThreadSafe
 */
// Copyright 2017 Stefan Bodewig
class BlockSort {

    /*
     * Some of the constructs used in the C code cannot be ported
     * literally to Java - for example macros, unsigned types.  Some
     * code has been hand-tuned to improve performance.  In order to
     * avoid memory pressure some structures are reused for several
     * blocks and some memory is even shared between sorting and the
     * MTF stage even though either algorithm uses it for its own
     * purpose.
     *
     * Comments preserved from the actual C code are prefixed with
     * "LBZ2:".
     */

    /*
     * 2012-05-20 Stefan Bodewig:
     *
     * This class seems to mix several revisions of libbzip2's code.
     * The mainSort function and those used by it look closer to the
     * 0.9.5 version but show some variations introduced later.  At
     * the same time the logic of Compress 1.4 to randomize the block
     * on bad input has been dropped after libbzip2 0.9.0 and replaced
     * by a fallback sorting algorithm.
     *
     * I've added the fallbackSort function of 1.0.6 and tried to
     * integrate it with the existing code without touching too much.
     * I've also removed the now unused randomization code.
     */

    /*
     * LBZ2: If you are ever unlucky/improbable enough to get a stack
     * overflow whilst sorting, increase the following constant and
     * try again. In practice I have never seen the stack go above 27
     * elems, so the following limit seems very generous.
     */
    private static final int QSORT_STACK_SIZE = 1000;

    private static final int FALLBACK_QSORT_STACK_SIZE = 100;

    private static final int STACK_SIZE =
            QSORT_STACK_SIZE < FALLBACK_QSORT_STACK_SIZE
                    ? FALLBACK_QSORT_STACK_SIZE : QSORT_STACK_SIZE;

    /*
     * Used when sorting. If too many long comparisons happen, we stop sorting,
     * and use fallbackSort instead.
     */
    private int workDone;
    private int workLimit;
    private boolean firstAttempt;

    private final int[] stack_ll = new int[STACK_SIZE]; // 4000 byte
    private final int[] stack_hh = new int[STACK_SIZE]; // 4000 byte
    private final int[] stack_dd = new int[QSORT_STACK_SIZE]; // 4000 byte

    private final int[] mainSort_runningOrder = new int[256]; // 1024 byte
    private final int[] mainSort_copy = new int[256]; // 1024 byte
    private final boolean[] mainSort_bigDone = new boolean[256]; // 256 byte

    private final int[] ftab = new int[65537]; // 262148 byte

    /**
     * Array instance identical to Data's sfmap, both are used only
     * temporarily and indepently, so we do not need to allocate
     * additional memory.
     */
    private final char[] quadrant;

    BlockSort(final BZip2CompressorOutputStream.Data data) {
        this.quadrant = data.sfmap;
    }

    void blockSort(final BZip2CompressorOutputStream.Data data, final int last) {
        this.workLimit = WORK_FACTOR * last;
        this.workDone = 0;
        this.firstAttempt = true;

        if (last + 1 < 10000) {
            fallbackSort(data, last);
        } else {
            mainSort(data, last);

            if (this.firstAttempt && (this.workDone > this.workLimit)) {
                fallbackSort(data, last);
            }
        }

        final int[] fmap = data.fmap;
        data.origPtr = -1;
        for (int i = 0; i <= last; i++) {
            if (fmap[i] == 0) {
                data.origPtr = i;
                break;
            }
        }

        // assert (data.origPtr != -1) : data.origPtr;
    }

    /**
     * Adapt fallbackSort to the expected interface of the rest of the
     * code, in particular deal with the fact that block starts at
     * offset 1 (in libbzip2 1.0.6 it starts at 0).
     */
    final void fallbackSort(final BZip2CompressorOutputStream.Data data,
                            final int last) {
        data.block[0] = data.block[last + 1];
        fallbackSort(data.fmap, data.block, last + 1);
        for (int i = 0; i < last + 1; i++) {
            --data.fmap[i];
        }
        for (int i = 0; i < last + 1; i++) {
            if (data.fmap[i] == -1) {
                data.fmap[i] = last;
                break;
            }
        }
    }

    /*---------------------------------------------*/

    /*---------------------------------------------*/
    /*--- LBZ2: Fallback O(N log(N)^2) sorting        ---*/
    /*--- algorithm, for repetitive blocks      ---*/
    /*---------------------------------------------*/

    /*
     * This is the fallback sorting algorithm libbzip2 1.0.6 uses for
     * repetitive or very short inputs.
     *
     * The idea is inspired by Manber-Myers string suffix sorting
     * algorithm.  First a bucket sort places each permutation of the
     * block into a bucket based on its first byte.  Permutations are
     * represented by pointers to their first character kept in
     * (partially) sorted order inside the array ftab.
     *
     * The next step visits all buckets in order and performs a
     * quicksort on all permutations of the bucket based on the index
     * of the bucket the second byte of the permutation belongs to,
     * thereby forming new buckets.  When arrived here the
     * permutations are sorted up to the second character and we have
     * buckets of permutations that are identical up to two
     * characters.
     *
     * Repeat the step of quicksorting each bucket, now based on the
     * bucket holding the sequence of the third and forth character
     * leading to four byte buckets.  Repeat this doubling of bucket
     * sizes until all buckets only contain single permutations or the
     * bucket size exceeds the block size.
     *
     * I.e.
     *
     * "abraba" form three buckets for the chars "a", "b", and "r" in
     * the first step with
     *
     * fmap = { 'a:' 5, 3, 0, 'b:' 4, 1, 'r', 2 }
     *
     * when looking at the bucket of "a"s the second characters are in
     * the buckets that start with fmap-index 0 (rolled over), 3 and 3
     * respectively, forming two new buckets "aa" and "ab", so we get
     *
     * fmap = { 'aa:' 5, 'ab:' 3, 0, 'ba:' 4, 'br': 1, 'ra:' 2 }
     *
     * since the last bucket only contained a single item it didn't
     * have to be sorted at all.
     *
     * There now is just one bucket with more than one permutation
     * that remains to be sorted.  For the permutation that starts
     * with index 3 the third and forth char are in bucket 'aa' at
     * index 0 and for the one starting at block index 0 they are in
     * bucket 'ra' with sort index 5.  The fully sorted order then becomes.
     *
     * fmap = { 5, 3, 0, 4, 1, 2 }
     *
     */

    /**
     * @param fmap points to the index of the starting point of a
     *        permutation inside the block of data in the current
     *        partially sorted order
     * @param eclass points from the index of a character inside the
     *        block to the first index in fmap that contains the
     *        bucket of its suffix that is sorted in this step.
     * @param lo lower boundary of the fmap-interval to be sorted
     * @param hi upper boundary of the fmap-interval to be sorted
     */
    private void fallbackSimpleSort(final int[] fmap,
                                    final int[] eclass,
                                    final int lo,
                                    final int hi) {
        if (lo == hi) {
            return;
        }

        int j;
        if (hi - lo > 3) {
            for (int i = hi - 4; i >= lo; i--) {
                final int tmp = fmap[i];
                final int ec_tmp = eclass[tmp];
                for (j = i + 4; j <= hi && ec_tmp > eclass[fmap[j]];
                     j += 4) {
                    fmap[j - 4] = fmap[j];
                }
                fmap[j - 4] = tmp;
            }
        }

        for (int i = hi - 1; i >= lo; i--) {
            final int tmp = fmap[i];
            final int ec_tmp = eclass[tmp];
            for (j = i + 1; j <= hi && ec_tmp > eclass[fmap[j]]; j++) {
                fmap[j - 1] = fmap[j];
            }
            fmap[j-1] = tmp;
        }
    }

    private static final int FALLBACK_QSORT_SMALL_THRESH = 10;

    /**
     * swaps two values in fmap
     */
    private void fswap(final int[] fmap, final int zz1, final int zz2) {
        final int zztmp = fmap[zz1];
        fmap[zz1] = fmap[zz2];
        fmap[zz2] = zztmp;
    }

    /**
     * swaps two intervals starting at yyp1 and yyp2 of length yyn inside fmap.
     */
    private void fvswap(final int[] fmap, int yyp1, int yyp2, int yyn) {
        while (yyn > 0) {
            fswap(fmap, yyp1, yyp2);
            yyp1++; yyp2++; yyn--;
        }
    }

    private int fmin(final int a, final int b) {
        return a < b ? a : b;
    }

    private void fpush(final int sp, final int lz, final int hz) {
        stack_ll[sp] = lz;
        stack_hh[sp] = hz;
    }

    private int[] fpop(final int sp) {
        return new int[] { stack_ll[sp], stack_hh[sp] };
    }

    /**
     * @param fmap points to the index of the starting point of a
     *        permutation inside the block of data in the current
     *        partially sorted order
     * @param eclass points from the index of a character inside the
     *        block to the first index in fmap that contains the
     *        bucket of its suffix that is sorted in this step.
     * @param loSt lower boundary of the fmap-interval to be sorted
     * @param hiSt upper boundary of the fmap-interval to be sorted
     */
    private void fallbackQSort3(final int[] fmap,
                                final int[] eclass,
                                final int loSt,
                                final int hiSt) {
        int lo, unLo, ltLo, hi, unHi, gtHi, n;

        long r = 0;
        int sp = 0;
        fpush(sp++, loSt, hiSt);

        while (sp > 0) {
            final int[] s = fpop(--sp);
            lo = s[0]; hi = s[1];

            if (hi - lo < FALLBACK_QSORT_SMALL_THRESH) {
                fallbackSimpleSort(fmap, eclass, lo, hi);
                continue;
            }

            /* LBZ2: Random partitioning.  Median of 3 sometimes fails to
               avoid bad cases.  Median of 9 seems to help but
               looks rather expensive.  This too seems to work but
               is cheaper.  Guidance for the magic constants
               7621 and 32768 is taken from Sedgewick's algorithms
               book, chapter 35.
            */
            r = ((r * 7621) + 1) % 32768;
            final long r3 = r % 3;
            final long med;
            if (r3 == 0) {
                med = eclass[fmap[lo]];
            } else if (r3 == 1) {
                med = eclass[fmap[(lo + hi) >>> 1]];
            } else {
                med = eclass[fmap[hi]];
            }

            unLo = ltLo = lo;
            unHi = gtHi = hi;

            // looks like the ternary partition attributed to Wegner
            // in the cited Sedgewick paper
            while (true) {
                while (true) {
                    if (unLo > unHi) {
                        break;
                    }
                    n = eclass[fmap[unLo]] - (int) med;
                    if (n == 0) {
                        fswap(fmap, unLo, ltLo);
                        ltLo++; unLo++;
                        continue;
                    }
                    if (n > 0) {
                        break;
                    }
                    unLo++;
                }
                while (true) {
                    if (unLo > unHi) {
                        break;
                    }
                    n = eclass[fmap[unHi]] - (int) med;
                    if (n == 0) {
                        fswap(fmap, unHi, gtHi);
                        gtHi--; unHi--;
                        continue;
                    }
                    if (n < 0) {
                        break;
                    }
                    unHi--;
                }
                if (unLo > unHi) {
                    break;
                }
                fswap(fmap, unLo, unHi); unLo++; unHi--;
            }

            if (gtHi < ltLo) {
                continue;
            }

            n = fmin(ltLo - lo, unLo - ltLo);
            fvswap(fmap, lo, unLo - n, n);
            int m = fmin(hi - gtHi, gtHi - unHi);
            fvswap(fmap, unHi + 1, hi - m + 1, m);

            n = lo + unLo - ltLo - 1;
            m = hi - (gtHi - unHi) + 1;

            if (n - lo > hi - m) {
                fpush(sp++, lo, n);
                fpush(sp++, m, hi);
            } else {
                fpush(sp++, m, hi);
                fpush(sp++, lo, n);
            }
        }
    }


    /*---------------------------------------------*/

    private int[] eclass;

    private int[] getEclass() {
        if (eclass == null) {
            eclass = new int[quadrant.length / 2];
        }
        return eclass;
    }

    /*
     * The C code uses an array of ints (each int holding 32 flags) to
     * represents the bucket-start flags (bhtab).  It also contains
     * optimizations to skip over 32 consecutively set or
     * consecutively unset bits on word boundaries at once.  For now
     * I've chosen to use the simpler but potentially slower code
     * using BitSet - also in the hope that using the BitSet#nextXXX
     * methods may be fast enough.
     */

    /**
     * @param fmap points to the index of the starting point of a
     *        permutation inside the block of data in the current
     *        partially sorted order
     * @param block the original data
     * @param nblock size of the block
     */
    final void fallbackSort(final int[] fmap, final byte[] block, final int nblock) {
        final int[] ftab = new int[257];
        int H, i, j, k, l, r, cc, cc1;
        int nNotDone;
        final int nBhtab;
        final int[] eclass = getEclass();

        for (i = 0; i < nblock; i++) {
            eclass[i] = 0;
        }
        /*--
          LBZ2: Initial 1-char radix sort to generate
          initial fmap and initial BH bits.
          --*/
        for (i = 0; i < nblock; i++) {
            ftab[block[i] & 0xff]++;
        }
        for (i = 1; i < 257;    i++) {
            ftab[i] += ftab[i - 1];
        }

        for (i = 0; i < nblock; i++) {
            j = block[i] & 0xff;
            k = ftab[j] - 1;
            ftab[j] = k;
            fmap[k] = i;
        }

        nBhtab = 64 + nblock;
        final BitSet bhtab = new BitSet(nBhtab);
        for (i = 0; i < 256; i++) {
            bhtab.set(ftab[i]);
        }

        /*--
          LBZ2: Inductively refine the buckets.  Kind-of an
          "exponential radix sort" (!), inspired by the
          Manber-Myers suffix array construction algorithm.
          --*/

        /*-- LBZ2: set sentinel bits for block-end detection --*/
        for (i = 0; i < 32; i++) {
            bhtab.set(nblock + 2 * i);
            bhtab.clear(nblock + 2 * i + 1);
        }

        /*-- LBZ2: the log(N) loop --*/
        H = 1;
        while (true) {

            j = 0;
            for (i = 0; i < nblock; i++) {
                if (bhtab.get(i)) {
                    j = i;
                }
                k = fmap[i] - H;
                if (k < 0) {
                    k += nblock;
                }
                eclass[k] = j;
            }

            nNotDone = 0;
            r = -1;
            while (true) {

                /*-- LBZ2: find the next non-singleton bucket --*/
                k = r + 1;
                k = bhtab.nextClearBit(k);
                l = k - 1;
                if (l >= nblock) {
                    break;
                }
                k = bhtab.nextSetBit(k + 1);
                r = k - 1;
                if (r >= nblock) {
                    break;
                }

                /*-- LBZ2: now [l, r] bracket current bucket --*/
                if (r > l) {
                    nNotDone += (r - l + 1);
                    fallbackQSort3(fmap, eclass, l, r);

                    /*-- LBZ2: scan bucket and generate header bits-- */
                    cc = -1;
                    for (i = l; i <= r; i++) {
                        cc1 = eclass[fmap[i]];
                        if (cc != cc1) {
                            bhtab.set(i);
                            cc = cc1;
                        }
                    }
                }
            }

            H *= 2;
            if (H > nblock || nNotDone == 0) {
                break;
            }
        }
    }

    /*---------------------------------------------*/

    /*
     * LBZ2: Knuth's increments seem to work better than Incerpi-Sedgewick here.
     * Possibly because the number of elems to sort is usually small, typically
     * &lt;= 20.
     */
    private static final int[] INCS = { 1, 4, 13, 40, 121, 364, 1093, 3280,
            9841, 29524, 88573, 265720, 797161,
            2391484 };

    /**
     * This is the most hammered method of this class.
     *
     * <p>
     * This is the version using unrolled loops. Normally I never use such ones
     * in Java code. The unrolling has shown a noticable performance improvement
     * on JRE 1.4.2 (Linux i586 / HotSpot Client). Of course it depends on the
     * JIT compiler of the vm.
     * </p>
     */
    private boolean mainSimpleSort(final BZip2CompressorOutputStream.Data dataShadow,
                                   final int lo, final int hi, final int d,
                                   final int lastShadow) {
        final int bigN = hi - lo + 1;
        if (bigN < 2) {
            return this.firstAttempt && (this.workDone > this.workLimit);
        }

        int hp = 0;
        while (INCS[hp] < bigN) {
            hp++;
        }

        final int[] fmap = dataShadow.fmap;
        final char[] quadrant = this.quadrant;
        final byte[] block = dataShadow.block;
        final int lastPlus1 = lastShadow + 1;
        final boolean firstAttemptShadow = this.firstAttempt;
        final int workLimitShadow = this.workLimit;
        int workDoneShadow = this.workDone;

        // Following block contains unrolled code which could be shortened by
        // coding it in additional loops.

        HP: while (--hp >= 0) {
            final int h = INCS[hp];
            final int mj = lo + h - 1;

            for (int i = lo + h; i <= hi;) {
                // copy
                for (int k = 3; (i <= hi) && (--k >= 0); i++) {
                    final int v = fmap[i];
                    final int vd = v + d;
                    int j = i;

                    // for (int a;
                    // (j > mj) && mainGtU((a = fmap[j - h]) + d, vd,
                    // block, quadrant, lastShadow);
                    // j -= h) {
                    // fmap[j] = a;
                    // }
                    //
                    // unrolled version:

                    // start inline mainGTU
                    boolean onceRunned = false;
                    int a = 0;

                    HAMMER: while (true) {
                        if (onceRunned) {
                            fmap[j] = a;
                            if ((j -= h) <= mj) { //NOSONAR
                                break HAMMER;
                            }
                        } else {
                            onceRunned = true;
                        }

                        a = fmap[j - h];
                        int i1 = a + d;
                        int i2 = vd;

                        // following could be done in a loop, but
                        // unrolled it for performance:
                        if (block[i1 + 1] == block[i2 + 1]) {
                            if (block[i1 + 2] == block[i2 + 2]) {
                                if (block[i1 + 3] == block[i2 + 3]) {
                                    if (block[i1 + 4] == block[i2 + 4]) {
                                        if (block[i1 + 5] == block[i2 + 5]) {
                                            if (block[(i1 += 6)] == block[(i2 += 6)]) { //NOSONAR
                                                int x = lastShadow;
                                                X: while (x > 0) {
                                                    x -= 4;
                                                    if (block[i1 + 1] == block[i2 + 1]) {
                                                        if (quadrant[i1] == quadrant[i2]) {
                                                            if (block[i1 + 2] == block[i2 + 2]) {
                                                                if (quadrant[i1 + 1] == quadrant[i2 + 1]) {
                                                                    if (block[i1 + 3] == block[i2 + 3]) {
                                                                        if (quadrant[i1 + 2] == quadrant[i2 + 2]) {
                                                                            if (block[i1 + 4] == block[i2 + 4]) {
                                                                                if (quadrant[i1 + 3] == quadrant[i2 + 3]) {
                                                                                    if ((i1 += 4) >= lastPlus1) { //NOSONAR
                                                                                        i1 -= lastPlus1;
                                                                                    }
                                                                                    if ((i2 += 4) >= lastPlus1) { //NOSONAR
                                                                                        i2 -= lastPlus1;
                                                                                    }
                                                                                    workDoneShadow++;
                                                                                    continue X;
                                                                                }
                                                                                if ((quadrant[i1 + 3] > quadrant[i2 + 3])) {
                                                                                    continue HAMMER;
                                                                                }
                                                                                break HAMMER;
                                                                            }
                                                                            if ((block[i1 + 4] & 0xff) > (block[i2 + 4] & 0xff)) {
                                                                                continue HAMMER;
                                                                            }
                                                                            break HAMMER;
                                                                        }
                                                                        if ((quadrant[i1 + 2] > quadrant[i2 + 2])) {
                                                                            continue HAMMER;
                                                                        }
                                                                        break HAMMER;
                                                                    }
                                                                    if ((block[i1 + 3] & 0xff) > (block[i2 + 3] & 0xff)) {
                                                                        continue HAMMER;
                                                                    }
                                                                    break HAMMER;
                                                                }
                                                                if ((quadrant[i1 + 1] > quadrant[i2 + 1])) {
                                                                    continue HAMMER;
                                                                }
                                                                break HAMMER;
                                                            }
                                                            if ((block[i1 + 2] & 0xff) > (block[i2 + 2] & 0xff)) {
                                                                continue HAMMER;
                                                            }
                                                            break HAMMER;
                                                        }
                                                        if ((quadrant[i1] > quadrant[i2])) {
                                                            continue HAMMER;
                                                        }
                                                        break HAMMER;
                                                    }
                                                    if ((block[i1 + 1] & 0xff) > (block[i2 + 1] & 0xff)) {
                                                        continue HAMMER;
                                                    }
                                                    break HAMMER;

                                                }
                                                break HAMMER;
                                            } // while x > 0
                                            if ((block[i1] & 0xff) > (block[i2] & 0xff)) {
                                                continue HAMMER;
                                            }
                                            break HAMMER;
                                        }
                                        if ((block[i1 + 5] & 0xff) > (block[i2 + 5] & 0xff)) {
                                            continue HAMMER;
                                        }
                                        break HAMMER;
                                    }
                                    if ((block[i1 + 4] & 0xff) > (block[i2 + 4] & 0xff)) {
                                        continue HAMMER;
                                    }
                                    break HAMMER;
                                }
                                if ((block[i1 + 3] & 0xff) > (block[i2 + 3] & 0xff)) {
                                    continue HAMMER;
                                }
                                break HAMMER;
                            }
                            if ((block[i1 + 2] & 0xff) > (block[i2 + 2] & 0xff)) {
                                continue HAMMER;
                            }
                            break HAMMER;
                        }
                        if ((block[i1 + 1] & 0xff) > (block[i2 + 1] & 0xff)) {
                            continue HAMMER;
                        }
                        break HAMMER;

                    } // HAMMER
                    // end inline mainGTU

                    fmap[j] = v;
                }

                if (firstAttemptShadow && (i <= hi)
                        && (workDoneShadow > workLimitShadow)) {
                    break HP;
                }
            }
        }

        this.workDone = workDoneShadow;
        return firstAttemptShadow && (workDoneShadow > workLimitShadow);
    }

/*--
   LBZ2: The following is an implementation of
   an elegant 3-way quicksort for strings,
   described in a paper "Fast Algorithms for
   Sorting and Searching Strings", by Robert
   Sedgewick and Jon L. Bentley.
--*/

    private static void vswap(final int[] fmap, int p1, int p2, int n) {
        n += p1;
        while (p1 < n) {
            final int t = fmap[p1];
            fmap[p1++] = fmap[p2];
            fmap[p2++] = t;
        }
    }

    private static byte med3(final byte a, final byte b, final byte c) {
        return (a < b) ? (b < c ? b : a < c ? c : a) : (b > c ? b : a > c ? c
                : a);
    }

    private static final int SMALL_THRESH = 20;
    private static final int DEPTH_THRESH = 10;
    private static final int WORK_FACTOR = 30;

    /**
     * Method "mainQSort3", file "blocksort.c", BZip2 1.0.2
     */
    private void mainQSort3(final BZip2CompressorOutputStream.Data dataShadow,
                            final int loSt, final int hiSt, final int dSt,
                            final int last) {
        final int[] stack_ll = this.stack_ll;
        final int[] stack_hh = this.stack_hh;
        final int[] stack_dd = this.stack_dd;
        final int[] fmap = dataShadow.fmap;
        final byte[] block = dataShadow.block;

        stack_ll[0] = loSt;
        stack_hh[0] = hiSt;
        stack_dd[0] = dSt;

        for (int sp = 1; --sp >= 0;) {
            final int lo = stack_ll[sp];
            final int hi = stack_hh[sp];
            final int d = stack_dd[sp];

            if ((hi - lo < SMALL_THRESH) || (d > DEPTH_THRESH)) {
                if (mainSimpleSort(dataShadow, lo, hi, d, last)) {
                    return;
                }
            } else {
                final int d1 = d + 1;
                final int med = med3(block[fmap[lo] + d1],
                        block[fmap[hi] + d1], block[fmap[(lo + hi) >>> 1] + d1]) & 0xff;

                int unLo = lo;
                int unHi = hi;
                int ltLo = lo;
                int gtHi = hi;

                while (true) {
                    while (unLo <= unHi) {
                        final int n = (block[fmap[unLo] + d1] & 0xff)
                                - med;
                        if (n == 0) {
                            final int temp = fmap[unLo];
                            fmap[unLo++] = fmap[ltLo];
                            fmap[ltLo++] = temp;
                        } else if (n < 0) {
                            unLo++;
                        } else {
                            break;
                        }
                    }

                    while (unLo <= unHi) {
                        final int n = (block[fmap[unHi] + d1] & 0xff)
                                - med;
                        if (n == 0) {
                            final int temp = fmap[unHi];
                            fmap[unHi--] = fmap[gtHi];
                            fmap[gtHi--] = temp;
                        } else if (n > 0) {
                            unHi--;
                        } else {
                            break;
                        }
                    }

                    if (unLo > unHi) {
                        break;
                    }
                    final int temp = fmap[unLo];
                    fmap[unLo++] = fmap[unHi];
                    fmap[unHi--] = temp;
                }

                if (gtHi < ltLo) {
                    stack_ll[sp] = lo;
                    stack_hh[sp] = hi;
                    stack_dd[sp] = d1;
                    sp++;
                } else {
                    int n = ((ltLo - lo) < (unLo - ltLo)) ? (ltLo - lo) : (unLo - ltLo);
                    vswap(fmap, lo, unLo - n, n);
                    int m = ((hi - gtHi) < (gtHi - unHi)) ? (hi - gtHi) : (gtHi - unHi);
                    vswap(fmap, unLo, hi - m + 1, m);

                    n = lo + unLo - ltLo - 1;
                    m = hi - (gtHi - unHi) + 1;

                    stack_ll[sp] = lo;
                    stack_hh[sp] = n;
                    stack_dd[sp] = d;
                    sp++;

                    stack_ll[sp] = n + 1;
                    stack_hh[sp] = m - 1;
                    stack_dd[sp] = d1;
                    sp++;

                    stack_ll[sp] = m;
                    stack_hh[sp] = hi;
                    stack_dd[sp] = d;
                    sp++;
                }
            }
        }
    }

    private static final int SETMASK = (1 << 21);
    private static final int CLEARMASK = (~SETMASK);

    final void mainSort(final BZip2CompressorOutputStream.Data dataShadow,
                        final int lastShadow) {
        final int[] runningOrder = this.mainSort_runningOrder;
        final int[] copy = this.mainSort_copy;
        final boolean[] bigDone = this.mainSort_bigDone;
        final int[] ftab = this.ftab;
        final byte[] block = dataShadow.block;
        final int[] fmap = dataShadow.fmap;
        final char[] quadrant = this.quadrant;
        final int workLimitShadow = this.workLimit;
        final boolean firstAttemptShadow = this.firstAttempt;

        // LBZ2: Set up the 2-byte frequency table
        for (int i = 65537; --i >= 0;) {
            ftab[i] = 0;
        }

        /*
         * In the various block-sized structures, live data runs from 0 to
         * last+NUM_OVERSHOOT_BYTES inclusive. First, set up the overshoot area
         * for block.
         */
        for (int i = 0; i < BZip2Constants.NUM_OVERSHOOT_BYTES; i++) {
            block[lastShadow + i + 2] = block[(i % (lastShadow + 1)) + 1];
        }
        for (int i = lastShadow + BZip2Constants.NUM_OVERSHOOT_BYTES +1; --i >= 0;) {
            quadrant[i] = 0;
        }
        block[0] = block[lastShadow + 1];

        // LBZ2: Complete the initial radix sort:

        int c1 = block[0] & 0xff;
        for (int i = 0; i <= lastShadow; i++) {
            final int c2 = block[i + 1] & 0xff;
            ftab[(c1 << 8) + c2]++;
            c1 = c2;
        }

        for (int i = 1; i <= 65536; i++) {
            ftab[i] += ftab[i - 1];
        }

        c1 = block[1] & 0xff;
        for (int i = 0; i < lastShadow; i++) {
            final int c2 = block[i + 2] & 0xff;
            fmap[--ftab[(c1 << 8) + c2]] = i;
            c1 = c2;
        }

        fmap[--ftab[((block[lastShadow + 1] & 0xff) << 8) + (block[1] & 0xff)]] = lastShadow;

        /*
         * LBZ2: Now ftab contains the first loc of every small bucket. Calculate the
         * running order, from smallest to largest big bucket.
         */
        for (int i = 256; --i >= 0;) {
            bigDone[i] = false;
            runningOrder[i] = i;
        }

        // h = 364, 121, 40, 13, 4, 1
        for (int h = 364; h != 1;) { //NOSONAR
            h /= 3;
            for (int i = h; i <= 255; i++) {
                final int vv = runningOrder[i];
                final int a = ftab[(vv + 1) << 8] - ftab[vv << 8];
                final int b = h - 1;
                int j = i;
                for (int ro = runningOrder[j - h]; (ftab[(ro + 1) << 8] - ftab[ro << 8]) > a; ro = runningOrder[j
                        - h]) {
                    runningOrder[j] = ro;
                    j -= h;
                    if (j <= b) {
                        break;
                    }
                }
                runningOrder[j] = vv;
            }
        }

        /*
         * LBZ2: The main sorting loop.
         */
        for (int i = 0; i <= 255; i++) {
            /*
             * LBZ2: Process big buckets, starting with the least full.
             */
            final int ss = runningOrder[i];

            // Step 1:
            /*
             * LBZ2: Complete the big bucket [ss] by quicksorting any unsorted small
             * buckets [ss, j]. Hopefully previous pointer-scanning phases have
             * already completed many of the small buckets [ss, j], so we don't
             * have to sort them at all.
             */
            for (int j = 0; j <= 255; j++) {
                final int sb = (ss << 8) + j;
                final int ftab_sb = ftab[sb];
                if ((ftab_sb & SETMASK) != SETMASK) {
                    final int lo = ftab_sb & CLEARMASK;
                    final int hi = (ftab[sb + 1] & CLEARMASK) - 1;
                    if (hi > lo) {
                        mainQSort3(dataShadow, lo, hi, 2, lastShadow);
                        if (firstAttemptShadow
                                && (this.workDone > workLimitShadow)) {
                            return;
                        }
                    }
                    ftab[sb] = ftab_sb | SETMASK;
                }
            }

            // Step 2:
            // LBZ2: Now scan this big bucket so as to synthesise the
            // sorted order for small buckets [t, ss] for all t != ss.

            for (int j = 0; j <= 255; j++) {
                copy[j] = ftab[(j << 8) + ss] & CLEARMASK;
            }

            for (int j = ftab[ss << 8] & CLEARMASK, hj = (ftab[(ss + 1) << 8] & CLEARMASK); j < hj; j++) {
                final int fmap_j = fmap[j];
                c1 = block[fmap_j] & 0xff;
                if (!bigDone[c1]) {
                    fmap[copy[c1]] = (fmap_j == 0) ? lastShadow : (fmap_j - 1);
                    copy[c1]++;
                }
            }

            for (int j = 256; --j >= 0;) {
                ftab[(j << 8) + ss] |= SETMASK;
            }

            // Step 3:
            /*
             * LBZ2: The ss big bucket is now done. Record this fact, and update the
             * quadrant descriptors. Remember to update quadrants in the
             * overshoot area too, if necessary. The "if (i < 255)" test merely
             * skips this updating for the last bucket processed, since updating
             * for the last bucket is pointless.
             */
            bigDone[ss] = true;

            if (i < 255) {
                final int bbStart = ftab[ss << 8] & CLEARMASK;
                final int bbSize = (ftab[(ss + 1) << 8] & CLEARMASK) - bbStart;
                int shifts = 0;

                while ((bbSize >> shifts) > 65534) {
                    shifts++;
                }

                for (int j = 0; j < bbSize; j++) {
                    final int a2update = fmap[bbStart + j];
                    final char qVal = (char) (j >> shifts);
                    quadrant[a2update] = qVal;
                    if (a2update < BZip2Constants.NUM_OVERSHOOT_BYTES) {
                        quadrant[a2update + lastShadow + 1] = qVal;
                    }
                }
            }

        }
    }

}