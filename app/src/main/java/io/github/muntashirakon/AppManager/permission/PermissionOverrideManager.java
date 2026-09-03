// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

/**
 * Application-scoped entry point for permission-overlay reconciliation.
 */
public final class PermissionOverrideManager {
    private static volatile PermissionOverrideReconciler sReconciler;

    private PermissionOverrideManager() {
    }

    @WorkerThread
    private static PermissionOverrideReconciler reconciler() {
        PermissionOverrideReconciler value = sReconciler;
        if (value == null) {
            synchronized (PermissionOverrideManager.class) {
                value = sReconciler;
                if (value == null) {
                    value = new PermissionOverrideReconciler(
                            AppsDb.getInstance().permissionOverrideDao(),
                            new ConnectivityPermissionOverridePlatform());
                    sReconciler = value;
                }
            }
        }
        return value;
    }

    @AnyThread
    public static void reconcileAll() {
        ThreadUtils.postOnBackgroundThread(() -> reconciler().reconcileAll());
    }

    @AnyThread
    public static void reconcile(@NonNull String packageName, int userId) {
        ThreadUtils.postOnBackgroundThread(() -> reconciler().reconcile(packageName, userId));
    }

    @AnyThread
    public static void remove(@NonNull String packageName, int userId) {
        ThreadUtils.postOnBackgroundThread(() -> reconciler().remove(packageName, userId));
    }

    @WorkerThread
    public static void removeNow(@NonNull String packageName, int userId) {
        reconciler().removeNow(packageName, userId);
    }
}
