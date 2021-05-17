// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.runner.Runner;

public final class SystemProperties {
    @NonNull
    public static String get(@NonNull String key, @NonNull String defaultVal) {
        try {
            return android.os.SystemProperties.get(key, defaultVal);
        } catch (Exception e) {
            Log.w("SystemProperties", "Unable to use SystemProperties.get", e);
            Runner.Result result = Runner.runCommand(new String[]{"getprop", key, defaultVal});
            if (result.isSuccessful()) return result.getOutput().trim();
            else return defaultVal;
        }
    }

    public static boolean getBoolean(@NonNull String key, boolean defaultVal) {
        String val = get(key, String.valueOf(defaultVal));
        if ("1".equals(val)) return true;
        return Boolean.parseBoolean(val);
    }

    public static int getInt(@NonNull String key, int defaultVal) {
        String val = get(key, String.valueOf(defaultVal));
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
