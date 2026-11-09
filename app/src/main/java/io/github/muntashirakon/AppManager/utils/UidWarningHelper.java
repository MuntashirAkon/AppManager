// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.IPackageManager;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.ProcessCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

public class UidWarningHelper {
    public static final String TAG = "UidWarningHelper";

    /**
     * Unwraps context wrappers to find the underlying Activity if available.
     */
    @Nullable
    public static Activity getActivity(@Nullable Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    /**
     * Checks if a given UID belongs to the system (App ID < 10000).
     * Correctly handles multi-user userOffsets by checking the App ID.
     */
    public static boolean isSystemUid(int uid) {
        if (uid < 0) return false;
        int appId = UserHandleHidden.getAppId(uid);
        return appId < ProcessCompat.FIRST_APPLICATION_UID;
    }

    /**
     * Warns before performing AppOps operations on a single package if its UID is shared.
     */
    public static void warnIfSharedUid(@NonNull Context context, int uid, @NonNull Runnable onProceed) {
        warnIfSharedUid(context, uid, null, onProceed, null);
    }

    /**
     * Warns before performing AppOps operations on a single package if its UID is shared.
     *
     * @param currentPackageName The package currently being viewed/edited. If provided,
     *                           the warning dialog will highlight another package sharing the UID
     *                           rather than displaying the current package itself.
     */
    public static void warnIfSharedUid(@NonNull Context context, int uid,
                                       @Nullable String currentPackageName,
                                       @NonNull Runnable onProceed) {
        warnIfSharedUid(context, uid, currentPackageName, onProceed, null);
    }

    /**
     * Warns before performing AppOps operations on a single package if its UID is shared.
     */
    public static void warnIfSharedUid(@NonNull Context context, int uid,
                                       @Nullable String currentPackageName,
                                       @NonNull Runnable onProceed,
                                       @Nullable Runnable onCancel) {
        if (uid < 0) {
            onProceed.run();
            return;
        }
        ThreadUtils.postOnBackgroundThread(() -> {
            String[] packages = null;
            try {
                IPackageManager pm = PackageManagerCompat.getPackageManager();
                if (pm != null) {
                    packages = pm.getPackagesForUid(uid);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to resolve packages for uid " + uid, e);
            }
            final String[] finalPackages = packages;
            ThreadUtils.postOnMainThread(() -> {
                Activity activity = getActivity(context);
                if (activity == null) {
                    Log.w(TAG, "Cannot display warning dialog on non-activity context, proceeding directly");
                    onProceed.run();
                    return;
                }
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                if (finalPackages != null && finalPackages.length > 1) {
                    String samplePackage = finalPackages[0];
                    if (currentPackageName != null) {
                        for (String pkg : finalPackages) {
                            if (!pkg.equals(currentPackageName)) {
                                samplePackage = pkg;
                                break;
                            }
                        }
                    }
                    int count = finalPackages.length - 1;
                    String otherPackagesText = context.getResources()
                            .getQuantityString(R.plurals.warning_shared_uid_other_packages, count, count);
                    boolean system = isSystemUid(uid);
                    int titleRes = system ? R.string.warning_system_shared_uid_title : R.string.are_you_sure;
                    String message = system
                            ? context.getString(R.string.warning_system_shared_uid_single, uid, otherPackagesText, samplePackage)
                            : context.getString(R.string.warning_shared_uid_single, uid, otherPackagesText, samplePackage);

                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                            .setTitle(titleRes)
                            .setMessage(message)
                            .setPositiveButton(R.string.yes, (dialog, which) -> onProceed.run())
                            .setNegativeButton(R.string.cancel, (dialog, which) -> {
                                if (onCancel != null) onCancel.run();
                            })
                            .setOnCancelListener(dialog -> {
                                if (onCancel != null) onCancel.run();
                            });
                    if (system) {
                        builder.setIcon(io.github.muntashirakon.ui.R.drawable.ic_caution);
                    }
                    builder.show();
                } else {
                    onProceed.run();
                }
            });
        });
    }

    /**
     * Warns before performing batch AppOps operations if any package in the list shares a UID.
     * Queries are executed on a background thread to prevent UI thread blocking / ANRs.
     */
    public static void warnIfSharedUids(@NonNull Context context, @NonNull List<String> packageNames, @NonNull Runnable onProceed) {
        warnIfSharedUids(context, packageNames, null, onProceed, null);
    }

    /**
     * Warns before performing batch AppOps operations if any package in the list shares a UID.
     */
    public static void warnIfSharedUids(@NonNull Context context, @NonNull List<String> packageNames,
                                        @Nullable List<Integer> userIds,
                                        @NonNull Runnable onProceed) {
        warnIfSharedUids(context, packageNames, userIds, onProceed, null);
    }

    /**
     * Warns before performing batch AppOps operations if any package in the list shares a UID.
     * Queries are executed on a background thread to prevent UI thread blocking / ANRs.
     */
    public static void warnIfSharedUids(@NonNull Context context, @NonNull List<String> packageNames,
                                        @Nullable List<Integer> userIds,
                                        @NonNull Runnable onProceed,
                                        @Nullable Runnable onCancel) {
        if (packageNames.isEmpty()) {
            onProceed.run();
            return;
        }
        ThreadUtils.postOnBackgroundThread(() -> {
            boolean hasSystemShared = false;
            boolean hasRegularShared = false;
            int defaultUserId = UserHandleHidden.myUserId();
            IPackageManager pm = null;
            try {
                pm = PackageManagerCompat.getPackageManager();
            } catch (Exception e) {
                Log.w(TAG, "Failed to obtain IPackageManager", e);
            }
            Set<Integer> checkedUids = new HashSet<>();

            for (int i = 0; i < packageNames.size(); i++) {
                String pkg = packageNames.get(i);
                int userId = defaultUserId;
                if (userIds != null && !userIds.isEmpty()) {
                    if (i < userIds.size() && userIds.get(i) != null) {
                        userId = userIds.get(i);
                    } else if (userIds.size() == 1 && userIds.get(0) != null) {
                        userId = userIds.get(0);
                    }
                }
                try {
                    int uid = PackageUtils.getAppUid(new UserPackagePair(pkg, userId));
                    if (uid < 0) continue;
                    // If regular shared UID is already found, non-system UID packages cannot escalate the warning
                    if (hasRegularShared && !isSystemUid(uid)) {
                        continue;
                    }
                    if (!checkedUids.add(uid)) {
                        continue;
                    }
                    String[] packages = pm != null ? pm.getPackagesForUid(uid) : null;
                    if (packages != null && packages.length > 1) {
                        if (isSystemUid(uid)) {
                            hasSystemShared = true;
                            break; // Worst case encountered, stop early
                        } else {
                            hasRegularShared = true;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to resolve packages for package " + pkg, e);
                }
            }

            final boolean finalSystemShared = hasSystemShared;
            final boolean finalRegularShared = hasRegularShared;
            ThreadUtils.postOnMainThread(() -> {
                Activity activity = getActivity(context);
                if (activity == null) {
                    Log.w(TAG, "Cannot display warning dialog on non-activity context, proceeding directly");
                    onProceed.run();
                    return;
                }
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                if (finalSystemShared) {
                    new MaterialAlertDialogBuilder(activity)
                            .setIcon(io.github.muntashirakon.ui.R.drawable.ic_caution)
                            .setTitle(R.string.warning_system_shared_uid_title)
                            .setMessage(R.string.warning_system_shared_uid_multiple)
                            .setPositiveButton(R.string.yes, (dialog, which) -> onProceed.run())
                            .setNegativeButton(R.string.cancel, (dialog, which) -> {
                                if (onCancel != null) onCancel.run();
                            })
                            .setOnCancelListener(dialog -> {
                                if (onCancel != null) onCancel.run();
                            })
                            .show();
                } else if (finalRegularShared) {
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.are_you_sure)
                            .setMessage(R.string.warning_shared_uid_multiple)
                            .setPositiveButton(R.string.yes, (dialog, which) -> onProceed.run())
                            .setNegativeButton(R.string.cancel, (dialog, which) -> {
                                if (onCancel != null) onCancel.run();
                            })
                            .setOnCancelListener(dialog -> {
                                if (onCancel != null) onCancel.run();
                            })
                            .show();
                } else {
                    onProceed.run();
                }
            });
        });
    }
}
