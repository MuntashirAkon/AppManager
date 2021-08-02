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

import io.github.muntashirakon.io.ProxyFile;

public class ProxyDocumentFile extends DocumentFile {
    public static final String TAG = "DF";

    private File mFile;

    public ProxyDocumentFile(@NonNull File file) {
        super(getParentDocumentFile(file));
        mFile = file;
    }

    public ProxyDocumentFile(@Nullable DocumentFile parent, @NonNull File file) {
        super(parent);
        mFile = file;
    }

    @Override
    @Nullable
    public DocumentFile createFile(@NonNull String mimeType, @NonNull String displayName) {
        // Tack on extension when valid MIME type provided
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        if (extension != null && !mimeType.equals("application/octet-stream")) {
            // application/octet-stream is an special mime that needs on extension
            displayName += "." + extension;
        }
        final File target = new ProxyFile(mFile, displayName);
        try {
            target.createNewFile();
            return new ProxyDocumentFile(this, target);
        } catch (IOException e) {
            Log.w(TAG, "Failed to createFile: " + e);
            return null;
        }
    }

    @Override
    @Nullable
    public DocumentFile createDirectory(@NonNull String displayName) {
        final File target = new ProxyFile(mFile, displayName);
        if (target.isDirectory() || target.mkdir()) {
            return new ProxyDocumentFile(this, target);
        } else {
            return null;
        }
    }

    @NonNull
    @Override
    public Uri getUri() {
        return Uri.fromFile(mFile);
    }

    @Override
    public String getName() {
        return mFile.getName();
    }

    @Override
    @NonNull
    public String getType() {
        if (mFile.isDirectory()) {
            return "resource/folder";
        } else {
            return getTypeForName(mFile.getName());
        }
    }

    public File getFile() {
        return mFile;
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
        File file;
        if (mFile instanceof ProxyFile) {
            file = new ProxyFile(mFile, displayName);
        } else file = new File(mFile, displayName);
        return file.exists() ? new ProxyDocumentFile(this, file) : null;
    }

    @NonNull
    @Override
    public DocumentFile[] listFiles() {
        final ArrayList<DocumentFile> results = new ArrayList<>();
        final File[] files = mFile.listFiles();
        if (files != null) {
            for (File file : files) {
                results.add(new ProxyDocumentFile(this, file));
            }
        }
        return results.toArray(new DocumentFile[0]);
    }

    @Override
    public boolean renameTo(@NonNull String displayName) {
        final File target = new ProxyFile(mFile.getParentFile(), displayName);
        if (mFile.renameTo(target)) {
            mFile = target;
            return true;
        } else {
            return false;
        }
    }

    public boolean renameTo(@NonNull ProxyDocumentFile targetFile) {
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
            final String extension = name.substring(lastDot + 1).toLowerCase();
            final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }

        return "application/octet-stream";
    }

    private static boolean deleteContents(File dir) {
        File[] files = dir.listFiles();
        boolean success = true;
        if (files != null) {
            for (File file : files) {
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
    private static DocumentFile getParentDocumentFile(@NonNull File file) {
        File parent = file.getParentFile();
        if (parent != null) {
            return new ProxyDocumentFile(parent);
        }
        return null;
    }
}
