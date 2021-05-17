// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.compressors.gzip;

import java.util.zip.Deflater;

/**
 * Parameters for the GZIP compressor.
 *
 * @since 1.7
 */
// Copyright 2013 Emmanuel Bourg
public class GzipParameters {

    private int compressionLevel = Deflater.DEFAULT_COMPRESSION;
    private long modificationTime;
    private String filename;
    private String comment;
    private int operatingSystem = 255; // Unknown OS by default

    public int getCompressionLevel() {
        return compressionLevel;
    }

    /**
     * Sets the compression level.
     *
     * @param compressionLevel the compression level (between 0 and 9)
     * @see Deflater#NO_COMPRESSION
     * @see Deflater#BEST_SPEED
     * @see Deflater#DEFAULT_COMPRESSION
     * @see Deflater#BEST_COMPRESSION
     */
    public void setCompressionLevel(final int compressionLevel) {
        if (compressionLevel < -1 || compressionLevel > 9) {
            throw new IllegalArgumentException("Invalid gzip compression level: " + compressionLevel);
        }
        this.compressionLevel = compressionLevel;
    }

    public long getModificationTime() {
        return modificationTime;
    }

    /**
     * Sets the modification time of the compressed file.
     *
     * @param modificationTime the modification time, in milliseconds
     */
    public void setModificationTime(final long modificationTime) {
        this.modificationTime = modificationTime;
    }

    public String getFilename() {
        return filename;
    }

    /**
     * Sets the name of the compressed file.
     *
     * @param fileName the name of the file without the directory path
     */
    public void setFilename(final String fileName) {
        this.filename = fileName;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        this.comment = comment;
    }

    public int getOperatingSystem() {
        return operatingSystem;
    }

    /**
     * Sets the operating system on which the compression took place.
     * The defined values are:
     * <ul>
     *   <li>0: FAT file system (MS-DOS, OS/2, NT/Win32)</li>
     *   <li>1: Amiga</li>
     *   <li>2: VMS (or OpenVMS)</li>
     *   <li>3: Unix</li>
     *   <li>4: VM/CMS</li>
     *   <li>5: Atari TOS</li>
     *   <li>6: HPFS file system (OS/2, NT)</li>
     *   <li>7: Macintosh</li>
     *   <li>8: Z-System</li>
     *   <li>9: CP/M</li>
     *   <li>10: TOPS-20</li>
     *   <li>11: NTFS file system (NT)</li>
     *   <li>12: QDOS</li>
     *   <li>13: Acorn RISCOS</li>
     *   <li>255: Unknown</li>
     * </ul>
     *
     * @param operatingSystem the code of the operating system
     */
    public void setOperatingSystem(final int operatingSystem) {
        this.operatingSystem = operatingSystem;
    }
}