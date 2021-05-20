// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types;

import android.app.usage.StorageStats;
import android.content.pm.PackageStats;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

public class PackageSizeInfo {
    public final String packageName;
    public final long codeSize;
    public final long dataSize;
    public final long cacheSize;
    public final long mediaSize;
    public final long obbSize;

    @SuppressWarnings("deprecation")
    public PackageSizeInfo(@NonNull PackageStats packageStats) {
        packageName = packageStats.packageName;
        codeSize = packageStats.codeSize + packageStats.externalCodeSize;
        dataSize = packageStats.dataSize + packageStats.externalDataSize;
        cacheSize = packageStats.cacheSize + packageStats.externalCacheSize;
        obbSize = packageStats.externalObbSize;
        mediaSize = packageStats.externalMediaSize;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public PackageSizeInfo(@NonNull String packageName, @NonNull StorageStats storageStats) {
        this.packageName = packageName;
        cacheSize = storageStats.getCacheBytes();
        codeSize = storageStats.getAppBytes();
        dataSize = storageStats.getDataBytes() - cacheSize;
        // TODO(24/1/21): List obb and media size
        mediaSize = 0L;
        obbSize = 0L;
    }

    public long getTotalSize() {
        return codeSize + dataSize + cacheSize + mediaSize + obbSize;
    }
}
