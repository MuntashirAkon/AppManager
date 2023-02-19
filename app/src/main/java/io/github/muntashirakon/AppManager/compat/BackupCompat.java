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
public class BackupCompat {
    private final IBackupManager mBackupManager;

    public BackupCompat() {
        mBackupManager = getBackupManager();
    }

    /**
     * @see IBackupManager#setBackupEnabledForUser(int, boolean)
     */
    public void setBackupEnabledForUser(@UserIdInt int userId, boolean isEnabled) {
        ExUtils.exceptionAsIgnored(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mBackupManager.setBackupEnabledForUser(userId, isEnabled);
            } else {
                mBackupManager.setBackupEnabled(isEnabled);
            }
        });
    }

    /**
     * @see IBackupManager#isBackupEnabledForUser(int)
     */
    public boolean isBackupEnabledForUser(@UserIdInt int userId) {
        return Boolean.TRUE.equals(ExUtils.exceptionAsNull(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return mBackupManager.isBackupEnabledForUser(userId);
            }
            if (UserHandleHidden.myUserId() == userId) {
                return mBackupManager.isBackupEnabled();
            }
            // Multiuser backup only available since Android 10
            return false;
        }));
    }

    public boolean setBackupPassword(String currentPw, String newPw) {
        return Boolean.TRUE.equals(ExUtils.exceptionAsNull(() -> mBackupManager.setBackupPassword(currentPw, newPw)));
    }

    public boolean hasBackupPassword() {
        return Boolean.TRUE.equals(ExUtils.exceptionAsNull(mBackupManager::hasBackupPassword));
    }

    @SuppressWarnings("deprecation")
    public void adbBackup(@UserIdInt int userId, ParcelFileDescriptor fd, boolean includeApks, boolean includeObbs,
                          boolean includeShared, boolean doWidgets, boolean allApps, boolean allIncludesSystem,
                          boolean doCompress, boolean doKeyValue, String[] packageNames) throws RemoteException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mBackupManager.adbBackup(userId, fd, includeApks, includeObbs, includeShared, doWidgets, allApps, allIncludesSystem, doCompress, doKeyValue, packageNames);
        } else {
            if (UserHandleHidden.myUserId() != userId) {
                throw new RemoteException("Backup only allowed for current user");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mBackupManager.adbBackup(fd, includeApks, includeObbs, includeShared, doWidgets, allApps, allIncludesSystem, doCompress, doKeyValue, packageNames);
            } else {
                mBackupManager.fullBackup(fd, includeApks, includeObbs, includeShared, doWidgets, allApps, allIncludesSystem, doCompress, packageNames);
            }
        }
    }

    @SuppressWarnings("deprecation")
    public void adbRestore(@UserIdInt int userId, ParcelFileDescriptor fd) throws RemoteException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mBackupManager.adbRestore(userId, fd);
        } else {
            if (UserHandleHidden.myUserId() != userId) {
                throw new RemoteException("Backup only allowed for current user");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mBackupManager.adbRestore(fd);
            } else mBackupManager.fullRestore(fd);
        }
    }

    @SuppressWarnings("deprecation")
    public boolean isAppEligibleForBackupForUser(@UserIdInt int userId, String packageName) {
        return Boolean.TRUE.equals(ExUtils.exceptionAsNull(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return mBackupManager.isAppEligibleForBackupForUser(userId, packageName);
            } else {
                if (UserHandleHidden.myUserId() != userId) {
                    // Multiuser support unavailable
                    return false;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    return mBackupManager.isAppEligibleForBackup(packageName);
                }
                // In API 23 and earlier, set it to eligible by default
                return true;
            }
        }));
    }

    @SuppressWarnings("deprecation")
    @NonNull
    public String[] filterAppsEligibleForBackupForUser(@UserIdInt int userId, @NonNull String[] packages) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ArrayUtils.defeatNullable(ExUtils.<String[]>exceptionAsNull(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    mBackupManager.filterAppsEligibleForBackupForUser(userId, packages);
                } else {
                    mBackupManager.filterAppsEligibleForBackup(packages);
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
                        mBackupManager.isAppEligibleForBackup(packageName)));
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
