// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper;
import io.github.muntashirakon.io.FileStatus;
import io.github.muntashirakon.io.ProxyFile;
import io.github.muntashirakon.io.ProxyFiles;

public final class BackupUtils {
    @NonNull
    private static List<String> getBackupPackages() {
        File backupPath = BackupFiles.getBackupDirectory();
        List<String> packages;
        String[] files = backupPath.list((dir, name) -> new ProxyFile(dir, name).isDirectory());
        if (files != null) packages = new ArrayList<>(Arrays.asList(files));
        else return new ArrayList<>();
        packages.remove(SaveLogHelper.SAVED_LOGS_DIR);
        packages.remove(BackupFiles.APK_SAVING_DIRECTORY);
        packages.remove(BackupFiles.TEMPORARY_DIRECTORY);
        // We don't need to check the contents of the packages at this stage.
        // It's the caller's job to check contents if needed.
        return packages;
    }

    @WorkerThread
    @NonNull
    public static HashMap<String, Backup> storeAllAndGetLatestBackupMetadata() {
        HashMap<String, Backup> backupMetadata = new HashMap<>();
        HashMap<String, List<MetadataManager.Metadata>> allBackupMetadata = getAllMetadata();
        List<Backup> backups = new ArrayList<>();
        for (List<MetadataManager.Metadata> metadataList : allBackupMetadata.values()) {
            if (metadataList.size() == 0) continue;
            Backup latestBackup = null;
            Backup backup;
            for (MetadataManager.Metadata metadata : metadataList) {
                backup = Backup.fromBackupMetadata(metadata);
                backups.add(backup);
                if (latestBackup == null || backup.backupTime > latestBackup.backupTime) {
                    latestBackup = backup;
                }
            }
            backupMetadata.put(latestBackup.packageName, latestBackup);
        }
        AppManager.getDb().backupDao().insert(backups);
        return backupMetadata;
    }

    @WorkerThread
    @Nullable
    public static Backup storeAllAndGetLatestBackupMetadata(String packageName) {
        MetadataManager.Metadata[] allBackupMetadata = MetadataManager.getMetadata(packageName);
        List<Backup> backups = new ArrayList<>();
        Backup latestBackup = null;
        Backup backup;
        for (MetadataManager.Metadata metadata : allBackupMetadata) {
            backup = Backup.fromBackupMetadata(metadata);
            backups.add(backup);
            if (latestBackup == null || backup.backupTime > latestBackup.backupTime) {
                latestBackup = backup;
            }
        }
        AppManager.getDb().backupDao().insert(backups);
        return latestBackup;
    }

    @WorkerThread
    @NonNull
    public static HashMap<String, Backup> getAllLatestBackupMetadataFromDb() {
        HashMap<String, Backup> backupMetadata = new HashMap<>();
        List<Backup> backups = AppManager.getDb().backupDao().getAll();
        for (Backup backup : backups) {
            Backup latestBackup = backupMetadata.get(backup.packageName);
            if (latestBackup == null || backup.backupTime > latestBackup.backupTime) {
                backupMetadata.put(backup.packageName, backup);
            }
        }
        return backupMetadata;
    }

    /**
     * Retrieves all metadata for all packages
     */
    @WorkerThread
    @NonNull
    public static HashMap<String, List<MetadataManager.Metadata>> getAllMetadata() {
        HashMap<String, List<MetadataManager.Metadata>> backupMetadata = new HashMap<>();
        List<String> backupPackages = getBackupPackages();
        for (String dirtyPackageName : backupPackages) {
            MetadataManager.Metadata[] metadataList = MetadataManager.getMetadata(dirtyPackageName);
            for (MetadataManager.Metadata metadata : metadataList) {
                if (!backupMetadata.containsKey(metadata.packageName)) {
                    backupMetadata.put(metadata.packageName, new ArrayList<>());
                }
                //noinspection ConstantConditions
                backupMetadata.get(metadata.packageName).add(metadata);
            }
        }
        return backupMetadata;
    }

    @NonNull
    static Pair<Integer, Integer> getUidAndGid(String filepath, int uid) {
        try {
            FileStatus status = ProxyFiles.stat(new ProxyFile(filepath));
            return new Pair<>(status.st_uid, status.st_gid);
        } catch (Exception e) {
            // Fallback to kernel user ID
            return new Pair<>(uid, uid);
        }
    }

    @Nullable
    public static String getShortBackupName(@NonNull String backupFileName) {
        if (TextUtils.isDigitsOnly(backupFileName)) {
            // It's already a user handle
            return null;
        } else {
            int firstUnderscore = backupFileName.indexOf('_');
            if (firstUnderscore != -1) {
                // Found an underscore
                String userHandle = backupFileName.substring(0, firstUnderscore);
                if (TextUtils.isDigitsOnly(userHandle)) {
                    // The new backup system
                    return backupFileName.substring(firstUnderscore + 1);
                }
            }
            // Could be the old naming style
            throw new IllegalArgumentException("Invalid backup name " + backupFileName);
        }
    }

    static int getUserHandleFromBackupName(@NonNull String backupFileName) {
        if (TextUtils.isDigitsOnly(backupFileName)) return Integer.parseInt(backupFileName);
        else {
            int firstUnderscore = backupFileName.indexOf('_');
            if (firstUnderscore != -1) {
                // Found an underscore
                String userHandle = backupFileName.substring(0, firstUnderscore);
                if (TextUtils.isDigitsOnly(userHandle)) {
                    // The new backup system
                    return Integer.parseInt(userHandle);
                }
            }
            throw new IllegalArgumentException("Invalid backup name");
        }
    }

    @NonNull
    static String[] getExcludeDirs(boolean includeCache, @Nullable String[] others) {
        List<String> excludeDirs = new ArrayList<>();
        if (includeCache) {
            excludeDirs.addAll(Arrays.asList(BackupManager.CACHE_DIRS));
        }
        if (others != null) {
            excludeDirs.addAll(Arrays.asList(others));
        }
        return excludeDirs.toArray(new String[0]);
    }
}
