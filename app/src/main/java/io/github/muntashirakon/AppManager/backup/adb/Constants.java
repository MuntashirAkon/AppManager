// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.adb;

import android.app.backup.BackupAgent;
import android.os.Build;

/**
 * Constants taken from {@code android.app.backup.FullBackup}, {@code com.android.server.backup.BackupManagerService},
 * {@code com.android.server.backup.utils.PasswordUtils}, {@code com.android.server.backup.BackupPasswordManager}
 */
final class Constants {
    /**
     * Path containing APK files i.e. /apps/{package}/a
     */
    public static final String APK_TREE_TOKEN = "a";
    /**
     * Path containing OBB files i.e. /apps/{package}/obb
     */
    public static final String OBB_TREE_TOKEN = "obb";
    /**
     * Path containing key-value data. Maximum 5 MB.
     */
    public static final String KEY_VALUE_DATA_TOKEN = "k";


    /**
     * Path containing internal data from CE context i.e. /apps/{package}/r excluding files, no_backup, cache,
     * code_cache and shared_prefs directories. This corresponds to /data/user/{userId}/{package}.
     */
    public static final String ROOT_TREE_TOKEN = "r";
    /**
     * Path containing files data from CE context i.e. /apps/{package}/f. This corresponds to /data/user/{userId}/{package}/files.
     * But this is not always correct. {@link BackupAgent} is capable of fetching the exact location since it runs in
     * the app's context.
     */
    public static final String FILES_TREE_TOKEN = "f";
    /**
     * Path containing no_backup data from CE context i.e. /apps/{package}/nb. This path is never used as of Android 13.
     */
    public static final String NO_BACKUP_TREE_TOKEN = "nb";
    /**
     * Path containing databases from CE context i.e. /apps/{package}/db. This corresponds to /data/user/{userId}/{package}/databases.
     * But this is not always correct. {@link BackupAgent} is capable of fetching the exact location since it runs in
     * the app's context.
     */
    public static final String DATABASE_TREE_TOKEN = "db";
    /**
     * Path containing shared preferences from CE context i.e. /apps/{package}/db. This corresponds to /data/user/{userId}/{package}/shared_prefs.
     * But this is not always correct. {@link BackupAgent} is capable of fetching the exact location since it runs in
     * the app's context.
     */
    public static final String SHAREDPREFS_TREE_TOKEN = "sp";
    /**
     * Path containing caches data from CE context i.e. /apps/{package}/c. This path is never used as of Android 13.
     */
    public static final String CACHE_TREE_TOKEN = "c";

    /**
     * Same as {@link #ROOT_TREE_TOKEN} but for DE context.
     */
    public static final String DEVICE_ROOT_TREE_TOKEN = "d_r";
    /**
     * Same as {@link #FILES_TREE_TOKEN} but for DE context.
     */
    public static final String DEVICE_FILES_TREE_TOKEN = "d_f";
    /**
     * Same as {@link #NO_BACKUP_TREE_TOKEN} but for DE context. This path is never used as of Android 13.
     */
    public static final String DEVICE_NO_BACKUP_TREE_TOKEN = "d_nb";
    /**
     * Same as {@link #DATABASE_TREE_TOKEN} but for DE context.
     */
    public static final String DEVICE_DATABASE_TREE_TOKEN = "d_db";
    /**
     * Same as {@link #SHAREDPREFS_TREE_TOKEN} but for DE context.
     */
    public static final String DEVICE_SHAREDPREFS_TREE_TOKEN = "d_sp";
    /**
     * Same as {@link #CACHE_TREE_TOKEN} but for DE context. This path is never used as of Android 13.
     */
    public static final String DEVICE_CACHE_TREE_TOKEN = "d_c";

    /**
     * Files containing in the external storage directory returned by {@link android.content.Context#getExternalFilesDir(String)}.
     * {@link BackupAgent} is capable of fetching this directory as it runs in the app's context. Location is /apps/{package}/ef.
     */
    public static final String MANAGED_EXTERNAL_TREE_TOKEN = "ef";
    /**
     * Path containing files from the external storages. This path is never used as of Android 13.
     */
    public static final String SHARED_STORAGE_TOKEN = "shared";

    /**
     * All apps are stored inside this directory in the backup file. The immediate children are the package names.
     */
    public static final String APPS_PREFIX = "apps/";
    /**
     * Shared storages are stored in this directory in the backup file. The immediate children are the volume names.
     * This is never really used as of Android 13.
     */
    public static final String SHARED_PREFIX = SHARED_STORAGE_TOKEN + "/";

    // Name and current contents version of the full-backup manifest file
    //
    // Manifest version history:
    //
    // 1 : initial release
    public static final String BACKUP_MANIFEST_FILENAME = "_manifest";
    public static final int BACKUP_MANIFEST_VERSION = 1;

    // External archive format version history:
    //
    // 1 : initial release (4+)
    // 2 : no format change per se; version bump to facilitate PBKDF2 version skew detection (unused)
    // 3 : introduced "_meta" metadata file; no other format change per se (5+)
    // 4 : added support for new device-encrypted storage locations (7+)
    // 5 : added support for key-value packages (8+)
    public static final int BACKUP_FILE_VERSION = 5;
    public static final String BACKUP_FILE_HEADER_MAGIC = "ANDROID BACKUP\n";
    public static final String BACKUP_METADATA_FILENAME = "_meta";
    public static final int BACKUP_METADATA_VERSION = 1;
    public static final int BACKUP_WIDGET_METADATA_TOKEN = 0x01FFED01;

    // Configuration of PBKDF2 that we use for generating pw hashes and intermediate keys
    public static final int PBKDF2_HASH_ROUNDS = 10000;
    public static final int PBKDF2_KEY_SIZE = 256;     // bits
    public static final int PBKDF2_SALT_SIZE = 512;    // bits
    public static final String ENCRYPTION_ALGORITHM_NAME = "AES-256";

    public static final String PBKDF_CURRENT = "PBKDF2WithHmacSHA1";
    public static final String PBKDF_FALLBACK = "PBKDF2WithHmacSHA1And8bit";

    private Constants() {
    }

    public static int getBackupFileVersionFromApi(int api) {
        if (api <= 0) {
            api = Build.VERSION.SDK_INT;
        }
        if (api >= Build.VERSION_CODES.O) {
            return BACKUP_FILE_VERSION;
        }
        if (api >= Build.VERSION_CODES.N) {
            return 4;
        }
        if (api >= Build.VERSION_CODES.LOLLIPOP) {
            return 3;
        }
        if (api >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return 1;
        }
        throw new IllegalArgumentException("Invalid/unsupported api " + api);
    }
}
