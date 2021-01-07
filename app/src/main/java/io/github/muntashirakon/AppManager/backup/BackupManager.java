/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.backup;

import java.util.Arrays;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.UserIdInt;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.types.PrivilegedFile;
import io.github.muntashirakon.AppManager.utils.DateUtils;

public class BackupManager {
    public static final String TAG = "BackupManager";

    static final String EXT_DATA = "/Android/data/";
    static final String EXT_MEDIA = "/Android/media/";
    static final String EXT_OBB = "/Android/obb/";
    static final String[] CACHE_DIRS = new String[]{"cache", "code_cache", "no_backup"};
    static final String SOURCE_PREFIX = "source";
    static final String DATA_PREFIX = "data";
    static final String KEYSTORE_PREFIX = "keystore";
    static final int KEYSTORE_PLACEHOLDER = -1000;

    public static final String ICON_FILE = "icon.png";
    static final String RULES_TSV = "rules.am.tsv";
    static final String PERMS_TSV = "perms.am.tsv";
    static final String MISC_TSV = "misc.am.tsv";
    static final String CHECKSUMS_TXT = "checksums.txt";
    static final String CERT_PREFIX = "cert_";
    static final String MASTER_KEY = ".masterkey";

    @NonNull
    static String getExt(@TarUtils.TarType String tarType) {
        if (TarUtils.TAR_BZIP2.equals(tarType)) return ".tar.bz2";
        else return ".tar.gz";
    }

    /**
     * @param packageName Package name of the app
     * @param flags       One or more of the {@link BackupFlags.BackupFlag}
     */
    @NonNull
    public static BackupManager getNewInstance(String packageName, int flags) {
        return new BackupManager(packageName, flags);
    }

    @NonNull
    private final String packageName;
    @NonNull
    private final MetadataManager metadataManager;
    @NonNull
    private final BackupFlags requestedFlags;
    @NonNull
    @UserIdInt
    private final int[] userHandles;

    protected BackupManager(@NonNull String packageName, int flags) {
        this.packageName = packageName;
        metadataManager = MetadataManager.getNewInstance();
        requestedFlags = new BackupFlags(flags);
        if (requestedFlags.backupAllUsers()) {
            userHandles = Users.getUsersHandles();
        } else userHandles = new int[]{Users.getCurrentUserHandle()};
        Log.e(TAG, "Users: " + Arrays.toString(userHandles));
    }

    public boolean backup(@Nullable String[] backupNames) {
        if (requestedFlags.isEmpty()) {
            Log.e(BackupOp.TAG, "Backup is requested without any flags.");
            return false;
        }
        backupNames = getProcessedBackupNames(backupNames);
        BackupFiles[] backupFilesList = new BackupFiles[userHandles.length];
        for (int i = 0; i < userHandles.length; ++i) {
            backupFilesList[i] = new BackupFiles(packageName, userHandles[i], backupNames);
        }
        for (int i = 0; i < userHandles.length; ++i) {
            BackupFiles.BackupFile[] backupFiles = requestedFlags.backupMultiple() ?
                    backupFilesList[i].getFreshBackupPaths() :
                    backupFilesList[i].getBackupPaths(true);
            for (BackupFiles.BackupFile backupFile : backupFiles) {
                try (BackupOp backupOp = new BackupOp(packageName, metadataManager, requestedFlags, backupFile, userHandles[i])) {
                    if (!backupOp.runBackup()) return false;
                } catch (BackupException e) {
                    Log.e(BackupOp.TAG, e.getMessage(), e);
                    return false;
                }
            }
        }
        return true;
    }

    @Nullable
    private String[] getProcessedBackupNames(@Nullable String[] backupNames) {
        if (requestedFlags.backupMultiple()) {
            // Multiple backups requested
            if (backupNames == null) {
                // Create a singleton backupNames array with current time
                backupNames = new String[]{DateUtils.formatDateTime(System.currentTimeMillis())};
            }
            for (int i = 0; i < backupNames.length; ++i) {
                // Replace illegal characters
                backupNames[i] = backupNames[i].trim().replaceAll("[\\\\/?\"<>|\\s]+", "_");  // [\\/:?"<>|\s]
            }
            Log.e(TAG, "Backup names: " + Arrays.toString(backupNames));
            return backupNames;
        } else return null; // Overwrite existing backup
    }

    /**
     * Restore a single backup but could be for all users
     *
     * @param backupNames Backup names is a singleton array consisting of the full name of a backup.
     *                    Full name means backup name along with the user handle, ie., for user 0,
     *                    the full name of base backup is {@code 0} and the full name of another
     *                    backup {@code foo} is {@code 0_foo}.
     * @return {@code true} on success and {@code false} on failure
     */
    public boolean restore(@Nullable String[] backupNames) {
        if (requestedFlags.isEmpty()) {
            Log.e(RestoreOp.TAG, "Restore is requested without any flags.");
            return false;
        }
        if (backupNames != null && backupNames.length != 1) {
            Log.e(RestoreOp.TAG, "Restore is requested from more than one backups!");
            return false;
        }
        int backupUserHandle = -1;
        if (backupNames != null) {
            // Strip userHandle from backup name
            String backupName = BackupUtils.getShortBackupName(backupNames[0]);
            backupUserHandle = BackupUtils.getUserHandleFromBackupName(backupNames[0]);
            if (backupName != null) {
                // There's a backup name, not just user handle
                backupNames = new String[]{backupName};
            } else {
                // There's only user handle. Set backupNames to null to restore base backup goes
                // by the name
                backupNames = null;
            }
        }
        for (int userHandle : userHandles) {
            // Set backup userHandle to the userHandle we're working with.
            // This value is only set if backupNames is null or it consisted of only user handle
            if (backupUserHandle == -1) backupUserHandle = userHandle;
            BackupFiles backupFiles = new BackupFiles(packageName, backupUserHandle, backupNames);
            BackupFiles.BackupFile[] backupFileList = backupFiles.getBackupPaths(false);
            // Only restore from the first backup though we shouldn't have more than one backup.
            if (backupFileList.length > 0) {
                if (backupFileList.length > 1) {
                    Log.w(RestoreOp.TAG, "More than one backups found!");
                }
                try (RestoreOp restoreOp = new RestoreOp(packageName, metadataManager, requestedFlags, backupFileList[0], userHandle)) {
                    if (!restoreOp.runRestore()) return false;
                } catch (BackupException e) {
                    e.printStackTrace();
                    Log.e(RestoreOp.TAG, e.getMessage(), e);
                    return false;
                }
            } else {
                Log.e(RestoreOp.TAG, "No backups found.");
            }
        }
        return true;
    }

    public boolean deleteBackup(@Nullable String[] backupNames) {
        if (backupNames == null) {
            // No backup names supplied, use user handle
            for (int userHandle : userHandles) {
                BackupFiles backupFiles = new BackupFiles(packageName, userHandle, null);
                BackupFiles.BackupFile[] backupFileList = backupFiles.getBackupPaths(false);
                for (BackupFiles.BackupFile backupFile : backupFileList) {
                    if (!backupFile.delete()) return false;
                }
            }
        } else {
            // backupNames is not null but that doesn't mean that it's not empty,
            // requested for only single backups
            for (String backupName : backupNames) {
                new BackupFiles.BackupFile(
                        new PrivilegedFile(BackupFiles.getPackagePath(packageName), backupName),
                        false
                ).delete();
            }
        }
        return true;
    }
}
