// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.archivers;

import java.util.Date;

/**
 * Represents an entry of an archive.
 */
// Copyright 2008 Torsten Curdt
public interface ArchiveEntry {

    /**
     * Gets the name of the entry in this archive. May refer to a file or directory or other item.
     *
     * <p>This method returns the raw name as it is stored inside of the archive.</p>
     *
     * @return The name of this entry in the archive.
     */
    String getName();

    /**
     * Gets the uncompressed size of this entry. May be -1 (SIZE_UNKNOWN) if the size is unknown
     *
     * @return the uncompressed size of this entry.
     */
    long getSize();

    /** Special value indicating that the size is unknown */
    long SIZE_UNKNOWN = -1;

    /**
     * Returns true if this entry refers to a directory.
     *
     * @return true if this entry refers to a directory.
     */
    boolean isDirectory();

    /**
     * Gets the last modified date of this entry.
     *
     * @return the last modified date of this entry.
     * @since 1.1
     */
    Date getLastModifiedDate();
}
