// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.annotation.UserIdInt;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Prefs;

public final class FreezeUtils {
    @IntDef({FREEZE_DISABLE, FREEZE_SUSPEND, FREEZE_HIDE, FREEZE_ADV_SUSPEND})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FreezeType {
    }

    public static final int FREEZE_DISABLE = 1;
    public static final int FREEZE_SUSPEND = 1 << 1;
    public static final int FREEZE_HIDE = 1 << 2;
    public static final int FREEZE_ADV_SUSPEND = 1 << 3;

    @WorkerThread
    public static void setFreezeMethod(@NonNull String packageName, @FreezeType int freezeType) {
        AppsDb.getInstance().freezeTypeDao().insert(packageName, freezeType);
    }

    @WorkerThread
    @FreezeType
    @Nullable
    public static Integer getFreezingMethod(@Nullable String packageName) {
        if (packageName != null) {
            io.github.muntashirakon.AppManager.db.entity.FreezeType freezeType;
            freezeType = AppsDb.getInstance().freezeTypeDao().get(packageName);
            if (freezeType != null) {
                return freezeType.type;
            }
        }
        // No package-specific freezing method exists
        return null;
    }

    public static boolean isFrozen(@NonNull ApplicationInfo applicationInfo) {
        // An app is frozen if one of the following operations return true: suspend, disable or hide
        if (!applicationInfo.enabled) {
            return true;
        }
        if (ApplicationInfoCompat.isSuspended(applicationInfo)) {
            return true;
        }
        return (ApplicationInfoCompat.getPrivateFlags(applicationInfo) & ApplicationInfoCompat.PRIVATE_FLAG_HIDDEN) != 0;
    }

    @Deprecated
    public static void freeze(@NonNull String packageName, @UserIdInt int userId) throws RemoteException {
        freeze(packageName, userId, Prefs.Blocking.getDefaultFreezingMethod());
    }

    public static void freeze(@NonNull String packageName, @UserIdInt int userId, @FreezeType int freezeType)
            throws RemoteException {
        if (freezeType == FREEZE_HIDE) {
            if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS)) {
                PackageManagerCompat.hidePackage(packageName, userId, true);
                return;
            }
            // No permission, fall-through
        } else if ((freezeType == FREEZE_SUSPEND || freezeType == FREEZE_ADV_SUSPEND) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (freezeType == FREEZE_ADV_SUSPEND) {
                // Force-stop app
                if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)) {
                    PackageManagerCompat.forceStopPackage(packageName, userId);
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.SUSPEND_APPS)) {
                    PackageManagerCompat.suspendPackages(new String[]{packageName}, userId, true);
                    return;
                }
                // No permission, fall-through
            } else {
                if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS)) {
                    PackageManagerCompat.suspendPackages(new String[]{packageName}, userId, true);
                    return;
                }
                // No permission, fall-through
            }
        }
        PackageManagerCompat.setApplicationEnabledSetting(packageName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0, userId);
    }

    public static void unfreeze(@NonNull String packageName, @UserIdInt int userId) throws RemoteException {
        // Ignore checking preference, unfreeze for all types
        if (PackageManagerCompat.isPackageHidden(packageName, userId)) {
            PackageManagerCompat.hidePackage(packageName, userId, false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && PackageManagerCompat.isPackageSuspended(packageName, userId)) {
            PackageManagerCompat.suspendPackages(new String[]{packageName}, userId, false);
        }
        if (PackageManagerCompat.getApplicationEnabledSetting(packageName, userId) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            PackageManagerCompat.setApplicationEnabledSetting(packageName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0, userId);
        }
    }
}
