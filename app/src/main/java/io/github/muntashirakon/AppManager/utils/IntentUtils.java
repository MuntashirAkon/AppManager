// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

public final class IntentUtils {
    public static Intent getAppDetailsSettings(String packageName) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .setData(Uri.parse("package:" + packageName));
    }
}
