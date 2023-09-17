// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Arrays;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;

/**
 * Manage backups for individual package belong to individual user.
 */
public class BackupManager {
    public static final String TAG = "BackupManager";

    /* language=regexp */
    static final String[] CACHE_DIRS = new String[]{"cache/.*", "code_cache/.*", "no_backup/.*"};
    /* language=regexp */
    static final String[] LIB_DIR = new String[]{"lib/"};
    public static final String SOURCE_PREFIX = "source";
    public static final String DATA_PREFIX = "data";
    static final String KEYSTORE_PREFIX = "keystore";
    static final int KEYSTORE_PLACEHOLDER = -1000;

    public static final String ICON_FILE = "icon.png";
    public static final String CERT_PREFIX = "cert_";
    static final String MASTER_KEY = ".masterkey";

    @NonNull
    public static String getExt(@TarUtils.TarType String tarType) {
        if (TarUtils.TAR_BZIP2.equals(tarType)) return ".tar.bz2";
        if (TarUtils.TAR_ZSTD.equals(tarType)) return ".tar.zst";
        else return ".tar.gz";
    }

    /**
     * @param targetPackage Package name with user ID whose backup will be restored. This does not
     *                      have any influence on backup names that has to be supplied separately.
     * @param flags         One or more of the {@link BackupFlags.BackupFlag}
     */
    @NonNull
    public static BackupManager getNewInstance(UserPackagePair targetPackage, int flags) {
        return new BackupManager(targetPackage, flags);
    }

    @NonNull
    private final UserPackagePair mTargetPackage;
    @NonNull
    private final MetadataManager mMetadataManager;
    @NonNull
    private final BackupFlags mRequestedFlags;

    private boolean mRequiresRestart;

    protected BackupManager(@NonNull UserPackagePair targetPackage, int flags) {
        mTargetPackage = targetPackage;
        mMetadataManager = MetadataManager.getNewInstance();
        mRequestedFlags = new BackupFlags(flags);
        try {
            BackupFiles.createNoMediaIfNotExists();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "Package: %s, user: %d", targetPackage.getPackageName(), targetPackage.getUserId());
    }

    public boolean requiresRestart() {
        return mRequiresRestart;
    }

    /**
     * Backup the given package belonging to the given user. If multiple backup names given, iterate
     * over the backup names and perform the identical backups several times.
     */
    public void backup(@Nullable String[] backupNames, @Nullable ProgressHandler progressHandler)
            throws BackupException {
        if (mRequestedFlags.isEmpty()) {
            throw new BackupException("Backup is requested without any flags.");
        }
        if (mTargetPackage.getPackageName().equals("android")) {
            throw new BackupException("Android System (android) cannot be backed up.");
        }
        backupNames = getProcessedBackupNames(backupNames);
        try {
            // Get backup files based on the number of backupNames
            BackupFiles backupFiles = new BackupFiles(mTargetPackage.getPackageName(), mTargetPackage.getUserId(), backupNames);
            BackupFiles.BackupFile[] backupFileList = mRequestedFlags.backupMultiple() ?
                    backupFiles.getFreshBackupPaths() : backupFiles.getBackupPaths(true);
            if (progressHandler != null) {
                int max = calculateMaxProgress(backupFileList.length);
                progressHandler.setProgressTextInterface(ProgressHandler.PROGRESS_PERCENT);
                progressHandler.postUpdate(max, 0f);
            }
            for (BackupFiles.BackupFile backupFile : backupFileList) {
                try (BackupOp backupOp = new BackupOp(mTargetPackage.getPackageName(), mMetadataManager, mRequestedFlags,
                        backupFile, mTargetPackage.getUserId())) {
                    backupOp.runBackup(progressHandler);
                    BackupUtils.putBackupToDbAndBroadcast(ContextUtils.getContext(), backupOp.getMetadata());
                }
            }
        } catch (IOException e) {
            throw new BackupException("Backup failed", e);
        }
    }

    @Nullable
    private String[] getProcessedBackupNames(@Nullable String[] backupNames) {
        if (mRequestedFlags.backupMultiple()) {
            // Multiple backups requested
            if (backupNames == null) {
                // Create a singleton backupNames array with current time
                backupNames = new String[]{DateUtils.formatDateTime(ContextUtils.getContext(), System.currentTimeMillis())};
            }
            for (int i = 0; i < backupNames.length; ++i) {
                // Replace illegal characters
                backupNames[i] = backupNames[i].trim().replaceAll("[\\\\/:?\"<>|\\s]+", "_");  // [\\/:?"<>|\s]
            }
            Log.e(TAG, "Backup names: %s", Arrays.toString(backupNames));
            return backupNames;
        } else return null; // Overwrite existing backup
    }

    /**
     * Restore a single backup for a given package belonging to the given package
     *
     * @param backupNames Backup names is a singleton array consisting of the full name of a backup.
     *                    Full name means backup name along with the user handle, ie., for user 0,
     *                    the full name of base backup is {@code 0} and the full name of another
     *                    backup {@code foo} is {@code 0_foo}.
     */
    public void restore(@Nullable String[] backupNames, @Nullable ProgressHandler progressHandler)
            throws BackupException {
        if (mRequestedFlags.isEmpty()) {
            throw new BackupException("Restore is requested without any flags.");
        }
        if (mTargetPackage.getPackageName().equals("android")) {
            throw new BackupException("Android System (android) cannot be restored.");
        }
        if (backupNames != null && backupNames.length != 1) {
            throw new BackupException("Restore is requested from more than one backups!");
        }
        // The user handle with backups, this is different from the target user handle
        int backupUserId = -1;
        if (backupNames != null) {
            // Strip userHandle from backup name
            String backupName = BackupUtils.getShortBackupName(backupNames[0]);
            backupUserId = BackupUtils.getUserHandleFromBackupName(backupNames[0]);
            if (backupName != null) {
                // There's a backup name, not just user handle
                backupNames = new String[]{backupName};
            } else {
                // There's only user handle. Set backupNames to null to restore base backup goes
                // by the name
                backupNames = null;
            }
        }
        // Set backup userHandle to the userHandle we're working with.
        // This value is only set if backupNames is null or it consisted of only user handle
        if (backupUserId == -1) backupUserId = mTargetPackage.getUserId();
        BackupFiles backupFiles;
        BackupFiles.BackupFile[] backupFileList;
        try {
            backupFiles = new BackupFiles(mTargetPackage.getPackageName(), backupUserId, backupNames);
            backupFileList = backupFiles.getBackupPaths(false);
        } catch (IOException e) {
            throw new BackupException("Could not get backup files.", e);
        }
        // Only restore from the first backup though we shouldn't have more than one backup.
        if (backupFileList.length > 0) {
            if (backupFileList.length > 1) {
                Log.w(RestoreOp.TAG, "More than one backups found! Restoring only the first backup.");
            }
            if (progressHandler != null) {
                int max = calculateMaxProgress(1);
                progressHandler.setProgressTextInterface(ProgressHandler.PROGRESS_PERCENT);
                progressHandler.postUpdate(max, 0f);
            }
            try (RestoreOp restoreOp = new RestoreOp(mTargetPackage.getPackageName(),
                    mMetadataManager, mRequestedFlags, backupFileList[0],
                    mTargetPackage.getUserId())) {
                restoreOp.runRestore(progressHandler);
                mRequiresRestart |= restoreOp.requiresRestart();
                BackupUtils.putBackupToDbAndBroadcast(ContextUtils.getContext(), restoreOp.getMetadata());
            }
        } else {
            Log.w(RestoreOp.TAG, "No backups found.");
        }
    }

    public void deleteBackup(@Nullable String[] backupNames) throws BackupException {
        if (backupNames == null) {
            // No backup names supplied, use user handle
            BackupFiles backupFiles;
            BackupFiles.BackupFile[] backupFileList;
            try {
                backupFiles = new BackupFiles(mTargetPackage.getPackageName(),
                        mTargetPackage.getUserId(), null);
                backupFileList = backupFiles.getBackupPaths(false);
            } catch (IOException e) {
                throw new BackupException("Could not get backup files.", e);
            }
            for (BackupFiles.BackupFile backupFile : backupFileList) {
                try {
                    MetadataManager.Metadata metadata = MetadataManager.getMetadata(backupFile);
                    if (!backupFile.isFrozen() && !backupFile.delete()) {
                        throw new BackupException("Could not delete the selected backups");
                    }
                    BackupUtils.deleteBackupToDbAndBroadcast(ContextUtils.getContext(), metadata);
                } catch (IOException e) {
                    throw new BackupException("Could not delete the selected backups", e);
                }
            }
        } else {
            // backupNames is not null but that doesn't mean that it's not empty,
            // requested for only single backups
            BackupFiles.BackupFile backupFile;
            for (String backupName : backupNames) {
                MetadataManager.Metadata metadata;
                try {
                    backupFile = new BackupFiles.BackupFile(BackupFiles.getPackagePath(mTargetPackage.getPackageName(),
                            false).findFile(backupName), false);
                    metadata = MetadataManager.getMetadata(backupFile);
                } catch (IOException e) {
                    throw new BackupException("Could not get backup files.", e);
                }
                if (!backupFile.isFrozen() && !backupFile.delete()) {
                    throw new BackupException("Could not delete the selected backups");
                }
                BackupUtils.deleteBackupToDbAndBroadcast(ContextUtils.getContext(), metadata);
            }
        }
    }

    public void verify(@Nullable String backupName) throws BackupException {
        // The user handle with backups, this is different from the target user handle
        int backupUserHandle = -1;
        if (backupName != null) {
            // Strip userHandle from backup name
            backupUserHandle = BackupUtils.getUserHandleFromBackupName(backupName);
            backupName = BackupUtils.getShortBackupName(backupName);
        }
        // Set backup userHandle to the userHandle we're working with.
        // This value is only set if backupNames is null or it consisted of only user handle
        if (backupUserHandle == -1) backupUserHandle = mTargetPackage.getUserId();
        BackupFiles backupFiles;
        BackupFiles.BackupFile[] backupFileList;
        try {
            backupFiles = new BackupFiles(mTargetPackage.getPackageName(), backupUserHandle,
                    backupName == null ? null : new String[]{backupName});
            backupFileList = backupFiles.getBackupPaths(false);
        } catch (IOException e) {
            throw new BackupException("Could not get backup files.", e);
        }
        // Only verify the first backup though we shouldn't have more than one backup.
        if (backupFileList.length > 0) {
            if (backupFileList.length > 1) {
                Log.w(VerifyOp.TAG, "More than one backups found! Verifying only the first backup.");
            }
            try (VerifyOp restoreOp = new VerifyOp(mMetadataManager, backupFileList[0])) {
                restoreOp.verify();
            }
        } else {
            throw new BackupException("No backups found.");
        }
    }

    private int calculateMaxProgress(int multiplier) {
        int tasks = 1;
        if (mRequestedFlags.backupApkFiles()) ++tasks;
        if (mRequestedFlags.backupData()) ++tasks;
        if (mRequestedFlags.backupExtras()) ++tasks;
        if (mRequestedFlags.backupRules()) ++tasks;
        return tasks * multiplier;
    }
}
