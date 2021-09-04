// SPDX-License-Identifier: GPL-3.0-or-later

package android.os.storage;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.UUID;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(StorageManager.class)
public class StorageManagerHidden {
    @RequiresApi(Build.VERSION_CODES.M)
    public static final int FLAG_FOR_WRITE = 1;  // 1 << 8 in later versions

    /**
     * Returns list of all mountable volumes.
     */
    @Nullable // @NonNull since Android 6 (M)
    public StorageVolume[] getVolumeList() {
        throw new UnsupportedOperationException();
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @NonNull
    public static StorageVolume[] getVolumeList(int userId, int flags) {
        throw new UnsupportedOperationException();
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public static UUID convert(String uuid) {
        throw new UnsupportedOperationException();
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public static String convert(UUID storageUuid) {
        throw new UnsupportedOperationException();
    }
}
