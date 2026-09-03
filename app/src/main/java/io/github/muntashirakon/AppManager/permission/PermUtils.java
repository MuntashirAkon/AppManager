// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import android.app.AppOpsManager;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.UserHandleHidden;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.annotation.WorkerThread;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;

public class PermUtils {
    /**
     * Grant the permission.
     *
     * <p>This also automatically grants app op if it has app op.
     *
     * @param setByTheUser   If the user has made the decision. This does not unset the flag
     * @param fixedByTheUser If the user requested that she/he does not want to be asked again
     */
    @RequiresPermission(allOf = {
            "android.permission.MANAGE_APP_OPS_MODES",
            ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS,
            ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS,
    })
    @WorkerThread
    public static void grantPermission(@NonNull PackageInfo packageInfo,
                                       @NonNull Permission permission,
                                       @NonNull AppOpsManagerCompat appOpsManager,
                                       boolean setByTheUser,
                                       boolean fixedByTheUser) throws PermissionException {
        int userId = UserHandleHidden.getUserId(packageInfo.applicationInfo.uid);
        PermissionContext context = new PermissionContext(packageInfo, permission, userId,
                appOpsManager, setByTheUser, fixedByTheUser);
        PermissionChangeResult result = PermissionControllerRegistry.getInstance()
                .resolve(context)
                .setGranted(context, true);
        if (!result.isSuccessful()) {
            throw new PermissionException(result.getMessage());
        }
    }

    /**
     * Revoke the permission.
     *
     * <p>This also disallows the app op for the permission if it has app op.
     *
     * @param fixedByTheUser If the user requested that she/he does not want to be asked again
     */
    @RequiresPermission(allOf = {
            "android.permission.MANAGE_APP_OPS_MODES",
            ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS,
            ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS,
    })
    @WorkerThread
    public static void revokePermission(@NonNull PackageInfo packageInfo,
                                        @NonNull Permission permission,
                                        @NonNull AppOpsManagerCompat appOpsManager,
                                        boolean fixedByTheUser) throws PermissionException {
        int userId = UserHandleHidden.getUserId(packageInfo.applicationInfo.uid);
        PermissionContext context = new PermissionContext(packageInfo, permission, userId,
                appOpsManager, true, fixedByTheUser);
        PermissionChangeResult result = PermissionControllerRegistry.getInstance()
                .resolve(context)
                .setGranted(context, false);
        if (!result.isSuccessful()) {
            throw new PermissionException(result.getMessage());
        }
    }

    /**
     * @return {@code true} iff app-op was changed
     */
    @RequiresPermission("android.permission.MANAGE_APP_OPS_MODES")
    public static boolean allowAppOp(AppOpsManagerCompat appOpsManager, int appOp, String packageName, int uid)
            throws PermissionException {
        return setAppOpMode(appOpsManager, appOp, packageName, uid, AppOpsManager.MODE_ALLOWED);
    }

    /**
     * @return {@code true} iff app-op was changed
     */
    @RequiresPermission("android.permission.MANAGE_APP_OPS_MODES")
    public static boolean disallowAppOp(AppOpsManagerCompat appOpsManager, int appOp, String packageName, int uid)
            throws PermissionException {
        return setAppOpMode(appOpsManager, appOp, packageName, uid, AppOpsManager.MODE_IGNORED);
    }

    /**
     * Set mode of an app-op if needed.
     *
     * @return {@code true} iff app-op was changed
     */
    @RequiresPermission("android.permission.MANAGE_APP_OPS_MODES")
    public static boolean setAppOpMode(@NonNull AppOpsManagerCompat appOpsManager,
                                       int appOp,
                                       String packageName,
                                       int uid,
                                       @AppOpsManagerCompat.Mode int mode) throws PermissionException {
        return AppOpPermissionController
                .getInstance()
                .setAppOpMode(appOpsManager, appOp, packageName, uid, mode);
    }

    public static boolean systemSupportsRuntimePermissions() {
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1;
    }

    @AnyThread
    public static boolean isModifiable(@NonNull Permission permission) {
        return PermissionControllerRegistry.getInstance().isModifiable(permission);
    }
}
