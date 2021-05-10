/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.utils;

import android.annotation.SuppressLint;
import android.miui.AppOpsUtils;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.misc.SystemProperties;

public class MiuiUtils {

    public static boolean isMiui() {
        return !TextUtils.isEmpty(SystemProperties.get("ro.miui.ui.version.name", ""));
    }

    @NonNull
    public static String getMiuiVersionName() {
        String versionName = SystemProperties.get("ro.miui.ui.version.name", "");
        return !TextUtils.isEmpty(versionName) ? versionName : "";
    }

    public static int getMiuiVersionCode() {
        return SystemProperties.getInt("ro.miui.ui.version.code", 0);
    }

    public static String getActualMiuiVersion() {
        return Build.VERSION.INCREMENTAL;
    }

    private static int[] parseVersionIntoParts(String version) {
        try {
            String[] versionParts = version.split("\\.");
            int[] intVersionParts = new int[versionParts.length];

            for (int i = 0; i < versionParts.length; i++)
                intVersionParts[i] = Integer.parseInt(versionParts[i]);

            return intVersionParts;
        } catch (Exception e) {
            return new int[]{-1};
        }
    }

    /**
     * @return 0 if versions are equal, values less than 0 if ver1 is lower than ver2, value more than 0 if ver1 is higher than ver2
     */
    private static int compareVersions(String version1, String version2) {
        if (version1.equals(version2))
            return 0;

        int[] version1Parts = parseVersionIntoParts(version1);
        int[] version2Parts = parseVersionIntoParts(version2);

        for (int i = 0; i < version2Parts.length; i++) {
            if (i >= version1Parts.length)
                return -1;

            if (version1Parts[i] < version2Parts[i])
                return -1;

            if (version1Parts[i] > version2Parts[i])
                return 1;
        }

        return 1;
    }

    public static boolean isActualMiuiVersionAtLeast(String targetVer) {
        return compareVersions(getActualMiuiVersion(), targetVer) >= 0;
    }

    @SuppressLint("PrivateApi")
    public static boolean isMiuiOptimizationDisabled() {
        if (!SystemProperties.getBoolean("persist.sys.miui_optimization", true)) {
            return true;
        }
        try {
            return AppOpsUtils.isXOptMode();
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean isFixedMiui() {
        return isActualMiuiVersionAtLeast("20.2.20") || isMiuiOptimizationDisabled();
    }
}