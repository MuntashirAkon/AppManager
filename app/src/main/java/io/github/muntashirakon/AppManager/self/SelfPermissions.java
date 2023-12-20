// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import android.Manifest;
import android.annotation.UserIdInt;
import android.app.AppOpsManager;
import android.app.AppOpsManagerHidden;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.Paths;

public class SelfPermissions {
    public static final String SHELL_PACKAGE_NAME = "com.android.shell";

    public static void init() {
        if (!canModifyPermissions()) {
            return;
        }
        String[] permissions = new String[]{
                Manifest.permission.DUMP,
                ManifestCompat.permission.GET_APP_OPS_STATS,
                ManifestCompat.permission.INTERACT_ACROSS_USERS,
                Manifest.permission.READ_LOGS,
                Manifest.permission.WRITE_SECURE_SETTINGS
        };
        int userId = UserHandleHidden.myUserId();
        for (String permission : permissions) {
            if (!checkSelfPermission(permission)) {
                try {
                    PermissionCompat.grantPermission(BuildConfig.APPLICATION_ID, permission, userId);
                } catch (Exception ignore) {
                }
            }
        }
        // Grant usage stats permission (both permission and app op needs to be granted)
        if (FeatureController.isUsageAccessEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS)) {
                try {
                    PermissionCompat.grantPermission(BuildConfig.APPLICATION_ID, Manifest.permission.PACKAGE_USAGE_STATS, userId);
                } catch (Exception ignore) {
                }
            }
            try {
                AppOpsManagerCompat appOps = new AppOpsManagerCompat();
                appOps.setMode(AppOpsManagerHidden.OP_GET_USAGE_STATS, Process.myUid(), BuildConfig.APPLICATION_ID, AppOpsManager.MODE_ALLOWED);
            } catch (RemoteException ignore) {
            }
        }
    }

    public static boolean canBlockByIFW() {
        return Paths.get(ComponentsBlocker.SYSTEM_RULES_PATH).canWrite();
    }

    public static boolean canWriteToDataData() {
        return Paths.get("/data/data").canWrite();
    }

    public static boolean canModifyAppComponentStates(@UserIdInt int userId, @Nullable String packageName,
                                                      boolean testOnlyApp) {
        if (!checkCrossUserPermission(userId, false)) {
            return false;
        }
        final int callingUid = Users.getSelfOrRemoteUid();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Since Oreo, shell can only disable components of test only apps
            if (callingUid == Ops.SHELL_UID && !testOnlyApp) {
                return false;
            }
        }
        if (BuildConfig.APPLICATION_ID.equals(packageName)) {
            // We can change components for this package
            return true;
        }
        return checkSelfOrRemotePermission(Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE, callingUid);
    }

    public static boolean canModifyAppOpMode() {
        int callingUid = Users.getSelfOrRemoteUid();
        boolean canModify = checkSelfOrRemotePermission(ManifestCompat.permission.UPDATE_APP_OPS_STATS, callingUid);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            canModify &= checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_APP_OPS_MODES, callingUid);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                canModify &= checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_APPOPS, callingUid);
            }
        }
        return canModify;
    }

    public static boolean canModifyPermissions() {
        int callingUid = Users.getSelfOrRemoteUid();
        return checkSelfOrRemotePermission(ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS, callingUid)
                || checkSelfOrRemotePermission(ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS, callingUid);
    }

    public static boolean checkGetGrantRevokeRuntimePermissions() {
        int callingUid = Users.getSelfOrRemoteUid();
        return checkSelfOrRemotePermission(ManifestCompat.permission.GET_RUNTIME_PERMISSIONS, callingUid)
                || checkSelfOrRemotePermission(ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS, callingUid)
                || checkSelfOrRemotePermission(ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS, callingUid);
    }

    public static boolean canInstallExistingPackages() {
        int callingUid = Users.getSelfOrRemoteUid();
        if (checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES, callingUid)) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return checkSelfOrRemotePermission(ManifestCompat.permission.INSTALL_EXISTING_PACKAGES, callingUid);
        }
        return false;
    }

    public static boolean canFreezeUnfreezePackages() {
        // 1. Suspend (7+): MANAGE_USERS (<= 9), SUSPEND_APPS (>= 9)
        // 2. Disable: CHANGE_COMPONENT_ENABLED_STATE
        // 2. HIDE: MANAGE_USERS
        int callingUid = Users.getSelfOrRemoteUid();
        boolean canFreezeUnfreeze = checkSelfOrRemotePermission(Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE, callingUid)
                || checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS, callingUid);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            canFreezeUnfreeze |= checkSelfOrRemotePermission(ManifestCompat.permission.SUSPEND_APPS, callingUid);
        }
        return canFreezeUnfreeze;
    }

    public static boolean canClearAppCache() {
        int callingUid = Users.getSelfOrRemoteUid();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return checkSelfOrRemotePermission(ManifestCompat.permission.INTERNAL_DELETE_CACHE_FILES, callingUid);
        }
        return checkSelfOrRemotePermission(Manifest.permission.DELETE_CACHE_FILES, callingUid);
    }

    public static boolean canKillUid() {
        int callingUid = Users.getSelfOrRemoteUid();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfOrRemotePermission(ManifestCompat.permission.KILL_UID, callingUid);
        }
        return callingUid == Ops.SYSTEM_UID;
    }

    public static boolean checkNotificationListenerAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            return false;
        }
        int callingUid = Users.getSelfOrRemoteUid();
        if (checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_NOTIFICATION_LISTENERS, callingUid)) {
            return true;
        }
        return callingUid == Ops.ROOT_UID || callingUid == Ops.SYSTEM_UID || callingUid == Ops.PHONE_UID;
    }

    public static boolean checkUsageStatsPermission() {
        AppOpsManagerCompat appOps = new AppOpsManagerCompat();
        int callingUid = Users.getSelfOrRemoteUid();
        if (callingUid == Ops.ROOT_UID || callingUid == Ops.SYSTEM_UID) {
            return true;
        }
        int mode = appOps.checkOpNoThrow(AppOpsManagerHidden.OP_GET_USAGE_STATS, callingUid, getCallingPackage(callingUid));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mode == AppOpsManager.MODE_DEFAULT) {
            return checkSelfOrRemotePermission(Manifest.permission.PACKAGE_USAGE_STATS, callingUid);
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static boolean checkSelfStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Utils.isRoboUnitTest()) {
                return false;
            }
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public static boolean checkStoragePermission() {
        int callingUid = Users.getSelfOrRemoteUid();
        if (callingUid == Ops.ROOT_UID) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            String packageName = getCallingPackage(callingUid);
            AppOpsManagerCompat appOps = new AppOpsManagerCompat();
            int opMode = appOps.checkOpNoThrow(AppOpsManagerHidden.OP_MANAGE_EXTERNAL_STORAGE, callingUid, packageName);
            switch (opMode) {
                case AppOpsManager.MODE_DEFAULT:
                    return checkSelfOrRemotePermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE, callingUid);
                case AppOpsManager.MODE_ALLOWED:
                    return true;
                case AppOpsManager.MODE_ERRORED:
                case AppOpsManager.MODE_IGNORED:
                    return false;
                default:
                    throw new IllegalStateException("Unknown AppOpsManager mode " + opMode);
            }
        }
        return checkSelfOrRemotePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, callingUid);
    }

    public static boolean checkCrossUserPermission(@UserIdInt int userId, boolean requireFullPermission) {
        int callingUid = Users.getSelfOrRemoteUid();
        return checkCrossUserPermission(userId, requireFullPermission, callingUid);
    }

    public static boolean checkCrossUserPermission(@UserIdInt int userId, boolean requireFullPermission, int callingUid) {
        if (userId == UserHandleHidden.USER_NULL) {
            userId = UserHandleHidden.myUserId();
        }
        if (userId < 0 && userId != UserHandleHidden.USER_ALL) {
            throw new IllegalArgumentException("Invalid userId " + userId);
        }
        if (isSystemOrRootOrShell(callingUid) || userId == UserHandleHidden.getUserId(callingUid)) {
            return true;
        }
        if (requireFullPermission) {
            return checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS_FULL, callingUid);
        }
        return checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS_FULL, callingUid)
                || checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS, callingUid);
    }

    public static boolean isSystem() {
        return Users.getSelfOrRemoteUid() == Ops.SYSTEM_UID;
    }

    public static boolean isSystemOrRoot() {
        int callingUid = Users.getSelfOrRemoteUid();
        return callingUid == Ops.ROOT_UID || callingUid == Ops.SYSTEM_UID;
    }

    public static boolean isSystemOrRootOrShell() {
        return isSystemOrRootOrShell(Users.getSelfOrRemoteUid());
    }

    private static boolean isSystemOrRootOrShell(int callingUid) {
        return callingUid == Ops.ROOT_UID || callingUid == Ops.SYSTEM_UID || callingUid == Ops.SHELL_UID;
    }

    public static boolean checkSelfOrRemotePermission(@NonNull String permissionName) {
        return checkSelfOrRemotePermission(permissionName, Users.getSelfOrRemoteUid());
    }

    public static boolean checkSelfOrRemotePermission(@NonNull String permissionName, int uid) {
        if (uid == Ops.ROOT_UID) {
            // Root UID has all the permissions granted
            return true;
        }
        if (uid != Process.myUid()) {
            try {
                return PackageManagerCompat.getPackageManager().checkUidPermission(permissionName, uid)
                        == PackageManager.PERMISSION_GRANTED;
            } catch (RemoteException ignore) {
            }
        }
        return checkSelfPermission(permissionName);
    }

    public static boolean checkSelfPermission(@NonNull String permissionName) {
        return ContextCompat.checkSelfPermission(ContextUtils.getContext(), permissionName)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requireSelfPermission(@NonNull String permissionName) throws SecurityException {
        if (!checkSelfPermission(permissionName)) {
            throw new SecurityException("App Manager does not have the required permission " + permissionName);
        }
    }

    @NonNull
    public static String getCallingPackage(int callingUid) {
        if (callingUid == Ops.ROOT_UID || callingUid == Ops.SHELL_UID) {
            return SHELL_PACKAGE_NAME;
        }
        if (callingUid == Ops.SYSTEM_UID) {
            return "android";
        }
        return BuildConfig.APPLICATION_ID;
    }
}
