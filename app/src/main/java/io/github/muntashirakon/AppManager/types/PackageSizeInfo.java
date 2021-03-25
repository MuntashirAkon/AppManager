/*
 * Copyright (c) 2021 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
