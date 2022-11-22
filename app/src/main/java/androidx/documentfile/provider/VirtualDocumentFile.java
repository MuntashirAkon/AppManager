// SPDX-License-Identifier: GPL-3.0-or-later

package androidx.documentfile.provider;

import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Objects;

import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.fs.VirtualFileSystem;

// Mother of all virtual documents
public class VirtualDocumentFile extends DocumentFile {
    public static final String SCHEME = "vfs";

    @Nullable
    public static Pair<Integer, String> parseUri(@NonNull Uri uri) {
        try {
            return new Pair<>(Integer.decode(uri.getAuthority()), uri.getPath());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @NonNull
    private final VirtualFileSystem fs;
    @NonNull
    private final String fullPath;

    public VirtualDocumentFile(@Nullable DocumentFile parent, @NonNull VirtualFileSystem fs) {
        super(parent);
        this.fs = fs;
        this.fullPath = File.separator;
    }

    protected VirtualDocumentFile(@NonNull VirtualDocumentFile parent, @NonNull String relativePath) {
        super(Objects.requireNonNull(parent));
        this.fs = parent.fs;
        this.fullPath = FileUtils.addSegmentAtEnd(parent.fullPath, relativePath);
    }

    @Nullable
    @Override
    public DocumentFile createFile(@NonNull String mimeType, @NonNull String displayName) {
        // Tack on extension when valid MIME type provided
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        if (extension != null) {
            displayName += "." + extension;
        }
        String newFilePath = FileUtils.addSegmentAtEnd(fullPath, displayName);
        return fs.createNewFile(newFilePath) ? new VirtualDocumentFile(this, displayName) : null;
    }

    @Nullable
    @Override
    public DocumentFile createDirectory(@NonNull String displayName) {
        String newFilePath = FileUtils.addSegmentAtEnd(fullPath, displayName);
        return fs.mkdir(newFilePath) ? new VirtualDocumentFile(this, displayName) : null;
    }

    @NonNull
    public String getFullPath() {
        return fullPath;
    }

    @NonNull
    public VirtualFileSystem getFileSystem() {
        return fs;
    }

    @NonNull
    @Override
    public String getName() {
        if (fullPath.equals(File.separator)) {
            return File.separator;
        }
        return FileUtils.getLastPathComponent(fullPath);
    }

    @Nullable
    @Override
    public String getType() {
        if (fs.isFile(fullPath)) {
            String extension = FileUtils.getExtension(getName());
            if (extension.equals("")) {
                return null;
            }
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        } else if (fs.isDirectory(fullPath)) {
            return "resource/folder";
        }
        return null;
    }

    @Override
    public boolean isVirtual() {
        return true;
    }

    @Override
    public boolean isFile() {
        return fs.isFile(fullPath);
    }

    @Override
    public boolean isDirectory() {
        return fs.isDirectory(fullPath);
    }

    @Override
    public boolean exists() {
        return fs.checkAccess(fullPath, OsConstants.F_OK);
    }

    @Override
    public boolean canRead() {
        return fs.checkAccess(fullPath, OsConstants.R_OK);
    }

    @Override
    public boolean canWrite() {
        return fs.checkAccess(fullPath, OsConstants.W_OK);
    }

    @Override
    public boolean delete() {
        return fs.delete(fullPath);
    }

    @NonNull
    @Override
    public Uri getUri() {
        return VirtualFileSystem.getUri(fs.getFsId(), fullPath);
    }

    @NonNull
    public FileInputStream openInputStream() throws IOException {
        return fs.newInputStream(fullPath);
    }

    @NonNull
    public FileOutputStream openOutputStream(boolean append) throws IOException {
        return fs.newOutputStream(fullPath, append);
    }

    public FileChannel openChannel(int mode) throws IOException {
        return fs.openChannel(fullPath, mode);
    }

    public ParcelFileDescriptor openFileDescriptor(int mode) throws IOException {
        return fs.openFileDescriptor(fullPath, mode);
    }

    @Override
    public long lastModified() {
        return fs.lastModified(fullPath);
    }

    @Override
    public long length() {
        return fs.length(fullPath);
    }

    @Nullable
    @Override
    public VirtualDocumentFile findFile(@NonNull String displayName) {
        VirtualDocumentFile documentFile = new VirtualDocumentFile(this, FileUtils.getSanitizedPath(displayName));
        if (documentFile.exists()) {
            return documentFile;
        }
        return null;
    }

    @NonNull
    @Override
    public VirtualDocumentFile[] listFiles() {
        String[] children = fs.list(fullPath);
        if (children == null) return new VirtualDocumentFile[0];
        VirtualDocumentFile[] documentFiles = new VirtualDocumentFile[children.length];
        for (int i = 0; i < children.length; ++i) {
            documentFiles[i] = new VirtualDocumentFile(this, children[i]);
        }
        return documentFiles;
    }

    @Override
    public boolean renameTo(@NonNull String displayName) {
        String parent = FileUtils.removeLastPathSegment(fullPath);
        String newFile = FileUtils.addSegmentAtEnd(parent, displayName);
        return fs.renameTo(fullPath, newFile);
    }
}
