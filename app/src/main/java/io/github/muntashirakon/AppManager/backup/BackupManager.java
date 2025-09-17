// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.backup.struct.BackupOpOptions;
import io.github.muntashirakon.AppManager.backup.struct.DeleteOpOptions;
import io.github.muntashirakon.AppManager.backup.struct.RestoreOpOptions;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;

/**
 * Manage backups for individual package belong to individual user.
 */
public class BackupManager {
    public static final String TAG = BackupManager.class.getSimpleName();

    /* language=regexp */
    static final String[] CACHE_DIRS = new String[]{"cache/.*", "code_cache/.*", "no_backup/.*"};
    /* language=regexp */
    static final String[] LIB_DIR = new String[]{"lib/"};
    public static final String SOURCE_PREFIX = "source";
    public static final String DATA_PREFIX = "data";
    static final String KEYSTORE_PREFIX = "keystore";
    static final int KEYSTORE_PLACEHOLDER = -1000;
    static final String DATA_BACKUP_SPECIAL_PREFIX = "special:";
    static final String DATA_BACKUP_SPECIAL_ADB = DATA_BACKUP_SPECIAL_PREFIX + "adb";

    public static final String CERT_PREFIX = "cert_";
    static final String MASTER_KEY = ".masterkey";

    @NonNull
    public static String getExt(@TarUtils.TarType String tarType) {
        if (TarUtils.TAR_BZIP2.equals(tarType)) {
            return ".tar.bz2";
        } else if (TarUtils.TAR_ZSTD.equals(tarType)) {
            return ".tar.zst";
        } else return ".tar.gz";
    }

    private boolean mRequiresRestart;

    public BackupManager() {
        ExUtils.exceptionAsIgnored(BackupItems::createNoMediaIfNotExists);
    }

    public boolean requiresRestart() {
        return mRequiresRestart;
    }

    public void backup(@NonNull BackupOpOptions options, @Nullable ProgressHandler progressHandler)
            throws BackupException {
        if (options.packageName.equals("android")) {
            throw new BackupException("Android System (android) cannot be backed up.");
        }
        if (options.flags.isEmpty()) {
            throw new BackupException("Backup is requested without any flags.");
        }
        BackupItems.BackupItem backupItem;
        try {
            if (options.override) {
                backupItem = BackupItems.findOrCreateBackupItem(options.userId, options.backupName, options.packageName);
            } else {
                backupItem = BackupItems.createBackupItemGracefully(options.userId, options.backupName, options.packageName);
            }
        } catch (IOException e) {
            throw new BackupException("Could not create BackupItem.", e);
        }
        if (progressHandler != null) {
            int max = calculateMaxProgress(options.flags);
            progressHandler.setProgressTextInterface(ProgressHandler.PROGRESS_PERCENT);
            progressHandler.postUpdate(max, 0f);
        }
        try (BackupOp backupOp = new BackupOp(options.packageName, options.flags, backupItem, options.userId)) {
            backupOp.runBackup(progressHandler);
            BackupUtils.putBackupToDbAndBroadcast(ContextUtils.getContext(), backupOp.getMetadata());
        }
    }

    /**
     * Restore a single backup for a given package belonging to the given package
     */
    public void restore(@NonNull RestoreOpOptions options, @Nullable ProgressHandler progressHandler)
            throws BackupException {
        if (options.packageName.equals("android")) {
            throw new BackupException("Android System (android) cannot be restored.");
        }
        if (options.flags.isEmpty()) {
            throw new BackupException("Restore is requested without any flags.");
        }
        BackupItems.BackupItem backupItem;
        try {
            if (options.relativeDir != null) {
                backupItem = BackupItems.findBackupItem(options.relativeDir);
            } else {
                // Use base backup
                Backup baseBackup = BackupUtils.retrieveBaseBackupFromDb(options.userId, options.packageName);
                if (baseBackup != null) {
                    backupItem = baseBackup.getItem();
                } else {
                    throw new BackupException("No base backup found.");
                }
            }
        } catch (IOException e) {
            throw new BackupException("Could not get backup files.", e);
        }
        if (progressHandler != null) {
            int max = calculateMaxProgress(options.flags);
            progressHandler.setProgressTextInterface(ProgressHandler.PROGRESS_PERCENT);
            progressHandler.postUpdate(max, 0f);
        }
        try (RestoreOp restoreOp = new RestoreOp(options.packageName, options.flags, backupItem, options.userId)) {
            restoreOp.runRestore(progressHandler);
            mRequiresRestart |= restoreOp.requiresRestart();
        }
    }

    public void deleteBackup(@NonNull DeleteOpOptions options) throws BackupException {
        List<BackupItems.BackupItem> backupItemList;
        if (options.relativeDirs == null) {
            // Delete base backup
            Backup baseBackup = BackupUtils.retrieveBaseBackupFromDb(options.userId, options.packageName);
            if (baseBackup != null) {
                try {
                    backupItemList = Collections.singletonList(baseBackup.getItem());
                } catch (IOException e) {
                    throw new BackupException("Could not get backup files.", e);
                }
            } else backupItemList = Collections.emptyList();
        } else {
            backupItemList = new ArrayList<>(options.relativeDirs.length);
            for (String relativeDir : options.relativeDirs) {
                try {
                    backupItemList.add(BackupItems.findBackupItem(relativeDir));
                } catch (IOException e) {
                    throw new BackupException("Could not get backup files.", e);
                }
            }
        }
        for (BackupItems.BackupItem backupItem : backupItemList) {
            BackupMetadataV5 metadata;
            try {
                metadata = backupItem.getMetadata();
            } catch (IOException e) {
                throw new BackupException("Could not retrieve metadata from backup.", e);
            }
            if (!backupItem.isFrozen() && !backupItem.delete()) {
                throw new BackupException("Could not delete the selected backups");
            }
            BackupUtils.deleteBackupToDbAndBroadcast(ContextUtils.getContext(), metadata);
        }
    }

    public void verify(@NonNull String relativeDir) throws BackupException {
        BackupItems.BackupItem backupItem;
        try {
            backupItem = BackupItems.findBackupItem(relativeDir);
        } catch (IOException e) {
            throw new BackupException("Could not get backup files.", e);
        }
        try (VerifyOp restoreOp = new VerifyOp(backupItem)) {
            restoreOp.verify();
        }
    }

    private static int calculateMaxProgress(@NonNull BackupFlags backupFlags) {
        int tasks = 1;
        if (backupFlags.backupApkFiles()) ++tasks;
        if (backupFlags.backupData()) ++tasks;
        if (backupFlags.backupExtras()) ++tasks;
        if (backupFlags.backupRules()) ++tasks;
        return tasks;
    }
}
