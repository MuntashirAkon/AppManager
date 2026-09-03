// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import android.content.pm.PackageInfo;
import android.os.Build;

import androidx.annotation.NonNull;

/**
 * Applies the in-memory part of a permission grant or revoke operation.
 *
 * <p>This class deliberately does not write to PackageManager or AppOps. Keeping the state
 * transition separate from those side effects makes the existing permission behaviour testable
 * while permission controllers are introduced.</p>
 */
final class PermissionMutation {
    private PermissionMutation() {
    }

    /**
     * Prepares {@code permission} for a grant.
     *
     * @return whether the existing behaviour requires killing a legacy app after its AppOp changes
     */
    static boolean prepareGrant(@NonNull PackageInfo packageInfo,
                                @NonNull Permission permission,
                                boolean setByTheUser,
                                boolean fixedByTheUser) throws PermissionException {
        boolean killApp = false;
        if (!permission.isReadOnly()
                && (!permission.isRuntime() || supportsRuntimePermissions(packageInfo))) {
            // Runtime/development permissions. In case of runtime, it is not a pre-23 app.

            // Ensure the permission AppOp is enabled before the permission grant.
            if (permission.affectsAppOp() && !permission.isAppOpAllowed()) {
                permission.setAppOpAllowed(true);
            }

            // Grant the permission if needed.
            if (!permission.isGranted()) {
                permission.setGranted(true);
            }

            // Update the permission flags.
            if (!fixedByTheUser) {
                // The app can ask for the permission again because the user no longer has it fixed
                // in a denied state.
                if (permission.isUserFixed()) {
                    permission.setUserFixed(false);
                }
                if (setByTheUser && !permission.isUserSet()) {
                    permission.setUserSet(true);
                }
            } else {
                if (!permission.isUserFixed()) {
                    permission.setUserFixed(true);
                }
                if (permission.isUserSet()) {
                    permission.setUserSet(false);
                }
            }
        } else { // Read-only or legacy permissions
            // Legacy apps cannot have a non-granted runtime permission, but check just in case.
            ensureLegacyRuntimePermissionIsGranted(permission);

            // A read-only permission without a corresponding AppOp cannot be toggled. Mutability
            // is checked by the caller; if an AppOp exists, it supplies the compatibility control.
            if (permission.affectsAppOp()) {
                if (!permission.isAppOpAllowed()) {
                    permission.setAppOpAllowed(true);

                    // Legacy apps do not know that they must retry access after a runtime
                    // permission compatibility change. Restart them so they can observe the new
                    // AppOp state.
                    killApp = true;
                }

                // Mark that the permission is no longer kept granted only for compatibility.
                if (permission.isRevokedCompat()) {
                    permission.setRevokedCompat(false);
                }
            }

            // Explicitly granting a permission means the user has reviewed it.
            if (permission.isReviewRequired()) {
                permission.unsetReviewRequired();
            }
        }
        return killApp;
    }

    /**
     * Prepares {@code permission} for a revoke.
     *
     * @return whether the existing behaviour requires killing a legacy app after its AppOp changes
     */
    static boolean prepareRevoke(@NonNull PackageInfo packageInfo,
                                 @NonNull Permission permission,
                                 boolean fixedByTheUser) throws PermissionException {
        boolean killApp = false;
        if (!permission.isReadOnly()
                && (!permission.isRuntime() || supportsRuntimePermissions(packageInfo))) {
            // Runtime/development permissions. In case of runtime, it is not a pre-23 app.

            // Revoke the permission if needed.
            if (permission.isGranted()) {
                permission.setGranted(false);
            }

            // Update the permission flags.
            if (fixedByTheUser) {
                // Record that the user does not want to be asked again.
                if (permission.isUserSet() || !permission.isUserFixed()) {
                    permission.setUserSet(false);
                    permission.setUserFixed(true);
                }
            } else if (!permission.isUserSet() || permission.isUserFixed()) {
                permission.setUserSet(true);
                permission.setUserFixed(false);
            }

            // Keep the permission and its AppOp in the same effective state.
            if (permission.affectsAppOp()) {
                permission.setAppOpAllowed(false);
            }
        } else { // Read-only or legacy permissions
            // Legacy apps cannot have a non-granted runtime permission, but check just in case.
            ensureLegacyRuntimePermissionIsGranted(permission);

            // A read-only permission without a corresponding AppOp cannot be toggled. Mutability
            // is checked by the caller; if an AppOp exists, it supplies the compatibility control.
            if (permission.affectsAppOp()) {
                if (permission.isAppOpAllowed()) {
                    permission.setAppOpAllowed(false);

                    // Disabling an AppOp may leave a legacy app holding state it should no longer
                    // access. Restarting it matches the existing runtime-permission behaviour.
                    killApp = true;
                }

                // Mark that the permission remains granted only for compatibility.
                if (!permission.isRevokedCompat()) {
                    permission.setRevokedCompat(true);
                }
            }
        }
        return killApp;
    }

    private static void ensureLegacyRuntimePermissionIsGranted(@NonNull Permission permission)
            throws PermissionException {
        if (permission.isRuntime() && !permission.isGranted()) {
            throw new PermissionException("Legacy app cannot have not-granted runtime permission "
                    + permission.getName());
        }
    }

    private static boolean supportsRuntimePermissions(@NonNull PackageInfo packageInfo) {
        return packageInfo.applicationInfo.targetSdkVersion > Build.VERSION_CODES.LOLLIPOP_MR1;
    }
}
