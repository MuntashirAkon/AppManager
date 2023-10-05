// SPDX-License-Identifier: GPL-3.0-or-later

package android.provider;

import android.os.Build;

import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(Settings.class)
public final class SettingsHidden {
    public static final class Global {
        /**
         * Whether ADB over Wifi is enabled.
         */
        @RequiresApi(Build.VERSION_CODES.R)
        public static /*final*/ String ADB_WIFI_ENABLED = "adb_wifi_enabled";
    }
}
