// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.archivers.tar;

import java.util.Objects;

/**
 * This class represents struct sparse in a Tar archive.
 * <p>
 * Whereas, "struct sparse" is:
 * <pre>
 * struct sparse {
 * char offset[12];   // offset 0
 * char numbytes[12]; // offset 12
 * };
 * </pre>
 * @since 1.20
 */
// Copyright 2019 Peter Alfred Lee
public final class TarArchiveStructSparse {
    private final long offset;
    private final long numbytes;

    public TarArchiveStructSparse(final long offset, final long numbytes) {
        this.offset = offset;
        this.numbytes = numbytes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TarArchiveStructSparse that = (TarArchiveStructSparse) o;
        return offset == that.offset &&
                numbytes == that.numbytes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset, numbytes);
    }

    @Override
    public String toString() {
        return "TarArchiveStructSparse{" +
                "offset=" + offset +
                ", numbytes=" + numbytes +
                '}';
    }

    public long getOffset() {
        return offset;
    }

    public long getNumbytes() {
        return numbytes;
    }
}