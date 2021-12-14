// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.app.PendingIntent;
import android.os.Build;

public class PendingIntentCompat {
    public static final int FLAG_IMMUTABLE;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            FLAG_IMMUTABLE = PendingIntent.FLAG_IMMUTABLE;
        } else FLAG_IMMUTABLE = 0;
    }

    private PendingIntentCompat() {
    }
}
