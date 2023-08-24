// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.fs.VirtualFileSystem;

// Copyright 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
// Modified from FileProvider.kt
public class FmProvider extends ContentProvider {
    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".file";

    @NonNull
    public static Uri getContentUri(@NonNull Path path) {
        return getContentUri(path.getUri());
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    @NonNull
    static Uri getContentUri(@NonNull Uri uri) {
        Uri.Builder builder = uri.buildUpon()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .path(null);
        // Uri could be a file, content or vfs
        // 1. file:// Only use path
        // 2. content:// Use ! + authority followed by path
        // 3. vfs:// Use !! + authority (vfs ID) followed by path
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            builder.appendPath("!" + uri.getAuthority());
        } else if (VirtualFileSystem.SCHEME.equals(uri.getScheme())) {
            builder.appendPath("!!" + uri.getAuthority());
        }
        for (String segment : uri.getPathSegments()) {
            builder.appendPath(segment);
        }
        // The rests (query params, etc.) remains the same
        return builder.build();
    }

    private static final String[] DEFAULT_PROJECTION = new String[]{
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            MediaStore.MediaColumns.DATA,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    };

    private static final String[] CHOOSER_ACTIVITY_DEFAULT_PROJECTION = new String[]{
            OpenableColumns.DISPLAY_NAME
    };

    private HandlerThread mCallbackThread;
    private Handler mCallbackHandler;

    @Override
    public boolean onCreate() {
        mCallbackThread = new HandlerThread("FmProvider.HandlerThread");
        mCallbackThread.start();
        mCallbackHandler = new Handler(mCallbackThread.getLooper());
        return true;
    }

    @Override
    public void shutdown() {
        mCallbackThread.quitSafely();
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        if (info.exported) {
            throw new SecurityException("Provider must not be exported");
        }
        if (!info.grantUriPermissions) {
            throw new SecurityException("Provider must grant uri permissions");
        }
    }

    @NonNull
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        // ContentProvider has already checked granted permissions
        String[] defaultProjection = getDefaultProjection();
        List<String> columns;
        if (projection != null) {
            columns = new ArrayList<>();
            for (String column : projection) {
                if (ArrayUtils.contains(defaultProjection, column)) {
                    columns.add(column);
                }
            }
        } else columns = Arrays.asList(defaultProjection);
        Path path = ExUtils.exceptionAsNull(() -> getFileProviderPath(uri));
        if (path == null) {
            return new MatrixCursor(columns.toArray(new String[0]), 0);
        }
        List<Object> row = new ArrayList<>();
        for (String column : columns) {
            switch (column) {
                case OpenableColumns.DISPLAY_NAME:
                    row.add(path.getName());
                    break;
                case OpenableColumns.SIZE:
                    row.add(path.isFile() ? path.length() : null);
                    break;
                case MediaStore.MediaColumns.DATA:
                    String filePath = path.getFilePath();
                    if (filePath == null
                            || !new File(filePath).canRead()
                            || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        row.add(null);
                        continue;
                    }
                    row.add(filePath);
                    break;
                // TODO: We should actually implement a DocumentsProvider since we are handling
                //  ACTION_OPEN_DOCUMENT.
                case DocumentsContract.Document.COLUMN_MIME_TYPE:
                    row.add(path.isDirectory() ? DocumentsContract.Document.MIME_TYPE_DIR : path.getType());
                    break;
                case DocumentsContract.Document.COLUMN_LAST_MODIFIED:
                    row.add(path.lastModified());
                    break;
            }
        }
        return new MatrixCursor(columns.toArray(new String[0]), 1) {{
            addRow(row);
        }};
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return ExUtils.exceptionAsNull(() -> getFileProviderPath(uri).getType());
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("No external inserts");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("No external deletes");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("No external updates");
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        // ContentProvider has already checked granted permissions
        return getFileProviderPath(uri).openFileDescriptor(checkMode(mode), mCallbackThread);
    }

    private static String[] getDefaultProjection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && Binder.getCallingUid() == Process.SYSTEM_UID) {
            // com.android.internal.app.ChooserActivity.queryResolver() in Q queries with a null
            // projection (meaning all columns) on main thread but only actually needs the display
            // name (and document flags). However, if we do return all the columns, we may perform
            // network requests and crash it due to StrictMode. So just work around by only
            // returning the display name in this case.
            return CHOOSER_ACTIVITY_DEFAULT_PROJECTION;
        } else {
            return DEFAULT_PROJECTION;
        }
    }

    @NonNull
    private static Path getFileProviderPath(@NonNull Uri uri) throws FileNotFoundException {
        return Paths.getStrict(getFileProviderPathInternal(uri));
    }

    /**
     * Decode path.
     *
     * @see #getContentUri(Uri)
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    @NonNull
    static Uri getFileProviderPathInternal(@NonNull Uri uri) {
        List<String> pathParts = uri.getPathSegments();
        int pathStartIndex = 0;
        String scheme = ContentResolver.SCHEME_FILE;
        String authority = "";
        if (pathParts.size() > 0) {
            String firstPart = pathParts.get(0);
            if (firstPart.startsWith("!!")) {
                // Virtual File System
                pathStartIndex = 1;
                scheme = VirtualFileSystem.SCHEME;
                authority = firstPart.substring(2);
            } else if (firstPart.startsWith("!")) {
                // Content provider
                pathStartIndex = 1;
                scheme = ContentResolver.SCHEME_CONTENT;
                authority = firstPart.substring(1);
            }
        }
        Uri.Builder builder = uri.buildUpon()
                .scheme(scheme)
                .authority(authority)
                .path(null);
        for (int i = pathStartIndex; i < pathParts.size(); ++i) {
            builder.appendPath(pathParts.get(i));
        }
        return builder.build();
    }

    @NonNull
    private static String checkMode(@NonNull String mode) {
        // Add `t` flag if neither truncate nor append is supplied
        if (mode.indexOf('w') != -1 && mode.indexOf('a') == -1) {
            // w exists but a doesn't
            if (mode.indexOf('t') == -1) {
                return mode + 't';
            }
        }
        return mode;
    }
}
