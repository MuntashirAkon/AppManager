// SPDX-License-Identifier: GPL-3.0-or-later

package androidx.documentfile.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.io.Paths;

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
        if (Binder.getCallingPid() == Process.myPid() || mContext.checkCallingUriPermission(mUri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            // Writing is allowed
            return true;
        }
        // TODO: 15/9/23 Handle actual path in case no write permission is granted
//        // For media documents, also check if the underlying file is writable as a fallback
//        String path = getRealPath(mContext, mUri);
//        return path == null || Paths.get(path).canWrite();
        return false;
    }

    @Override
    public boolean exists() {
        return true;
    }

//    @Nullable
//    private static String getRealPath(@NonNull Context context, @NonNull Uri self) {
//        final ContentResolver resolver = context.getContentResolver();
//        try (Cursor c = resolver.query(self, new String[]{MediaStore.MediaColumns.DATA}, null, null, null)) {
//            if (c != null && c.moveToFirst() && !c.isNull(0)) {
//                return c.getString(0);
//            } else {
//                return null;
//            }
//        } catch (Exception e) {
//            Log.w(TAG, "Failed query: " + e);
//            return null;
//        }
//    }
}
