// SPDX-License-Identifier: MIT

package io.github.muntashirakon.test.shadows;

import com.google.common.collect.ImmutableMap;

import java.io.File;

/**
 * Provides a utility class for OsConstants See
 * https://unix.superglobalmegacorp.com/Net2/newsrc/sys/stat.h.html.
 */
final class OsConstantsValues {

  private OsConstantsValues() {}

  // Type of file.
  public static final String S_IFMT = "S_IFMT";

  // Directory.
  public static final String S_IFDIR = "S_IFDIR";

  // Regular file.
  public static final String S_IFREG = "S_IFREG";

  // Symbolic link.
  public static final String S_IFLNK = "S_IFLNK";

  // Type of file value.
  public static final int S_IFMT_VALUE = 0x0170000;

  // Directory value.
  public static final int S_IFDIR_VALUE = 0x0040000;

  // Regular file value.
  public static final int S_IFREG_VALUE = 0x0100000;

  // Link value.
  public static final int S_IFLNK_VALUE = 0x0120000;

  // File open mode values from
  // https://github.com/torvalds/linux/blob/master/include/uapi/asm-generic/fcntl.h
  static final ImmutableMap<String, Integer> OPEN_MODE_VALUES =
      new ImmutableMap.Builder<String, Integer>()
          .put("O_RDONLY", 0x0000)
          .put("O_WRONLY", 0x0001)
          .put("O_RDWR", 0x0002)
          .put("O_ACCMODE", 0x0003)
          .put("O_CREAT", 0x0100)
          .put("O_EXCL", 0x0200)
          .put("O_TRUNC", 0x1000)
          .put("O_APPEND", 0x2000)
          // access values
          .put("R_OK", 4)
          .put("W_OK", 2)
          .put("X_OK", 8)
          .put("F_OK", 0)
          .build();

  /** Returns the st_mode for the path. */
  public static int getMode(String path) {
    if (path == null) {
      return 0;
    }

    File file = new File(path);
    if (file.isDirectory()) {
      return S_IFDIR_VALUE;
    }
    if (file.isFile()) {
      return S_IFREG_VALUE;
    }
    if (!canonicalize(path).equals(path)) {
      return S_IFLNK_VALUE;
    }
    return 0;
  }

  private static String canonicalize(String path) {
    try {
      return new File(path).getCanonicalPath();
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
}