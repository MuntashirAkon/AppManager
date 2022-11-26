// SPDX-License-Identifier: MIT

package io.github.muntashirakon.test.shadows;

import android.system.OsConstants;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.lang.reflect.Field;
import java.util.regex.Pattern;

@Implements(value = OsConstants.class, minSdk = 21)
public final class ShadowOsConstants {
  private static final Pattern ERRNO_PATTERN = Pattern.compile("E[A-Z0-9]+");

  @Implementation
  public static void initConstants() {
    int errnos = 1;
    try {
      for (Field field : OsConstants.class.getDeclaredFields()) {
        final String fieldName = field.getName();

        if (ERRNO_PATTERN.matcher(fieldName).matches() && field.getType() == int.class) {
          field.setInt(null, errnos++);
        }
        // Type of file.
        if (fieldName.equals(OsConstantsValues.S_IFMT)) {
          field.setInt(null, OsConstantsValues.S_IFMT_VALUE);
          continue;
        }
        // Directory.
        if (fieldName.equals(OsConstantsValues.S_IFDIR)) {
          field.setInt(null, OsConstantsValues.S_IFDIR_VALUE);
          continue;
        }
        // Regular file.
        if (fieldName.equals(OsConstantsValues.S_IFREG)) {
          field.setInt(null, OsConstantsValues.S_IFREG_VALUE);
          continue;
        }
        // Symbolic link.
        if (fieldName.equals(OsConstantsValues.S_IFLNK)) {
          field.setInt(null, OsConstantsValues.S_IFLNK_VALUE);
        }

        // File open modes.
        if (OsConstantsValues.OPEN_MODE_VALUES.containsKey(fieldName)) {
          field.setInt(null, OsConstantsValues.OPEN_MODE_VALUES.get(fieldName));
        }
      }
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}