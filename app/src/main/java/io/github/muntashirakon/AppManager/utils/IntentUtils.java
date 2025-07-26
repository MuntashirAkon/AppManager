// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.NonNull;

public final class IntentUtils {
    @NonNull
    public static Intent getAppDetailsSettings(@NonNull String packageName) {
        return getSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageName);
    }

    @NonNull
    public static Intent getSettings(@NonNull String action, String packageName) {
        return new Intent(action)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .setData(Uri.parse("package:" + packageName));
    }
}
