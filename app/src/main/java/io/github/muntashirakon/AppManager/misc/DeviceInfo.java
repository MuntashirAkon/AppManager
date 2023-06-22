// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.core.content.pm.PackageInfoCompat;

import java.util.Arrays;
import java.util.Locale;

import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;

public class DeviceInfo {
    public final String[] abis = Build.SUPPORTED_ABIS;
    public final String[] abis32Bits = Build.SUPPORTED_32_BIT_ABIS;
    public final String[] abis64Bits = Build.SUPPORTED_64_BIT_ABIS;
    public final String brand = Build.BRAND;
    public final String buildID = Build.DISPLAY;
    public final String buildVersion = Build.VERSION.INCREMENTAL;
    public final String device = Build.DEVICE;
    public final String hardware = Build.HARDWARE;
    public final String manufacturer = Build.MANUFACTURER;
    public final String model = Build.MODEL;
    public final String product = Build.PRODUCT;
    public final String releaseVersion = Build.VERSION.RELEASE;
    @IntRange(from = 0)
    public final int sdkVersion = Build.VERSION.SDK_INT;
    public final long versionCode;
    public final String versionName;
    public final CharSequence inferredMode;

    public DeviceInfo(@NonNull Context context) {
        PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            packageInfo = null;
        }
        if (packageInfo != null) {
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
            versionName = packageInfo.versionName;
        } else {
            versionCode = -1;
            versionName = null;
        }
        inferredMode = Ops.getInferredMode(context);
    }

    @NonNull
    @Override
    public String toString() {
        return "App version: " + versionName + "\n"
                + "App version code: " + versionCode + "\n"
                + "Android build version: " + buildVersion + "\n"
                + "Android release version: " + releaseVersion + "\n"
                + "Android SDK version: " + sdkVersion + "\n"
                + "Android build ID: " + buildID + "\n"
                + "Device brand: " + brand + "\n"
                + "Device manufacturer: " + manufacturer + "\n"
                + "Device name: " + device + "\n"
                + "Device model: " + model + "\n"
                + "Device product name: " + product + "\n"
                + "Device hardware name: " + hardware + "\n"
                + "ABIs: " + Arrays.toString(abis) + "\n"
                + "ABIs (32bit): " + Arrays.toString(abis32Bits) + "\n"
                + "ABIs (64bit): " + Arrays.toString(abis64Bits) + "\n"
                + "System language: " + Locale.getDefault().toLanguageTag() + "\n"
                + "In-App Language: " + Prefs.Appearance.getLanguage() + "\n"
                + "Mode: " + Ops.getMode() + "\n"
                + "Inferred Mode: " + inferredMode;
    }
}
