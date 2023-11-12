// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.provider.DocumentsContract;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.provider.DocumentsContractCompat;
import androidx.core.util.Pair;
import androidx.documentfile.provider.DocumentFile;
import androidx.documentfile.provider.DocumentFileUtils;
import androidx.documentfile.provider.ExtendedRawDocumentFile;
import androidx.documentfile.provider.MediaDocumentFile;
import androidx.documentfile.provider.VirtualDocumentFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.compat.StorageManagerCompat;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.fs.VirtualFileSystem;

/**
 * Provide an interface to {@link File} and {@link DocumentFile} with basic functionalities.
 */
class PathImpl extends Path {
    public static final String TAG = PathImpl.class.getSimpleName();

    private static final List<Boolean> EXCLUSIVE_ACCESS_GRANTED = new ArrayList<>();
    private static final List<String> EXCLUSIVE_ACCESS_PATHS = new ArrayList<>();

    static {
        setAccessPaths();
    }

    private static void setAccessPaths() {
        if (Process.myUid() == Process.ROOT_UID || Process.myUid() == Process.SYSTEM_UID || Process.myUid() == Process.SHELL_UID) {
            // Root/ADB
            return;
        }
        // We cannot use Path API here
        // Read-write
        Context context = ContextUtils.getContext();
        EXCLUSIVE_ACCESS_PATHS.add(Objects.requireNonNull(context.getFilesDir().getParentFile()).getAbsolutePath());
        EXCLUSIVE_ACCESS_GRANTED.add(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            EXCLUSIVE_ACCESS_PATHS.add(context.createDeviceProtectedStorageContext().getDataDir().getAbsolutePath());
            EXCLUSIVE_ACCESS_GRANTED.add(true);
        }
        File[] extDirs = context.getExternalCacheDirs();
        if (extDirs != null) {
            for (File dir : extDirs) {
                if (dir == null) continue;
                EXCLUSIVE_ACCESS_PATHS.add(Objects.requireNonNull(dir.getParentFile()).getAbsolutePath());
                EXCLUSIVE_ACCESS_GRANTED.add(true);
            }
        }
        if (SelfPermissions.checkSelfStoragePermission()) {
            int userId = UserHandleHidden.myUserId();
            String[] cards;
            if (userId == 0) {
                cards = new String[]{
                        "/sdcard",
                        "/storage/emulated/" + userId,
                        "/storage/self/primary"
                };
            } else cards = new String[]{"/storage/emulated/" + userId};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Add Android/data and Android/obb to the exemption list
                boolean canInstallApps = SelfPermissions.checkSelfPermission(Manifest.permission.REQUEST_INSTALL_PACKAGES)
                        || SelfPermissions.checkSelfPermission(Manifest.permission.INSTALL_PACKAGES);
                for (String card : cards) {
                    EXCLUSIVE_ACCESS_PATHS.add(card + "/Android/data");
                    EXCLUSIVE_ACCESS_GRANTED.add(false);
                    if (!canInstallApps) {
                        EXCLUSIVE_ACCESS_PATHS.add(card + "/Android/obb");
                        EXCLUSIVE_ACCESS_GRANTED.add(false);
                    }
                }
            }
            // Lowest priority
            for (String card : cards) {
                EXCLUSIVE_ACCESS_PATHS.add(card);
                EXCLUSIVE_ACCESS_GRANTED.add(true);
            }
        }
        // Assert sizes
        if (EXCLUSIVE_ACCESS_PATHS.size() != EXCLUSIVE_ACCESS_GRANTED.size()) {
            throw new RuntimeException();
        }
    }

    private static boolean needPrivilegedAccess(@NonNull String path) {
        if (Process.myUid() == Process.ROOT_UID || Process.myUid() == Process.SYSTEM_UID || Process.myUid() == Process.SHELL_UID) {
            // Root/shell
            return false;
        }
        for (int i = 0; i < EXCLUSIVE_ACCESS_PATHS.size(); ++i) {
            if (path.startsWith(EXCLUSIVE_ACCESS_PATHS.get(i))) {
                // May need no privileged access
                return !EXCLUSIVE_ACCESS_GRANTED.get(i);
            }
        }
        return true;
    }

    @NonNull
    private static DocumentFile getRequiredRawDocument(@NonNull String path) {
        if (needPrivilegedAccess(path) && LocalServices.alive()) {
            try {
                FileSystemManager fs = LocalServices.getFileSystemManager();
                return new ExtendedRawDocumentFile(fs.getFile(path));
            } catch (RemoteException e) {
                Log.w(TAG, "Could not get privileged access to path " + path + " due to \"" + e.getMessage() + "\"");
                // Fall-back to unprivileged access
            }
        }
        ExtendedFile file = FileSystemManager.getLocal().getFile(path);
        return new ExtendedRawDocumentFile(LocalFileOverlay.getOverlayFile(file));
    }

    // An invalid MIME so that it doesn't match any extension
    private static final String DEFAULT_MIME = "application/x-invalid-mime-type";

    /* package */ PathImpl(@NonNull Context context, @NonNull String fileLocation) {
        super(context, getRequiredRawDocument(fileLocation));
    }

    /* package */ PathImpl(@NonNull Context context, @NonNull VirtualFileSystem fs) {
        super(context, new VirtualDocumentFile(getParentFile(context, fs), fs));
    }

    /* package */ PathImpl(@NonNull Context context, @NonNull String fileLocation, boolean privileged) throws RemoteException {
        super(context, null);
        if (privileged) {
            FileSystemManager fs = LocalServices.getFileSystemManager();
            documentFile = new ExtendedRawDocumentFile(fs.getFile(fileLocation));
        } else {
            ExtendedFile file = FileSystemManager.getLocal().getFile(fileLocation);
            documentFile = new ExtendedRawDocumentFile(LocalFileOverlay.getOverlayFile(file));
        }
    }

    /* package */ PathImpl(@NonNull Context context, @NonNull Uri uri) {
        super(context, null);
        // At first check if the Uri is in VFS since it gets higher priority.
        Path fsRoot = VirtualFileSystem.getFsRoot(uri);
        if (fsRoot != null) {
            documentFile = fsRoot.documentFile;
            return;
        }
        if (uri.getScheme() == null) {
            throw new IllegalArgumentException("Uri has no scheme: " + uri);
        }
        DocumentFile documentFile;
        switch (uri.getScheme()) {
            case ContentResolver.SCHEME_CONTENT:
                if (isDocumentsProvider(context, uri.getAuthority())) { // We can't use DocumentsContract.isDocumentUri() because it expects something that isn't always correct
                    boolean isTreeUri = DocumentsContractCompat.isTreeUri(uri);
                    documentFile = Objects.requireNonNull(isTreeUri ? DocumentFile.fromTreeUri(context, uri) : DocumentFile.fromSingleUri(context, uri));
                } else {
                    // Content provider
                    documentFile = new MediaDocumentFile(null, context, uri);
                }
                break;
            case ContentResolver.SCHEME_FILE:
                documentFile = getRequiredRawDocument(uri.getPath());
                break;
            case VirtualFileSystem.SCHEME: {
                Pair<Integer, String> parsedUri = VirtualDocumentFile.parseUri(uri);
                if (parsedUri != null) {
                    Path rootPath = VirtualFileSystem.getFsRoot(parsedUri.first);
                    if (rootPath != null) {
                        String path = Paths.sanitize(parsedUri.second, true);
                        if (TextUtils.isEmpty(path) || path.equals(File.separator)) {
                            // Root requested
                            documentFile = rootPath.documentFile;
                        } else {
                            // Find file is acceptable here since the file always exists
                            String[] pathComponents = path.split(File.separator);
                            DocumentFile finalDocumentFile = rootPath.documentFile;
                            for (String pathComponent : pathComponents) {
                                finalDocumentFile = Objects.requireNonNull(finalDocumentFile.findFile(pathComponent));
                            }
                            documentFile = finalDocumentFile;
                        }
                        break;
                    }
                }
            }
            default:
                throw new IllegalArgumentException("Unsupported uri " + uri);
        }
        // Setting mDocumentFile at the end ensures that it is never null
        this.documentFile = documentFile;
    }

    /**
     * NOTE: This construct is only applicable for tree Uri
     */
    /* package */ PathImpl(@Nullable Path parent, @NonNull Context context, @NonNull Uri documentUri) {
        super(context, null);
        DocumentFile parentDocumentFile = parent != null ? parent.documentFile : null;
        documentFile = DocumentFileUtils.newTreeDocumentFile(parentDocumentFile, context, documentUri);
    }

    private PathImpl(@NonNull Context context, @NonNull DocumentFile documentFile) {
        super(context, null);
        if (documentFile instanceof ExtendedRawDocumentFile) {
            ExtendedFile file = ((ExtendedRawDocumentFile) documentFile).getFile();
            if (file instanceof LocalFile) {
                ExtendedFile newFile = LocalFileOverlay.getOverlayFileOrNull(file);
                if (newFile != null) {
                    documentFile = new ExtendedRawDocumentFile(newFile);
                }
            }
        }
        this.documentFile = documentFile;
    }

    @NonNull
    public String getName() {
        // Last path segment is required.
        String name = documentFile.getName();
        if (name != null) {
            return name;
        }
        return DocumentFileUtils.resolveAltNameForSaf(documentFile);
    }


    /**
     * Return the underlying {@link ExtendedFile} if the path is backed by a real file,
     * {@code null} otherwise.
     */
    @Nullable
    public ExtendedFile getFile() {
        if (documentFile instanceof ExtendedRawDocumentFile) {
            return ((ExtendedRawDocumentFile) documentFile).getFile();
        }
        return null;
    }

    /**
     * Same as {@link #getFile()} except it return a raw string.
     */
    @Nullable
    public String getFilePath() {
        if (documentFile instanceof ExtendedRawDocumentFile) {
            return documentFile.getUri().getPath();
        }
        return null;
    }

    /**
     * Same as {@link #getFile()} except it returns the real path if the
     * current path is a symbolic link.
     */
    @Nullable
    public String getRealFilePath() throws IOException {
        if (documentFile instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).getCanonicalPath();
        }
        return null;
    }

    /**
     * Same as {@link #getFile()} except it returns the real path if the
     * current path is a symbolic link.
     */
    @Nullable
    public Path getRealPath() throws IOException {
        if (documentFile instanceof ExtendedRawDocumentFile) {
            return Paths.get(Objects.requireNonNull(getFile()).getCanonicalFile());
        }
        return null;
    }

    @NonNull
    public String getType() {
        String type = getRealDocumentFile(documentFile).getType();
        if (type == null) {
            type = PathContentInfoImpl.fromExtension(this).getMimeType();
        }
        if (type == null) {
            type = "application/octet-stream";
        }
        return type;
    }

    @NonNull
    public PathContentInfo getPathContentInfo() {
        return PathContentInfoImpl.fromPath(this);
    }

    @CheckResult
    public long length() {
        return getRealDocumentFile(documentFile).length();
    }

    @CheckResult
    public boolean recreate() {
        DocumentFile documentFile = getRealDocumentFile(this.documentFile);
        if (documentFile.isDirectory()) {
            // Directory does not need to be created again.
            return true;
        }
        if (documentFile.exists() && !documentFile.isFile()) return false;
        // For Linux documents, recreate using file APIs
        if (documentFile instanceof ExtendedRawDocumentFile) {
            try {
                File f = Objects.requireNonNull(((ExtendedRawDocumentFile) documentFile).getFile());
                if (f.exists()) f.delete();
                return f.createNewFile();
            } catch (IOException | SecurityException e) {
                return false;
            }
        }
        // In other cases, open OutputStream to make the file empty.
        // We can directly use openOutputStream because if it were a mount point, it would be a directory.
        try (OutputStream ignored = openOutputStream(false)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @NonNull
    public Path createNewFile(@NonNull String displayName, @Nullable String mimeType) throws IOException {
        displayName = Paths.sanitize(displayName, true);
        if (displayName == null) {
            throw new IOException("Empty display name.");
        }
        return createFileAsDirectChild(context, documentFile, displayName, mimeType);
    }

    @NonNull
    public Path createNewDirectory(@NonNull String displayName) throws IOException {
        displayName = Paths.sanitize(displayName, true);
        if (displayName == null) {
            throw new IOException("Empty display name.");
        }
        if (displayName.indexOf(File.separatorChar) != -1) {
            throw new IllegalArgumentException("Display name contains file separator.");
        }
        DocumentFile documentFile = getRealDocumentFile(this.documentFile);
        if (!documentFile.isDirectory()) {
            throw new IOException("Current file is not a directory.");
        }
        checkVfs(Paths.appendPathSegment(documentFile.getUri(), displayName));
        DocumentFile file = documentFile.createDirectory(displayName);
        if (file == null) throw new IOException("Could not create directory named " + displayName);
        return new PathImpl(context, file);
    }

    @NonNull
    public Path createNewArbitraryFile(@NonNull String displayName, @Nullable String mimeType) throws IOException {
        displayName = Paths.sanitize(displayName, true);
        if (displayName == null) {
            throw new IOException("Empty display name.");
        }
        String[] names = displayName.split(File.separator);
        if (names.length == 0) {
            throw new IllegalArgumentException("Display name is empty.");
        }
        for (String name : names) {
            if (name.equals("..")) {
                throw new IOException("Could not create directories in the parent directory.");
            }
        }
        DocumentFile file = createArbitraryDirectories(documentFile, names, names.length - 1);
        return createFileAsDirectChild(context, file, names[names.length - 1], mimeType);
    }

    @NonNull
    public Path createDirectoriesIfRequired(@NonNull String displayName) throws IOException {
        displayName = Paths.sanitize(displayName, true);
        if (displayName == null) {
            throw new IOException("Empty display name.");
        }
        String[] dirNames = displayName.split(File.separator);
        if (dirNames.length == 0) {
            throw new IllegalArgumentException("Display name is empty");
        }
        for (String name : dirNames) {
            if (name.equals("..")) {
                throw new IOException("Could not create directories in the parent directory.");
            }
        }
        DocumentFile file = createArbitraryDirectories(documentFile, dirNames, dirNames.length);
        return new PathImpl(context, file);
    }

    @NonNull
    public Path createDirectories(@NonNull String displayName) throws IOException {
        displayName = Paths.sanitize(displayName, true);
        if (displayName == null) {
            throw new IOException("Empty display name.");
        }
        String[] dirNames = displayName.split(File.separator);
        if (dirNames.length == 0) {
            throw new IllegalArgumentException("Display name is empty");
        }
        for (String name : dirNames) {
            if (name.equals("..")) {
                throw new IOException("Could not create directories in the parent directory.");
            }
        }
        DocumentFile file = createArbitraryDirectories(documentFile, dirNames, dirNames.length - 1);
        // Special case for the last segment
        String lastSegment = dirNames[dirNames.length - 1];
        Path fsRoot = VirtualFileSystem.getFsRoot(Paths.appendPathSegment(file.getUri(), lastSegment));
        DocumentFile t = fsRoot != null ? fsRoot.documentFile : file.findFile(lastSegment);
        if (t != null) {
            throw new IOException(t.getUri() + " already exists.");
        }
        t = file.createDirectory(lastSegment);
        if (t == null) {
            throw new IOException("Directory" + file.getUri() + File.separator + lastSegment + " could not be created.");
        }
        return new PathImpl(context, t);
    }

    @Nullable
    public Path getParent() {
        DocumentFile file = getRealDocumentFile(documentFile).getParentFile();
        return file == null ? null : new PathImpl(context, file);
    }

    public boolean hasFile(@NonNull String displayName) {
        return findFileInternal(documentFile, displayName) != null;
    }

    @NonNull
    public Path findFile(@NonNull String displayName) throws FileNotFoundException {
        DocumentFile nextPath = findFileInternal(documentFile, displayName);
        if (nextPath == null) {
            throw new FileNotFoundException("Cannot find " + this + File.separatorChar + displayName);
        }
        return new PathImpl(context, nextPath);
    }

    @NonNull
    public Path findOrCreateFile(@NonNull String displayName, @Nullable String mimeType) throws IOException {
        displayName = Paths.sanitize(displayName, true);
        if (displayName == null) {
            throw new IOException("Empty display name.");
        }
        if (displayName.indexOf(File.separatorChar) != -1) {
            throw new IllegalArgumentException("Display name contains file separator.");
        }
        DocumentFile documentFile = getRealDocumentFile(this.documentFile);
        if (!documentFile.isDirectory()) {
            throw new IOException("Current file is not a directory.");
        }
        String extension = null;
        if (mimeType != null) {
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        } else mimeType = DEFAULT_MIME;
        String nameWithExtension = displayName + (extension != null ? "." + extension : "");
        checkVfs(Paths.appendPathSegment(documentFile.getUri(), nameWithExtension));
        DocumentFile file = documentFile.findFile(displayName);
        if (file != null) {
            if (file.isDirectory()) {
                throw new IOException("Directory cannot be converted to file");
            }
            return new PathImpl(context, file);
        }
        file = documentFile.createFile(mimeType, displayName);
        if (file == null) {
            throw new IOException("Could not create " + documentFile.getUri() + File.separatorChar + nameWithExtension + " with type " + mimeType);
        }
        return new PathImpl(context, file);
    }

    @NonNull
    public Path findOrCreateDirectory(@NonNull String displayName) throws IOException {
        displayName = Paths.sanitize(displayName, true);
        if (displayName == null) {
            throw new IOException("Empty display name.");
        }
        if (displayName.indexOf(File.separatorChar) != -1) {
            throw new IllegalArgumentException("Display name contains file separator.");
        }
        DocumentFile documentFile = getRealDocumentFile(this.documentFile);
        if (!documentFile.isDirectory()) {
            throw new IOException("Current file is not a directory.");
        }
        Path fsRoot = VirtualFileSystem.getFsRoot(Paths.appendPathSegment(documentFile.getUri(), displayName));
        if (fsRoot != null) return fsRoot;
        DocumentFile file = documentFile.findFile(displayName);
        if (file != null) {
            if (!file.isDirectory()) {
                throw new IOException("Existing file is not a directory");
            }
            return new PathImpl(context, file);
        }
        file = documentFile.createDirectory(displayName);
        if (file == null) throw new IOException("Could not create directory named " + displayName);
        return new PathImpl(context, file);
    }

    @NonNull
    public PathAttributes getAttributes() throws IOException {
        if (documentFile instanceof ExtendedRawDocumentFile) {
            return PathAttributesImpl.fromFile((ExtendedRawDocumentFile) documentFile);
        }
        if (documentFile instanceof VirtualDocumentFile) {
            return PathAttributesImpl.fromVirtual((VirtualDocumentFile) documentFile);
        }
        return PathAttributesImpl.fromSaf(context, documentFile);
    }

    @CheckResult
    public boolean exists() {
        return getRealDocumentFile(documentFile).exists();
    }

    @CheckResult
    public boolean isDirectory() {
        return getRealDocumentFile(documentFile).isDirectory();
    }

    @CheckResult
    public boolean isFile() {
        return getRealDocumentFile(documentFile).isFile();
    }

    @CheckResult
    public boolean isVirtual() {
        return getRealDocumentFile(documentFile).isVirtual();
    }

    @CheckResult
    public boolean isSymbolicLink() {
        if (getRealDocumentFile(documentFile) instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).isSymlink();
        }
        return false;
    }

    public boolean createNewSymbolicLink(String target) {
        if (getRealDocumentFile(documentFile) instanceof ExtendedRawDocumentFile) {
            try {
                return Objects.requireNonNull(getFile()).createNewSymlink(target);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public boolean canRead() {
        return getRealDocumentFile(documentFile).canRead();
    }

    public boolean canWrite() {
        return getRealDocumentFile(documentFile).canWrite();
    }

    public boolean canExecute() {
        if (getRealDocumentFile(documentFile) instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).canExecute();
        }
        return false;
    }

    public int getMode() {
        if (documentFile instanceof ExtendedRawDocumentFile) {
            try {
                return Objects.requireNonNull(getFile()).getMode();
            } catch (ErrnoException e) {
                return 0;
            }
        }
        if (documentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile) documentFile).getMode();
        }
        return 0;
    }

    public boolean setMode(int mode) {
        if (documentFile instanceof ExtendedRawDocumentFile) {
            try {
                Objects.requireNonNull(getFile()).setMode(mode);
                return true;
            } catch (ErrnoException e) {
                return false;
            }
        }
        if (documentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile) documentFile).setMode(mode);
        }
        return false;
    }

    @Nullable
    public UidGidPair getUidGid() {
        if (documentFile instanceof ExtendedRawDocumentFile) {
            try {
                return Objects.requireNonNull(getFile()).getUidGid();
            } catch (ErrnoException e) {
                return null;
            }
        }
        if (documentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile) documentFile).getUidGid();
        }
        return null;
    }

    public boolean setUidGid(UidGidPair uidGidPair) {
        if (documentFile instanceof ExtendedRawDocumentFile) {
            try {
                return Objects.requireNonNull(getFile()).setUidGid(uidGidPair.uid, uidGidPair.gid);
            } catch (ErrnoException e) {
                return false;
            }
        }
        if (documentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile) documentFile).setUidGid(uidGidPair);
        }
        return false;
    }

    @Nullable
    public String getSelinuxContext() {
        if (documentFile instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).getSelinuxContext();
        }
        return null;
    }

    public boolean setSelinuxContext(@Nullable String context) {
        if (documentFile instanceof ExtendedRawDocumentFile) {
            if (context == null) {
                return Objects.requireNonNull(getFile()).restoreSelinuxContext();
            }
            return Objects.requireNonNull(getFile()).setSelinuxContext(context);
        }
        return false;
    }

    public boolean isMountPoint() {
        return VirtualFileSystem.isMountPoint(getUri());
    }

    public boolean mkdir() {
        DocumentFile documentFile = getRealDocumentFile(this.documentFile);
        if (documentFile.exists()) {
            return false;
        }
        if (documentFile instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).mkdir();
        } else {
            DocumentFile parent = documentFile.getParentFile();
            if (parent != null) {
                DocumentFile thisFile = parent.createDirectory(getName());
                if (thisFile != null) {
                    this.documentFile = thisFile;
                    return true;
                }
            }
        }
        return false;
    }

    public boolean mkdirs() {
        DocumentFile documentFile = getRealDocumentFile(this.documentFile);
        if (documentFile.exists()) {
            return false;
        }
        if (documentFile instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).mkdirs();
        }
        // For others, directory can't be created recursively as parent must exist
        DocumentFile parent = documentFile.getParentFile();
        if (parent != null) {
            DocumentFile thisFile = parent.createDirectory(getName());
            if (thisFile != null) {
                this.documentFile = thisFile;
                return true;
            }
        }
        return false;
    }

    public boolean renameTo(@NonNull String displayName) {
        displayName = Paths.sanitize(displayName, true);
        if (displayName == null) {
            // Empty display name
            return false;
        }
        if (displayName.contains(File.separator)) {
            // Display name must belong to the same directory.
            return false;
        }
        DocumentFile parent = documentFile.getParentFile();
        if (parent == null) {
            return false;
        }
        DocumentFile file = parent.findFile(displayName);
        if (file != null) {
            // File exists
            return false;
        }
        Path fsRoot = VirtualFileSystem.getFsRoot(Paths.appendPathSegment(parent.getUri(), displayName));
        if (fsRoot != null) {
            // Mount point exists
            return false;
        }
        Uri oldMountPoint = documentFile.getUri();
        if (documentFile.renameTo(displayName)) {
            if (VirtualFileSystem.getFileSystem(oldMountPoint) != null) {
                // Change mount point
                VirtualFileSystem.alterMountPoint(oldMountPoint, documentFile.getUri());
            }
            return true;
        }
        return false;
    }

    public boolean moveTo(@NonNull Path path, boolean override) {
        DocumentFile source = getRealDocumentFile(documentFile);
        DocumentFile dest = getRealDocumentFile(path.documentFile);
        if (!source.exists()) {
            // Source itself does not exist.
            return false;
        }
        if (dest.exists() && !dest.canWrite()) {
            // There's no point is attempting to move if the destination is read-only.
            return false;
        }
        if (dest.getUri().toString().startsWith(source.getUri().toString())) {
            // Destination cannot be the same or a subdirectory of source
            return false;
        }
        if (source instanceof ExtendedRawDocumentFile && dest instanceof ExtendedRawDocumentFile) {
            // Try Linux-default file move
            File srcFile = Objects.requireNonNull(((ExtendedRawDocumentFile) source).getFile());
            File dstFile = Objects.requireNonNull(((ExtendedRawDocumentFile) dest).getFile());
            // Java rename cannot infer anything about the source and destination. Therefore, hacks are needed
            if (srcFile.isFile()) { // Source is a file
                if (dstFile.isDirectory()) {
                    // Move source file inside this directory
                    dstFile = new File(dstFile, srcFile.getName());
                } else if (dstFile.isFile()) {
                    // If destination is a file, it overrides it
                    if (!override) {
                        // Overriding is disabled
                        return false;
                    }
                }
                // else destination does not exist and Java is able to create it
            } else if (srcFile.isDirectory()) { // Source is a directory
                if (dstFile.isDirectory()) {
                    // Move source directory inside this directory
                    dstFile = new File(dstFile, srcFile.getName());
                } else if (dstFile.isFile()) {
                    // Destination cannot be a file
                    return false;
                }
                // else destination does not exist and Java is able to create it
            } else {
                // Unsupported file
                return false;
            }
            if (srcFile.renameTo(dstFile)) {
                documentFile = getRequiredRawDocument(dstFile.getAbsolutePath());
                if (VirtualFileSystem.getFileSystem(Uri.fromFile(srcFile)) != null) {
                    // Move mount point
                    VirtualFileSystem.alterMountPoint(Uri.fromFile(srcFile), Uri.fromFile(dstFile));
                }
                return true;
            }
        }
        // Try Path#renameTo if both are located in the same directory. Mount point is already handled here.
        DocumentFile sourceParent = source.getParentFile();
        DocumentFile destParent = dest.getParentFile();
        if (sourceParent != null && sourceParent.equals(destParent)) {
            // If both path are located in the same directory, rename them
            if (renameTo(path.getName())) {
                return true;
            }
        }
        // Try copy and delete approach
        if (source.isDirectory()) { // Source is a directory
            if (dest.isDirectory()) {
                // Destination is a directory: Apply copy-and-delete inside the dest
                DocumentFile newPath = dest.createDirectory(Objects.requireNonNull(source.getName()));
                if (newPath == null || newPath.listFiles().length != 0) {
                    // Couldn't create directory or the directory is not empty
                    return false;
                }
                try {
                    // Copy all the directory items to the new path and delete source
                    copyDirectory(context, source, newPath);
                    source.delete();
                    documentFile = newPath;
                    return true;
                } catch (IOException e) {
                    return false;
                }
            }
            if (!dest.exists()) {
                // Destination does not exist, simply create and copy
                // Make sure that parent exists, and it is a directory
                if (destParent == null || !destParent.isDirectory()) {
                    return false;
                }
                DocumentFile newPath = destParent.createDirectory(Objects.requireNonNull(dest.getName()));
                if (newPath == null || newPath.listFiles().length != 0) {
                    // Couldn't create directory or the directory is not empty
                    return false;
                }
                try {
                    // Copy all the directory items to the new path and delete source
                    copyDirectory(context, source, newPath);
                    source.delete();
                    documentFile = newPath;
                    return true;
                } catch (IOException e) {
                    return false;
                }
            }
            // Current path is a directory but target is not a directory
            return false;
        }
        if (source.isFile()) { // Source is a file
            DocumentFile newPath;
            if (dest.isDirectory()) {
                // Move the file inside the directory
                newPath = dest.createFile(DEFAULT_MIME, getName());
            } else if (dest.isFile()) {
                // Rename the file and override the existing dest
                if (!override) {
                    // overriding is disabled
                    return false;
                }
                newPath = dest;
            } else if (destParent != null) {
                // File does not exist, create a new one
                newPath = destParent.createFile(DEFAULT_MIME, Objects.requireNonNull(dest.getName()));
            } else {
                // File does not exist, but nothing could be done about it
                return false;
            }
            if (newPath == null) {
                // For some reason, newPath could not be created
                return false;
            }
            try {
                // Copy the contents of the source file and delete it
                copyFile(context, source, newPath);
                source.delete();
                documentFile = newPath;
                return true;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }


    @Nullable
    public Path copyTo(@NonNull Path path) {
        return copyTo(path, true);
    }

    @Nullable
    public Path copyTo(@NonNull Path path, boolean override) {
        DocumentFile source = getRealDocumentFile(documentFile);
        DocumentFile dest = getRealDocumentFile(path.documentFile);
        if (!source.exists()) {
            // Source itself does not exist.
            Log.d(TAG, "Source does not exist.");
            return null;
        }
        if (dest.exists() && !dest.canWrite()) {
            // There's no point is attempting to copy if the destination is read-only.
            Log.d(TAG, "Read-only destination.");
            return null;
        }
        // Add separator to avoid matching wrong files
        String destStr = dest.getUri() + File.separator;
        String srcStr = source.getUri() + File.separator;
        if (destStr.startsWith(srcStr)) {
            // Destination cannot be the same or a subdirectory of source
            Log.d(TAG, "Destination is a subdirectory of source.");
            return null;
        }
        DocumentFile destParent = dest.getParentFile();
        if (source.isDirectory()) { // Source is a directory
            if (dest.isDirectory()) {
                // Destination is a directory: Apply copy source inside the dest
                String name = Objects.requireNonNull(source.getName());
                DocumentFile newPath = dest.findFile(name);
                if (newPath != null) {
                    // Desired directory exists
                    if (!override) {
                        Log.d(TAG, "Overwriting isn't enabled.");
                        return null;
                    }
                    // Check if this is the source
                    if (source.getUri().equals(newPath.getUri())) {
                        Log.d(TAG, "Source and destination are the same.");
                        return null;
                    }
                }
                newPath = dest.createDirectory(name);
                if (newPath == null) {
                    // Couldn't create directory
                    Log.d(TAG, "Could not create directory in the destination.");
                    return null;
                }
                try {
                    // Copy all the directory items to the new path
                    copyDirectory(context, source, newPath, override);
                    return new PathImpl(context, newPath);
                } catch (IOException e) {
                    Log.d(TAG, "Could not copy files.", e);
                    return null;
                }
            }
            if (!dest.exists()) {
                // Destination does not exist, simply create and copy
                // Make sure that parent exists, and it is a directory
                if (destParent == null || !destParent.isDirectory()) {
                    Log.d(TAG, "Parent of destination must exist.");
                    return null;
                }
                DocumentFile newPath = destParent.createDirectory(Objects.requireNonNull(dest.getName()));
                if (newPath == null) {
                    // Couldn't create directory or the directory is not empty
                    Log.d(TAG, "Could not create directory or non-empty directory.");
                    return null;
                }
                try {
                    // Copy all the directory items to the new path
                    copyDirectory(context, source, newPath, override);
                    return new PathImpl(context, newPath);
                } catch (IOException e) {
                    Log.d(TAG, "Could not copy files.", e);
                    return null;
                }
            }
            // Current path is a directory but target is not a directory
            Log.d(TAG, "Source is a directory while destination is not.");
            return null;
        }
        if (source.isFile()) { // Source is a file
            DocumentFile newPath;
            if (dest.isDirectory()) {
                // Move the file inside the directory
                newPath = dest.findFile(getName());
                if (newPath != null) {
                    // File exists
                    if (!override) {
                        Log.d(TAG, "Overwriting isn't enabled.");
                        return null;
                    }
                    // Check if this is the source
                    if (source.getUri().equals(newPath.getUri())) {
                        Log.d(TAG, "Source and destination are the same.");
                        return null;
                    }
                }
                newPath = dest.createFile(DEFAULT_MIME, getName());
            } else if (dest.isFile()) {
                // Override the existing dest
                if (!override) {
                    // overriding is disabled
                    Log.d(TAG, "Overwriting isn't enabled.");
                    return null;
                }
                newPath = dest;
            } else if (destParent != null) {
                // File does not exist, create a new one
                newPath = destParent.createFile(DEFAULT_MIME, Objects.requireNonNull(dest.getName()));
            } else {
                // File does not exist, but nothing could be done about it
                Log.d(TAG, "Could not copy file.");
                return null;
            }
            if (newPath == null) {
                // For some reason, newPath could not be created
                Log.d(TAG, "Could not create file in the destination.");
                return null;
            }
            try {
                // Copy the contents of the source file
                copyFile(context, source, newPath);
                return new PathImpl(context, newPath);
            } catch (IOException e) {
                Log.d(TAG, "Could not copy files.", e);
                return null;
            }
        }
        Log.d(TAG, "Unknown error during copying.");
        return null;
    }

    private static void copyFile(@NonNull Context context, @NonNull DocumentFile src, @NonNull DocumentFile dst)
            throws IOException {
        copyFile(new PathImpl(context, src), new PathImpl(context, dst));
    }

    private static void copyFile(@NonNull Path src, @NonNull Path dst) throws IOException {
        if (src.isMountPoint() || dst.isMountPoint()) {
            throw new IOException("Either source or destination are a mount point.");
        }
        IoUtils.copy(src, dst);
    }

    // Copy directory content
    private static void copyDirectory(@NonNull Context context, @NonNull DocumentFile src, @NonNull DocumentFile dst,
                                      boolean override) throws IOException {
        copyDirectory(new PathImpl(context, src), new PathImpl(context, dst), override);
    }

    private static void copyDirectory(@NonNull Context context, @NonNull DocumentFile src, @NonNull DocumentFile dst)
            throws IOException {
        copyDirectory(new PathImpl(context, src), new PathImpl(context, dst), true);
    }

    private static void copyDirectory(@NonNull Path src, @NonNull Path dst, boolean override) throws IOException {
        for (Path file : src.listFiles()) {
            String name = file.getName();
            if (file.isDirectory()) {
                Path newDir = dst.createNewDirectory(name);
                Path fsRoot = VirtualFileSystem.getFsRoot(file.getUri());
                if (fsRoot != null) {
                    VirtualFileSystem.alterMountPoint(file.getUri(), newDir.getUri());
                }
                copyDirectory(file, newDir, override);
            } else if (file.isFile()) {
                if (dst.hasFile(name) && !override) {
                    // Override disabled
                    continue;
                }
                Path newFile = dst.createNewFile(name, null);
                copyFile(file, newFile);
            }
        }
    }

    public long lastModified() {
        return documentFile.lastModified();
    }

    public boolean setLastModified(long time) {
        if (documentFile instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).setLastModified(time);
        }
        if (documentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile) documentFile).setLastModified(time);
        }
        return false;
    }

    public long lastAccess() {
        if (documentFile instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).lastAccess();
        }
        if (documentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile) documentFile).lastAccess();
        }
        return 0;
    }

    public boolean setLastAccess(long millis) {
        if (documentFile instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).setLastAccess(millis);
        }
//        // TODO: 28/6/23
//        if (mDocumentFile instanceof VirtualDocumentFile) {
//            return ((VirtualDocumentFile) mDocumentFile).setLastAccess();
//        }
        return false;
    }

    public long creationTime() {
        if (documentFile instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).creationTime();
        }
        if (documentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile) documentFile).creationTime();
        }
        return 0;
    }

    @NonNull
    public Path[] listFiles() {
        // Get all file systems mounted at this Uri
        DocumentFile documentFile = getRealDocumentFile(this.documentFile);
        VirtualFileSystem[] fileSystems = VirtualFileSystem.getFileSystemsAtUri(documentFile.getUri());
        HashMap<String, Uri> nameMountPointMap = new HashMap<>(fileSystems.length);
        for (VirtualFileSystem fs : fileSystems) {
            Uri mountPoint = Objects.requireNonNull(fs.getMountPoint());
            nameMountPointMap.put(mountPoint.getLastPathSegment(), mountPoint);
        }
        // List documents at this folder
        DocumentFile[] ss = documentFile.listFiles();
        if (nameMountPointMap.isEmpty()) {
            // No need to go further
            Path[] paths = new Path[ss.length];
            for (int i = 0; i < ss.length; ++i) {
                paths[i] = new PathImpl(context, ss[i]);
            }
            return paths;
        }
        List<Path> paths = new ArrayList<>(ss.length + fileSystems.length);
        // Remove mount points
        for (DocumentFile s : ss) {
            if (nameMountPointMap.get(s.getName()) != null) {
                // Mount point exists, remove it from map
                nameMountPointMap.remove(s.getName());
            }
            paths.add(new PathImpl(context, s));
        }
        // Add all the other mount points
        for (Uri mountPoint : nameMountPointMap.values()) {
            paths.add(new PathImpl(context, mountPoint));
        }
        return paths.toArray(new Path[0]);
    }

    @NonNull
    public String[] listFileNames(@Nullable FilenameFilter filter) {
        Path[] ss = listFiles();
        ArrayList<String> files = new ArrayList<>();
        for (Path s : ss) {
            s.getName();
            if (filter == null || filter.accept(s, s.getName())) {
                files.add(s.getName());
            }
        }
        return files.toArray(new String[0]);
    }

    @NonNull
    public ParcelFileDescriptor openFileDescriptor(@NonNull String mode, @NonNull HandlerThread callbackThread)
            throws FileNotFoundException {
        DocumentFile documentFile = getRealDocumentFile(this.documentFile);
        if (documentFile instanceof ExtendedRawDocumentFile) {
            ExtendedFile file = Objects.requireNonNull(getFile());
            if (file instanceof RemoteFile) {
                int modeBits = ParcelFileDescriptor.parseMode(mode);
                try {
                    return StorageManagerCompat.openProxyFileDescriptor(modeBits, new ProxyStorageCallback(
                            file.getAbsolutePath(), modeBits, callbackThread));
                } catch (IOException e) {
                    throw (FileNotFoundException) new FileNotFoundException(e.getMessage()).initCause(e);
                }
            } // else use the default content provider
        } else if (documentFile instanceof VirtualDocumentFile) {
            int modeBits = ParcelFileDescriptor.parseMode(mode);
            try {
                return ((VirtualDocumentFile) documentFile).openFileDescriptor(modeBits);
            } catch (IOException e) {
                throw (FileNotFoundException) new FileNotFoundException(e.getMessage()).initCause(e);
            }
        }
        return FileUtils.getFdFromUri(context, documentFile.getUri(), mode);
    }

    @NonNull
    public OutputStream openOutputStream(boolean append) throws IOException {
        DocumentFile documentFile = resolveFileOrNull(this.documentFile);
        if (documentFile == null) {
            throw new IOException(this.documentFile.getUri() + " is a directory");
        }
        if (documentFile instanceof ExtendedRawDocumentFile) {
            try {
                return Objects.requireNonNull(getFile()).newOutputStream(append);
            } catch (IOException e) {
                throw new IOException("Could not open file for writing: " + documentFile.getUri(), e);
            }
        } else if (documentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile) documentFile).openOutputStream(append);
        }
        String mode = "w" + (append ? "a" : "t");
        OutputStream os = context.getContentResolver().openOutputStream(documentFile.getUri(), mode);
        if (os == null) {
            throw new IOException("Could not resolve Uri: " + documentFile.getUri());
        }
        return os;
    }

    @NonNull
    public InputStream openInputStream() throws IOException {
        DocumentFile documentFile = resolveFileOrNull(this.documentFile);
        if (documentFile == null) {
            throw new IOException(this.documentFile.getUri() + " is a directory");
        }
        if (documentFile instanceof ExtendedRawDocumentFile) {
            try {
                return Objects.requireNonNull(getFile()).newInputStream();
            } catch (IOException e) {
                throw new IOException("Could not open file for reading: " + documentFile.getUri(), e);
            }
        } else if (documentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile) documentFile).openInputStream();
        }
        InputStream is = context.getContentResolver().openInputStream(documentFile.getUri());
        if (is == null) {
            throw new IOException("Could not resolve Uri: " + documentFile.getUri());
        }
        return is;
    }

    public FileChannel openFileChannel(int mode) throws IOException {
        DocumentFile documentFile = resolveFileOrNull(this.documentFile);
        if (documentFile == null) {
            throw new IOException(this.documentFile.getUri() + " is a directory");
        }
        if (documentFile instanceof ExtendedRawDocumentFile) {
            ExtendedFile file = Objects.requireNonNull(getFile());
            if (file instanceof RemoteFile) {
                try {
                    return LocalServices.getFileSystemManager().openChannel(file, mode);
                } catch (RemoteException e) {
                    throw new IOException(e);
                }
            }
            return FileSystemManager.getLocal().openChannel(file, mode);
        } else if (documentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile) documentFile).openChannel(mode);
        }
        throw new IOException("Target is not backed by a real file");
    }

    @NonNull
    private static Path createFileAsDirectChild(@NonNull Context context,
                                                @NonNull DocumentFile documentFile,
                                                @NonNull String displayName,
                                                @Nullable String mimeType) throws IOException {
        if (displayName.indexOf(File.separatorChar) != -1) {
            throw new IllegalArgumentException("Display name contains file separator.");
        }
        documentFile = getRealDocumentFile(documentFile);
        if (!documentFile.isDirectory()) {
            throw new IOException("Current file is not a directory.");
        }
        String extension = null;
        if (mimeType != null) {
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        } else mimeType = DEFAULT_MIME;
        String nameWithExtension = displayName + (extension != null ? "." + extension : "");
        checkVfs(Paths.appendPathSegment(documentFile.getUri(), nameWithExtension));
        DocumentFile f = documentFile.findFile(displayName);
        if (f != null) {
            if (f.isDirectory()) {
                throw new IOException("Directory cannot be converted to file");
            }
            // Delete the file if exists
            f.delete();
        }
        DocumentFile file = documentFile.createFile(mimeType, displayName);
        if (file == null) {
            throw new IOException("Could not create " + documentFile.getUri() + File.separatorChar + nameWithExtension + " with type " + mimeType);
        }
        return new PathImpl(context, file);
    }

    @Nullable
    private static DocumentFile findFileInternal(@NonNull DocumentFile documentFile, @NonNull String dirtyDisplayName) {
        String displayName = Paths.sanitize(dirtyDisplayName, true);
        if (displayName == null) {
            // Empty display name
            return null;
        }
        String[] parts = displayName.split(File.separator);
        documentFile = getRealDocumentFile(documentFile);
        for (String part : parts) {
            // Check for mount point
            Uri newUri = Paths.appendPathSegment(documentFile.getUri(), part);
            Path fsRoot = VirtualFileSystem.getFsRoot(newUri);
            // Mount point has the higher priority
            documentFile = fsRoot != null ? fsRoot.documentFile : documentFile.findFile(part);
            if (documentFile == null) return null;
        }
        return documentFile;
    }

    @Nullable
    private static DocumentFile getParentFile(@NonNull Context context, @NonNull VirtualFileSystem vfs) {
        Uri mountPoint = vfs.getMountPoint();
        if (mountPoint == null) {
            return null;
        }
        // FIXME: 9/9/23 This doesn't actually work for content URIs
        Uri parentUri = Paths.removeLastPathSegment(mountPoint);
        return new PathImpl(context, parentUri).documentFile;
    }

    @NonNull
    private static DocumentFile createArbitraryDirectories(@NonNull DocumentFile documentFile,
                                                           @NonNull String[] names,
                                                           int length) throws IOException {
        DocumentFile file = getRealDocumentFile(documentFile);
        for (int i = 0; i < length; ++i) {
            Path fsRoot = VirtualFileSystem.getFsRoot(Paths.appendPathSegment(file.getUri(), names[i]));
            DocumentFile t = fsRoot != null ? fsRoot.documentFile : file.findFile(names[i]);
            if (t == null) {
                t = file.createDirectory(names[i]);
            } else if (!t.isDirectory()) {
                throw new IOException(t.getUri() + " exists and it is not a directory.");
            }
            if (t == null) {
                throw new IOException("Could not create directory " + file.getUri() + File.separatorChar + names[i]);
            }
            file = t;
        }
        return file;
    }

    @NonNull
    private static DocumentFile getRealDocumentFile(@NonNull DocumentFile documentFile) {
        Path fsRoot = VirtualFileSystem.getFsRoot(documentFile.getUri());
        if (fsRoot != null) {
            return fsRoot.documentFile;
        }
        return documentFile;
    }

    @Nullable
    private static DocumentFile resolveFileOrNull(@NonNull DocumentFile documentFile) {
        DocumentFile realDocumentFile = getRealDocumentFile(documentFile);
        if (!realDocumentFile.isDirectory()) {
            return realDocumentFile;
        }
        // Try original
        if (!documentFile.isDirectory()) {
            return documentFile;
        }
        return null;
    }

    private static void checkVfs(Uri uri) throws IOException {
        if (VirtualFileSystem.getFileSystem(uri) != null) {
            throw new IOException("Destination is a mount point.");
        }
    }

    private static boolean isDocumentsProvider(@NonNull Context context, @Nullable String authority) {
        final Intent intent = new Intent(DocumentsContract.PROVIDER_INTERFACE);
        final List<ResolveInfo> infos = context.getPackageManager().queryIntentContentProviders(intent, 0);
        for (ResolveInfo info : infos) {
            if (Objects.equals(authority, info.providerInfo.authority)) {
                return true;
            }
        }
        return false;
    }

    private static class ProxyStorageCallback extends StorageManagerCompat.ProxyFileDescriptorCallbackCompat {
        @NonNull
        private final FileChannel mChannel;

        private ProxyStorageCallback(String path, int modeBits, HandlerThread thread) throws IOException {
            super(thread);
            try {
                FileSystemManager fs = LocalServices.getFileSystemManager();
                mChannel = fs.openChannel(path, modeBits);
            } catch (IOException e) {
                thread.quitSafely();
                throw e;
            } catch (Throwable throwable) {
                thread.quitSafely();
                throw new IOException(throwable);
            }
        }

        @Override
        public long onGetSize() throws ErrnoException {
            try {
                return mChannel.size();
            } catch (IOException e) {
                throw new ErrnoException(e.getMessage(), OsConstants.EBADF);
            }
        }

        @Override
        public int onRead(long offset, int size, byte[] data) throws ErrnoException {
            ByteBuffer bf = ByteBuffer.wrap(data);
            bf.limit(size);
            try {
                return mChannel.read(bf, offset);
            } catch (IOException e) {
                throw new ErrnoException(e.getMessage(), OsConstants.EBADF);
            }
        }

        @Override
        public int onWrite(long offset, int size, byte[] data) throws ErrnoException {
            ByteBuffer bf = ByteBuffer.wrap(data);
            bf.limit(size);
            try {
                return mChannel.write(bf, offset);
            } catch (IOException e) {
                throw new ErrnoException(e.getMessage(), OsConstants.EBADF);
            }
        }

        @Override
        public void onFsync() throws ErrnoException {
            try {
                mChannel.force(true);
            } catch (IOException e) {
                throw new ErrnoException(e.getMessage(), OsConstants.EBADF);
            }
        }

        @Override
        protected void onRelease() {
            try {
                mChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected void finalize() throws Throwable {
            mChannel.close();
        }
    }
}
