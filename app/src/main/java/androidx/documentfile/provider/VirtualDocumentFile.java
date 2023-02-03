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

import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.UidGidPair;
import io.github.muntashirakon.io.fs.VirtualFileSystem;

// Mother of all virtual documents
public class VirtualDocumentFile extends DocumentFile {
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
    private String fullPath;

    public VirtualDocumentFile(@Nullable DocumentFile parent, @NonNull VirtualFileSystem fs) {
        super(parent);
        this.fs = fs;
        this.fullPath = File.separator;
    }

    protected VirtualDocumentFile(@NonNull VirtualDocumentFile parent, @NonNull String displayName) {
        super(Objects.requireNonNull(parent));
        if (displayName.contains(File.separator)) {
            throw new IllegalArgumentException("displayName cannot contain a separator");
        }
        this.fs = parent.fs;
        this.fullPath = Paths.appendPathSegment(parent.fullPath, displayName);
    }

    @Nullable
    @Override
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
        String newFilePath = Paths.appendPathSegment(fullPath, displayName);
        return fs.createNewFile(newFilePath) ? new VirtualDocumentFile(this, displayName) : null;
    }

    @Nullable
    @Override
    public DocumentFile createDirectory(@NonNull String displayName) {
        if (displayName.contains(File.separator)) {
            // displayName cannot contain a separator
            return null;
        }
        String newFilePath = Paths.appendPathSegment(fullPath, displayName);
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
        return Paths.getLastPathSegment(fullPath);
    }

    @Nullable
    @Override
    public String getType() {
        if (fs.isFile(fullPath)) {
            String extension = Paths.getPathExtension(getName());
            if (extension == null) {
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

    public int getMode() {
        return fs.getMode(fullPath);
    }

    public boolean setMode(int mode) {
        fs.setMode(fullPath, mode);
        return true;
    }

    @Nullable
    public UidGidPair getUidGid() {
        return fs.getUidGid(fullPath);
    }

    public boolean setUidGid(@NonNull UidGidPair uidGidPair) {
        fs.setUidGid(fullPath, uidGidPair.uid, uidGidPair.gid);
        return true;
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

    public boolean setLastModified(long millis) {
        return fs.setLastModified(fullPath, millis);
    }

    public long lastAccess() {
        return fs.lastAccess(fullPath);
    }

    public long creationTime() {
        return fs.creationTime(fullPath);
    }

    @Override
    public long length() {
        return fs.length(fullPath);
    }

    @Nullable
    @Override
    public VirtualDocumentFile findFile(@NonNull String displayName) {
        displayName =  Paths.getSanitizedPath(displayName, true);
        if (displayName == null || displayName.contains(File.separator)) {
            return null;
        }
        VirtualDocumentFile documentFile = new VirtualDocumentFile(this, displayName);
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
        if (displayName.contains(File.separator)) {
            // displayName cannot contain a separator
            return false;
        }
        String parent = Paths.removeLastPathSegment(fullPath);
        String newFile = Paths.appendPathSegment(parent, displayName);
        if(fs.renameTo(fullPath, newFile)) {
            fullPath = newFile;
            return true;
        }
        return false;
    }
}
