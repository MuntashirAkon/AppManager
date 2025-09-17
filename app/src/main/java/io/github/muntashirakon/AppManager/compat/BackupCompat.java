// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.annotation.UserIdInt;
import android.app.backup.IBackupManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import aosp.libcore.util.EmptyArray;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;

/**
 * A complete recreation of the `bu` command (i.e. com.android.commands.bu.Backup class) with support for setting a
 * file location. Although the help page of the command include an -f switch for file, it actually does not work with
 * the command and only intended for ADB itself.
 */
public final class BackupCompat {

    private BackupCompat() {
    }

    /**
     * @see IBackupManager#setBackupEnabledForUser(int, boolean)
     */
    public static void setBackupEnabledForUser(@UserIdInt int userId, boolean isEnabled) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getBackupManager().setBackupEnabledForUser(userId, isEnabled);
            } else {
                getBackupManager().setBackupEnabled(isEnabled);
            }
        } catch (RemoteException e) {
            ExUtils.rethrowFromSystemServer(e);
        }
    }

    /**
     * @see IBackupManager#isBackupEnabledForUser(int)
     */
    public static boolean isBackupEnabledForUser(@UserIdInt int userId) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return getBackupManager().isBackupEnabledForUser(userId);
            }
            if (UserHandleHidden.myUserId() == userId) {
                return getBackupManager().isBackupEnabled();
            }
            // Multiuser backup only available since Android 10
            return false;
        } catch (RemoteException e) {
            return ExUtils.rethrowFromSystemServer(e);
        }
    }

    public static boolean setBackupPassword(String currentPw, String newPw) {
        try {
            return getBackupManager().setBackupPassword(currentPw, newPw);
        } catch (RemoteException e) {
            return ExUtils.rethrowFromSystemServer(e);
        }
    }

    public static boolean hasBackupPassword() {
        try {
            return getBackupManager().hasBackupPassword();
        } catch (RemoteException e) {
            return ExUtils.rethrowFromSystemServer(e);
        }
    }

    @SuppressWarnings("deprecation")
    public static void adbBackup(@UserIdInt int userId, ParcelFileDescriptor fd, boolean includeApks, boolean includeObbs,
                                 boolean includeShared, boolean doWidgets, boolean allApps, boolean allIncludesSystem,
                                 boolean doCompress, boolean doKeyValue, String[] packageNames) throws RemoteException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getBackupManager().adbBackup(userId, fd, includeApks, includeObbs, includeShared, doWidgets, allApps, allIncludesSystem, doCompress, doKeyValue, packageNames);
        } else {
            if (UserHandleHidden.myUserId() != userId) {
                throw new RemoteException("Backup only allowed for current user");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getBackupManager().adbBackup(fd, includeApks, includeObbs, includeShared, doWidgets, allApps, allIncludesSystem, doCompress, doKeyValue, packageNames);
            } else {
                getBackupManager().fullBackup(fd, includeApks, includeObbs, includeShared, doWidgets, allApps, allIncludesSystem, doCompress, packageNames);
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static void adbRestore(@UserIdInt int userId, ParcelFileDescriptor fd) throws RemoteException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getBackupManager().adbRestore(userId, fd);
        } else {
            if (UserHandleHidden.myUserId() != userId) {
                throw new RemoteException("Backup only allowed for current user");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getBackupManager().adbRestore(fd);
            } else getBackupManager().fullRestore(fd);
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean isAppEligibleForBackupForUser(@UserIdInt int userId, String packageName) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return getBackupManager().isAppEligibleForBackupForUser(userId, packageName);
            } else {
                if (UserHandleHidden.myUserId() != userId) {
                    // Multiuser support unavailable
                    return false;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    return getBackupManager().isAppEligibleForBackup(packageName);
                }
                // In API 23 and earlier, set it to eligible by default
                return true;
            }
        } catch (RemoteException e) {
            return ExUtils.rethrowFromSystemServer(e);
        }
    }

    @SuppressWarnings("deprecation")
    @NonNull
    public static String[] filterAppsEligibleForBackupForUser(@UserIdInt int userId, @NonNull String[] packages) {
        IBackupManager backupManager = getBackupManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ArrayUtils.defeatNullable(ExUtils.<String[]>exceptionAsNull(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    backupManager.filterAppsEligibleForBackupForUser(userId, packages);
                } else {
                    backupManager.filterAppsEligibleForBackup(packages);
                }
                return null;
            }));
        }
        if (UserHandleHidden.myUserId() != userId) {
            // Multiuser support unavailable
            return EmptyArray.STRING;
        }
        // Check individually
        List<String> eligibleApps = new ArrayList<>(packages.length);
        for (String packageName : packages) {
            boolean isEligible;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                isEligible = Boolean.TRUE.equals(ExUtils.exceptionAsNull(() ->
                        backupManager.isAppEligibleForBackup(packageName)));
            } else isEligible = true;
            if (isEligible) {
                eligibleApps.add(packageName);
            }
        }
        return eligibleApps.toArray(new String[0]);
    }

    public static IBackupManager getBackupManager() {
        return IBackupManager.Stub.asInterface(ProxyBinder.getService("backup"));
    }
}
