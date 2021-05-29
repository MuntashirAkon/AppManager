// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.permission.SplitPermissionInfoParcelable;
import android.os.Build;
import android.os.RemoteException;
import android.permission.IPermissionManager;
import android.util.SparseArray;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;

public class PermissionCompat {
    public static final int FLAG_PERMISSION_NONE = 0;

    /**
     * Permission flag: The permission is set in its current state
     * by the user and apps can still request it at runtime.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    public static final int FLAG_PERMISSION_USER_SET = 1;

    /**
     * Permission flag: The permission is set in its current state
     * by the user and it is fixed, i.e. apps can no longer request
     * this permission.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    public static final int FLAG_PERMISSION_USER_FIXED = 1 << 1;

    /**
     * Permission flag: The permission is set in its current state
     * by device policy and neither apps nor the user can change
     * its state.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    public static final int FLAG_PERMISSION_POLICY_FIXED = 1 << 2;

    /**
     * Permission flag: The permission is set in a granted state but
     * access to resources it guards is restricted by other means to
     * enable revoking a permission on legacy apps that do not support
     * runtime permissions. If this permission is upgraded to runtime
     * because the app was updated to support runtime permissions, the
     * the permission will be revoked in the upgrade process.
     *
     * @deprecated Renamed to {@link #FLAG_PERMISSION_REVOKED_COMPAT}.
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    public static final int FLAG_PERMISSION_REVOKE_ON_UPGRADE = 1 << 3;

    /**
     * Permission flag: The permission is set in its current state
     * because the app is a component that is a part of the system.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    public static final int FLAG_PERMISSION_SYSTEM_FIXED = 1 << 4;

    /**
     * Permission flag: The permission is granted by default because it
     * enables app functionality that is expected to work out-of-the-box
     * for providing a smooth user experience. For example, the phone app
     * is expected to have the phone permission.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    public static final int FLAG_PERMISSION_GRANTED_BY_DEFAULT = 1 << 5;

    /**
     * Permission flag: The permission has to be reviewed before any of
     * the app components can run.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    public static final int FLAG_PERMISSION_REVIEW_REQUIRED = 1 << 6;

    /**
     * Permission flag: The permission has not been explicitly requested by
     * the app but has been added automatically by the system. Revoke once
     * the app does explicitly request it.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int FLAG_PERMISSION_REVOKE_WHEN_REQUESTED = 1 << 7;

    /**
     * Permission flag: The permission's usage should be made highly visible to the user
     * when granted.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED = 1 << 8;

    /**
     * Permission flag: The permission's usage should be made highly visible to the user
     * when denied.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED = 1 << 9;

    /**
     * Permission flag: The permission is restricted but the app is exempt
     * from the restriction and is allowed to hold this permission in its
     * full form and the exemption is provided by the installer on record.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int FLAG_PERMISSION_RESTRICTION_INSTALLER_EXEMPT = 1 << 11;

    /**
     * Permission flag: The permission is restricted but the app is exempt
     * from the restriction and is allowed to hold this permission in its
     * full form and the exemption is provided by the system due to its
     * permission policy.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT = 1 << 12;

    /**
     * Permission flag: The permission is restricted but the app is exempt
     * from the restriction and is allowed to hold this permission and the
     * exemption is provided by the system when upgrading from an OS version
     * where the permission was not restricted to an OS version where the
     * permission is restricted.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int FLAG_PERMISSION_RESTRICTION_UPGRADE_EXEMPT = 1 << 13;


    /**
     * Permission flag: The permission is disabled but may be granted. If
     * disabled the data protected by the permission should be protected
     * by a no-op (empty list, default error, etc) instead of crashing the
     * client.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int FLAG_PERMISSION_APPLY_RESTRICTION = 1 << 14;

    /**
     * Permission flag: The permission is granted because the application holds a role.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int FLAG_PERMISSION_GRANTED_BY_ROLE = 1 << 15;

    /**
     * Permission flag: The permission should have been revoked but is kept granted for
     * compatibility. The data protected by the permission should be protected by a no-op (empty
     * list, default error, etc) instead of crashing the client. The permission will be revoked if
     * the app is upgraded to supports it.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    public static final int FLAG_PERMISSION_REVOKED_COMPAT = FLAG_PERMISSION_REVOKE_ON_UPGRADE;

    /**
     * Permission flag: The permission is one-time and should be revoked automatically on app
     * inactivity
     */
    @RequiresApi(Build.VERSION_CODES.R)
    public static final int FLAG_PERMISSION_ONE_TIME = 1 << 16;

    /**
     * Permission flag: Whether permission was revoked by auto-revoke.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    public static final int FLAG_PERMISSION_AUTO_REVOKED = 1 << 17;

    /**
     * Permission flags: Reserved for use by the permission controller. The platform and any
     * packages besides the permission controller should not assume any definition about these
     * flags.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    public static final int FLAGS_PERMISSION_RESERVED_PERMISSION_CONTROLLER = 1 << 28 | 1 << 29
            | 1 << 30 | 1 << 31;

    /**
     * Permission flags: Bitwise or of all permission flags allowing an
     * exemption for a restricted permission.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int FLAGS_PERMISSION_RESTRICTION_ANY_EXEMPT = FLAG_PERMISSION_RESTRICTION_INSTALLER_EXEMPT
            | FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT
            | FLAG_PERMISSION_RESTRICTION_UPGRADE_EXEMPT;

    /**
     * Mask for all permission flags.
     */
    @PermissionFlags
    public static final int MASK_PERMISSION_FLAGS_ALL;

    static {
        int allPerms = FLAG_PERMISSION_NONE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            allPerms |= FLAG_PERMISSION_USER_SET
                    | FLAG_PERMISSION_USER_FIXED
                    | FLAG_PERMISSION_POLICY_FIXED
                    | FLAG_PERMISSION_REVOKE_ON_UPGRADE
                    | FLAG_PERMISSION_SYSTEM_FIXED
                    | FLAG_PERMISSION_GRANTED_BY_DEFAULT;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            allPerms |= FLAG_PERMISSION_REVIEW_REQUIRED;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            allPerms |= FLAG_PERMISSION_REVOKE_WHEN_REQUESTED
                    | FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED
                    | FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED
                    | FLAG_PERMISSION_RESTRICTION_INSTALLER_EXEMPT
                    | FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT
                    | FLAG_PERMISSION_RESTRICTION_UPGRADE_EXEMPT
                    | FLAG_PERMISSION_APPLY_RESTRICTION
                    | FLAG_PERMISSION_GRANTED_BY_ROLE;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            allPerms |= FLAG_PERMISSION_REVOKED_COMPAT
                    | FLAG_PERMISSION_ONE_TIME
                    | FLAG_PERMISSION_AUTO_REVOKED;
        }
        MASK_PERMISSION_FLAGS_ALL = allPerms;
    }

    /**
     * Permission flags set when granting or revoking a permission.
     */
    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.M)
    @IntDef(flag = true, value = {
            FLAG_PERMISSION_NONE,
            FLAG_PERMISSION_USER_SET,
            FLAG_PERMISSION_USER_FIXED,
            FLAG_PERMISSION_POLICY_FIXED,
//            FLAG_PERMISSION_REVOKE_ON_UPGRADE,
            FLAG_PERMISSION_SYSTEM_FIXED,
            FLAG_PERMISSION_GRANTED_BY_DEFAULT,
            FLAG_PERMISSION_REVIEW_REQUIRED,
            FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED,
            FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED,
            FLAG_PERMISSION_REVOKE_WHEN_REQUESTED,
            FLAG_PERMISSION_RESTRICTION_UPGRADE_EXEMPT,
            FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT,
            FLAG_PERMISSION_RESTRICTION_INSTALLER_EXEMPT,
            FLAG_PERMISSION_APPLY_RESTRICTION,
            FLAG_PERMISSION_GRANTED_BY_ROLE,
            FLAG_PERMISSION_REVOKED_COMPAT,
            FLAG_PERMISSION_ONE_TIME,
            FLAG_PERMISSION_AUTO_REVOKED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PermissionFlags {
    }

    @SuppressWarnings("deprecation")
    @PermissionFlags
    public static int getPermissionFlags(@NonNull String permissionName,
                                         @NonNull String packageName,
                                         @UserIdInt int userId) throws RemoteException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            IPermissionManager permissionManager = getPermissionManager();
            return permissionManager.getPermissionFlags(permissionName, packageName, userId);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return AppManager.getIPackageManager().getPermissionFlags(permissionName, packageName, userId);
        } else return FLAG_PERMISSION_NONE;
    }

    /**
     * Replace a set of flags with another or {@code 0}
     *
     * @param flagMask   The flags to be replaced
     * @param flagValues The new flags to set (is a subset of flagMask)
     * @see <a href="https://cs.android.com/android/platform/superproject/+/master:cts/tests/tests/permission/src/android/permission/cts/PermissionFlagsTest.java">PermissionFlagsTest.java</a>
     */
    @SuppressWarnings("deprecation")
    public static void updatePermissionFlags(@NonNull String permissionName,
                                             @NonNull String packageName,
                                             @PermissionFlags int flagMask,
                                             @PermissionFlags int flagValues,
                                             boolean checkAdjustPolicyFlagPermission,
                                             @UserIdInt int userId) throws RemoteException {
        IPackageManager pm = AppManager.getIPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            IPermissionManager permissionManager = getPermissionManager();
            permissionManager.updatePermissionFlags(permissionName, packageName, flagMask, flagValues,
                    checkAdjustPolicyFlagPermission, userId);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pm.updatePermissionFlags(permissionName, packageName, flagMask, flagValues,
                    checkAdjustPolicyFlagPermission, userId);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.updatePermissionFlags(permissionName, packageName, flagMask, flagValues, userId);
        }
    }

    @SuppressWarnings("deprecation")
    public static void grantPermission(@NonNull String packageName,
                                       @NonNull String permissionName,
                                       @UserIdInt int userId)
            throws RemoteException {
        IPackageManager pm = AppManager.getIPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            IPermissionManager permissionManager = getPermissionManager();
            permissionManager.grantRuntimePermission(packageName, permissionName, userId);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.grantRuntimePermission(packageName, permissionName, userId);
        } else {
            pm.grantPermission(packageName, permissionName);
        }
    }

    public static void revokePermission(@NonNull String packageName,
                                        @NonNull String permissionName,
                                        @UserIdInt int userId) throws RemoteException {
        revokePermission(packageName, permissionName, userId, null);
    }

    @SuppressWarnings("deprecation")
    public static void revokePermission(@NonNull String packageName,
                                        @NonNull String permissionName,
                                        @UserIdInt int userId,
                                        @Nullable String reason) throws RemoteException {
        IPackageManager pm = AppManager.getIPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            IPermissionManager permissionManager = getPermissionManager();
            permissionManager.revokeRuntimePermission(packageName, permissionName, userId, reason);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.revokeRuntimePermission(packageName, permissionName, userId);
        } else {
            pm.revokePermission(packageName, permissionName);
        }
    }

    @SuppressWarnings("deprecation")
    @NonNull
    public static PermissionInfo getPermissionInfo(String permissionName, String packageName, int flags)
            throws RemoteException {
        IPackageManager pm = AppManager.getIPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return getPermissionManager().getPermissionInfo(permissionName, packageName, flags);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return pm.getPermissionInfo(permissionName, packageName, flags);
        } else return pm.getPermissionInfo(permissionName, flags);
    }

    @SuppressWarnings("deprecation")
    @NonNull
    public static PermissionGroupInfo getPermissionGroupInfo(String groupName, int flags) throws RemoteException {
        IPackageManager pm = AppManager.getIPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return getPermissionManager().getPermissionGroupInfo(groupName, flags);
        } else return pm.getPermissionGroupInfo(groupName, flags);
    }

    public static List<SplitPermissionInfoParcelable> getSplitPermissions() throws RemoteException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return getPermissionManager().getSplitPermissions();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return AppManager.getIPackageManager().getSplitPermissions();
        }
        return Collections.emptyList();
    }

    public static boolean getCheckAdjustPolicyFlagPermission(@NonNull ApplicationInfo info) {
        return info.targetSdkVersion >= Build.VERSION_CODES.Q;
    }

    @NonNull
    public static IPermissionManager getPermissionManager() {
        return IPermissionManager.Stub.asInterface(ProxyBinder.getService("permissionmgr"));
    }

    @SuppressLint("WrongConstant")
    @NonNull
    public static SparseArray<String> getPermissionFlagsWithString(@PermissionFlags int flags) {
        SparseArray<String> permissionFlagsWithString = new SparseArray<>();
        for (int i = 0; i < 18; ++i) {
            if ((flags & (1 << i)) != 0) {
                permissionFlagsWithString.put(1 << i, permissionFlagToString((1 << i)));
            }
        }
        return permissionFlagsWithString;
    }

    @SuppressLint("NewApi")
    @NonNull
    public static String permissionFlagToString(@PermissionFlags int flag) {
        switch (flag) {
            case FLAG_PERMISSION_GRANTED_BY_DEFAULT:
                return "GRANTED_BY_DEFAULT";
            case FLAG_PERMISSION_POLICY_FIXED:
                return "POLICY_FIXED";
            case FLAG_PERMISSION_SYSTEM_FIXED:
                return "SYSTEM_FIXED";
            case FLAG_PERMISSION_USER_SET:
                return "USER_SET";
            case FLAG_PERMISSION_USER_FIXED:
                return "USER_FIXED";
            case FLAG_PERMISSION_REVIEW_REQUIRED:
                return "REVIEW_REQUIRED";
            case FLAG_PERMISSION_REVOKE_WHEN_REQUESTED:
                return "REVOKE_WHEN_REQUESTED";
            case FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED:
                return "USER_SENSITIVE_WHEN_GRANTED";
            case FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED:
                return "USER_SENSITIVE_WHEN_DENIED";
            case FLAG_PERMISSION_RESTRICTION_INSTALLER_EXEMPT:
                return "RESTRICTION_INSTALLER_EXEMPT";
            case FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT:
                return "RESTRICTION_SYSTEM_EXEMPT";
            case FLAG_PERMISSION_RESTRICTION_UPGRADE_EXEMPT:
                return "RESTRICTION_UPGRADE_EXEMPT";
            case FLAG_PERMISSION_APPLY_RESTRICTION:
                return "APPLY_RESTRICTION";
            case FLAG_PERMISSION_GRANTED_BY_ROLE:
                return "GRANTED_BY_ROLE";
            case FLAG_PERMISSION_REVOKED_COMPAT:
                return "REVOKED_COMPAT";
            case FLAG_PERMISSION_ONE_TIME:
                return "ONE_TIME";
            case FLAG_PERMISSION_AUTO_REVOKED:
                return "AUTO_REVOKED";
            case FLAG_PERMISSION_NONE:
            default:
                return Integer.toString(flag);
        }
    }
}
