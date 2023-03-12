// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.settings.Ops;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public final class PermissionUtils {
    public static boolean hasDumpPermission() {
        Context context = AppManager.getContext();
        if (hasSelfPermission(Manifest.permission.DUMP)) {
            return true;
        }
        if (Ops.isPrivileged()) {
            try {
                PermissionCompat.grantPermission(context.getPackageName(), Manifest.permission.DUMP,
                        UserHandleHidden.myUserId());
                return true;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static boolean hasAccessToUsers() {
        Context context = AppManager.getContext();
        if (hasSelfPermission(ManifestCompat.permission.INTERACT_ACROSS_USERS)
                || hasSelfPermission(ManifestCompat.permission.MANAGE_USERS)) {
            return true;
        }
        if (Ops.isPrivileged()) {
            try {
                PermissionCompat.grantPermission(
                        context.getPackageName(),
                        ManifestCompat.permission.INTERACT_ACROSS_USERS,
                        UserHandleHidden.myUserId());
                return true;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Utils.isRoboUnitTest()) {
                return false;
            }
            return Environment.isExternalStorageManager();
        }
        return hasSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public static boolean hasTermuxPermission() {
        return hasSelfPermission(ManifestCompat.permission.TERMUX_RUN_COMMAND);
    }

    public static boolean hasAppOpsPermission() {
        return hasSelfPermission(ManifestCompat.permission.GET_APP_OPS_STATS);
    }

    public static boolean hasInternet() {
        return PermissionUtils.hasSelfPermission(Manifest.permission.INTERNET);
    }

    public static boolean hasSelfPermission(String permissionName) {
        return ContextCompat.checkSelfPermission(ContextUtils.getContext(), permissionName) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings({"deprecation", "InlinedApi"})
    public static boolean hasUsageStatsPermission(@NonNull Context context) {
        AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        assert appOpsManager != null;
        final int mode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(),
                    context.getPackageName());
        } else {
            mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(),
                    context.getPackageName());
        }
        if (mode == AppOpsManager.MODE_DEFAULT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static boolean hasSelfOrRemotePermission(@NonNull String permissionName) {
        int uid = getSelfOrRemoteUid();
        if (uid == 0) {
            // Root UID has all the permissions granted
            return true;
        }
        if (uid != Process.myUid()) {
            try {
                return PackageManagerCompat.getPackageManager().checkUidPermission(permissionName, getSelfOrRemoteUid())
                        == PackageManager.PERMISSION_GRANTED;
            } catch (RemoteException ignore) {
            }
        }
        return hasSelfPermission(permissionName);
    }

    public static int getSelfOrRemoteUid() {
        return ExUtils.requireNonNullElse(() -> LocalServices.getAmService().getUid(), Process.myUid());
    }
}
