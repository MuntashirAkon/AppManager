// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.PermissionCompat;
import io.github.muntashirakon.AppManager.users.Users;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public final class PermissionUtils {
    public static final String TERMUX_PERM_RUN_COMMAND = "com.termux.permission.RUN_COMMAND";
    public static final String PERMISSION_GET_APP_OPS_STATS = "android.permission.GET_APP_OPS_STATS";

    public static boolean hasDumpPermission() {
        Context context = AppManager.getContext();
        if (!hasPermission(context, Manifest.permission.DUMP)) {
            if (LocalServer.isAMServiceAlive()) {
                try {
                    PermissionCompat.grantPermission(context.getPackageName(), Manifest.permission.DUMP,
                            Users.getCurrentUserHandle());
                    return true;
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        } else return true;
        return false;
    }

    public static boolean hasStoragePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return hasPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public static boolean hasTermuxPermission(Context context) {
        return hasPermission(context, TERMUX_PERM_RUN_COMMAND);
    }

    public static boolean hasAppOpsPermission(Context context) {
        return hasPermission(context, PERMISSION_GET_APP_OPS_STATS);
    }

    public static boolean hasPermission(Context context, String permissionName) {
        return ContextCompat.checkSelfPermission(context, permissionName) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings({"deprecation", "InlinedApi"})
    public static boolean hasUsageStatsPermission(@NonNull Context context) {
        AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        assert appOpsManager != null;
        final int mode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.getPackageName());
        } else {
            mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.getPackageName());
        }
        if (mode == AppOpsManager.MODE_DEFAULT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }
}
