// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import android.Manifest;
import android.annotation.UserIdInt;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.io.Paths;

public class SelfPermissions {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Since Oreo, shell can only disable components of test only apps
            final int callingUid = Users.getSelfOrRemoteUid();
            if (callingUid == Ops.SHELL_UID && !testOnlyApp) {
                return false;
            }
        }
        if (BuildConfig.APPLICATION_ID.equals(packageName)) {
            // We can change components for this package
            return true;
        }
        return checkSelfOrRemotePermission(Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE);
    }

    public static boolean canModifyAppOpMode() {
        boolean canModify = checkSelfOrRemotePermission(ManifestCompat.permission.UPDATE_APP_OPS_STATS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            canModify &= checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_APP_OPS_MODES);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                canModify &= checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_APPOPS);
            }
        }
        return canModify;
    }

    public static boolean canModifyPermissions() {
        return checkSelfOrRemotePermission(ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS)
                || checkSelfOrRemotePermission(ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS);
    }

    public static boolean checkGetGrantRevokeRuntimePermissions() {
        return checkSelfOrRemotePermission(ManifestCompat.permission.GET_RUNTIME_PERMISSIONS)
                || checkSelfOrRemotePermission(ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS)
                || checkSelfOrRemotePermission(ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS);
    }

    public static boolean canInstallExistingPackages() {
        if (checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES)) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return checkSelfOrRemotePermission(ManifestCompat.permission.INSTALL_EXISTING_PACKAGES);
        }
        return false;
    }

    public static boolean canFreezeUnfreezePackages() {
        // 1. Suspend (7+): MANAGE_USERS (<= 9), SUSPEND_APPS (>= 9)
        // 2. Disable: CHANGE_COMPONENT_ENABLED_STATE
        // 2. HIDE: MANAGE_USERS
        boolean canFreezeUnfreeze = checkSelfOrRemotePermission(Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE)
                || checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            canFreezeUnfreeze |= checkSelfOrRemotePermission(ManifestCompat.permission.SUSPEND_APPS);
        }
        return canFreezeUnfreeze;
    }

    public static boolean canClearAppCache() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return checkSelfOrRemotePermission(ManifestCompat.permission.INTERNAL_DELETE_CACHE_FILES);
        }
        return checkSelfOrRemotePermission(Manifest.permission.DELETE_CACHE_FILES);
    }

    public static boolean canKillUid() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfOrRemotePermission(ManifestCompat.permission.KILL_UID);
        }
        return Users.getSelfOrRemoteUid() == Ops.SYSTEM_UID;
    }

    public static boolean checkNotificationListenerAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            return false;
        }
        int callingUid = Users.getSelfOrRemoteUid();
        if (checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_NOTIFICATION_LISTENERS)) {
            return true;
        }
        return callingUid == Ops.ROOT_UID || callingUid == Ops.SYSTEM_UID || callingUid == Ops.PHONE_UID;
    }

    public static boolean checkCrossUserPermission(@UserIdInt int userId, boolean requireFullPermission) {
        if (userId < 0) {
            throw new IllegalArgumentException("Invalid userId " + userId);
        }
        int callingUid = Users.getSelfOrRemoteUid();
        if (isSystemOrRootOrShell(callingUid)) {
            return true;
        }
        if (userId == UserHandleHidden.getUserId(callingUid)) {
            return true;
        }
        if (requireFullPermission) {
            return checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS_FULL);
        }
        return checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS_FULL)
                || checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS);
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
        int uid = Users.getSelfOrRemoteUid();
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
}
