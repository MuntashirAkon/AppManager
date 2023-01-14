// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.misc.SystemProperties;

public class MotorolaUtils {
    public static boolean isMotorola() {
        return SystemProperties.getInt("ro.mot.build.version.sdk_int", 0) != 0;
    }

    @Nullable
    public static String getMotorolaVersion() {
        int sdk = SystemProperties.getInt("ro.mot.build.version.sdk_int", 0);
        int increment = SystemProperties.getInt("ro.mot.build.product.increment", 0);
        if (sdk == 0) {
            return null;
        }
        return sdk + "." + increment;
    }
}
