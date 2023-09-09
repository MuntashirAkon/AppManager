// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.provider.DocumentsContractCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.documentfile.provider.DocumentFileUtils;
import androidx.documentfile.provider.ExtendedRawDocumentFile;
import androidx.documentfile.provider.VirtualDocumentFile;

import java.io.IOException;

public class PathAttributes {
    @NonNull
    public static PathAttributes fromFile(@NonNull ExtendedRawDocumentFile file) {
        ExtendedFile f = file.getFile();
        return new PathAttributes(f.getName(), file.getType(), f.lastModified(), f.lastAccess(), f.creationTime(),
                f.isFile(), f.isDirectory(), f.isSymlink(), f.length());
    }

    @NonNull
    public static PathAttributes fromVirtual(@NonNull VirtualDocumentFile file) {
        return new PathAttributes(file.getName(), file.getType(), file.lastModified(), file.lastAccess(), file.creationTime(),
                file.isFile(), file.isDirectory(), false, file.length());
    }

    @NonNull
    public static PathAttributes fromSaf(@NonNull Context context, @NonNull DocumentFile safDocumentFile)
            throws IOException {
        Uri documentUri = safDocumentFile.getUri();
        ContentResolver resolver = context.getContentResolver();
        try (Cursor c = resolver.query(documentUri, null, null, null, null)) {
            if (!c.moveToFirst()) {
                throw new IOException("Could not fetch attributes for tree " + documentUri);
            }
            String[] columns = c.getColumnNames();
            String name = null;
            String type = null;
            long lastModified = 0;
            long size = 0;
            for (int i = 0; i < columns.length; ++i) {
                switch (columns[i]) {
                    case DocumentsContract.Document.COLUMN_DISPLAY_NAME:
                        name = c.getString(i);
                        break;
                    case DocumentsContract.Document.COLUMN_MIME_TYPE:
                        type = c.getString(i);
                        break;
                    case DocumentsContract.Document.COLUMN_LAST_MODIFIED:
                        lastModified = c.getLong(i);
                        break;
                    case DocumentsContract.Document.COLUMN_SIZE:
                        size = c.getLong(i);
                        break;
                }
            }
            boolean isDirectory = DocumentsContract.Document.MIME_TYPE_DIR.equals(type);
            if (name == null) {
                name = DocumentFileUtils.resolveAltNameForSaf(safDocumentFile);
            }
            return new PathAttributes(name, type, lastModified, 0, 0, !isDirectory, isDirectory,
                    false, size);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @NonNull
    public static PathAttributes fromSafTreeCursor(@NonNull Uri treeUri, @NonNull Cursor c) {
        if (!DocumentsContractCompat.isTreeUri(treeUri)) {
            throw new IllegalArgumentException("Not a tree document.");
        }
        String[] columns = c.getColumnNames();
        String name = null;
        String type = null;
        long lastModified = 0;
        long size = 0;
        for (int i = 0; i < columns.length; ++i) {
            switch (columns[i]) {
                case DocumentsContract.Document.COLUMN_DISPLAY_NAME:
                    name = c.getString(i);
                    break;
                case DocumentsContract.Document.COLUMN_MIME_TYPE:
                    type = c.getString(i);
                    break;
                case DocumentsContract.Document.COLUMN_LAST_MODIFIED:
                    lastModified = c.getLong(i);
                    break;
                case DocumentsContract.Document.COLUMN_SIZE:
                    size = c.getLong(i);
                    break;
            }
        }
        boolean isDirectory = DocumentsContract.Document.MIME_TYPE_DIR.equals(type);
        if (name == null) {
            name = DocumentFileUtils.resolveAltNameForTreeUri(treeUri);
        }
        return new PathAttributes(name, type, lastModified, 0, 0, !isDirectory, isDirectory,
                false, size);
    }

    @NonNull
    public final String name;
    @Nullable
    public final String mimeType;
    public final long lastModified;
    public final long lastAccess;
    public final long creationTime;
    public final boolean isRegularFile;
    public final boolean isDirectory;
    public final boolean isSymbolicLink;
    public final boolean isOtherFile;
    public final long size;

    private PathAttributes(@NonNull String displayName, @Nullable String mimeType, long lastModified, long lastAccess,
                           long creationTime, boolean isRegularFile, boolean isDirectory, boolean isSymbolicLink,
                           long size) {
        this.name = displayName;
        this.mimeType = mimeType;
        this.lastModified = lastModified;
        this.lastAccess = lastAccess;
        this.creationTime = creationTime;
        this.isRegularFile = isRegularFile;
        this.isDirectory = isDirectory;
        this.isSymbolicLink = isSymbolicLink;
        this.isOtherFile = !isRegularFile && !isDirectory && !isSymbolicLink;
        this.size = size;
    }
}
