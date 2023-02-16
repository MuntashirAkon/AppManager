// SPDX-License-Identifier: GPL-3.0-or-later

package androidx.appcompat.app;

import android.content.Context;

import androidx.annotation.NonNull;

public class PublicTwilightManager {
    public static boolean isNight(@NonNull Context context) {
        return TwilightManager.getInstance(context).isNight();
    }
}
