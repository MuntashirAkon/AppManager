// SPDX-License-Identifier: GPL-3.0-or-later

package androidx.documentfile.provider;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.io.Paths;

public final class DocumentFileUtils {
    @NonNull
    public static DocumentFile newTreeDocumentFile(@Nullable DocumentFile parent, @NonNull Context context, @NonNull Uri uri) {
        return new TreeDocumentFile(parent, context, uri);
    }

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

    @Nullable
    public static ResolveInfo getUriSource(@NonNull Context context, @NonNull Uri uri) {
        String authority = uri.getAuthority();
        if (authority == null) {
            return null;
        }
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(DocumentsContract.PROVIDER_INTERFACE);
        List<ResolveInfo> infos = pm.queryIntentContentProviders(intent, 0);
        for (ResolveInfo info : infos) {
            if (Objects.equals(authority, info.providerInfo.authority)) {
                return info;
            }
        }
        return null;
    }
}
