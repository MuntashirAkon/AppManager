// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.app.AppOpsManagerHidden;
import android.os.Build;

public final class CompatUtils {
    private static Boolean sIsAndroid14Beta = null;

    public static boolean isAndroid14AndUp() {
        if (sIsAndroid14Beta == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                sIsAndroid14Beta = Build.VERSION.SDK_INT >= 34 || AppOpsManagerHidden._NUM_OP == 131;
            } else sIsAndroid14Beta = false;
        }
        return sIsAndroid14Beta;
    }
}
