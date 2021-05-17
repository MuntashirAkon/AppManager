// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.compressors.bzip2;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.compress.compressors.FileNameUtil;

/**
 * Utility code for the BZip2 compression format.
 * @ThreadSafe
 * @since 1.1
 */
// Copyright 2017 Stefan Bodewig
public abstract class BZip2Utils {

    private static final FileNameUtil fileNameUtil;

    static {
        final Map<String, String> uncompressSuffix =
            new LinkedHashMap<>();
        // backwards compatibility: BZip2Utils never created the short
        // tbz form, so .tar.bz2 has to be added explicitly
        uncompressSuffix.put(".tar.bz2", ".tar");
        uncompressSuffix.put(".tbz2", ".tar");
        uncompressSuffix.put(".tbz", ".tar");
        uncompressSuffix.put(".bz2", "");
        uncompressSuffix.put(".bz", "");
        fileNameUtil = new FileNameUtil(uncompressSuffix, ".bz2");
    }

    /** Private constructor to prevent instantiation of this utility class. */
    private BZip2Utils() {
    }

    /**
     * Detects common bzip2 suffixes in the given file name.
     *
     * @param fileName name of a file
     * @return {@code true} if the file name has a common bzip2 suffix,
     *         {@code false} otherwise
     */
    public static boolean isCompressedFilename(final String fileName) {
        return fileNameUtil.isCompressedFilename(fileName);
    }

    /**
     * Maps the given name of a bzip2-compressed file to the name that the
     * file should have after uncompression. Commonly used file type specific
     * suffixes like ".tbz" or ".tbz2" are automatically detected and
     * correctly mapped. For example the name "package.tbz2" is mapped to
     * "package.tar". And any file names with the generic ".bz2" suffix
     * (or any other generic bzip2 suffix) is mapped to a name without that
     * suffix. If no bzip2 suffix is detected, then the file name is returned
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
     * compression with bzip2. Currently this method simply appends the suffix
     * ".bz2" to the file name based on the standard behavior of the "bzip2"
     * program, but a future version may implement a more complex mapping if
     * a new widely used naming pattern emerges.
     *
     * @param fileName name of a file
     * @return name of the corresponding compressed file
     */
    public static String getCompressedFilename(final String fileName) {
        return fileNameUtil.getCompressedFilename(fileName);
    }

}