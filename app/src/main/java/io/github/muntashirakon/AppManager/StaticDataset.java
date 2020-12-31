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
import android.util.DisplayMetrics;

import java.util.HashMap;
import java.util.Map;

import androidx.collection.ArrayMap;
import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;
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

    public static final String LDPI = "ldpi";
    public static final String MDPI = "mdpi";
    public static final String TVDPI = "tvdpi";
    public static final String HDPI = "hdpi";
    public static final String XHDPI = "xhdpi";
    public static final String XXHDPI = "xxhdpi";
    public static final String XXXHDPI = "xxxhdpi";

    public static final ArrayMap<String, Integer> DENSITY_NAME_TO_DENSITY
            = new ArrayMap<String, Integer>(8) {
        {
            put(LDPI, DisplayMetrics.DENSITY_LOW);
            put(MDPI, DisplayMetrics.DENSITY_MEDIUM);
            put(TVDPI, DisplayMetrics.DENSITY_TV);
            put(HDPI, DisplayMetrics.DENSITY_HIGH);
            put(XHDPI, DisplayMetrics.DENSITY_XHIGH);
            put(XXHDPI, DisplayMetrics.DENSITY_XXHIGH);
            put(XXXHDPI, DisplayMetrics.DENSITY_XXXHIGH);
        }
    };
    public static final int DEVICE_DENSITY;

    static {
        DEVICE_DENSITY = AppManager.getContext().getResources().getDisplayMetrics().densityDpi;
    }

    public static final Map<String, Integer> LOCALE_RANKING = new HashMap<>();

    static {
        Context context = AppManager.getContext();
        LocaleListCompat localeList = ConfigurationCompat.getLocales(context.getResources().getConfiguration());
        for (int i = 0; i < localeList.size(); i++) {
            LOCALE_RANKING.put(localeList.get(i).getLanguage(), i);
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
