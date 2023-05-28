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

public class PathContentInfo {
    public static final String TAG = PathContentInfo.class.getSimpleName();
    // Associations not present in ContentInfoUtil, they're derived from simple-name
    private static final HashMap<String, String> sSimpleNameMimeAssociations = new HashMap<String, String>() {{
        put("SQLite", "application/vnd.sqlite3");
    }};

    private static final HashMap<String, Boolean> sPartialOverrides = new HashMap<String, Boolean>() {{
        put("application/zip", true);
    }};

    private static ContentInfoUtil contentInfoUtil;

    @NonNull
    public static PathContentInfo fromExtension(@NonNull Path path) {
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
    public static PathContentInfo fromPath(@NonNull Path path) {
        if (path.isDirectory()) {
            return DIRECTORY;
        }
        if (contentInfoUtil == null) {
            contentInfoUtil = new ContentInfoUtil();
        }
        String ext = path.getExtension();
        ContentInfo extInfo = ext != null ? ContentInfoUtil.findExtensionMatch(ext) : null;
        ContentType2 extType2 = ext != null ? ContentType2.fromFileExtension(ext) : null;
        try (InputStream is = path.openInputStream()) {
            ContentInfo contentInfo = contentInfoUtil.findMatch(is);
            if (contentInfo != null) {
                // FIXME: 20/11/22 This will not work for invalid extensions. A better option is to use magic-mime-db
                //  instead which is currently a WIP.
                if (extInfo != null) {
                    return withPartialOverride(fromPathContentInfo(
                            new PathContentInfo(extInfo.getName(), contentInfo.getMessage(), extInfo.getMimeType(),
                                    extInfo.getFileExtensions(), contentInfo.isPartial())), extType2);
                }
                if (extType2 != null) {
                    return fromPathContentInfo(new PathContentInfo(extType2.getSimpleName(), contentInfo.getMessage(),
                            extType2.getMimeType(), extType2.getFileExtensions(), contentInfo.isPartial()));
                }
                return fromContentInfo(contentInfo);
            }
        } catch (Throwable e) {
            Log.e(TAG, "Could not load MIME type for path " + path, e);
        }
        if (extInfo != null) {
            return withPartialOverride(fromContentInfo(extInfo), extType2);
        }
        if (extType2 != null) {
            return fromContentType2(extType2);
        }
        return fromContentType2(ContentType2.OTHER);
    }

    private static PathContentInfo withPartialOverride(@NonNull PathContentInfo contentInfo, @Nullable ContentType2 contentType2) {
        if (contentType2 != null) {
            boolean partial = contentInfo.isPartial() || Boolean.TRUE.equals(sPartialOverrides.get(contentInfo.getMimeType()));
            if (partial) {
                // Override MIME type, name and extension
                return new PathContentInfo(contentType2.getSimpleName(), contentInfo.getMessage(),
                        contentType2.getMimeType(), contentType2.getFileExtensions(), false);
            }
        }
        return contentInfo;
    }

    @NonNull
    private static PathContentInfo fromContentInfo(@NonNull ContentInfo contentInfo) {
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
            return new PathContentInfo(contentInfo.getName(), contentInfo.getMessage(), mime,
                    extensions.isEmpty() ? null : extensions.toArray(new String[0]), contentInfo.isPartial());
        }
        return new PathContentInfo(contentInfo.getName(), contentInfo.getMessage(), contentInfo.getMimeType(),
                contentInfo.getFileExtensions(), contentInfo.isPartial());
    }

    @NonNull
    private static PathContentInfo fromPathContentInfo(@NonNull PathContentInfo contentInfo) {
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
            return new PathContentInfo(contentInfo.getName(), contentInfo.getMessage(), mime,
                    extensions.isEmpty() ? null : extensions.toArray(new String[0]), contentInfo.isPartial());
        }
        return contentInfo;
    }

    @NonNull
    private static PathContentInfo fromContentType2(@NonNull ContentType2 contentType2) {
        return new PathContentInfo(contentType2.getSimpleName(), null, contentType2.getMimeType(),
                contentType2.getFileExtensions(), false);
    }

    public static final PathContentInfo DIRECTORY = new PathContentInfo("Directory", null,
            "resource/folder", null, false);

    @NonNull
    private final String mName;
    @Nullable
    private final String mMessage;
    @Nullable
    private final String mMimeType;
    @Nullable
    private final String[] mFileExtensions;
    private final boolean mPartial;

    public PathContentInfo(@NonNull String name, @Nullable String message, @Nullable String mimeType,
                           @Nullable String[] fileExtensions, boolean partial) {
        mName = name;
        mMessage = message;
        mMimeType = mimeType;
        mFileExtensions = fileExtensions;
        mPartial = partial;
    }

    /**
     * Returns the short name of the content either from the content-type or extracted from the message. If the
     * content-type is known then this is a specific name string. Otherwise, this is usually the first word of the
     * message generated by the magic file.
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Returns the mime-type or null if none.
     */
    @Nullable
    public String getMimeType() {
        return mMimeType;
    }

    /**
     * Returns the full message as generated by the magic matching code or null if none. This should be similar to the
     * output from the Unix file(1) command.
     */
    @Nullable
    public String getMessage() {
        return mMessage;
    }

    /**
     * Returns an array of associated file-extensions or null if none.
     */
    @Nullable
    public String[] getFileExtensions() {
        return mFileExtensions;
    }

    /**
     * Whether this was a partial match. For some types, there is a main matching pattern and then more
     * specific patterns which detect additional features of the type. A partial match means that none of the more
     * specific patterns fully matched the content. It's probably still of the type but just not a variant that the
     * entries from the magic file(s) know about.
     */
    public boolean isPartial() {
        return mPartial;
    }
}
