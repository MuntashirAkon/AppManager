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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.io.Path;

// Copyright 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
// Modified from FileProvider.kt
public class FmProvider extends ContentProvider {
    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".file";

    @NonNull
    public static Uri getContentUri(@NonNull Path path) {
        return getContentUri(path.getUri());
    }

    @VisibleForTesting
    @NonNull
    static Uri getContentUri(@NonNull Uri uri) {
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .path((Uri.encode(uri.getScheme().equals(ContentResolver.SCHEME_CONTENT) ? "!" + uri.getAuthority() : "")
                        + uri.getPath()))
                .build();
    }

    static final String[] COLUMNS = new String[]{
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            MediaStore.MediaColumns.DATA
    };

    HandlerThread callbackThread;
    Handler callbackHandler;

    @Override
    public boolean onCreate() {
        callbackThread = new HandlerThread("FmProvider.HandlerThread");
        callbackThread.start();
        callbackHandler = new Handler(callbackThread.getLooper());
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        callbackThread.quitSafely();
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

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        // ContentProvider has already checked granted permissions
        String[] projectionColumns = projection == null ? COLUMNS : projection;
        Path path;
        try {
            path = getFileProviderPath(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (String column : projectionColumns) {
            switch (column) {
                case OpenableColumns.DISPLAY_NAME:
                    columns.add(column);
                    values.add(path.getName());
                    break;
                case OpenableColumns.SIZE:
                    columns.add(column);
                    values.add(path.length());
                    break;
                case MediaStore.MediaColumns.DATA:
                    String filePath = path.getFilePath();
                    if (filePath == null || !new File(filePath).canRead()) {
                        continue;
                    }
                    columns.add(column);
                    values.add(filePath);
                    break;
                // TODO: We should actually implement a DocumentsProvider since we are handling
                //  ACTION_OPEN_DOCUMENT.
                case DocumentsContract.Document.COLUMN_MIME_TYPE:
                    columns.add(column);
                    values.add(path.getType());
                    break;
                case DocumentsContract.Document.COLUMN_LAST_MODIFIED:
                    values.add(path.lastModified());
                    break;
            }
        }
        return new MatrixCursor(columns.toArray(new String[0]), 1) {{
            addRow(values);
        }};
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        try {
            return getFileProviderPath(uri).getType();
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("No external inserts");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("No external updates");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("No external deletes");
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        // ContentProvider has already checked granted permissions
        return getFileProviderPath(uri).openFileDescriptor(checkMode(mode), callbackThread);
    }

    @NonNull
    private static Path getFileProviderPath(@NonNull Uri uri) throws FileNotFoundException {
        return new Path(AppManager.getContext(), getFileProviderPathInternal(uri));
    }

    @VisibleForTesting
    @NonNull
    static Uri getFileProviderPathInternal(@NonNull Uri uri) {
        String uriPath = Uri.decode(uri.getPath());
        if (uriPath.startsWith("/!")) {
            // Content provider
            return Uri.parse(uriPath.replaceFirst("/!", "content://"));
        } else {
            // File
            return Uri.parse("file://" + uriPath);
        }
    }

    @NonNull
    private String checkMode(@NonNull String mode) {
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
