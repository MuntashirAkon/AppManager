// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.archivers.tar;

import java.io.IOException;
import java.io.InputStream;

/**
 * This is an inputstream that always return 0,
 * this is used when reading the "holes" of a sparse file
 */
// Copyright 2021 Robin Schimpf
class TarArchiveSparseZeroInputStream extends InputStream {

    /**
     * Just return 0
     */
    @Override
    public int read() throws IOException {
        return 0;
    }

    /**
     * these's nothing need to do when skipping
     *
     * @param n bytes to skip
     * @return bytes actually skipped
     */
    @Override
    public long skip(final long n) {
        return n;
    }
}