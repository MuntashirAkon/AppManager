// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.content.Context;
import android.content.Intent;
import android.system.ErrnoException;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.types.PackageChangeReceiver;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.UidGidPair;

public final class BackupUtils {
    private static final Pattern UUID_PATTERN = Pattern.compile("[a-f\\d]{8}(-[a-f\\d]{4}){3}-[a-f\\d]{12}");

    public static boolean isUuid(@NonNull String name) {
        return UUID_PATTERN.matcher(name).matches();
    }

    @NonNull
    private static List<Path> getBackupPaths() {
        Path baseDirectory = BackupFiles.getBaseDirectory();
        List<Path> backupPaths;
        Path[] paths = baseDirectory.listFiles(Path::isDirectory);
        backupPaths = new ArrayList<>(paths.length);
        for (Path path : paths) {
            if (isUuid(path.getName())) {
                // UUID-based backups only store one backup per folder
                backupPaths.add(path);
            }
            if (SaveLogHelper.SAVED_LOGS_DIR.equals(path.getName())) {
                continue;
            }
            if (BackupFiles.APK_SAVING_DIRECTORY.equals(path.getName())) {
                continue;
            }
            if (BackupFiles.TEMPORARY_DIRECTORY.equals(path.getName())) {
                continue;
            }
            // Other backups can store multiple backups per folder
            backupPaths.addAll(Arrays.asList(path.listFiles(Path::isDirectory)));
        }
        // We don't need to check further at this stage.
        // It's the caller's job to check the contents if needed.
        return backupPaths;
    }

    @WorkerThread
    @NonNull
    public static HashMap<String, Backup> storeAllAndGetLatestBackupMetadata() {
        AppDb appDb = new AppDb();
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
        appDb.deleteAllBackups();
        appDb.insertBackups(backups);
        return backupMetadata;
    }

    @WorkerThread
    @NonNull
    public static HashMap<String, Backup> getAllLatestBackupMetadataFromDb() {
        HashMap<String, Backup> backupMetadata = new HashMap<>();
        for (Backup backup : new AppDb().getAllBackups()) {
            Backup latestBackup = backupMetadata.get(backup.packageName);
            if (latestBackup == null || backup.backupTime > latestBackup.backupTime) {
                backupMetadata.put(backup.packageName, backup);
            }
        }
        return backupMetadata;
    }

    public static void putBackupToDbAndBroadcast(@NonNull Context context, @NonNull MetadataManager.Metadata metadata) {
        if (Utils.isRoboUnitTest()) {
            return;
        }
        AppDb appDb = new AppDb();
        appDb.insert(Backup.fromBackupMetadata(metadata));
        appDb.updateApplication(context, metadata.packageName);
        Intent intent = new Intent(PackageChangeReceiver.ACTION_DB_PACKAGE_ALTERED);
        intent.setPackage(context.getPackageName());
        intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, new String[]{metadata.packageName});
        context.sendBroadcast(intent);
    }

    public static void deleteBackupToDbAndBroadcast(@NonNull Context context, @NonNull MetadataManager.Metadata metadata) {
        AppDb appDb = new AppDb();
        appDb.deleteBackup(Backup.fromBackupMetadata(metadata));
        appDb.updateApplication(context, metadata.packageName);
        Intent intent = new Intent(PackageChangeReceiver.ACTION_DB_PACKAGE_REMOVED);
        intent.setPackage(context.getPackageName());
        intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, new String[]{metadata.packageName});
        context.sendBroadcast(intent);
    }

    @WorkerThread
    @NonNull
    public static List<Backup> getBackupMetadataFromDb(@NonNull String packageName) {
        return new AppDb().getAllBackups(packageName);
    }

    @WorkerThread
    @Nullable
    public static Backup getLatestBackupMetadataFromDb(@NonNull String packageName) {
        List<Backup> backups = getBackupMetadataFromDb(packageName);
        Backup latestBackup = null;
        for (Backup backup : backups) {
            if (latestBackup == null || backup.backupTime > latestBackup.backupTime) {
                latestBackup = backup;
            }
        }
        return latestBackup;
    }

    /**
     * Retrieves all metadata for all packages
     */
    @WorkerThread
    @NonNull
    public static HashMap<String, List<MetadataManager.Metadata>> getAllMetadata() {
        HashMap<String, List<MetadataManager.Metadata>> backupMetadata = new HashMap<>();
        List<Path> backupPaths = getBackupPaths();
        for (Path backupPath : backupPaths) {
            try {
                MetadataManager.Metadata metadata = MetadataManager.getMetadata(backupPath);
                if (!backupMetadata.containsKey(metadata.packageName)) {
                    backupMetadata.put(metadata.packageName, new ArrayList<>());
                }
                //noinspection ConstantConditions
                backupMetadata.get(metadata.packageName).add(metadata);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return backupMetadata;
    }

    @NonNull
    static Pair<Integer, Integer> getUidAndGid(@NonNull Path filepath, int uid) {
        try {
            UidGidPair uidGidPair = Objects.requireNonNull(filepath.getFile()).getUidGid();
            return new Pair<>(uidGidPair.uid, uidGidPair.gid);
        } catch (ErrnoException e) {
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
        // Lib dirs has to be ignored by default
        List<String> excludeDirs = new ArrayList<>(Arrays.asList(BackupManager.LIB_DIR));
        if (includeCache) {
            excludeDirs.addAll(Arrays.asList(BackupManager.CACHE_DIRS));
        }
        if (others != null) {
            excludeDirs.addAll(Arrays.asList(others));
        }
        return excludeDirs.toArray(new String[0]);
    }
}
