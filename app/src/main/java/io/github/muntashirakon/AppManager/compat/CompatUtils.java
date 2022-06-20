// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.content.pm.IPackageManager;
import android.os.Build;

public final class CompatUtils {
    private static Boolean sIsAndroid13Beta = null;

    public static boolean isAndroid13AndUp() {
        if (sIsAndroid13Beta == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                sIsAndroid13Beta = Build.VERSION.SDK_INT >= 33;
                if (!sIsAndroid13Beta) {
                    try {
                        IPackageManager.class.getMethod("getPackageInfo", String.class, long.class, int.class);
                        sIsAndroid13Beta = true;
                    } catch (NoSuchMethodException e) {
                        sIsAndroid13Beta = false;
                    }
                }
            } else sIsAndroid13Beta = false;
        }
        return sIsAndroid13Beta;
    }
}
