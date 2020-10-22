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

package io.github.muntashirakon.AppManager;

import android.content.Context;
import android.os.Build;
import android.os.LocaleList;
import android.util.DisplayMetrics;

import java.util.HashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.misc.VMRuntime;

public class StaticDataset {
    private static String[] trackerCodeSignatures;
    private static String[] trackerNames;

    public static final String ARMEABI_V7A = "armeabi_v7a";
    public static final String ARM64_V8A = "arm64_v8a";
    public static final String X86 = "x86";
    public static final String X86_64 = "x86_64";

    public static Map<String, String> ALL_ABIS = new HashMap<>();

    static {
        ALL_ABIS.put(ARMEABI_V7A, VMRuntime.ABI_ARMEABI_V7A);
        ALL_ABIS.put(ARM64_V8A, VMRuntime.ABI_ARM64_V8A);
        ALL_ABIS.put(X86, VMRuntime.ABI_X86);
        ALL_ABIS.put(X86_64, VMRuntime.ABI_X86_64);
    }

    private static final String LDPI = "ldpi";
    private static final String MDPI = "mdpi";
    private static final String TVDPI = "tvdpi";
    private static final String HDPI = "hdpi";
    private static final String XHDPI = "xhdpi";
    private static final String XXHDPI = "xxhdpi";
    private static final String XXXHDPI = "xxxhdpi";

    public static final Map<String, Integer> DENSITY_NAME_TO_DENSITY = new HashMap<>();
    public static final int DEVICE_DENSITY;

    static {
        DENSITY_NAME_TO_DENSITY.put(LDPI, DisplayMetrics.DENSITY_LOW);
        DENSITY_NAME_TO_DENSITY.put(MDPI, DisplayMetrics.DENSITY_MEDIUM);
        DENSITY_NAME_TO_DENSITY.put(TVDPI, DisplayMetrics.DENSITY_TV);
        DENSITY_NAME_TO_DENSITY.put(HDPI, DisplayMetrics.DENSITY_HIGH);
        DENSITY_NAME_TO_DENSITY.put(XHDPI, DisplayMetrics.DENSITY_XHIGH);
        DENSITY_NAME_TO_DENSITY.put(XXHDPI, DisplayMetrics.DENSITY_XXHIGH);
        DENSITY_NAME_TO_DENSITY.put(XXXHDPI, DisplayMetrics.DENSITY_XXXHIGH);

        DEVICE_DENSITY = AppManager.getContext().getResources().getDisplayMetrics().densityDpi;
    }

    public static final Map<String, Integer> LOCALE_RANKING = new HashMap<>();

    static {
        Context context = AppManager.getContext();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            LOCALE_RANKING.put(context.getResources().getConfiguration().locale.getLanguage(), 0);
        } else {
            LocaleList localeList = context.getResources().getConfiguration().getLocales();
            for (int i = 0; i < localeList.size(); i++) {
                LOCALE_RANKING.put(localeList.get(i).getLanguage(), i);
            }
        }
    }

    public static String[] getTrackerCodeSignatures() {
        if (trackerCodeSignatures == null) {
            trackerCodeSignatures = AppManager.getContext().getResources().getStringArray(R.array.tracker_signatures);
        }
        return trackerCodeSignatures;
    }

    public static String[] getTrackerNames() {
        if (trackerNames == null) {
            trackerNames = AppManager.getContext().getResources().getStringArray(R.array.tracker_names);
        }
        return trackerNames;
    }
}
