/*
 * Copyright (c) 2021 Muntashir Al-Islam
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
