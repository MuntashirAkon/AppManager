// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.uri;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getStyledKeyValue;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;

public final class GrantUriUtils {
    @NonNull
    public static CharSequence toLocalisedString(@NonNull Context context, @NonNull Uri uri) {
        if (!ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            throw new UnsupportedOperationException("Invalid URI: " + uri);
        }
        String authority = uri.getAuthority();
        List<String> paths = uri.getPathSegments();
        boolean isTree = paths.size() >= 2 && "tree".equals(paths.get(0));
        boolean isDocument = paths.size() >= 2 && "document".equals(paths.get(0));
        String basePath = isTree ? paths.get(1) : null;
        String file;
        if (isTree) {
            file = paths.size() == 4 ? paths.get(3) : null;
        } else if (isDocument) {
            file = paths.get(1);
        } else {
            // Other types of file
            file = uri.getPath();
        }
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (basePath != null) {
            String realPath = getRealPath(authority, basePath);
            sb.append(getStyledKeyValue(context, R.string.folder, realPath != null ? realPath : basePath));
        }
        if (file != null) {
            if (basePath != null) sb.append("\n");
            String realFile = getRealPath(authority, file);
            sb.append(getStyledKeyValue(context, R.string.file, realFile != null ? realFile : file));
        }
        sb.append("\n")
                .append(getSmallerText(getStyledKeyValue(context, R.string.authority, authority)))
                .append("\n")
                .append(getSmallerText(getStyledKeyValue(context, R.string.type, isTree ? "Tree" : "Document")));
        return sb;
    }

    @Nullable
    private static String getRealPath(@NonNull String authority, @NonNull String dirtyFile) {
        switch (authority) {
            case "com.android.externalstorage.documents": {
                int splitIndex = dirtyFile.indexOf(":", 1);
                String rootId = dirtyFile.substring(0, splitIndex);
                String path = dirtyFile.substring(splitIndex + 1);
                if ("primary".equals(rootId) || "home".equals(rootId)) {
                    return new File(Environment.getExternalStorageDirectory(), path).getAbsolutePath();
                }
                return String.format(Locale.ROOT, "/storage/%s/%s", rootId, path);
            }
            case "com.android.providers.downloads.documents": {
                int splitIndex = dirtyFile.indexOf(":", 1);
                String rootId = dirtyFile.substring(0, splitIndex);
                String path = dirtyFile.substring(splitIndex + 1);
                if ("raw".equals(rootId)) {
                    // Raw/absolute path
                    return path;
                }
                // Other cases are: msf (MediaStore file), msd (MediaStore directory) or simply an integer.
                break;
            }
            case "com.termux.documents":
                // Same as the dirty file
                return dirtyFile;
            case "me.zhanghai.android.files.file_provider":
                Uri uri = Uri.parse(dirtyFile);
                if (uri == null || !ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
                    // Unsupported file
                    return dirtyFile;
                } else {
                    return uri.getPath();
                }
        }
        // Others aren't supported
        return null;
    }
}
