// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.Nullable;

public final class NonNullUtils {
    public static long defeatNullable(@Nullable Long longValue) {
        return longValue == null ? 0 : longValue;
    }

    public static int defeatNullable(@Nullable Integer longValue) {
        return longValue == null ? 0 : longValue;
    }
}
