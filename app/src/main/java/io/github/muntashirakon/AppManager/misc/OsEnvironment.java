package io.github.muntashirakon.AppManager.misc;

import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;

// Keep this in sync with https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/os/Environment.java
// Last snapshot https://cs.android.com/android/_/android/platform/frameworks/base/+/bc3d8b9071d4f0b2903d6836770d974e70366290

@SuppressWarnings("unused")
public final class OsEnvironment {
    private static final String TAG = "OsEnvironment";

    private static final String ENV_EXTERNAL_STORAGE = "EXTERNAL_STORAGE";
    private static final String ENV_ANDROID_DATA = "ANDROID_DATA";

    private static final String DIR_ANDROID = "Android";
    private static final String DIR_DATA = "data";
    private static final String DIR_MEDIA = "media";
    private static final String DIR_OBB = "obb";
    private static final String DIR_FILES = "files";
    private static final String DIR_CACHE = "cache";

    private static final File DIR_ANDROID_DATA = getDirectory(ENV_ANDROID_DATA, "/data");

    private static UserEnvironment sCurrentUser;
    private static boolean sUserRequired;

    static {
        initForCurrentUser();
    }

    public static void initForCurrentUser() {
        int userId = 0;
        try {
            // FIXME: Get user id using root since this is only intended for root users
            @SuppressWarnings("JavaReflectionMemberAccess")
            Method myUserId = UserHandle.class.getMethod("myUserId");
            userId = (int) myUserId.invoke(null);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        sCurrentUser = new UserEnvironment(userId);
    }

    public static class UserEnvironment {
        private final int mUserId;

        public UserEnvironment(int userId) {
            mUserId = userId;
        }

        public File[] getExternalDirs() {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                // The reflection calls cannot be hidden since they are called internally by the
                // system. The alternative is to parse the Context.getExternalCacheDirs(). Some
                // results could be null even though StorageVolume is not null.
                try {
                    @SuppressWarnings("JavaReflectionMemberAccess")
                    Method getVolumeList = StorageManager.class.getMethod("getVolumeList", int.class, int.class);
                    final @NonNull StorageVolume[] volumes = (StorageVolume[]) Objects.requireNonNull(getVolumeList.invoke(null, mUserId, 1 << 8 /* FLAG_FOR_WRITE */));
                    Log.e(TAG, Arrays.toString(volumes));
                    final File[] files = new File[volumes.length];
                    for (int i = 0; i < volumes.length; i++) {
                        @SuppressWarnings("JavaReflectionMemberAccess")
                        Method getPathFile = StorageVolume.class.getMethod("getPathFile");
                        files[i] = (File) getPathFile.invoke(volumes[i]);
                    }
                    return files;
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
            String rawExternalStorage = System.getenv(ENV_EXTERNAL_STORAGE);
            String rawEmulatedTarget = System.getenv("EMULATED_STORAGE_TARGET");
            List<File> externalForApp = new ArrayList<>();
            if (!TextUtils.isEmpty(rawEmulatedTarget)) {
                // Device has emulated storage; external storage paths should have
                // userId burned into them.
                final String rawUserId = Integer.toString(mUserId);
                //noinspection ConstantConditions
                final File emulatedTargetBase = new File(rawEmulatedTarget);

                // /storage/emulated/0
                externalForApp.add(buildPath(emulatedTargetBase, rawUserId));
            } else {
                // Device has physical external storage; use plain paths.
                if (TextUtils.isEmpty(rawExternalStorage)) {
                    Log.w(TAG, "EXTERNAL_STORAGE undefined; falling back to default");
                    rawExternalStorage = "/storage/sdcard0";
                }
                // /storage/sdcard0
                //noinspection ConstantConditions
                externalForApp.add(new File(rawExternalStorage));
            }
            return externalForApp.toArray(new File[0]);
        }

        @Deprecated
        public File getExternalStorageDirectory() {
            return getExternalDirs()[0];
        }

        @Deprecated
        public File getExternalStoragePublicDirectory(String type) {
            return buildExternalStoragePublicDirs(type)[0];
        }

        public File[] buildExternalStoragePublicDirs(String type) {
            return buildPaths(getExternalDirs(), type);
        }

        public File[] buildExternalStorageAndroidDataDirs() {
            return buildPaths(getExternalDirs(), DIR_ANDROID, DIR_DATA);
        }

        public File[] buildExternalStorageAndroidObbDirs() {
            return buildPaths(getExternalDirs(), DIR_ANDROID, DIR_OBB);
        }

        public File[] buildExternalStorageAppDataDirs(String packageName) {
            return buildPaths(getExternalDirs(), DIR_ANDROID, DIR_DATA, packageName);
        }

        public File[] buildExternalStorageAppMediaDirs(String packageName) {
            return buildPaths(getExternalDirs(), DIR_ANDROID, DIR_MEDIA, packageName);
        }

        public File[] buildExternalStorageAppObbDirs(String packageName) {
            return buildPaths(getExternalDirs(), DIR_ANDROID, DIR_OBB, packageName);
        }

        public File[] buildExternalStorageAppFilesDirs(String packageName) {
            return buildPaths(getExternalDirs(), DIR_ANDROID, DIR_DATA, packageName, DIR_FILES);
        }

        public File[] buildExternalStorageAppCacheDirs(String packageName) {
            return buildPaths(getExternalDirs(), DIR_ANDROID, DIR_DATA, packageName, DIR_CACHE);
        }
    }

    @NonNull
    public static File getDataAppDirectory() {
        return new File(DIR_ANDROID_DATA, "app");
    }

    @NonNull
    public static File getDataDataDirectory() {
        return new File(DIR_ANDROID_DATA, "data");
    }

    /**
     * Returns the path for android-specific data on the SD card.
     */
    public static File[] buildExternalStorageAndroidDataDirs() {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStorageAndroidDataDirs();
    }

    /**
     * Generates the raw path to an application's data
     */
    public static File[] buildExternalStorageAppDataDirs(String packageName) {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStorageAppDataDirs(packageName);
    }

    /**
     * Generates the raw path to an application's media
     */
    public static File[] buildExternalStorageAppMediaDirs(String packageName) {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStorageAppMediaDirs(packageName);
    }

    /**
     * Generates the raw path to an application's OBB files
     */
    public static File[] buildExternalStorageAppObbDirs(String packageName) {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStorageAppObbDirs(packageName);
    }

    /**
     * Generates the path to an application's files.
     */
    public static File[] buildExternalStorageAppFilesDirs(String packageName) {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStorageAppFilesDirs(packageName);
    }

    /**
     * Generates the path to an application's cache.
     */
    public static File[] buildExternalStorageAppCacheDirs(String packageName) {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStorageAppCacheDirs(packageName);
    }

    public static File[] buildExternalStoragePublicDirs(@NonNull String dirType) {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStoragePublicDirs(dirType);
    }

    @NonNull
    static File getDirectory(String variableName, String defaultPath) {
        String path = System.getenv(variableName);
        return path == null ? new File(defaultPath) : new File(path);
    }

    public static void setUserRequired(boolean userRequired) {
        sUserRequired = userRequired;
    }

    private static void throwIfUserRequired() {
        if (sUserRequired) {
            Log.wtf(TAG, "Path requests must specify a user by using UserEnvironment",
                    new Throwable());
        }
    }

    /**
     * Append path segments to each given base path, returning result.
     */
    @NonNull
    public static File[] buildPaths(@NonNull File[] base, String... segments) {
        File[] result = new File[base.length];
        for (int i = 0; i < base.length; i++) {
            result[i] = buildPath(base[i], segments);
        }
        return result;
    }

    /**
     * Append path segments to given base path, returning result.
     */
    public static File buildPath(File base, @NonNull String... segments) {
        File cur = base;
        for (String segment : segments) {
            if (cur == null) {
                cur = new File(segment);
            } else {
                cur = new File(cur, segment);
            }
        }
        return cur;
    }
}
