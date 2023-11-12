// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoUtil;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import io.github.muntashirakon.AppManager.fm.ContentType2;
import io.github.muntashirakon.AppManager.logs.Log;

class PathContentInfoImpl extends PathContentInfo {
    public static final String TAG = PathContentInfoImpl.class.getSimpleName();
    // Associations not present in ContentInfoUtil, they're derived from simple-name
    private static final HashMap<String, String> sSimpleNameMimeAssociations = new HashMap<String, String>() {{
        put("SQLite", "application/vnd.sqlite3");
    }};

    private static final HashMap<String, Boolean> sPartialOverrides = new HashMap<String, Boolean>() {{
        put("application/zip", true);
    }};

    private static ContentInfoUtil sContentInfoUtil;

    @NonNull
    public static PathContentInfoImpl fromExtension(@NonNull Path path) {
        if (path.isDirectory()) {
            return DIRECTORY;
        }
        String ext = path.getExtension();
        ContentInfo extInfo = ext != null ? ContentInfoUtil.findExtensionMatch(ext) : null;
        ContentType2 extType2 = ext != null ? ContentType2.fromFileExtension(ext) : null;
        if (extInfo != null) {
            return withPartialOverride(fromContentInfo(extInfo), extType2);
        }
        if (extType2 != null) {
            return fromContentType2(extType2);
        }
        return fromContentType2(ContentType2.OTHER);
    }

    @NonNull
    public static PathContentInfoImpl fromPath(@NonNull Path path) {
        if (path.isDirectory()) {
            return DIRECTORY;
        }
        if (sContentInfoUtil == null) {
            sContentInfoUtil = new ContentInfoUtil();
        }
        String ext = path.getExtension();
        ContentInfo extInfo = ext != null ? ContentInfoUtil.findExtensionMatch(ext) : null;
        ContentType2 extType2 = ext != null ? ContentType2.fromFileExtension(ext) : null;
        try (InputStream is = path.openInputStream()) {
            ContentInfo contentInfo = sContentInfoUtil.findMatch(is);
            if (contentInfo != null) {
                // FIXME: 20/11/22 This will not work for invalid extensions. A better option is to use magic-mime-db
                //  instead which is currently a WIP.
                if (extInfo != null) {
                    return withPartialOverride(fromPathContentInfo(
                            new PathContentInfoImpl(extInfo.getName(), contentInfo.getMessage(), extInfo.getMimeType(),
                                    extInfo.getFileExtensions(), contentInfo.isPartial())), extType2);
                }
                if (extType2 != null) {
                    return fromPathContentInfo(new PathContentInfoImpl(extType2.getSimpleName(), contentInfo.getMessage(),
                            extType2.getMimeType(), extType2.getFileExtensions(), contentInfo.isPartial()));
                }
                return fromContentInfo(contentInfo);
            }
        } catch (Throwable e) {
            Log.e(TAG, "Could not load MIME type for path %s", e, path);
        }
        if (extInfo != null) {
            return withPartialOverride(fromContentInfo(extInfo), extType2);
        }
        if (extType2 != null) {
            return fromContentType2(extType2);
        }
        return fromContentType2(ContentType2.OTHER);
    }

    private static PathContentInfoImpl withPartialOverride(@NonNull PathContentInfoImpl contentInfo, @Nullable ContentType2 contentType2) {
        if (contentType2 != null) {
            boolean partial = contentInfo.isPartial() || Boolean.TRUE.equals(sPartialOverrides.get(contentInfo.getMimeType()));
            if (partial) {
                // Override MIME type, name and extension
                return new PathContentInfoImpl(contentType2.getSimpleName(), contentInfo.getMessage(),
                        contentType2.getMimeType(), contentType2.getFileExtensions(), false);
            }
        }
        return contentInfo;
    }

    @NonNull
    private static PathContentInfoImpl fromContentInfo(@NonNull ContentInfo contentInfo) {
        String mime = sSimpleNameMimeAssociations.get(contentInfo.getName());
        if (mime != null) {
            ContentType2 contentType2 = ContentType2.fromMimeType(mime);
            // Association exists, replace MIME type and merge file extensions
            HashSet<String> extensions = new HashSet<>();
            if (contentInfo.getFileExtensions() != null) {
                extensions.addAll(Arrays.asList(contentInfo.getFileExtensions()));
            }
            if (contentType2.getFileExtensions() != null) {
                extensions.addAll(Arrays.asList(contentType2.getFileExtensions()));
            }
            return new PathContentInfoImpl(contentInfo.getName(), contentInfo.getMessage(), mime,
                    extensions.isEmpty() ? null : extensions.toArray(new String[0]), contentInfo.isPartial());
        }
        return new PathContentInfoImpl(contentInfo.getName(), contentInfo.getMessage(), contentInfo.getMimeType(),
                contentInfo.getFileExtensions(), contentInfo.isPartial());
    }

    @NonNull
    private static PathContentInfoImpl fromPathContentInfo(@NonNull PathContentInfoImpl contentInfo) {
        String mime = sSimpleNameMimeAssociations.get(contentInfo.getName());
        if (mime != null) {
            ContentType2 contentType2 = ContentType2.fromMimeType(mime);
            // Association exists, replace MIME type and merge file extensions
            HashSet<String> extensions = new HashSet<>();
            if (contentInfo.getFileExtensions() != null) {
                extensions.addAll(Arrays.asList(contentInfo.getFileExtensions()));
            }
            if (contentType2.getFileExtensions() != null) {
                extensions.addAll(Arrays.asList(contentType2.getFileExtensions()));
            }
            return new PathContentInfoImpl(contentInfo.getName(), contentInfo.getMessage(), mime,
                    extensions.isEmpty() ? null : extensions.toArray(new String[0]), contentInfo.isPartial());
        }
        return contentInfo;
    }

    @NonNull
    private static PathContentInfoImpl fromContentType2(@NonNull ContentType2 contentType2) {
        return new PathContentInfoImpl(contentType2.getSimpleName(), null, contentType2.getMimeType(),
                contentType2.getFileExtensions(), false);
    }

    private static final PathContentInfoImpl DIRECTORY = new PathContentInfoImpl("Directory", null,
            "resource/folder", null, false);


    private PathContentInfoImpl(@NonNull String name, @Nullable String message, @Nullable String mimeType,
                                @Nullable String[] fileExtensions, boolean partial) {
        super(name, message, mimeType, fileExtensions, partial);
    }
}
