// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.annotation.SuppressLint;
import android.annotation.UserIdInt;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class BackupDataDirectoryInfo {
    public static final String TAG = BackupDataDirectoryInfo.class.getSimpleName();

    @SuppressLint("SdCardPath")
    @NonNull
    public static BackupDataDirectoryInfo getInfo(@NonNull String dataDir, @UserIdInt int userId) {
        String storageCe = String.format(Locale.ROOT, "/data/user/%d/", userId);
        if (dataDir.startsWith("/data/data/") || dataDir.startsWith(storageCe)) {
            return new BackupDataDirectoryInfo(dataDir, true, TYPE_INTERNAL, TYPE_CREDENTIAL_PROTECTED);
        }
        String storageDe = String.format(Locale.ROOT, "/data/user_de/%d/", userId);
        if (dataDir.startsWith(storageDe)) {
            return new BackupDataDirectoryInfo(dataDir, true, TYPE_INTERNAL, TYPE_DEVICE_PROTECTED);
        }
        if (dataDir.startsWith("/sdcard/")) {
            return getExternalInfo(dataDir, "/sdcard/");
        }
        if (dataDir.startsWith("/storage/sdcard/")) {
            return getExternalInfo(dataDir, "/storage/sdcard/");
        }
        if (dataDir.startsWith("/storage/sdcard0/")) {
            return getExternalInfo(dataDir, "/storage/sdcard0/");
        }
        String storageEmulatedDir = String.format(Locale.ROOT, "/storage/emulated/%d/", userId);
        if (dataDir.startsWith(storageEmulatedDir)) {
            return getExternalInfo(dataDir, storageEmulatedDir);
        }
        String dataMediaDir = String.format(Locale.ROOT, "/data/media/%d/", userId);
        if (dataDir.startsWith(dataMediaDir)) {
            return getExternalInfo(dataDir, dataMediaDir);
        }
        Log.i(TAG, "getInfo: Unrecognized path %s, returning true as fallback.", dataDir);
        return new BackupDataDirectoryInfo(dataDir, true, TYPE_UNKNOWN, TYPE_CUSTOM);
    }

    @NonNull
    private static BackupDataDirectoryInfo getExternalInfo(@NonNull String dataDir, @NonNull String baseDir) {
        String relativeDir = dataDir.substring(baseDir.length()); // No starting separator
        int subType = TYPE_CUSTOM;
        if (relativeDir.startsWith("Android/data/")) {
            subType = TYPE_ANDROID_DATA;
        }
        if (relativeDir.startsWith("Android/obb/")) {
            subType = TYPE_ANDROID_OBB;
        }
        if (relativeDir.startsWith("Android/media/")) {
            subType = TYPE_ANDROID_MEDIA;
        }
        return new BackupDataDirectoryInfo(dataDir, Paths.get(baseDir).isDirectory(), TYPE_EXTERNAL, subType);
    }

    @IntDef(value = {
            TYPE_INTERNAL,
            TYPE_EXTERNAL,
            TYPE_UNKNOWN,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {
    }

    @IntDef(value = {
            TYPE_CUSTOM,
            TYPE_ANDROID_DATA,
            TYPE_ANDROID_MEDIA,
            TYPE_ANDROID_OBB,
            TYPE_CREDENTIAL_PROTECTED,
            TYPE_DEVICE_PROTECTED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SubType {
    }

    public static final int TYPE_CUSTOM = 0;
    public static final int TYPE_ANDROID_DATA = 1;
    public static final int TYPE_ANDROID_MEDIA = 2;
    public static final int TYPE_ANDROID_OBB = 3;
    public static final int TYPE_CREDENTIAL_PROTECTED = 4;
    public static final int TYPE_DEVICE_PROTECTED = 5;

    public static final int TYPE_INTERNAL = 1;
    public static final int TYPE_EXTERNAL = 2;
    public static final int TYPE_UNKNOWN = 3;

    public final String rawRath;
    public final Path path;
    public final boolean isMounted;
    @Type
    public final int type;
    @SubType
    public final int subtype;

    private BackupDataDirectoryInfo(String path, boolean isMounted, @Type int type, @SubType int subtype) {
        this.rawRath = path;
        this.path = Paths.get(path);
        this.isMounted = isMounted;
        this.type = type;
        this.subtype = subtype;
    }

    public boolean isExternal() {
        return type == TYPE_EXTERNAL;
    }
}
