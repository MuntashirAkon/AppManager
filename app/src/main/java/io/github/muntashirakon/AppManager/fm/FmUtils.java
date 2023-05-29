// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.content.ContentResolver;
import android.net.Uri;

import androidx.annotation.NonNull;

import io.github.muntashirakon.io.Path;

final class FmUtils {
    @NonNull
    public static String getDisplayablePath(@NonNull Path path) {
        return getDisplayablePath(path.getUri());
    }

    @NonNull
    public static String getDisplayablePath(@NonNull Uri uri) {
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            return uri.getPath();
        }
        return uri.toString();
    }
}
