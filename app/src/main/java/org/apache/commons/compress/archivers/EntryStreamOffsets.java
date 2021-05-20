// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.archivers;

/**
 * Provides information about ArchiveEntry stream offsets.
 */
// Copyright 2017 Zbynek Vyskovsky
public interface EntryStreamOffsets {

    /** Special value indicating that the offset is unknown. */
    long OFFSET_UNKNOWN = -1;

    /**
     * Gets the offset of data stream within the archive file,
     *
     * @return
     *      the offset of entry data stream, {@code OFFSET_UNKNOWN} if not known.
     */
    long getDataOffset();

    /**
     * Indicates whether the stream is contiguous, i.e. not split among
     * several archive parts, interspersed with control blocks, etc.
     *
     * @return
     *      true if stream is contiguous, false otherwise.
     */
    boolean isStreamContiguous();
}