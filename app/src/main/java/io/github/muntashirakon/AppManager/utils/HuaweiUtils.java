// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.os.Build;

public final class HuaweiUtils {
    public static boolean isHuaweiDevice() {
        String manufacturer = Build.MANUFACTURER;
        String brand = Build.BRAND;
        return manufacturer.equalsIgnoreCase("HUAWEI") || brand.equalsIgnoreCase("HUAWEI");
    }

    public static boolean isEmui() {
        String emuiVersion = System.getProperty("ro.build.version.emui");
        return emuiVersion != null && !emuiVersion.isEmpty();
    }

    public static boolean isHarmonyOs() {
        String harmonyVersion = System.getProperty("ro.harmony.version");
        return harmonyVersion != null && !harmonyVersion.isEmpty();
    }

    public  static boolean isStockHuawei() {
        return isHuaweiDevice() && (isHarmonyOs() || isEmui());
    }
}
