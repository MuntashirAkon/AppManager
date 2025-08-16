// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

public final class IntentUtils {
    @NonNull
    public static Intent getAppDetailsSettings(@NonNull String packageName) {
        return getSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageName);
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM) // Added in r20
    @NonNull
    public static Intent getAppStorageSettings(@NonNull String packageName) {
        return getSettings("com.android.settings.APP_STORAGE_SETTINGS", packageName);
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @NonNull
    public static Intent getNetPolicySettings(@NonNull String packageName) {
        return getSettings(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, packageName);
    }

    @SuppressLint("BatteryLife")
    @RequiresApi(Build.VERSION_CODES.M)
    @NonNull
    public static Intent getBatteryOptSettings(@NonNull String packageName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return getSettings("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL", packageName);
        }
        return getSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, null);
    }

    @NonNull
    public static Intent getSettings(@NonNull String action, @Nullable String packageName) {
        Intent intent = new Intent(action)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        if (packageName != null) {
            if (action.equals(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName);
            } else intent.setData(Uri.parse("package:" + packageName));
        }
        return intent;
    }
}
