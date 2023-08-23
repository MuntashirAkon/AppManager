// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.types.PackageChangeReceiver;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public final class BackupUtils {
    public static final String TAG = BackupUtils.class.getSimpleName();

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
    public static List<Backup> getBackupMetadataFromDbNoLockValidate(@NonNull String packageName) {
        List<Backup> backups = new AppDb().getAllBackupsNoLock(packageName);
        List<Backup> validatedBackups = new ArrayList<>(backups.size());
        for (Backup backup : backups) {
            try {
                if (backup.getBackupPath().exists()) {
                    validatedBackups.add(backup);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return validatedBackups;
    }

    @WorkerThread
    @Nullable
    public static Backup getLatestBackupMetadataFromDbNoLockValidate(@NonNull String packageName) {
        List<Backup> backups = getBackupMetadataFromDbNoLockValidate(packageName);
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

    @SuppressLint("SdCardPath")
    @NonNull
    static String[] getDataDirectories(@NonNull ApplicationInfo applicationInfo, boolean loadInternal,
                                       boolean loadExternal, boolean loadMediaObb) {
        // Data directories *must* be readable and non-empty
        ArrayList<String> dataDirs = new ArrayList<>();
        if (applicationInfo.dataDir == null) {
            throw new IllegalArgumentException("Data directory cannot be empty.");
        }
        int userId = UserHandleHidden.getUserId(applicationInfo.uid);
        if (loadInternal) {
            String dataDir = applicationInfo.dataDir;
            if (dataDir.startsWith("/data/data/")) {
                dataDir = Utils.replaceOnce(dataDir, "/data/data/", String.format(Locale.ROOT, "/data/user/%d/", userId));
            }
            dataDirs.add(dataDir);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && applicationInfo.deviceProtectedDataDir != null) {
                // /data/user_de/{userId}
                dataDirs.add(applicationInfo.deviceProtectedDataDir);
            }
        }
        // External directories could be /sdcard, /storage/sdcard, /storage/emulated/{userId}
        OsEnvironment.UserEnvironment ue = OsEnvironment.getUserEnvironment(userId);
        if (loadExternal) {
            Path[] externalFiles = ue.buildExternalStorageAppDataDirs(applicationInfo.packageName);
            for (Path externalFile : externalFiles) {
                // Replace /storage/emulated/{!myUserId} with /data/media/{!myUserId}
                externalFile = Paths.getAccessiblePath(externalFile);
                if (externalFile.listFiles().length > 0) {
                    dataDirs.add(externalFile.getFilePath());
                }
            }
        }
        if (loadMediaObb) {
            List<Path> externalFiles = new ArrayList<>();
            externalFiles.addAll(Arrays.asList(ue.buildExternalStorageAppMediaDirs(applicationInfo.packageName)));
            externalFiles.addAll(Arrays.asList(ue.buildExternalStorageAppObbDirs(applicationInfo.packageName)));
            for (Path externalFile : externalFiles) {
                // Replace /storage/emulated/{!myUserId} with /data/media/{!myUserId}
                externalFile = Paths.getAccessiblePath(externalFile);
                if (externalFile.listFiles().length > 0) {
                    dataDirs.add(externalFile.getFilePath());
                }
            }
        }
        return dataDirs.toArray(new String[0]);
    }

    /**
     * Get a writable data directory from the given directory. This is useful for restoring a backup.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    @SuppressLint("SdCardPath")
    static String getWritableDataDirectory(@NonNull String dataDir, @UserIdInt int oldUserId, @UserIdInt int newUserId) {
        if (dataDir.startsWith("/data/data/")) {
            // /data/data/ -> /data/user/{newUserId}/
            return Utils.replaceOnce(dataDir, "/data/data/", String.format(Locale.ROOT, "/data/user/%d/", newUserId));
        }
        String dataUserDir = String.format(Locale.ROOT, "/data/user/%d/", oldUserId);
        if (dataDir.startsWith(dataUserDir)) {
            // /data/user/{oldUserId} -> /data/user/{newUserId}/
            return Utils.replaceOnce(dataDir, dataUserDir, String.format(Locale.ROOT, "/data/user/%d/", newUserId));
        }
        String dataUserDeDir = String.format(Locale.ROOT, "/data/user_de/%d/", oldUserId);
        if (dataDir.startsWith(dataUserDeDir)) {
            // /data/user_de/{oldUserId} -> /data/user_de/{newUserId}/
            return Utils.replaceOnce(dataDir, dataUserDeDir, String.format(Locale.ROOT, "/data/user_de/%d/", newUserId));
        }
        if (dataDir.startsWith("/sdcard/")) {
            // /sdcard/ -> /storage/emulated/{newUserId}/ or /data/media/{newUserId}/ in a multiuser system
            return getExternalStorage(dataDir, "/sdcard/", newUserId);
        }
        if (dataDir.startsWith("/storage/sdcard/")) {
            // /storage/sdcard/ -> /storage/emulated/{newUserId}/ or /data/media/{newUserId}/ in a multiuser system, otherwise /sdcard/
            return getExternalStorage(dataDir, "/storage/sdcard/", newUserId);
        }
        if (dataDir.startsWith("/storage/sdcard0/")) {
            // /storage/sdcard0/ -> /storage/emulated/{newUserId}/ or /data/media/{newUserId}/ in a multiuser system, otherwise /sdcard/
            return getExternalStorage(dataDir, "/storage/sdcard0/", newUserId);
        }
        String oldStorageEmulatedDir = String.format(Locale.ROOT, "/storage/emulated/%d/", oldUserId);
        if (dataDir.startsWith(oldStorageEmulatedDir)) {
            // /storage/emulated/{oldUserId}/ -> /storage/emulated/{newUserId}/ or /data/media/{newUserId}/ in a multiuser system, otherwise /sdcard/
            return getExternalStorage(dataDir, oldStorageEmulatedDir, newUserId);
        }
        String oldDataMediaDir = String.format(Locale.ROOT, "/data/media/%d/", oldUserId);
        if (dataDir.startsWith(oldDataMediaDir)) {
            // /data/media/{oldUserId}/ -> /storage/emulated/{newUserId}/ or /data/media/{newUserId}/ in a multiuser system, otherwise /sdcard/
            return getExternalStorage(dataDir, oldDataMediaDir, newUserId);
        }
        Log.i(TAG, "getWritableDataDirectory: Unrecognized path %s, using as is.", dataDir);
        return dataDir;
    }

    @SuppressLint("SdCardPath")
    @NonNull
    private static String getExternalStorage(@NonNull String dataDir, @NonNull String match, @UserIdInt int userId) {
        if (Users.getAllUsers().size() > 1) {
            // Multiuser system, use either /storage/emulated/{userId} or /data/media/{userId}
            String storageEmulatedDir = String.format(Locale.ROOT, "/storage/emulated/%d/", userId);
            if (userId == UserHandleHidden.myUserId() && Paths.get(storageEmulatedDir).canRead()) {
                return Utils.replaceOnce(dataDir, match, storageEmulatedDir);
            }
            return Utils.replaceOnce(dataDir, match, String.format(Locale.ROOT, "/data/media/%d/", userId));
        }
        // Otherwise, use /sdcard
        return Utils.replaceOnce(dataDir, match, "/sdcard/");
    }
}
