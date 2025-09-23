
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

public class ShizukuUtils {

    public static boolean isShizukuAvailable() {
        try {
            return Shizuku.isPreV11() ? Shizuku.checkPermission(0) == 0 : Shizuku.checkSelfPermission() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable
    public static ShizukuRemoteProcess runCommand(@NonNull String command) {
        if (!isShizukuAvailable()) {
            return null;
        }
        try {
            return Shizuku.newProcess(new String[]{"sh", "-c", command}, null, null);
        } catch (RemoteException e) {
            return null;
        }
    }
}
