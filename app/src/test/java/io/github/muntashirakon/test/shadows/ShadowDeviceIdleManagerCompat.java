// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.test.shadows;

import androidx.annotation.NonNull;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import io.github.muntashirakon.AppManager.compat.DeviceIdleManagerCompat;

@Implements(DeviceIdleManagerCompat.class)
public class ShadowDeviceIdleManagerCompat {
    @Implementation
    public static boolean isBatteryOptimizedApp(@NonNull String packageName) {
        return true;
    }
}
