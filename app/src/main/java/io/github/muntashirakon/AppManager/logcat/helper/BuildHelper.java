// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.helper;

import android.os.Build;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

// Copyright 2012 Nolan Lawson
public class BuildHelper {
    // From android.os.Build
    private static final List<String> BUILD_FIELDS = Arrays.asList(
            "BOARD", "BOOTLOADER", "BRAND", "CPU_ABI", "CPU_ABI2",
            "DEVICE", "DISPLAY", "FINGERPRINT", "HARDWARE", "HOST",
            "ID", "MANUFACTURER", "MODEL", "PRODUCT", "RADIO",
            "SERIAL", "TAGS", "TIME", "TYPE", "USER");

    // From android.os.Build.Version
    private static final List<String> BUILD_VERSION_FIELDS = Arrays.asList(
            "CODENAME", "INCREMENTAL", "RELEASE", "SDK_INT");

    @NonNull
    public static String getBuildInformationAsString() {
        SortedMap<String, String> keysToValues = new TreeMap<>();
        for (String buildField : BUILD_FIELDS) {
            putKeyValue(Build.class, buildField, keysToValues);
        }
        for (String buildVersionField : BUILD_VERSION_FIELDS) {
            putKeyValue(Build.VERSION.class, buildVersionField, keysToValues);
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (Entry<String, String> entry : keysToValues.entrySet()) {
            stringBuilder.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        return stringBuilder.toString();
    }

    private static void putKeyValue(@NonNull Class<?> clazz, String buildField,
                                    @NonNull SortedMap<String, String> keysToValues) {
        try {
            Field field = clazz.getField(buildField);
            Object value = field.get(null);
            String key = clazz.getSimpleName().toLowerCase(Locale.ROOT) + "." + buildField.toLowerCase(Locale.ROOT);
            keysToValues.put(key, String.valueOf(value));
        } catch (SecurityException | NoSuchFieldException | IllegalAccessException ignore) {
        }
    }
}
