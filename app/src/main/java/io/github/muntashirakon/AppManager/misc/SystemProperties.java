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

import android.annotation.SuppressLint;
import android.os.Build;

import com.android.internal.util.TextUtils;

import java.util.HashMap;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.runner.Runner;

public final class SystemProperties {
    @SuppressLint("PrivateApi")
    @NonNull
    public static String get(@NonNull String key, @NonNull String defaultVal) {
        try {
            String value = getInstance().getValue(key, defaultVal);
            if (value.equals(defaultVal)) {
                value = (String) Class.forName("android.os.SystemProperties")
                        .getDeclaredMethod("get", String.class)
                        .invoke(null, key);
                if (value == null) throw new Exception();
            }
            return value;
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

    private static SystemProperties instance;

    public static SystemProperties getInstance() {
        if (instance == null) {
            instance = new SystemProperties();
            instance.reloadProps();
        }
        return instance;
    }

    private final HashMap<String, String> propertyList;

    static {
        try {
            System.loadLibrary("am");
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private SystemProperties() {
        propertyList = new HashMap<>();
    }

    public String getValue(String name, String defaultValue) {
        String value = propertyList.get(name);
        if (TextUtils.isEmpty(value)) return defaultValue;
        return value;
    }

    synchronized public void reloadProps() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                readAndroidPropertiesPost26();
            } else {
                int i = 0;
                while (true) {
                    String[] property = new String[2];
                    if (!readAndroidPropertyPre26(i, property)) {
                        break;
                    }
                    propertyList.put(property[0], property[1]);
                    i++;
                }
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public native void readAndroidPropertiesPost26();

    public native boolean readAndroidPropertyPre26(int n, String[] property);

    @SuppressWarnings("unused")  // Called via JNI
    synchronized public void handleProperty(String key, String value) {
        propertyList.put(key, value);
        Log.e("SP", key + ": " + value);
    }
}
