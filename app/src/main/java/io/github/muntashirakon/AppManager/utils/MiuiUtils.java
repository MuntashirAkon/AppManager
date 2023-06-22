// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.annotation.SuppressLint;
import android.miui.AppOpsUtils;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.misc.SystemProperties;
import io.github.muntashirakon.AppManager.miui.MiuiVersionInfo;

// Copyright 2020 Aefyr
public class MiuiUtils {
    @Nullable
    private static MiuiVersionInfo sMiuiVersionInfo;

    public static boolean isMiui() {
        return !TextUtils.isEmpty(SystemProperties.get("ro.miui.ui.version.name", ""));
    }

    @Nullable
    public static MiuiVersionInfo getMiuiVersionInfo() {
        if (sMiuiVersionInfo != null) {
            return sMiuiVersionInfo;
        }
        if (!isMiui()) {
            return null;
        }
        String versionString = Build.VERSION.INCREMENTAL;
        if (TextUtils.isDigitsOnly(versionString)) {
            return sMiuiVersionInfo = new MiuiVersionInfo(versionString, null, true);
        }
        if (!versionString.startsWith("V")) {
            throw new IllegalStateException("Stable version must begin with `V`");
        }
        versionString = versionString.substring(1);
        int firstNoDigitIndex = -1;
        final int len = versionString.length();
        for (char cp, i = 0; i < len; ++i) {
            cp = versionString.charAt(i);
            if (cp >= '0' && cp <= '9' || cp == '.') {
                continue;
            }
            // Non-digit
            firstNoDigitIndex = i;
            break;
        }
        return sMiuiVersionInfo = new MiuiVersionInfo(versionString.substring(0, firstNoDigitIndex), versionString.substring(firstNoDigitIndex), false);
    }

    @NonNull
    private static int[] parseVersionIntoParts(@NonNull String version) {
        try {
            String[] versionParts = version.split("\\.");
            int[] intVersionParts = new int[versionParts.length];

            for (int i = 0; i < versionParts.length; i++) {
                intVersionParts[i] = Integer.parseInt(versionParts[i]);
            }

            return intVersionParts;
        } catch (Exception e) {
            return new int[]{-1};
        }
    }

    /**
     * @return 0 if versions are equal, values less than 0 if ver1 is lower than ver2, value more than 0 if ver1 is higher than ver2
     */
    private static int compareVersions(@NonNull String version1, @NonNull String version2) {
        if (version1.equals(version2)) {
            return 0;
        }

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

    public static boolean isActualMiuiVersionAtLeast(@NonNull String targetVersion, @NonNull String targetVersionBeta) {
        MiuiVersionInfo actualVersionInfo = getMiuiVersionInfo();
        if (actualVersionInfo == null) {
            // Not MIUI
            return false;
        }
        String sourceVersion = actualVersionInfo.getMiuiVersion();
        if (sourceVersion == null) {
            // Beta versions do not have MIUI version string.
            // Compare beta versions
            return compareVersions(actualVersionInfo.version, targetVersionBeta) >= 0;
        }
        // This is a stable version
        return compareVersions(sourceVersion, targetVersion) >= 0;
    }

    @SuppressLint("PrivateApi")
    public static boolean isMiuiOptimizationDisabled() {
        // ApplicationPackageManager#isXOptMode()
        if (!SystemProperties.getBoolean("persist.sys.miui_optimization", !"1".equals(SystemProperties.get("ro.miui.cts", "0")))) {
            return true;
        }
        try {
            return AppOpsUtils.isXOptMode();
        } catch (Throwable e) {
            return false;
        }
    }
}