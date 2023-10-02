// SPDX-License-Identifier: GPL-3.0-or-later

package androidx.documentfile.provider;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.List;

import io.github.muntashirakon.io.Paths;

public class DocumentFileUtils {
    public static boolean isSingleDocumentFile(@Nullable DocumentFile documentFile) {
        return documentFile instanceof SingleDocumentFile;
    }

    public static boolean isTreeDocumentFile(@Nullable DocumentFile documentFile) {
        return documentFile instanceof TreeDocumentFile;
    }

    @NonNull
    public static String resolveAltNameForSaf(@NonNull DocumentFile documentFile) {
        // For Document Uris, an invalid Uri can return no display name
        if (DocumentFileUtils.isSingleDocumentFile(documentFile)) {
            // It's impossible to figure out the correct display name, but since this path is incorrect,
            // return the full last path segment
            return documentFile.getUri().getLastPathSegment();
        }
        if (DocumentFileUtils.isTreeDocumentFile(documentFile)) {
            // The last path segment of the last path segment is the real name
            return resolveAltNameForTreeUri(documentFile.getUri());
        }
        throw new IllegalArgumentException("Invalid DocumentFile, expected a SAF document.");
    }

    public static String resolveAltNameForTreeUri(@NonNull Uri treeUri) {
        // The last path segment of the last path segment is the real name
        List<String> segments = treeUri.getPathSegments();
        String primaryName = segments.get(1);
        if (segments.size() == 2) {
            return primaryName;
        }
        String secondaryName = segments.get(3);
        if (secondaryName.startsWith(primaryName + File.separator)) {
            secondaryName = Paths.getLastPathSegment(secondaryName.substring(primaryName.length() + 1));
        }
        if (!secondaryName.isEmpty()) {
            return secondaryName;
        }
        throw new IllegalArgumentException("Invalid Uri, expected a tree Uri.");
    }
}
