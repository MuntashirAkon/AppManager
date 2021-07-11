// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.Context;
import android.content.UriPermission;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;

import java.util.List;

public final class SAFUtils {
    private SAFUtils() {
    }

    @NonNull
    public static ArrayMap<Uri, Long> getUrisWithDate(@NonNull Context context) {
        List<UriPermission> permissionList = context.getContentResolver().getPersistedUriPermissions();
        ArrayMap<Uri, Long> uris = new ArrayMap<>(permissionList.size());
        for (UriPermission permission : permissionList) {
            if (permission.isReadPermission() && permission.isWritePermission()) {
                // We only work with rw directories
                uris.put(permission.getUri(), permission.getPersistedTime());
            }
        }
        return uris;
    }
}
