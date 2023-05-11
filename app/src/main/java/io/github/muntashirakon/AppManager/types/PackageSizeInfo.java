// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types;

import android.annotation.UserIdInt;
import android.app.usage.StorageStats;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;

import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class PackageSizeInfo {
    public final String packageName;
    public final long codeSize;
    public final long dataSize;
    public final long cacheSize;
    public final long mediaSize;
    public final long obbSize;

    @SuppressWarnings("deprecation")
    public PackageSizeInfo(@NonNull android.content.pm.PackageStats packageStats) {
        packageName = packageStats.packageName;
        codeSize = packageStats.codeSize + packageStats.externalCodeSize;
        dataSize = packageStats.dataSize + packageStats.externalDataSize;
        cacheSize = packageStats.cacheSize + packageStats.externalCacheSize;
        obbSize = packageStats.externalObbSize;
        mediaSize = packageStats.externalMediaSize;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @WorkerThread
    public PackageSizeInfo(@NonNull String packageName, @NonNull StorageStats storageStats, @UserIdInt int userHandle) {
        this.packageName = packageName;
        cacheSize = storageStats.getCacheBytes();
        codeSize = storageStats.getAppBytes();
        dataSize = storageStats.getDataBytes() - cacheSize;
        OsEnvironment.UserEnvironment ue = OsEnvironment.getUserEnvironment(userHandle);
        mediaSize = getMediaSizeInternal(ue);
        obbSize = getObbSizeInternal(ue);
    }

    public long getTotalSize() {
        return codeSize + dataSize + cacheSize + mediaSize + obbSize;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private long getMediaSizeInternal(@NonNull OsEnvironment.UserEnvironment ue) {
        Path[] files = ue.buildExternalStorageAppMediaDirs(packageName);
        long size = 0L;
        for (Path file : files) {
            if (file.exists()) size += Paths.size(file);
        }
        return size;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private long getObbSizeInternal(@NonNull OsEnvironment.UserEnvironment ue) {
        Path[] files = ue.buildExternalStorageAppObbDirs(packageName);
        long size = 0L;
        for (Path file : files) {
            if (file.exists()) {
                size += Paths.size(file);
            }
        }
        return size;
    }
}
