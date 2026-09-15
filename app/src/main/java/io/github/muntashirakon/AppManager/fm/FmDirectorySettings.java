// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.FmDirectorySize;
import io.github.muntashirakon.AppManager.db.entity.FmDirectorySort;
import io.github.muntashirakon.io.Path;


@WorkerThread
public final class FmDirectorySettings {
    private static final long ACCESS_UPDATE_INTERVAL = 86_400_000L;

    private FmDirectorySettings() {
    }

    @Nullable
    public static FmDirectorySort getSort(@NonNull Path directory) {
        FmDirectorySort settings = AppsDb.getInstance().fmDirectorySortDao()
                .get(FmDirectoryKey.fromUri(directory.getUri()));
        if (settings != null && System.currentTimeMillis() - settings.lastUsedAt >= ACCESS_UPDATE_INTERVAL) {
            settings.lastUsedAt = System.currentTimeMillis();
            AppsDb.getInstance().fmDirectorySortDao().upsert(settings);
        }
        return settings;
    }

    public static void saveSort(@NonNull Path directory, int sortOrder, boolean reverseSort) {
        String key = FmDirectoryKey.fromUri(directory.getUri());
        FmDirectorySort settings = new FmDirectorySort();
        settings.directoryKey = key;
        settings.sortOrder = sortOrder;
        settings.reverseSort = reverseSort;
        settings.lastUsedAt = System.currentTimeMillis();
        AppsDb.getInstance().fmDirectorySortDao().upsert(settings);
    }

    public static void deleteSort(@NonNull Path directory) {
        AppsDb.getInstance().fmDirectorySortDao().delete(FmDirectoryKey.fromUri(directory.getUri()));
    }

    @Nullable
    public static FmDirectorySize getSize(@NonNull Path directory) {
        String key = FmDirectoryKey.fromUri(directory.getUri());
        FmDirectorySize size = AppsDb.getInstance().fmDirectorySizeDao().get(key);
        if (size != null && System.currentTimeMillis() - size.lastUsedAt >= ACCESS_UPDATE_INTERVAL) {
            size.lastUsedAt = System.currentTimeMillis();
            AppsDb.getInstance().fmDirectorySizeDao().upsert(size);
        }
        return size;
    }

    public static void saveSize(@NonNull Path directory, long sizeBytes, long calculatedAt) {
        String key = FmDirectoryKey.fromUri(directory.getUri());
        FmDirectorySize size = new FmDirectorySize();
        size.directoryKey = key;
        size.sizeBytes = sizeBytes;
        size.calculatedAt = calculatedAt;
        size.lastUsedAt = System.currentTimeMillis();
        AppsDb.getInstance().fmDirectorySizeDao().upsert(size);
    }

    public static void deleteSize(@NonNull Path directory) {
        AppsDb.getInstance().fmDirectorySizeDao().delete(FmDirectoryKey.fromUri(directory.getUri()));
    }

    @NonNull
    public static String getDirectoryKey(@NonNull Path directory) {
        return FmDirectoryKey.fromUri(directory.getUri());
    }
}
