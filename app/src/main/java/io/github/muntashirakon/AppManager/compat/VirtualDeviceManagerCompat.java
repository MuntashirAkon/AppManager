// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public final class VirtualDeviceManagerCompat {
    public static final String PERSISTENT_DEVICE_ID_DEFAULT = "default:" + Context.DEVICE_ID_DEFAULT;
}
