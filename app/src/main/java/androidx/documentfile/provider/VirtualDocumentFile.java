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
    private final VirtualFileSystem mFs;
    @NonNull
    private String mFullPath;

    public VirtualDocumentFile(@Nullable DocumentFile parent, @NonNull VirtualFileSystem fs) {
        super(parent);
        mFs = fs;
        mFullPath = File.separator;
    }

    protected VirtualDocumentFile(@NonNull VirtualDocumentFile parent, @NonNull String displayName) {
        super(Objects.requireNonNull(parent));
        if (displayName.contains(File.separator)) {
            throw new IllegalArgumentException("displayName cannot contain a separator");
        }
        mFs = parent.mFs;
        mFullPath = Paths.appendPathSegment(parent.mFullPath, displayName);
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
        String newFilePath = Paths.appendPathSegment(mFullPath, displayName);
        return mFs.createNewFile(newFilePath) ? new VirtualDocumentFile(this, displayName) : null;
    }

    @Nullable
    @Override
    public DocumentFile createDirectory(@NonNull String displayName) {
        if (displayName.contains(File.separator)) {
            // displayName cannot contain a separator
            return null;
        }
        String newFilePath = Paths.appendPathSegment(mFullPath, displayName);
        return mFs.mkdir(newFilePath) ? new VirtualDocumentFile(this, displayName) : null;
    }

    @NonNull
    public String getFullPath() {
        return mFullPath;
    }

    @NonNull
    public VirtualFileSystem getFileSystem() {
        return mFs;
    }

    @NonNull
    @Override
    public String getName() {
        if (mFullPath.equals(File.separator)) {
            return File.separator;
        }
        return Paths.getLastPathSegment(mFullPath);
    }

    @Nullable
    @Override
    public String getType() {
        if (mFs.isFile(mFullPath)) {
            String extension = Paths.getPathExtension(getName());
            if (extension == null) {
                return null;
            }
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        } else if (mFs.isDirectory(mFullPath)) {
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
        return mFs.isFile(mFullPath);
    }

    @Override
    public boolean isDirectory() {
        return mFs.isDirectory(mFullPath);
    }

    @Override
    public boolean exists() {
        return mFs.checkAccess(mFullPath, OsConstants.F_OK);
    }

    @Override
    public boolean canRead() {
        return mFs.checkAccess(mFullPath, OsConstants.R_OK);
    }

    @Override
    public boolean canWrite() {
        return mFs.checkAccess(mFullPath, OsConstants.W_OK);
    }

    public int getMode() {
        return mFs.getMode(mFullPath);
    }

    public boolean setMode(int mode) {
        mFs.setMode(mFullPath, mode);
        return true;
    }

    @Nullable
    public UidGidPair getUidGid() {
        return mFs.getUidGid(mFullPath);
    }

    public boolean setUidGid(@NonNull UidGidPair uidGidPair) {
        mFs.setUidGid(mFullPath, uidGidPair.uid, uidGidPair.gid);
        return true;
    }

    @Override
    public boolean delete() {
        return mFs.delete(mFullPath);
    }

    @NonNull
    @Override
    public Uri getUri() {
        return VirtualFileSystem.getUri(mFs.getFsId(), mFullPath);
    }

    @NonNull
    public FileInputStream openInputStream() throws IOException {
        return mFs.newInputStream(mFullPath);
    }

    @NonNull
    public FileOutputStream openOutputStream(boolean append) throws IOException {
        return mFs.newOutputStream(mFullPath, append);
    }

    public FileChannel openChannel(int mode) throws IOException {
        return mFs.openChannel(mFullPath, mode);
    }

    @NonNull
    public ParcelFileDescriptor openFileDescriptor(int mode) throws IOException {
        return mFs.openFileDescriptor(mFullPath, mode);
    }

    @Override
    public long lastModified() {
        return mFs.lastModified(mFullPath);
    }

    public boolean setLastModified(long millis) {
        return mFs.setLastModified(mFullPath, millis);
    }

    public long lastAccess() {
        return mFs.lastAccess(mFullPath);
    }

    public long creationTime() {
        return mFs.creationTime(mFullPath);
    }

    @Override
    public long length() {
        return mFs.length(mFullPath);
    }

    @Nullable
    @Override
    public VirtualDocumentFile findFile(@NonNull String displayName) {
        displayName =  Paths.sanitize(displayName, true);
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
        String[] children = mFs.list(mFullPath);
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
        String parent = Paths.removeLastPathSegment(mFullPath);
        String newFile = Paths.appendPathSegment(parent, displayName);
        if(mFs.renameTo(mFullPath, newFile)) {
            mFullPath = newFile;
            return true;
        }
        return false;
    }
}
