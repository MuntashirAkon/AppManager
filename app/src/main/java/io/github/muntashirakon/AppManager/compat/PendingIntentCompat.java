// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.app.PendingIntent;
import android.os.Build;

public class PendingIntentCompat {
    public static final int FLAG_IMMUTABLE;
    public static final int FLAG_MUTABLE;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            FLAG_IMMUTABLE = PendingIntent.FLAG_IMMUTABLE;
        } else FLAG_IMMUTABLE = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            FLAG_MUTABLE = PendingIntent.FLAG_MUTABLE;
        } else FLAG_MUTABLE = 0;
    }

    private PendingIntentCompat() {
    }
}
