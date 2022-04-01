// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import android.content.res.Resources;
import android.util.DisplayMetrics;

import androidx.collection.ArrayMap;
import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;

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
        DEVICE_DENSITY = Resources.getSystem().getDisplayMetrics().densityDpi;
    }

    public static final Map<String, Integer> LOCALE_RANKING = new HashMap<>();

    static {
        LocaleListCompat localeList = ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration());
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
