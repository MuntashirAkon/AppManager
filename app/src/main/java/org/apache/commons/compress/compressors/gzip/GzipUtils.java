// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.compressors.gzip;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.compress.compressors.FileNameUtil;

/**
 * Utility code for the gzip compression format.
 * @ThreadSafe
 */
// Copyright 2009 sebbASF
public class GzipUtils {

    private static final FileNameUtil fileNameUtil;

    static {
        // using LinkedHashMap so .tgz is preferred over .taz as
        // compressed extension of .tar as FileNameUtil will use the
        // first one found
        final Map<String, String> uncompressSuffix =
            new LinkedHashMap<>();
        uncompressSuffix.put(".tgz", ".tar");
        uncompressSuffix.put(".taz", ".tar");
        uncompressSuffix.put(".svgz", ".svg");
        uncompressSuffix.put(".cpgz", ".cpio");
        uncompressSuffix.put(".wmz", ".wmf");
        uncompressSuffix.put(".emz", ".emf");
        uncompressSuffix.put(".gz", "");
        uncompressSuffix.put(".z", "");
        uncompressSuffix.put("-gz", "");
        uncompressSuffix.put("-z", "");
        uncompressSuffix.put("_z", "");
        fileNameUtil = new FileNameUtil(uncompressSuffix, ".gz");
    }

    /** Private constructor to prevent instantiation of this utility class. */
    private GzipUtils() {
    }

    /**
     * Detects common gzip suffixes in the given file name.
     *
     * @param fileName name of a file
     * @return {@code true} if the file name has a common gzip suffix,
     *         {@code false} otherwise
     */
    public static boolean isCompressedFilename(final String fileName) {
        return fileNameUtil.isCompressedFilename(fileName);
    }

    /**
     * Maps the given name of a gzip-compressed file to the name that the
     * file should have after uncompression. Commonly used file type specific
     * suffixes like ".tgz" or ".svgz" are automatically detected and
     * correctly mapped. For example the name "package.tgz" is mapped to
     * "package.tar". And any file names with the generic ".gz" suffix
     * (or any other generic gzip suffix) is mapped to a name without that
     * suffix. If no gzip suffix is detected, then the file name is returned
     * unmapped.
     *
     * @param fileName name of a file
     * @return name of the corresponding uncompressed file
     */
    public static String getUncompressedFilename(final String fileName) {
        return fileNameUtil.getUncompressedFilename(fileName);
    }

    /**
     * Maps the given file name to the name that the file should have after
     * compression with gzip. Common file types with custom suffixes for
     * compressed versions are automatically detected and correctly mapped.
     * For example the name "package.tar" is mapped to "package.tgz". If no
     * custom mapping is applicable, then the default ".gz" suffix is appended
     * to the file name.
     *
     * @param fileName name of a file
     * @return name of the corresponding compressed file
     */
    public static String getCompressedFilename(final String fileName) {
        return fileNameUtil.getCompressedFilename(fileName);
    }

}