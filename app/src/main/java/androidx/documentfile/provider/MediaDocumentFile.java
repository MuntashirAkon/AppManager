// SPDX-License-Identifier: GPL-3.0-or-later

package androidx.documentfile.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

public class MediaDocumentFile extends SingleDocumentFile {
    private final Context mContext;
    private final Uri mUri;

    public MediaDocumentFile(@Nullable DocumentFile parent, Context context, Uri uri) {
        super(parent, context, uri);
        mContext = context;
        mUri = uri;
    }

    @Override
    public boolean isVirtual() {
        return false;
    }

    @Override
    public boolean canWrite() {
        boolean writable = super.canWrite();
        if (writable) {
            return true;
        }
        // Ignore if grant doesn't allow write
        if (mContext.checkCallingOrSelfUriPermission(mUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        // Ignore documents without MIME
        if (TextUtils.isEmpty(getRawType(mContext, mUri))) {
            return false;
        }

        // For media documents, check if the underlying file is writable
        String path = getRealPath(mContext, mUri);
        return path == null || new File(path).canWrite();
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Nullable
    private static String getRealPath(@NonNull Context context, @NonNull Uri self) {
        return queryForString(context, self, MediaStore.MediaColumns.DATA, null);
    }

    @Nullable
    private static String getRawType(@NonNull Context context, @NonNull Uri self) {
        return queryForString(context, self, DocumentsContract.Document.COLUMN_MIME_TYPE, null);
    }

    @Nullable
    private static String queryForString(@NonNull Context context, @NonNull Uri self, @NonNull String column,
                                         @Nullable String defaultValue) {
        final ContentResolver resolver = context.getContentResolver();

        try (Cursor c = resolver.query(self, new String[]{column}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) {
                return c.getString(0);
            } else {
                return defaultValue;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed query: " + e);
            return defaultValue;
        }
    }
}
