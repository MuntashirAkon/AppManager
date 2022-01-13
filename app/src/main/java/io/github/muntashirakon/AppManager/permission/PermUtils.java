// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.servermanager.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.servermanager.PermissionCompat;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static io.github.muntashirakon.AppManager.servermanager.PermissionCompat.FLAG_PERMISSION_AUTO_REVOKED;
import static io.github.muntashirakon.AppManager.servermanager.PermissionCompat.FLAG_PERMISSION_ONE_TIME;
import static io.github.muntashirakon.AppManager.servermanager.PermissionCompat.FLAG_PERMISSION_REVIEW_REQUIRED;
import static io.github.muntashirakon.AppManager.servermanager.PermissionCompat.FLAG_PERMISSION_REVOKED_COMPAT;
import static io.github.muntashirakon.AppManager.servermanager.PermissionCompat.FLAG_PERMISSION_USER_FIXED;
import static io.github.muntashirakon.AppManager.servermanager.PermissionCompat.FLAG_PERMISSION_USER_SET;

public class PermUtils {
    private static final String KILL_REASON_APP_OP_CHANGE = "Permission related app op changed";

    /**
     * Grant the permission.
     *
     * <p>This also automatically grants app op if it has app op.
     *
     * @param setByTheUser   If the user has made the decision. This does not unset the flag
     * @param fixedByTheUser If the user requested that she/he does not want to be asked again
     * @return {@code true} iff the permission could be granted.
     */
    @WorkerThread
    public static boolean grantPermission(@NonNull PackageInfo packageInfo,
                                          @NonNull Permission permission,
                                          @NonNull AppOpsService appOpsService,
                                          boolean setByTheUser,
                                          boolean fixedByTheUser)
            throws RemoteException {
        boolean killApp = false;
        boolean wasGranted = permission.isGrantedIncludingAppOp();

        // We toggle permission only to apps that support runtime
        // permissions, otherwise we toggle the app op corresponding
        // to the permission if the permission is granted to the app.
        // Do not touch permissions fixed by the system.
        if (permission.isSystemFixed()) {
            return false;
        }

        if (supportsRuntimePermissions(packageInfo)) {
            // Ensure the permission app op is enabled before the permission grant.
            if (permission.affectsAppOp() && !permission.isAppOpAllowed()) {
                permission.setAppOpAllowed(true);
            }

            // Grant the permission if needed.
            if (!permission.isGranted()) {
                permission.setGranted(true);
            }

            // Update the permission flags.
            if (!fixedByTheUser) {
                // Now the apps can ask for the permission as the user
                // no longer has it fixed in a denied state.
                if (permission.isUserFixed()) {
                    permission.setUserFixed(false);
                }
                if (setByTheUser) {
                    if (!permission.isUserSet()) {
                        permission.setUserSet(true);
                    }
                }
            } else {
                if (!permission.isUserFixed()) {
                    permission.setUserFixed(true);
                }
                if (permission.isUserSet()) {
                    permission.setUserSet(false);
                }
            }
        } else {
            // Legacy apps cannot have a not granted permission but just in case.
            if (!permission.isGranted()) {
                return false;
            }

            // If the permissions has no corresponding app op, then it is a
            // third-party one, and we do not offer toggling of such permissions.
            if (permission.affectsAppOp()) {
                if (!permission.isAppOpAllowed()) {
                    permission.setAppOpAllowed(true);

                    // Legacy apps do not know that they have to retry access to a
                    // resource due to changes in runtime permissions (app ops in this
                    // case). Therefore, we restart them on app op change, so they
                    // can pick up the change.
                    killApp = true;
                }

                // Mark that the permission is not kept granted only for compatibility.
                if (permission.isRevokedCompat()) {
                    permission.setRevokedCompat(false);
                }
            }

            // Granting a permission explicitly means the user already
            // reviewed it so clear the review flag on every grant.
            if (permission.isReviewRequired()) {
                permission.unsetReviewRequired();
            }
        }

        persistChanges(packageInfo, permission, appOpsService, false, null);
        if (killApp) {
            ActivityManagerCompat.killUid(packageInfo.applicationInfo.uid, KILL_REASON_APP_OP_CHANGE);
        }

        return true;
    }

    /**
     * Revoke the permission.
     *
     * <p>This also disallows the app op for the permission if it has app op.
     *
     * @param fixedByTheUser If the user requested that she/he does not want to be asked again
     * @return {@code true} iff the permission could be revoked.
     */
    @WorkerThread
    public static boolean revokePermission(@NonNull PackageInfo packageInfo,
                                           @NonNull Permission permission,
                                           @NonNull AppOpsService appOpsService,
                                           boolean fixedByTheUser)
            throws RemoteException {
        boolean killApp = false;

        // We toggle permissions only to apps that support runtime
        // permissions, otherwise we toggle the app op corresponding
        // to the permission if the permission is granted to the app.
        // Do not touch permissions fixed by the system.
        if (permission.isSystemFixed()) {
            return false;
        }

        if (supportsRuntimePermissions(packageInfo)) {
            // Revoke the permission if needed.
            if (permission.isGranted()) {
                permission.setGranted(false);
            }

            // Update the permission flags.
            if (fixedByTheUser) {
                // Take a note that the user fixed the permission.
                if (permission.isUserSet() || !permission.isUserFixed()) {
                    permission.setUserSet(false);
                    permission.setUserFixed(true);
                }
            } else {
                if (!permission.isUserSet() || permission.isUserFixed()) {
                    permission.setUserSet(true);
                    permission.setUserFixed(false);
                }
            }

            if (permission.affectsAppOp()) {
                permission.setAppOpAllowed(false);
            }
        } else {
            // Legacy apps cannot have a non-granted permission but just in case.
            if (!permission.isGranted()) {
                return false;
            }

            // If the permission has no corresponding app op, then it is a
            // third-party one and we do not offer toggling of such permissions.
            if (permission.affectsAppOp()) {
                if (permission.isAppOpAllowed()) {
                    permission.setAppOpAllowed(false);

                    // Disabling an app op may put the app in a situation in which it
                    // has a handle to state it shouldn't have, so we have to kill the
                    // app. This matches the revoke runtime permission behavior.
                    killApp = true;
                }

                // Mark that the permission is kept granted only for compatibility.
                if (!permission.isRevokedCompat()) {
                    permission.setRevokedCompat(true);
                }
            }
        }

        persistChanges(packageInfo, permission, appOpsService, false, null);

        if (killApp) {
            ActivityManagerCompat.killUid(packageInfo.applicationInfo.uid, KILL_REASON_APP_OP_CHANGE);
        }

        return true;
    }

    @WorkerThread
    private static void persistChanges(@NonNull PackageInfo packageInfo,
                                       @NonNull Permission permission,
                                       @NonNull AppOpsService appOpsService,
                                       boolean mayKillBecauseOfAppOpsChange,
                                       @Nullable String revokeReason)
            throws RemoteException {
        int uid = packageInfo.applicationInfo.uid;
        int userId = UserHandleHidden.getUserId(uid);

        boolean shouldKillApp = false;

        if (!permission.isSystemFixed()) {
            if (permission.isGranted()) {
                PermissionCompat.grantPermission(packageInfo.packageName, permission.getName(), userId);
                Log.d("PERM", "Granted " + permission.getName());
            } else {
                boolean isCurrentlyGranted = PermissionCompat.checkPermission(permission.getName(),
                        packageInfo.packageName, userId) == PERMISSION_GRANTED;

                if (isCurrentlyGranted) {
                    if (revokeReason == null) {
                        PermissionCompat.revokePermission(packageInfo.packageName, permission.getName(), userId);
                    } else {
                        PermissionCompat.revokePermission(packageInfo.packageName, permission.getName(), userId, revokeReason);
                    }
                    Log.d("PERM", "Revoked " + permission.getName());
                }
            }
        }

        int flags = (permission.isUserSet() ? FLAG_PERMISSION_USER_SET : 0)
                | (permission.isUserFixed() ? FLAG_PERMISSION_USER_FIXED : 0)
                | (permission.isRevokedCompat()
                ? FLAG_PERMISSION_REVOKED_COMPAT : 0)
                // | (permission.isPolicyFixed() ? FLAG_PERMISSION_POLICY_FIXED : 0) // TODO: Disabled in AOSP
                | (permission.isReviewRequired()
                ? FLAG_PERMISSION_REVIEW_REQUIRED : 0);

        boolean checkAdjustPolicy = PermissionCompat.getCheckAdjustPolicyFlagPermission(packageInfo.applicationInfo);

        PermissionCompat.updatePermissionFlags(permission.getName(),
                packageInfo.packageName,
                FLAG_PERMISSION_USER_SET
                        | FLAG_PERMISSION_USER_FIXED
                        | FLAG_PERMISSION_REVOKED_COMPAT
                        // | FLAG_PERMISSION_POLICY_FIXED // TODO: Disabled in AOSP
                        | (permission.isReviewRequired()
                        ? 0 : FLAG_PERMISSION_REVIEW_REQUIRED)
                        | FLAG_PERMISSION_ONE_TIME
                        | FLAG_PERMISSION_AUTO_REVOKED, // clear auto revoke
                flags, checkAdjustPolicy, userId);

        if (permission.affectsAppOp()) {
            if (!permission.isSystemFixed()) {
                // Enabling/Disabling an app op may put the app in a situation in which it has
                // a handle to state it shouldn't have, so we have to kill the app. This matches
                // the revoke runtime permission behavior.
                if (permission.isAppOpAllowed()) {
                    boolean wasChanged = allowAppOp(appOpsService, permission.getAppOp(), packageInfo.packageName, uid);
                    shouldKillApp = wasChanged && !supportsRuntimePermissions(packageInfo);
                } else {
                    shouldKillApp = disallowAppOp(appOpsService, permission.getAppOp(), packageInfo.packageName, uid);
                }
            }
        }

        if (mayKillBecauseOfAppOpsChange && shouldKillApp) {
            ActivityManagerCompat.killUid(uid, KILL_REASON_APP_OP_CHANGE);
        }
    }

    public static boolean allowAppOp(AppOpsService appOpsService, int appOp, String packageName, int uid)
            throws RemoteException {
        return setAppOpMode(appOpsService, appOp, packageName, uid, AppOpsManager.MODE_ALLOWED);
    }

    public static boolean disallowAppOp(AppOpsService appOpsService, int appOp, String packageName, int uid)
            throws RemoteException {
        return setAppOpMode(appOpsService, appOp, packageName, uid, AppOpsManager.MODE_IGNORED);
    }

    /**
     * Set mode of an app-op if needed.
     *
     * @return {@code true} iff app-op was changed
     */
    public static boolean setAppOpMode(@NonNull AppOpsService appOpsService,
                                       int appOp,
                                       String packageName,
                                       int uid,
                                       @AppOpsManager.Mode int mode)
            throws RemoteException {
        int currentMode = appOpsService.checkOperation(appOp, uid, packageName);
        if (currentMode == mode) {
            return false;
        }
        appOpsService.setMode(appOp, uid, packageName, mode);
        return true;
    }

    public static boolean supportsRuntimePermissions(@NonNull PackageInfo packageInfo) {
        return packageInfo.applicationInfo.targetSdkVersion > Build.VERSION_CODES.LOLLIPOP_MR1;
    }
}
