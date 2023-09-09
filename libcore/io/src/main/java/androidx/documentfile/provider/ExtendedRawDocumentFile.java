// SPDX-License-Identifier: GPL-3.0-or-later

package androidx.documentfile.provider;

import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

import io.github.muntashirakon.io.ExtendedFile;

/**
 * Same as {@link RawDocumentFile} with additional support for {@link ExtendedFile}.
 */
public class ExtendedRawDocumentFile extends DocumentFile {
    public static final String TAG = "DF";

    private ExtendedFile mFile;

    public ExtendedRawDocumentFile(@NonNull ExtendedFile file) {
        super(getParentDocumentFile(file));
        mFile = file;
    }

    public ExtendedRawDocumentFile(@Nullable DocumentFile parent, @NonNull ExtendedFile file) {
        super(parent);
        mFile = file;
    }

    @Override
    @Nullable
    public DocumentFile createFile(@NonNull String mimeType, @NonNull String displayName) {
        if (displayName.contains(File.separator)) {
            // displayName cannot contain a separator
            return null;
        }
        // Tack on extension when valid MIME type provided
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        if (extension != null) {
            displayName += "." + extension;
        }
        final ExtendedFile target = mFile.getChildFile(displayName);
        try {
            target.createNewFile();
            return new ExtendedRawDocumentFile(this, target);
        } catch (IOException e) {
            Log.w(TAG, "Failed to create " + target, e);
            return null;
        }
    }

    @Override
    @Nullable
    public DocumentFile createDirectory(@NonNull String displayName) {
        if (displayName.contains(File.separator)) {
            // displayName cannot contain a separator
            return null;
        }
        final ExtendedFile target = mFile.getChildFile(displayName);
        if (target.isDirectory() || target.mkdir()) {
            return new ExtendedRawDocumentFile(this, target);
        } else {
            return null;
        }
    }

    @Override
    @NonNull
    public Uri getUri() {
        return Uri.fromFile(mFile);
    }

    public ExtendedFile getFile() {
        return mFile;
    }

    @Override
    @NonNull
    public String getName() {
        return mFile.getName();
    }

    @Override
    @Nullable
    public String getType() {
        if (mFile.isDirectory()) {
            return "resource/folder";
        } else if (mFile.isFile()) {
            String name = mFile.getName();
            final int lastDot = name.lastIndexOf('.');
            if (lastDot >= 0) {
                final String extension = name.substring(lastDot + 1).toLowerCase(Locale.ROOT);
                return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            }
        }
        return null;
    }

    @Override
    public boolean isDirectory() {
        return mFile.isDirectory();
    }

    @Override
    public boolean isFile() {
        return mFile.isFile();
    }

    @Override
    public boolean isVirtual() {
        return false;
    }

    @Override
    public long lastModified() {
        return mFile.lastModified();
    }

    @Override
    public long length() {
        return mFile.length();
    }

    @Override
    public boolean canRead() {
        return mFile.canRead();
    }

    @Override
    public boolean canWrite() {
        return mFile.canWrite();
    }

    @Override
    public boolean delete() {
        deleteContents(mFile);
        return mFile.delete();
    }

    @Override
    public boolean exists() {
        return mFile.exists();
    }

    @Nullable
    @Override
    public DocumentFile findFile(@NonNull String displayName) {
        if (displayName.contains(File.separator)) {
            // displayName cannot contain a separator
            return null;
        }
        ExtendedFile file = mFile.getChildFile(displayName);
        return file.exists() ? new ExtendedRawDocumentFile(this, file) : null;
    }

    @NonNull
    @Override
    public DocumentFile[] listFiles() {
        final ArrayList<DocumentFile> results = new ArrayList<>();
        final ExtendedFile[] files = mFile.listFiles();
        if (files != null) {
            for (ExtendedFile file : files) {
                results.add(new ExtendedRawDocumentFile(this, file));
            }
        }
        return results.toArray(new DocumentFile[0]);
    }

    @Override
    public boolean renameTo(@NonNull String displayName) {
        if (displayName.contains(File.separator)) {
            // displayName cannot contain a separator
            return false;
        }
        ExtendedFile parent = mFile.getParentFile();
        if (parent == null) return false;
        ExtendedFile target = mFile.getParentFile().getChildFile(displayName);
        if (mFile.renameTo(target)) {
            mFile = target;
            return true;
        } else {
            return false;
        }
    }

    public boolean renameTo(@NonNull ExtendedRawDocumentFile targetFile) {
        if (mFile.renameTo(targetFile.mFile)) {
            mFile = targetFile.mFile;
            return true;
        } else {
            return false;
        }
    }

    private static String getTypeForName(String name) {
        final int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0) {
            final String extension = name.substring(lastDot + 1).toLowerCase(Locale.ROOT);
            final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }
        return "application/octet-stream";
    }

    private static boolean deleteContents(ExtendedFile dir) {
        if (dir.isSymlink()) {
            // Do not follow symbolic links
            return true;
        }
        ExtendedFile[] files = dir.listFiles();
        boolean success = true;
        if (files != null) {
            for (ExtendedFile file : files) {
                if (file.isDirectory()) {
                    success &= deleteContents(file);
                }
                if (!file.delete()) {
                    Log.w(TAG, "Failed to delete " + file);
                    success = false;
                }
            }
        }
        return success;
    }

    @Nullable
    private static DocumentFile getParentDocumentFile(@NonNull ExtendedFile file) {
        ExtendedFile parent = file.getParentFile();
        if (parent != null) {
            return new ExtendedRawDocumentFile(parent);
        }
        return null;
    }
}
