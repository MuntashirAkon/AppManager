// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.compressors.bzip2;

/**
 * Constants for both the compress and decompress BZip2 classes.
 */
// Copyright 2008 Torsten Curdt
interface BZip2Constants {

    int BASEBLOCKSIZE = 100000;
    int MAX_ALPHA_SIZE = 258;
    int MAX_CODE_LEN = 23;
    int RUNA = 0;
    int RUNB = 1;
    int N_GROUPS = 6;
    int G_SIZE = 50;
    int N_ITERS = 4;
    int MAX_SELECTORS = (2 + (900000 / G_SIZE));
    int NUM_OVERSHOOT_BYTES = 20;

}