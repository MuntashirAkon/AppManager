// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.jetbrains.annotations.Contract;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.BroadcastUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public final class BackupUtils {
    public static final String TAG = BackupUtils.class.getSimpleName();

    public static final String[] TAR_TYPES = new String[]{TarUtils.TAR_GZIP, TarUtils.TAR_BZIP2, TarUtils.TAR_ZSTD};
    public static final String[] TAR_TYPES_READABLE = new String[]{"GZip", "BZip2", "Zstandard"};

    private static final Pattern UUID_PATTERN = Pattern.compile("[a-f\\d]{8}(-[a-f\\d]{4}){3}-[a-f\\d]{12}");

    public static boolean isUuid(@NonNull String name) {
        return UUID_PATTERN.matcher(name).matches();
    }

    @Nullable
    @Contract("!null -> !null")
    public static String getCompatBackupName(@Nullable String backupName) {
        if (MetadataManager.getCurrentBackupMetaVersion() >= 5) {
            return backupName;
        }
        return getV4SanitizedBackupName(backupName);
    }

    @NonNull
    public static String getV4BackupName(@UserIdInt int userId, @Nullable String backupName) {
        if (backupName == null) {
            return String.valueOf(userId);
        }
        // For v4 and earlier, backup name is used as a filename. So, necessary sanitization may be
        // required.
        return userId + "_" + getV4SanitizedBackupName(backupName);
    }

    @Nullable
    @Contract("!null -> !null")
    public static String getV4SanitizedBackupName(@Nullable String backupName) {
        if (backupName == null) {
            return null;
        }
        // [\\/:?"<>|\s]
        return backupName.trim().replaceAll("[\\\\/:?\"<>|\\s]+", "_");
    }

    @NonNull
    public static String getV5RelativeDir(@NonNull String backupUuid) {
        // backups/{backupUuid}
        return BackupItems.BACKUP_DIRECTORY + File.separator + backupUuid;
    }

    @NonNull
    public static String getV4RelativeDir(@NonNull String backupNameWithUser, @NonNull String packageName) {
        // Relative directory needs to be inferred: {packageName}/{backupNameWithUser}
        // where backupNameWithUser = {userid}[_{backup_name}]
        return packageName + File.separator + backupNameWithUser;
    }

    @NonNull
    public static String getV4RelativeDir(@UserIdInt int userId, @Nullable String backupName, @NonNull String packageName) {
        // Relative directory needs to be inferred: {packageName}/{backupName}
        // where backupName = {userid}[_{backup_name}]
        return packageName + File.separator + getV4BackupName(userId, backupName);
    }

    @Nullable
    public static String getRealBackupName(int backupVersion, @Nullable String backupNameWithUserId) {
        if (backupVersion >= 5) {
            return backupNameWithUserId;
        } else {
            // v4 or earlier backup: {userid}[_{backup_name}]
            if (backupNameWithUserId == null || TextUtils.isDigitsOnly(backupNameWithUserId)) {
                // It's only a user ID
                return null;
            } else {
                int firstUnderscore = backupNameWithUserId.indexOf('_');
                if (firstUnderscore != -1) {
                    // Found an underscore
                    String userHandle = backupNameWithUserId.substring(0, firstUnderscore);
                    if (TextUtils.isDigitsOnly(userHandle)) {
                        return backupNameWithUserId.substring(firstUnderscore + 1);
                    }
                }
                throw new IllegalArgumentException("Invalid backup name " + backupNameWithUserId);
            }
        }
    }

    public static String getReadableTarType(@TarUtils.TarType String tarType) {
        int i = ArrayUtils.indexOf(TAR_TYPES, tarType);
        if (i == -1) {
            return "GZip";
        }
        return TAR_TYPES_READABLE[i];
    }

    @WorkerThread
    @NonNull
    public static HashMap<String, Backup> storeAllAndGetLatestBackupMetadata() {
        AppDb appDb = new AppDb();
        HashMap<String, Backup> backupMetadata = new HashMap<>();
        HashMap<String, List<BackupMetadataV5>> allBackupMetadata = getAllMetadata();
        List<Backup> backups = new ArrayList<>();
        for (List<BackupMetadataV5> metadataList : allBackupMetadata.values()) {
            if (metadataList.isEmpty()) continue;
            Backup latestBackup = null;
            Backup backup;
            for (BackupMetadataV5 metadataV5 : metadataList) {
                backup = Backup.fromBackupMetadataV5(metadataV5);
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

    public static void putBackupToDbAndBroadcast(@NonNull Context context, @NonNull BackupMetadataV5 metadata) {
        if (Utils.isRoboUnitTest()) {
            return;
        }
        AppDb appDb = new AppDb();
        appDb.insert(Backup.fromBackupMetadataV5(metadata));
        appDb.updateApplication(context, metadata.metadata.packageName);
        BroadcastUtils.sendDbPackageAltered(context, new String[]{metadata.metadata.packageName});
    }

    public static void deleteBackupToDbAndBroadcast(@NonNull Context context, @NonNull BackupMetadataV5 metadata) {
        AppDb appDb = new AppDb();
        appDb.deleteBackup(Backup.fromBackupMetadataV5(metadata));
        appDb.updateApplication(context, metadata.metadata.packageName);
        BroadcastUtils.sendDbPackageAltered(context, new String[]{metadata.metadata.packageName});
    }

    @WorkerThread
    @NonNull
    public static List<Backup> getBackupMetadataFromDbNoLockValidate(@NonNull String packageName) {
        List<Backup> backups = new AppDb().getAllBackupsNoLock(packageName);
        List<Backup> validatedBackups = new ArrayList<>(backups.size());
        for (Backup backup : backups) {
            try {
                if (backup.getItem().exists()) {
                    validatedBackups.add(backup);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return validatedBackups;
    }

    @NonNull
    public static List<Backup> retrieveBackupFromDb(@UserIdInt int userId,
                                                    @Nullable String backupName,
                                                    @NonNull String packageName) {
        List<Backup> backups = getBackupMetadataFromDbNoLockValidate(packageName);
        if (backupName == null) {
            backupName = "";
        }
        backupName = getV4SanitizedBackupName(backupName);
        List<Backup> backupList = new ArrayList<>();
        for (Backup backup : backups) {
            if (backup.userId != userId) {
                continue;
            }
            if (!Objects.equals(backupName, getV4SanitizedBackupName(backup.backupName))) {
                continue;
            }
            backupList.add(backup);
        }
        return backupList;
    }

    @Nullable
    public static Backup retrieveLatestBackupFromDb(@UserIdInt int userId,
                                                    @Nullable String backupName,
                                                    @NonNull String packageName) {
        List<Backup> backups = getBackupMetadataFromDbNoLockValidate(packageName);
        if (backupName == null) {
            backupName = "";
        }
        backupName = getV4SanitizedBackupName(backupName);
        for (Backup backup : backups) {
            if (backup.userId == userId && Objects.equals(backupName, getV4SanitizedBackupName(backup.backupName))) {
                return backup;
            }
        }
        return null;
    }

    @Nullable
    public static Backup retrieveBaseBackupFromDb(@UserIdInt int userId,
                                                  @NonNull String packageName) {
        List<Backup> backups = getBackupMetadataFromDbNoLockValidate(packageName);
        for (Backup backup : backups) {
            if (backup.userId == userId && TextUtils.isEmpty(backup.backupName)) {
                return backup;
            }
        }
        return null;
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
    private static HashMap<String, List<BackupMetadataV5>> getAllMetadata() {
        HashMap<String, List<BackupMetadataV5>> backupMetadata = new HashMap<>();
        List<BackupItems.BackupItem> backupPaths = BackupItems.findAllBackupItems();
        for (BackupItems.BackupItem backupItem : backupPaths) {
            try {
                BackupMetadataV5 metadataV5 = backupItem.getMetadata();
                BackupMetadataV5.Metadata metadata = metadataV5.metadata;
                if (!backupMetadata.containsKey(metadata.packageName)) {
                    backupMetadata.put(metadata.packageName, new ArrayList<>());
                }
                //noinspection ConstantConditions
                backupMetadata.get(metadata.packageName).add(metadataV5);
            } catch (IOException e) {
                Log.w(TAG, "Invalid backup: %s", e, backupItem.getRelativeDir());
            }
        }
        return backupMetadata;
    }

    @NonNull
    public static String getDataFilePrefix(int index, @Nullable String fullExtension) {
        if (fullExtension == null) {
            return BackupManager.DATA_PREFIX + index;
        }
        return BackupManager.DATA_PREFIX + index + fullExtension;
    }

    @NonNull
    static String[] getExcludeDirs(boolean includeCache, @Nullable String... others) {
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
    static List<String> getDataDirectories(@NonNull ApplicationInfo applicationInfo, boolean loadInternal,
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
        return dataDirs;
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
