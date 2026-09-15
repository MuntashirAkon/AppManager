// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.content.ContentResolver;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

import io.github.muntashirakon.io.fs.VirtualFileSystem;

final class FmDirectoryKey {
    private FmDirectoryKey() {
    }

    @NonNull
    static String fromUri(@NonNull Uri uri) {
        String scheme = uri.getScheme();
        if (ContentResolver.SCHEME_FILE.equals(scheme) && uri.getPath() != null) {
            try {
                return "file:" + new File(uri.getPath()).getCanonicalPath();
            } catch (IOException ignored) {
                // Fall back to the normalized URI when canonicalization is unavailable.
            }
        } else if (VirtualFileSystem.SCHEME.equals(scheme)) {
            VirtualFileSystem fs = null;
            // Runtime VFS URIs carry the transient mount ID in their authority. Resolve
            // through that ID.
            try {
                if (uri.getAuthority() != null) {
                    fs = VirtualFileSystem.getFileSystem(Integer.parseInt(uri.getAuthority()));
                }
            } catch (NumberFormatException ignored) {
                // Fall back to the original mount-point lookup below.
            }
            if (fs == null) {
                fs = VirtualFileSystem.getFileSystem(uri);
            }
            if (fs != null && fs.getMountPoint() != null) {
                String path = uri.getPath() != null ? uri.getPath() : "";
                return "vfs:" + fs.getMountPoint().normalizeScheme() + ":" + path;
            }
        }
        return uri.normalizeScheme().toString();
    }
}
