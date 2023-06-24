// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.annotation.UserIdInt;
import android.os.UserHandleHidden;
import android.os.storage.StorageManagerHidden;
import android.os.storage.StorageVolume;
import android.os.storage.StorageVolumeHidden;
import android.text.TextUtils;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.rikka.tools.refine.Refine;
import io.github.muntashirakon.AppManager.compat.StorageManagerCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

// Keep this in sync with https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/os/Environment.java
// Last snapshot https://cs.android.com/android/_/android/platform/frameworks/base/+/bc3d8b9071d4f0b2903d6836770d974e70366290

@SuppressWarnings("unused")
public final class OsEnvironment {
    private static final String TAG = "OsEnvironment";

    private static final String ENV_EXTERNAL_STORAGE = "EXTERNAL_STORAGE";
    private static final String ENV_ANDROID_ROOT = "ANDROID_ROOT";
    private static final String ENV_ANDROID_DATA = "ANDROID_DATA";
    private static final String ENV_ANDROID_EXPAND = "ANDROID_EXPAND";
    private static final String ENV_ANDROID_STORAGE = "ANDROID_STORAGE";
    private static final String ENV_DOWNLOAD_CACHE = "DOWNLOAD_CACHE";
    private static final String ENV_OEM_ROOT = "OEM_ROOT";
    private static final String ENV_ODM_ROOT = "ODM_ROOT";
    private static final String ENV_VENDOR_ROOT = "VENDOR_ROOT";
    private static final String ENV_PRODUCT_ROOT = "PRODUCT_ROOT";
    private static final String ENV_SYSTEM_EXT_ROOT = "SYSTEM_EXT_ROOT";
    private static final String ENV_APEX_ROOT = "APEX_ROOT";

    public static final String DIR_ANDROID = "Android";
    private static final String DIR_DATA = "data";
    private static final String DIR_MEDIA = "media";
    private static final String DIR_OBB = "obb";
    private static final String DIR_FILES = "files";
    private static final String DIR_CACHE = "cache";

    private static final String DIR_ANDROID_ROOT = getDirectory(ENV_ANDROID_ROOT, "/system");
    private static final String DIR_ANDROID_DATA = getDirectory(ENV_ANDROID_DATA, "/data");
    private static final String DIR_ANDROID_EXPAND = getDirectory(ENV_ANDROID_EXPAND, "/mnt/expand");
    private static final String DIR_ANDROID_STORAGE = getDirectory(ENV_ANDROID_STORAGE, "/storage");
    private static final String DIR_DOWNLOAD_CACHE = getDirectory(ENV_DOWNLOAD_CACHE, "/cache");
    private static final String DIR_OEM_ROOT = getDirectory(ENV_OEM_ROOT, "/oem");
    private static final String DIR_ODM_ROOT = getDirectory(ENV_ODM_ROOT, "/odm");
    private static final String DIR_VENDOR_ROOT = getDirectory(ENV_VENDOR_ROOT, "/vendor");
    private static final String DIR_PRODUCT_ROOT = getDirectory(ENV_PRODUCT_ROOT, "/product");
    private static final String DIR_SYSTEM_EXT_ROOT = getDirectory(ENV_SYSTEM_EXT_ROOT, "/system_ext");
    private static final String DIR_APEX_ROOT = getDirectory(ENV_APEX_ROOT, "/apex");

    private static final UserEnvironment sCurrentUser;
    private static boolean sUserRequired;

    private static final SparseArray<UserEnvironment> sUserEnvironmentCache = new SparseArray<>(2);

    static {
        sCurrentUser = new UserEnvironment(UserHandleHidden.myUserId());
        sUserEnvironmentCache.put(sCurrentUser.mUserHandle, sCurrentUser);
    }

    @NonNull
    public static UserEnvironment getUserEnvironment(@UserIdInt int userHandle) {
        UserEnvironment ue = sUserEnvironmentCache.get(userHandle);
        if (ue != null) return ue;
        ue = new UserEnvironment(userHandle);
        sUserEnvironmentCache.put(userHandle, ue);
        return ue;
    }

    public static class UserEnvironment {
        @UserIdInt
        private final int mUserHandle;

        public UserEnvironment(@UserIdInt int userHandle) {
            mUserHandle = userHandle;
        }

        @NonNull
        public Path[] getExternalDirs() {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                final StorageVolume[] volumes = StorageManagerCompat.getVolumeList(ContextUtils.getContext(),
                        mUserHandle, StorageManagerHidden.FLAG_FOR_WRITE);
                final List<Path> files = new ArrayList<>();
                File tmpFile;
                for (@NonNull StorageVolume volume : volumes) {
                    StorageVolumeHidden vol = Refine.unsafeCast(volume);
                    tmpFile = vol.getPathFile();
                    if (tmpFile != null) {
                        files.add(Paths.get(tmpFile.getAbsolutePath()));
                    }
                }
                return files.toArray(new Path[0]);
            }
            String rawExternalStorage = System.getenv(ENV_EXTERNAL_STORAGE);
            String rawEmulatedTarget = System.getenv("EMULATED_STORAGE_TARGET");
            List<Path> externalForApp = new ArrayList<>();
            if (!TextUtils.isEmpty(rawEmulatedTarget)) {
                // Device has emulated storage; external storage paths should have
                // userId burned into them.
                final String rawUserId = Integer.toString(mUserHandle);
                //noinspection ConstantConditions
                final File emulatedTargetBase = new File(rawEmulatedTarget);

                // /storage/emulated/0
                externalForApp.add(Paths.build(emulatedTargetBase, rawUserId));
            } else {
                // Device has physical external storage; use plain paths.
                if (TextUtils.isEmpty(rawExternalStorage)) {
                    Log.w(TAG, "EXTERNAL_STORAGE undefined; falling back to default");
                    rawExternalStorage = "/storage/sdcard0";
                }
                // /storage/sdcard0
                externalForApp.add(Paths.get(rawExternalStorage));
            }
            return externalForApp.toArray(new Path[0]);
        }

        @Deprecated
        public Path getExternalStorageDirectory() {
            return getExternalDirs()[0];
        }

        @Deprecated
        public Path getExternalStoragePublicDirectory(String type) {
            return buildExternalStoragePublicDirs(type)[0];
        }

        public Path[] buildExternalStoragePublicDirs(String type) {
            return Paths.build(getExternalDirs(), type);
        }

        public Path[] buildExternalStorageAndroidDataDirs() {
            return Paths.build(getExternalDirs(), DIR_ANDROID, DIR_DATA);
        }

        public Path[] buildExternalStorageAndroidObbDirs() {
            return Paths.build(getExternalDirs(), DIR_ANDROID, DIR_OBB);
        }

        public Path[] buildExternalStorageAppDataDirs(String packageName) {
            return Paths.build(getExternalDirs(), DIR_ANDROID, DIR_DATA, packageName);
        }

        public Path[] buildExternalStorageAppMediaDirs(String packageName) {
            return Paths.build(getExternalDirs(), DIR_ANDROID, DIR_MEDIA, packageName);
        }

        public Path[] buildExternalStorageAppObbDirs(String packageName) {
            return Paths.build(getExternalDirs(), DIR_ANDROID, DIR_OBB, packageName);
        }

        public Path[] buildExternalStorageAppFilesDirs(String packageName) {
            return Paths.build(getExternalDirs(), DIR_ANDROID, DIR_DATA, packageName, DIR_FILES);
        }

        public Path[] buildExternalStorageAppCacheDirs(String packageName) {
            return Paths.build(getExternalDirs(), DIR_ANDROID, DIR_DATA, packageName, DIR_CACHE);
        }
    }

    /**
     * Return root of the "system" partition holding the core Android OS.
     * Always present and mounted read-only.
     */
    @NonNull
    public static Path getRootDirectory() {
        return Paths.get(DIR_ANDROID_ROOT);
    }

    /**
     * Return root directory of the "oem" partition holding OEM customizations,
     * if any. If present, the partition is mounted read-only.
     */
    @NonNull
    public static Path getOemDirectory() {
        return Paths.get(DIR_OEM_ROOT);
    }

    /**
     * Return root directory of the "odm" partition holding ODM customizations,
     * if any. If present, the partition is mounted read-only.
     */
    @NonNull
    public static Path getOdmDirectory() {
        return Paths.get(DIR_ODM_ROOT);
    }

    /**
     * Return root directory of the "vendor" partition that holds vendor-provided
     * software that should persist across simple reflashing of the "system" partition.
     */
    @NonNull
    public static Path getVendorDirectory() {
        return Paths.get(DIR_VENDOR_ROOT);
    }

    @NonNull
    public static String getVendorDirectoryRaw() {
        return DIR_VENDOR_ROOT;
    }

    /**
     * Return root directory of the "product" partition holding product-specific
     * customizations if any. If present, the partition is mounted read-only.
     */
    @NonNull
    public static Path getProductDirectory() {
        return Paths.get(DIR_PRODUCT_ROOT);
    }

    @NonNull
    public static String getProductDirectoryRaw() {
        return DIR_PRODUCT_ROOT;
    }

    /**
     * Return root directory of the "system_ext" partition holding system partition's extension
     * If present, the partition is mounted read-only.
     */
    @NonNull
    public static Path getSystemExtDirectory() {
        return Paths.get(DIR_SYSTEM_EXT_ROOT);
    }

    /**
     * Return the user data directory.
     */
    @NonNull
    public static Path getDataDirectory() {
        return Paths.get(DIR_ANDROID_DATA);
    }

    @NonNull
    public static String getDataDirectoryRaw() {
        return DIR_ANDROID_DATA;
    }

    @NonNull
    public static Path getDataSystemDirectory() {
        return Objects.requireNonNull(Paths.build(getDataDirectory(), "system"));
    }

    @NonNull
    public static Path getDataAppDirectory() {
        return Objects.requireNonNull(Paths.build(getDataDirectory(), "app"));
    }

    @NonNull
    public static Path getDataDataDirectory() {
        return Objects.requireNonNull(Paths.build(getDataDirectory(), "data"));
    }

    @NonNull
    public static Path getUserSystemDirectory(int userId) {
        return Objects.requireNonNull(Paths.build(getDataSystemDirectory(), "users", Integer.toString(userId)));
    }

    /**
     * Returns the path for android-specific data on the SD card.
     */
    public static Path[] buildExternalStorageAndroidDataDirs() {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStorageAndroidDataDirs();
    }

    /**
     * Generates the raw path to an application's data
     */
    public static Path[] buildExternalStorageAppDataDirs(String packageName) {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStorageAppDataDirs(packageName);
    }

    /**
     * Generates the raw path to an application's media
     */
    public static Path[] buildExternalStorageAppMediaDirs(String packageName) {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStorageAppMediaDirs(packageName);
    }

    /**
     * Generates the raw path to an application's OBB files
     */
    public static Path[] buildExternalStorageAppObbDirs(String packageName) {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStorageAppObbDirs(packageName);
    }

    /**
     * Generates the path to an application's files.
     */
    public static Path[] buildExternalStorageAppFilesDirs(String packageName) {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStorageAppFilesDirs(packageName);
    }

    /**
     * Generates the path to an application's cache.
     */
    public static Path[] buildExternalStorageAppCacheDirs(String packageName) {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStorageAppCacheDirs(packageName);
    }

    public static Path[] buildExternalStoragePublicDirs(@NonNull String dirType) {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStoragePublicDirs(dirType);
    }

    public static Path[] buildExternalStoragePublicDirs() {
        throwIfUserRequired();
        return sCurrentUser.getExternalDirs();
    }

    @NonNull
    static String getDirectory(String variableName, String defaultPath) {
        String path = System.getenv(variableName);
        return path == null ? defaultPath : path;
    }

    public static void setUserRequired(boolean userRequired) {
        sUserRequired = userRequired;
    }

    private static void throwIfUserRequired() {
        if (sUserRequired) {
            Log.e(TAG, "Path requests must specify a user by using UserEnvironment", new Throwable());
        }
    }
}
