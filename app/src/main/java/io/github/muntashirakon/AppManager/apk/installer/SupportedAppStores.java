// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import androidx.annotation.NonNull;

import java.util.HashMap;

public final class SupportedAppStores {
    public static HashMap<String, CharSequence> SUPPORTED_APP_STORES =
            new HashMap<String, CharSequence>() {{
                // Sorted by app label
                put("com.aurora.store", "Aurora Store");
                put("com.looker.droidify", "Droid-ify");
                put("org.fdroid.fdroid", "F-Droid");
                put("org.fdroid.basic", "F-Droid Basic");
                put("eu.bubu1.fdroidclassic", "F-Droid Classic");
                put("com.machiav3lli.fdroid", "Neo Store");
            }};

    public static boolean isAppStoreSupported(@NonNull String packageName) {
        return SUPPORTED_APP_STORES.containsKey(packageName);
    }
}
