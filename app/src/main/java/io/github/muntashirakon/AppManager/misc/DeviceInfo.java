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

package io.github.muntashirakon.AppManager.misc;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Arrays;
import java.util.Locale;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.core.content.pm.PackageInfoCompat;
import io.github.muntashirakon.AppManager.utils.AppPref;

public class DeviceInfo {
    private final String[] abis = Build.SUPPORTED_ABIS;
    private final String[] abis32Bits = Build.SUPPORTED_32_BIT_ABIS;
    private final String[] abis64Bits = Build.SUPPORTED_64_BIT_ABIS;
    private final String brand = Build.BRAND;
    private final String buildID = Build.DISPLAY;
    private final String buildVersion = Build.VERSION.INCREMENTAL;
    private final String device = Build.DEVICE;
    private final String hardware = Build.HARDWARE;
    private final String manufacturer = Build.MANUFACTURER;
    private final String model = Build.MODEL;
    private final String product = Build.PRODUCT;
    private final String releaseVersion = Build.VERSION.RELEASE;
    @IntRange(from = 0)
    private final int sdkVersion = Build.VERSION.SDK_INT;
    private final long versionCode;
    private final String versionName;

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
                + "In-App Language: " + AppPref.get(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR);
    }
}
