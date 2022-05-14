// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.content.ContentResolver;
import android.content.Context;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.webkit.MimeTypeMap;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.util.Pair;
import androidx.documentfile.provider.DexDocumentFile;
import androidx.documentfile.provider.DocumentFile;
import androidx.documentfile.provider.ExtendedRawDocumentFile;
import androidx.documentfile.provider.VirtualDocumentFile;
import androidx.documentfile.provider.ZipDocumentFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.compat.StorageManagerCompat;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.scanner.DexClasses;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;

/**
 * Provide an interface to {@link File} and {@link DocumentFile} with basic functionalities.
 */
// TODO: 8/5/22 Move all URI handling logic to Paths and keep only business logic here
public class Path implements Comparable<Path> {
    public static final String TAG = Path.class.getSimpleName();

    private static final Set<String> EXCLUSIVE_ACCESS_PATHS = new HashSet<String>() {{
        // We cannot use Path API here
        // Read-only
        add(Environment.getRootDirectory().getAbsolutePath());
        add(OsEnvironment.getDataDirectoryRaw() + "/app");
        add(OsEnvironment.getProductDirectoryRaw());
        add(OsEnvironment.getVendorDirectoryRaw());
        // Read-write
        Context context = AppManager.getContext();
        add(Objects.requireNonNull(context.getFilesDir().getParentFile()).getAbsolutePath());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            add(context.createDeviceProtectedStorageContext().getDataDir().getAbsolutePath());
        }
        File[] extDirs = context.getExternalCacheDirs();
        if (extDirs != null) {
            for (File dir : extDirs) {
                add(Objects.requireNonNull(dir.getParentFile()).getAbsolutePath());
            }
        }
        if (PermissionUtils.hasStoragePermission(context)) {
            // FIXME: 7/5/22 From A11, no access to the /sdcard/Android directory
            add("/sdcard");
            add("/storage/emulated/" + UserHandleHidden.myUserId());
        }
    }};

    @NonNull
    public static Path getPrimaryPath(@NonNull Context context, @Nullable String path) {
        return new Path(context, new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority("com.android.externalstorage.documents")
                .path("/tree/primary:" + (path == null ? "" : path))
                .build());
    }

    private static Uri uriWithAppendedPath(@NonNull Uri basePath, @NonNull String[] children) {
        for (String child : children) {
            basePath = Uri.withAppendedPath(basePath, child);
        }
        return basePath;
    }

    private static boolean needPrivilegedAccess(@NonNull String path) {
        for (String p : EXCLUSIVE_ACCESS_PATHS) {
            // FIXME: 7/5/22 Check exclusively for directory
            if (path.startsWith(p)) {
                // Need no privileged access
                return false;
            }
        }
        return true;
    }

    @NonNull
    private static DocumentFile getRequiredRawDocument(@NonNull String path) {
        if (needPrivilegedAccess(path)) {
            try {
                FileSystemManager fs = LocalServices.getFileSystemManager();
                return new ExtendedRawDocumentFile(fs.getFile(path));
            } catch (RemoteException e) {
                Log.w(TAG, "Could not get privileged access to path " + path, e);
                // Fall-back to unprivileged access
            }
        }
        return new ExtendedRawDocumentFile(FileSystemManager.getLocal().getFile(path));
    }

    // An invalid MIME so that it doesn't match any extension
    private static final String DEFAULT_MIME = "application/x-invalid-mime-type";

    @NonNull
    private final Context mContext;
    @NonNull
    private DocumentFile mDocumentFile;

    public Path(@NonNull Context context, @NonNull String fileLocation) {
        mContext = context;
        mDocumentFile = getRequiredRawDocument(fileLocation);
    }

    public Path(@NonNull Context context, @NonNull File fileLocation) {
        mContext = context;
        mDocumentFile = getRequiredRawDocument(fileLocation.getAbsolutePath());
    }

    public Path(@NonNull Context context, @NonNull String fileLocation, boolean privileged) throws RemoteException {
        mContext = context;
        if (privileged) {
            FileSystemManager fs = LocalServices.getFileSystemManager();
            mDocumentFile = new ExtendedRawDocumentFile(fs.getFile(fileLocation));
        } else {
            mDocumentFile = new ExtendedRawDocumentFile(FileSystemManager.getLocal().getFile(fileLocation));
        }
    }

    public Path(@NonNull Context context, int vfsId, @NonNull ZipFile zipFile, @Nullable String path) {
        mContext = context;
        mDocumentFile = new ZipDocumentFile(getParentFile(context, vfsId), vfsId, zipFile, path);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public Path(@NonNull Context context, int vfsId, @NonNull DexClasses dexClasses, @Nullable String path) {
        mContext = context;
        mDocumentFile = new DexDocumentFile(getParentFile(context, vfsId), vfsId, dexClasses, path);
    }

    public Path(@NonNull Context context, @NonNull DocumentFile documentFile) {
        mContext = context;
        mDocumentFile = documentFile;
    }

    public Path(@NonNull Context context, @NonNull Uri uri) {
        mContext = context;
        Path fsRoot = VirtualFileSystem.getFsRoot(uri);
        if (fsRoot != null) {
            mDocumentFile = fsRoot.mDocumentFile;
            return;
        }
        DocumentFile documentFile;
        switch (uri.getScheme()) {
            case ContentResolver.SCHEME_CONTENT:
                boolean isTreeUri = uri.getPath().startsWith("/tree/");
                documentFile = Objects.requireNonNull(isTreeUri ? DocumentFile.fromTreeUri(context, uri) : DocumentFile.fromSingleUri(context, uri));
                break;
            case ContentResolver.SCHEME_FILE:
                documentFile = getRequiredRawDocument(uri.getPath());
                break;
            case VirtualDocumentFile.SCHEME: {
                Pair<Integer, String> parsedUri = VirtualDocumentFile.parseUri(uri);
                if (parsedUri != null) {
                    Path rootPath = VirtualFileSystem.getFsRoot(parsedUri.first);
                    if (rootPath != null) {
                        if (parsedUri.second == null || parsedUri.second.equals(File.separator)) {
                            documentFile = rootPath.mDocumentFile;
                        } else {
                            // Find file is acceptable here since the file always exists
                            documentFile = Objects.requireNonNull(rootPath.mDocumentFile.findFile(parsedUri.second));
                        }
                        break;
                    }
                }
            }
            default:
                throw new IllegalArgumentException("Unsupported uri " + uri);
        }
        // Setting mDocumentFile at the end ensures that it is never null
        mDocumentFile = documentFile;
    }

    public Path(@NonNull Context context, @NonNull UriPermission uriPermission) throws FileNotFoundException {
        mContext = context;
        mDocumentFile = Objects.requireNonNull(DocumentFile.fromTreeUri(context, uriPermission.getUri()));
    }

    public Path(@NonNull Path path, @NonNull String child) {
        this(path.mContext, Uri.withAppendedPath(path.getUri(), Uri.encode(child)));
    }

    public Path(@NonNull Path path, @NonNull String... children) {
        this(path.mContext, uriWithAppendedPath(path.getUri(), children));
    }

    /**
     * Return the last segment of this path.
     */
    @Nullable
    public String getName() {
        return mDocumentFile.getName();
    }

    /**
     * Return a URI for the underlying document represented by this file. This
     * can be used with other platform APIs to manipulate or share the
     * underlying content. {@link DocumentFile#isDocumentUri(Context, Uri)} can
     * be used to test if the returned Uri is backed by an
     * {@link android.provider.DocumentsProvider}.
     */
    @NonNull
    public Uri getUri() {
        return mDocumentFile.getUri();
    }

    /**
     * Return the underlying {@link ExtendedFile} if the path is backed by a real file,
     * {@code null} otherwise.
     */
    @Nullable
    public ExtendedFile getFile() {
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            return ((ExtendedRawDocumentFile) mDocumentFile).getFile();
        }
        return null;
    }

    /**
     * Same as {@link #getFile()} except it return a raw string.
     */
    @Nullable
    public String getFilePath() {
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            return mDocumentFile.getUri().getPath();
        }
        return null;
    }

    /**
     * Same as {@link #getFile()} except it returns the real path if the
     * current path is a symbolic link.
     */
    @Nullable
    public String getRealFilePath() throws IOException {
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).getCanonicalPath();
        }
        return null;
    }

    /**
     * Return the MIME type of the path
     */
    @Nullable
    public String getType() {
        return mDocumentFile.getType();
    }

    /**
     * Returns the length of this path in bytes. Returns 0 if the path does not
     * exist, or if the length is unknown. The result for a directory is not
     * defined.
     */
    @CheckResult
    public long length() {
        return mDocumentFile.length();
    }

    /**
     * Recreate this path if it denotes a file.
     *
     * @return {@code true} iff the path has been recreated.
     */
    @CheckResult
    public boolean recreate() {
        if (isDirectory() || isMountPoint()) return false;
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            try {
                File f = Objects.requireNonNull(getFile());
                if (f.exists()) f.delete();
                return f.createNewFile();
            } catch (IOException | SecurityException e) {
                return false;
            }
        }
        try (OutputStream os = mContext.getContentResolver().openOutputStream(mDocumentFile.getUri())) {
            return os != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Create a new file as a direct child of this directory. If the file
     * already exists, and it is not a directory, it will try to delete it
     * and create a new one.
     *
     * @param displayName Display name for the file with or without extension.
     *                    The name must not contain any file separator.
     * @param mimeType    Mime type for the new file. Underlying provider may
     *                    choose to add extension based on the mime type. If
     *                    displayName contains an extension, set it to null.
     * @return The newly created file.
     * @throws IOException              If the target is a mount point, a directory, or the current file is not a
     *                                  directory, or failed for any other reason.
     * @throws IllegalArgumentException If the display name contains file separator.
     */
    @NonNull
    public Path createNewFile(@NonNull String displayName, @Nullable String mimeType) throws IOException {
        return createFileAsDirectChild(mContext, mDocumentFile, FileUtils.getSanitizedPath(displayName), mimeType);
    }

    /**
     * Create a new directory as a direct child of this directory.
     *
     * @param displayName Display name for the directory. The name must not
     *                    contain any file separator.
     * @return The newly created directory.
     * @throws IOException              If the target is a mount point or the current file is not a directory,
     *                                  or failed for any other reason.
     * @throws IllegalArgumentException If the display name contains file separator.
     */
    @NonNull
    public Path createNewDirectory(@NonNull String displayName) throws IOException {
        displayName = FileUtils.getSanitizedPath(displayName);
        if (displayName.indexOf(File.separatorChar) != -1) {
            throw new IllegalArgumentException("Display name contains file separator.");
        }
        if (!isDirectory()) {
            throw new IOException("Current file is not a directory.");
        }
        checkVfs(FileUtils.addSegmentAtEnd(getUri(), displayName));
        // TODO: 17/10/21 Handle already existing file/directory
        DocumentFile file = mDocumentFile.createDirectory(displayName);
        if (file == null) throw new IOException("Could not create directory named " + displayName);
        return new Path(mContext, file);
    }

    /**
     * Create a new file at some arbitrary level under this directory,
     * non-existing paths are created if necessary. If the file already exists,
     * and it isn't a directory, it will try to delete it and create a new one.
     * If mount points encountered while iterating through the paths, it will
     * try to create a new file under the last mount point.
     *
     * @param displayName Display name for the file with or without extension
     *                    and/or file separator.
     * @param mimeType    Mime type for the new file. Underlying provider may
     *                    choose to add extension based on the mime type. If
     *                    displayName contains an extension, set it to null.
     * @return The newly created file.
     * @throws IOException              If the target is a mount point, a directory or failed for any other reason.
     * @throws IllegalArgumentException If the display name is malformed.
     */
    @NonNull
    public Path createNewArbitraryFile(@NonNull String displayName, @Nullable String mimeType) throws IOException {
        displayName = FileUtils.getSanitizedPath(displayName);
        String[] names = displayName.split(File.separator);
        if (names.length < 1) {
            throw new IllegalArgumentException("Display name is empty");
        }
        DocumentFile file = createArbitraryDirectories(mDocumentFile, names, names.length - 1);
        return createFileAsDirectChild(mContext, file, names[names.length - 1], mimeType);
    }

    /**
     * Create all the non-existing directories under this directory. If mount
     * points encountered while iterating through the paths, it will try to
     * create a new directory under the last mount point.
     *
     * @param displayName Relative path to the target directory.
     * @return The newly created directory.
     * @throws IOException If the target is a mount point or failed for any other reason.
     */
    @NonNull
    public Path createDirectories(@NonNull String displayName) throws IOException {
        displayName = FileUtils.getSanitizedPath(displayName);
        String[] dirNames = displayName.split(File.separator);
        DocumentFile file = createArbitraryDirectories(mDocumentFile, dirNames, dirNames.length);
        return new Path(mContext, file);
    }

    /**
     * Delete this file. If this is a directory, it is deleted recursively.
     *
     * @return {@code true} if the file was deleted, {@code false} if the file
     * is a mount point or any other error occurred.
     */
    public boolean delete() {
        if (isMountPoint()) {
            return false;
        }
        mDocumentFile.delete();
        return !exists();
    }

    /**
     * Return the parent file of this document. If this is a mount point,
     * the parent is the parent of the mount point. For tree-documents,
     * the consistency of the parent file isn't guaranteed as the underlying
     * directory tree might be altered by another application.
     */
    @Nullable
    public Path getParentFile() {
        DocumentFile file = mDocumentFile.getParentFile();
        return file == null ? null : new Path(mContext, file);
    }

    /**
     * Whether this file has a file denoted by this abstract name. The file
     * isn't necessarily have to be a direct child of this file.
     *
     * @param displayName Display name for the file with extension and/or
     *                    file separator if applicable.
     * @return {@code true} if the file denoted by this abstract name exists.
     */
    public boolean hasFile(@NonNull String displayName) {
        return findFileInternal(this, displayName) != null;
    }

    /**
     * Return the file denoted by this abstract name in this file. File name
     * can be either case-sensitive or case-insensitive depending on the file
     * provider.
     *
     * @param displayName Display name for the file with extension and/or
     *                    file separator if applicable.
     * @return The first file that matches the name.
     * @throws FileNotFoundException If the file was not found.
     */
    @NonNull
    public Path findFile(@NonNull String displayName) throws FileNotFoundException {
        Path nextPath = findFileInternal(this, displayName);
        if (nextPath == null) throw new FileNotFoundException("Cannot find " + this + File.separatorChar + displayName);
        return nextPath;
    }

    /**
     * Return a file that is a direct child of this directory, creating if necessary.
     *
     * @param displayName Display name for the file with or without extension.
     *                    The name must not contain any file separator.
     * @param mimeType    Mime type for the new file. Underlying provider may
     *                    choose to add extension based on the mime type. If
     *                    displayName contains an extension, set it to null.
     * @return The existing or newly created file.
     * @throws IOException              If the target is a mount point, a directory, or the current file is not a
     *                                  directory, or failed for any other reason.
     * @throws IllegalArgumentException If the display name contains file separator.
     */
    @NonNull
    public Path findOrCreateFile(@NonNull String displayName, @Nullable String mimeType) throws IOException {
        displayName = FileUtils.getSanitizedPath(displayName);
        if (displayName.indexOf(File.separatorChar) != -1) {
            throw new IllegalArgumentException("Display name contains file separator.");
        }
        if (!isDirectory()) {
            throw new IOException("Current file is not a directory.");
        }
        String extension = null;
        if (mimeType != null) {
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        } else mimeType = DEFAULT_MIME;
        checkVfs(FileUtils.addSegmentAtEnd(mDocumentFile.getUri(), displayName + (extension != null ? "." + extension : "")));
        DocumentFile file = mDocumentFile.findFile(displayName);
        if (file != null) {
            if (file.isDirectory()) {
                throw new IOException("Directory cannot be converted to file");
            }
            return new Path(mContext, file);
        }
        file = mDocumentFile.createFile(mimeType, displayName);
        if (file == null) {
            throw new IOException("Could not create " + mDocumentFile.getUri() + File.separatorChar + displayName + " with type " + mimeType);
        }
        return new Path(mContext, file);
    }

    /**
     * Return a directory that is a direct child of this directory, creating
     * if necessary.
     *
     * @param displayName Display name for the directory. The name must not
     *                    contain any file separator.
     * @return The existing or newly created directory or mount point.
     * @throws IOException              If the target directory could not be created, or the existing or the
     *                                  current file is not a directory.
     * @throws IllegalArgumentException If the display name contains file
     *                                  separator.
     */
    @NonNull
    public Path findOrCreateDirectory(@NonNull String displayName) throws IOException {
        displayName = FileUtils.getSanitizedPath(displayName);
        if (displayName.indexOf(File.separatorChar) != -1) {
            throw new IllegalArgumentException("Display name contains file separator.");
        }
        if (!isDirectory()) {
            throw new IOException("Current file is not a directory.");
        }
        Path fsRoot = VirtualFileSystem.getFsRoot(FileUtils.addSegmentAtEnd(getUri(), displayName));
        if (fsRoot != null) return fsRoot;
        DocumentFile file = mDocumentFile.findFile(displayName);
        if (file != null) {
            if (!file.isDirectory()) {
                throw new IOException("Existing file is not a directory");
            }
            return new Path(mContext, file);
        }
        file = mDocumentFile.createDirectory(displayName);
        if (file == null) throw new IOException("Could not create directory named " + displayName);
        return new Path(mContext, file);
    }

    /**
     * Whether this file can be found. This is useful only for the paths
     * accessed using Java File API. In other cases, the file has to exist
     * before it can be accessed. However, in SAF, the file can be deleted
     * by another application in which case the URI becomes non-existent.
     *
     * @return {@code true} if the file exists.
     */
    @CheckResult
    public boolean exists() {
        return mDocumentFile.exists();
    }

    /**
     * Whether this file is a directory. A mount point is also considered as a
     * directory.
     * <p>
     * Note that the return value {@code false} does not necessarily mean that
     * the path is a file.
     *
     * @return {@code true} if the file is a directory or a mount point.
     */
    @CheckResult
    public boolean isDirectory() {
        return mDocumentFile.isDirectory() || isMountPoint();
    }

    /**
     * Whether this file is a file.
     * <p>
     * Note that the return value {@code false} does not necessarily mean that
     * the path is a directory.
     *
     * @return {@code true} if the file is a file.
     */
    @CheckResult
    public boolean isFile() {
        return mDocumentFile.isFile() && !isMountPoint();
    }

    /**
     * Whether the file is a virtual file i.e. it has no physical existence.
     *
     * @return {@code true} if this is a virtual file.
     */
    @CheckResult
    public boolean isVirtual() {
        return mDocumentFile.isVirtual();
    }

    /**
     * Whether the file is a symbolic link, only applicable for Java File API.
     *
     * @return {@code true} iff the file is accessed using Java File API and
     * is a symbolic link.
     */
    public boolean isSymbolicLink() {
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).isSymlink();
        }
        return false;
    }

    /**
     * Whether the file can be read.
     *
     * @return {@code true} if it can be read.
     */
    public boolean canRead() {
        return mDocumentFile.canRead();
    }

    /**
     * Whether the file can be written.
     *
     * @return {@code true} if it can be written.
     */
    public boolean canWrite() {
        return mDocumentFile.canWrite();
    }

    /**
     * Whether the file can be executed.
     *
     * @return {@code true} if it can be executed.
     */
    public boolean canExecute() {
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).canExecute();
        }
        return false;
    }

    /**
     * Whether the file is a mount point, thereby, is being overridden by another file system.
     *
     * @return {@code true} if this is a mount point.
     */
    public boolean isMountPoint() {
        return VirtualFileSystem.getFileSystem(getUri()) != null;
    }

    public boolean mkdir() {
        if (exists() || isMountPoint()) return true;
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).mkdir();
        } else {
            DocumentFile parent = mDocumentFile.getParentFile();
            if (parent != null) {
                DocumentFile thisFile = parent.createDirectory(getName());
                if (thisFile != null) {
                    mDocumentFile = thisFile;
                    return true;
                }
            }
        }
        return false;
    }

    public boolean mkdirs() {
        if (exists() || isMountPoint()) return true;
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).mkdirs();
        }
        // For others, directory can't be created recursively as parent must exist
        return mkdir();
    }

    public boolean renameTo(@NonNull String displayName) {
        // TODO: 16/10/21 Change mount point too
        displayName = FileUtils.getSanitizedPath(displayName);
        if (displayName.contains(File.separator)) {
            // display name must belong to the same directory.
            return false;
        }
        return mDocumentFile.renameTo(displayName);
    }

    /**
     * Move the given path based on the following criteria:
     * <ol>
     *     <li>If both paths are physical (i.e. uses File API), use normal move behaviour
     *     <li>If one of the paths are virtual, use special copy and delete operation
     * </ol>
     * <p>
     * Move behavior is as follows:
     * <ul>
     *     <li>If both are files or directories, rename applies (destination file is cleared if exists)
     *     <li>If target is a directory, move the file inside the directory
     *     <li>If the target is a file, exception occurs
     * </ul>
     *
     * @param path Target file/directory.
     * @return {@code true} on success and {@code false} on failure
     */
    public boolean moveTo(@NonNull Path path) {
        // TODO: 16/10/21 Find some way to handle move in VFS
        if (path.exists() && !path.canWrite()) {
            // There's no point is attempting to move if the destination is read-only
            return false;
        }
        if (mDocumentFile instanceof ExtendedRawDocumentFile && path.mDocumentFile instanceof ExtendedRawDocumentFile) {
            // Try using the default option
            File src = Objects.requireNonNull(getFile());
            File dst = Objects.requireNonNull(path.getFile());
            if (src.renameTo(dst)) {
                mDocumentFile = path.mDocumentFile;
                return true;
            }
        }
        Path srcParent = getParentFile();
        Path dstParent = path.getParentFile();
        if (!path.isDirectory() && srcParent != null && srcParent.equals(dstParent)) {
            // If both path are located in the same directory, rename them
            return renameTo(path.getName());
        }
        if (isDirectory()) {
            if (path.isDirectory()) { // Rename (copy and delete original)
                // Make sure that parent exists, and it is a directory
                if (dstParent == null || !dstParent.isDirectory()) return false;
                try {
                    // Recreate path
                    path.delete();
                    Path newPath = dstParent.createNewDirectory(path.getName());
                    // Copy all the directory items to the path
                    copyDirectory(this, newPath);
                    delete();
                    mDocumentFile = newPath.mDocumentFile;
                    return true;
                } catch (IOException e) {
                    return false;
                }
            } else {
                // Current path is a directory but target is a file
                return false;
            }
        } else {
            Path newPath;
            if (path.isDirectory()) {
                // Move the file inside the directory
                try {
                    newPath = path.createNewFile(getName(), null);
                } catch (IOException e) {
                    return false;
                }
            } else {
                // Rename the file
                newPath = path;
            }
            try {
                copyFile(this, newPath);
                delete();
                mDocumentFile = newPath.mDocumentFile;
                return true;
            } catch (IOException e) {
                return false;
            }
        }
    }

    private static void copyFile(@NonNull Path src, @NonNull Path dst) throws IOException {
        if (src.isMountPoint() || dst.isMountPoint()) {
            throw new IOException("Either source or destination are a mount point.");
        }
        FileUtils.copy(src, dst);
    }

    // Copy directory content
    private static void copyDirectory(@NonNull Path src, @NonNull Path dst) throws IOException {
        // TODO: 16/10/21 Find some way to handle VFS
        if (!src.isDirectory() || !dst.isDirectory()) {
            throw new IOException("Both source and destination have to be directory.");
        }
        for (Path file : src.listFiles()) {
            if (file.isDirectory()) {
                copyDirectory(file, dst.createNewDirectory(file.getName()));
            } else {
                Path newFile = dst.createNewFile(file.getName(), null);
                copyFile(file, newFile);
            }
        }
    }

    public long lastModified() {
        return mDocumentFile.lastModified();
    }

    public void setLastModified(long time) {
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            Objects.requireNonNull(getFile()).setLastModified(time);
        }
    }

    @NonNull
    public Path[] listFiles() {
        VirtualFileSystem.FileSystem[] fileSystems = VirtualFileSystem.getFileSystemsAtUri(getUri());
        HashMap<String, Path> namePathMap = new HashMap<>(fileSystems.length);
        for (VirtualFileSystem.FileSystem fs : fileSystems) {
            namePathMap.put(fs.getMountPoint().getLastPathSegment(), fs.getRootPath());
        }
        DocumentFile[] ss = mDocumentFile.listFiles();
        List<Path> paths = new ArrayList<>(ss.length + fileSystems.length);
        for (DocumentFile s : ss) {
            Path p = namePathMap.get(s.getName());
            if (p != null) {
                // Mount points have higher priority
                paths.add(p);
                namePathMap.remove(s.getName());
            } else {
                paths.add(new Path(mContext, s));
            }
        }
        // Add rests
        paths.addAll(namePathMap.values());
        return paths.toArray(new Path[0]);
    }

    @NonNull
    public Path[] listFiles(@Nullable FileFilter filter) {
        Path[] ss = listFiles();
        ArrayList<Path> files = new ArrayList<>();
        for (Path s : ss) {
            if ((filter == null) || filter.accept(s)) {
                files.add(s);
            }
        }
        return files.toArray(new Path[0]);
    }

    @NonNull
    public Path[] listFiles(@Nullable FilenameFilter filter) {
        Path[] ss = listFiles();
        ArrayList<Path> files = new ArrayList<>();
        for (Path s : ss) {
            if (s.getName() != null && (filter == null || filter.accept(this, s.getName()))) {
                files.add(s);
            }
        }
        return files.toArray(new Path[0]);
    }

    @NonNull
    public String[] listFileNames() {
        Path[] ss = listFiles();
        ArrayList<String> files = new ArrayList<>();
        for (Path s : ss) {
            if (s.getName() != null) {
                files.add(s.getName());
            }
        }
        return files.toArray(new String[0]);
    }

    @NonNull
    public String[] listFileNames(@Nullable FileFilter filter) {
        Path[] ss = listFiles();
        ArrayList<String> files = new ArrayList<>();
        for (Path s : ss) {
            if (s.getName() != null && (filter == null || filter.accept(s))) {
                files.add(s.getName());
            }
        }
        return files.toArray(new String[0]);
    }

    @NonNull
    public String[] listFileNames(@Nullable FilenameFilter filter) {
        Path[] ss = listFiles();
        ArrayList<String> files = new ArrayList<>();
        for (Path s : ss) {
            if (s.getName() != null && (filter == null || filter.accept(s, s.getName()))) {
                files.add(s.getName());
            }
        }
        return files.toArray(new String[0]);
    }

    @Nullable
    public ParcelFileDescriptor openFileDescriptor(@NonNull String mode, @NonNull HandlerThread callbackThread)
            throws FileNotFoundException {
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
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
        } else if (mDocumentFile instanceof VirtualDocumentFile) {
            if (!mDocumentFile.isFile()) return null;
            int modeBits = ParcelFileDescriptor.parseMode(mode);
            if ((modeBits & ParcelFileDescriptor.MODE_READ_ONLY) == 0) {
                throw new FileNotFoundException("Read-only file");
            }
            try {
                return StorageManagerCompat.openProxyFileDescriptor(modeBits, new VirtualStorageCallback(
                        (VirtualDocumentFile<?>) mDocumentFile, callbackThread));
            } catch (IOException e) {
                throw (FileNotFoundException) new FileNotFoundException(e.getMessage()).initCause(e);
            }
        }
        return mContext.getContentResolver().openFileDescriptor(mDocumentFile.getUri(), mode);
    }

    public OutputStream openOutputStream() throws IOException {
        return openOutputStream(false);
    }

    @NonNull
    public OutputStream openOutputStream(boolean append) throws IOException {
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).newOutputStream(append);
        } else if (mDocumentFile instanceof VirtualDocumentFile) {
            // For now, none of the virtual document files support writing
            throw new IOException("VFS does not yet support writing.");
        }
        String mode = "w" + (append ? "a" : "t");
        OutputStream os = mContext.getContentResolver().openOutputStream(mDocumentFile.getUri(), mode);
        if (os == null) {
            throw new IOException("Could not resolve Uri: " + mDocumentFile.getUri());
        }
        return os;
    }

    @NonNull
    public InputStream openInputStream() throws IOException {
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).newInputStream();
        } else if (mDocumentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile<?>) mDocumentFile).openInputStream();
        }
        InputStream is = mContext.getContentResolver().openInputStream(mDocumentFile.getUri());
        if (is == null) {
            throw new IOException("Could not resolve Uri: " + mDocumentFile.getUri());
        }
        return is;
    }

    @NonNull
    @Override
    public String toString() {
        return getUri().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Path)) return false;
        Path path = (Path) o;
        return mDocumentFile.getUri().equals(path.mDocumentFile.getUri());
    }

    @Override
    public int hashCode() {
        return mDocumentFile.getUri().hashCode();
    }

    @Override
    public int compareTo(@NonNull Path o) {
        return mDocumentFile.getUri().compareTo(o.mDocumentFile.getUri());
    }

    @FunctionalInterface
    public interface FilenameFilter {
        /**
         * Tests if a specified file should be included in a file list.
         *
         * @param dir  the directory in which the file was found.
         * @param name the name of the file.
         * @return <code>true</code> if and only if the name should be
         * included in the file list; <code>false</code> otherwise.
         */
        boolean accept(Path dir, String name);
    }

    @FunctionalInterface
    public interface FileFilter {

        /**
         * Tests whether or not the specified abstract pathname should be
         * included in a pathname list.
         *
         * @param pathname The abstract pathname to be tested
         * @return <code>true</code> if and only if <code>pathname</code>
         * should be included
         */
        boolean accept(Path pathname);
    }

    @NonNull
    private static Path createFileAsDirectChild(@NonNull Context context,
                                                @NonNull DocumentFile documentFile,
                                                @NonNull String displayName,
                                                @Nullable String mimeType) throws IOException {
        if (displayName.indexOf(File.separatorChar) != -1) {
            throw new IllegalArgumentException("Display name contains file separator.");
        }
        if (!documentFile.isDirectory()) {
            throw new IOException("Current file is not a directory.");
        }
        String extension = null;
        if (mimeType != null) {
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        } else mimeType = DEFAULT_MIME;
        checkVfs(FileUtils.addSegmentAtEnd(documentFile.getUri(), displayName + (extension != null ? "." + extension : "")));
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
            throw new IOException("Could not create " + documentFile.getUri() + File.separatorChar + displayName + " with type " + mimeType);
        }
        return new Path(context, file);
    }

    @Nullable
    private static Path findFileInternal(@NonNull Path path, @NonNull String dirtyDisplayName) {
        String[] parts = FileUtils.getSanitizedPath(dirtyDisplayName).split(File.separator);
        DocumentFile documentFile = path.mDocumentFile;
        for (String part : parts) {
            // Check for mount point
            Uri newUri = FileUtils.addSegmentAtEnd(documentFile.getUri(), part);
            Path fsRoot = VirtualFileSystem.getFsRoot(newUri);
            if (fsRoot != null) {
                // Mount point has the higher priority
                documentFile = fsRoot.mDocumentFile;
            } else documentFile = documentFile.findFile(part);
            if (documentFile == null) return null;
        }
        return new Path(path.mContext, documentFile);
    }

    @Nullable
    private static DocumentFile getParentFile(Context context, int vfsId) {
        Uri mountPoint = VirtualFileSystem.getMountPoint(vfsId);
        DocumentFile parentFile = null;
        if (mountPoint != null) {
            Uri parentUri = FileUtils.removeLastPathSegment(mountPoint);
            parentFile = new Path(context, parentUri).mDocumentFile;
        }
        return parentFile;
    }

    @NonNull
    private static DocumentFile createArbitraryDirectories(@NonNull DocumentFile documentFile,
                                                           @NonNull String[] names,
                                                           int length) throws IOException {
        DocumentFile file = documentFile;
        for (int i = 0; i < length; ++i) {
            Path fsRoot = VirtualFileSystem.getFsRoot(FileUtils.addSegmentAtEnd(file.getUri(), names[i]));
            DocumentFile t = fsRoot != null ? fsRoot.mDocumentFile : file.findFile(names[i]);
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

    private static void checkVfs(Uri uri) throws IOException {
        if (VirtualFileSystem.getFileSystem(uri) != null) {
            throw new IOException("Destination is a mount point.");
        }
    }

    private static class VirtualStorageCallback extends StorageManagerCompat.ProxyFileDescriptorCallbackCompat {
        private final InputStream mIs;
        private boolean mClosed;

        public VirtualStorageCallback(VirtualDocumentFile<?> document, HandlerThread callbackThread) throws IOException {
            super(callbackThread);
            mIs = document.openInputStream();
            // FIXME: 9/5/22 We really cannot use an InputStream because we need to support seeking. For now, we will
            //  skip the offset since the streams are fetched sequentially.
        }

        @Override
        public long onGetSize() throws ErrnoException {
            return -1; // Not a real file
        }

        @Override
        public int onRead(long offset, int size, byte[] data) throws ErrnoException {
            try {
                return mIs.read(data, 0, size);
            } catch (IOException e) {
                throw new ErrnoException(e.getMessage(), OsConstants.EBADF);
            }
        }

        @Override
        protected void onRelease() {
            try {
                mIs.close();
                mClosed = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected void finalize() throws Throwable {
            if (!mClosed) {
                mIs.close();
            }
        }
    }

    private static class ProxyStorageCallback extends StorageManagerCompat.ProxyFileDescriptorCallbackCompat {
        private final FileChannel mChannel;

        private ProxyStorageCallback(String path, int modeBits, HandlerThread thread) throws IOException {
            super(thread);
            try {
                FileSystemManager fs = LocalServices.getFileSystemManager();
                mChannel = fs.openChannel(path, modeBits);
            } catch (RemoteException e) {
                thread.quitSafely();
                throw new IOException(e);
            } catch (IOException e) {
                thread.quitSafely();
                throw e;
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
