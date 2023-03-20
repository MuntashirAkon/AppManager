// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.documentfile.provider.DocumentFile;
import androidx.documentfile.provider.ExtendedRawDocumentFile;
import androidx.documentfile.provider.VirtualDocumentFile;

import org.jetbrains.annotations.Contract;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import aosp.libcore.util.EmptyArray;
import io.github.muntashirakon.AppManager.compat.StorageManagerCompat;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;
import io.github.muntashirakon.io.fs.VirtualFileSystem;

/**
 * Provide an interface to {@link File} and {@link DocumentFile} with basic functionalities.
 */
public class Path implements Comparable<Path> {
    public static final String TAG = Path.class.getSimpleName();

    private static final List<Boolean> EXCLUSIVE_ACCESS_GRANTED = new ArrayList<>();
    private static final List<String> EXCLUSIVE_ACCESS_PATHS = new ArrayList<>();

    static {
        setAccessPaths();
    }

    private static void setAccessPaths() {
        if (Process.myUid() == 0 || Process.myUid() == 2000) {
            // Root/ADB
            return;
        }
        // We cannot use Path API here
        // Read-only
        EXCLUSIVE_ACCESS_PATHS.add(Environment.getRootDirectory().getAbsolutePath());
        EXCLUSIVE_ACCESS_GRANTED.add(true);
        EXCLUSIVE_ACCESS_PATHS.add(OsEnvironment.getDataDirectoryRaw() + "/app");
        EXCLUSIVE_ACCESS_GRANTED.add(true);
        EXCLUSIVE_ACCESS_PATHS.add(OsEnvironment.getProductDirectoryRaw());
        EXCLUSIVE_ACCESS_GRANTED.add(true);
        EXCLUSIVE_ACCESS_PATHS.add(OsEnvironment.getVendorDirectoryRaw());
        EXCLUSIVE_ACCESS_GRANTED.add(true);
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
        if (PermissionUtils.hasStoragePermission()) {
            int userId = UserHandleHidden.myUserId();
            String[] cards;
            if (userId == 0) {
                cards = new String[]{
                        "/sdcard",
                        "/storage/emulated/" + userId
                };
            } else cards = new String[]{"/storage/emulated/" + userId};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Add Android/data and Android/obb to the exemption list
                boolean canInstallApps = PermissionUtils.hasSelfPermission(Manifest.permission.REQUEST_INSTALL_PACKAGES);
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
        if (Process.myUid() == 0 || Process.myUid() == 2000) {
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
        if (needPrivilegedAccess(path)) {
            try {
                FileSystemManager fs = LocalServices.getFileSystemManager();
                return new ExtendedRawDocumentFile(fs.getFile(path));
            } catch (RemoteException e) {
                Log.w(TAG, "Could not get privileged access to path " + path + " due to \"" + e.getMessage() + "\"");
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

    /* package */ Path(@NonNull Context context, @NonNull String fileLocation) {
        mContext = context;
        mDocumentFile = getRequiredRawDocument(fileLocation);
    }

    /* package */ Path(@NonNull Context context, @NonNull VirtualFileSystem fs) {
        mContext = context;
        mDocumentFile = new VirtualDocumentFile(getParentFile(context, fs), fs);
    }

    /* package */ Path(@NonNull Context context, @NonNull String fileLocation, boolean privileged) throws RemoteException {
        mContext = context;
        if (privileged) {
            FileSystemManager fs = LocalServices.getFileSystemManager();
            mDocumentFile = new ExtendedRawDocumentFile(fs.getFile(fileLocation));
        } else {
            mDocumentFile = new ExtendedRawDocumentFile(FileSystemManager.getLocal().getFile(fileLocation));
        }
    }

    /* package */ Path(@NonNull Context context, @NonNull Uri uri) {
        mContext = context;
        // At first check if the Uri is in VFS since it gets higher priority.
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
            case VirtualFileSystem.SCHEME: {
                Pair<Integer, String> parsedUri = VirtualDocumentFile.parseUri(uri);
                if (parsedUri != null) {
                    Path rootPath = VirtualFileSystem.getFsRoot(parsedUri.first);
                    if (rootPath != null) {
                        String path = Paths.getSanitizedPath(parsedUri.second, true);
                        if (TextUtilsCompat.isEmpty(path) || path.equals(File.separator)) {
                            // Root requested
                            documentFile = rootPath.mDocumentFile;
                        } else {
                            // Find file is acceptable here since the file always exists
                            String[] pathComponents = path.split(File.separator);
                            DocumentFile finalDocumentFile = rootPath.mDocumentFile;
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
        mDocumentFile = documentFile;
    }

    private Path(@NonNull Context context, @NonNull DocumentFile documentFile) {
        mContext = context;
        mDocumentFile = documentFile;
    }

    /**
     * Return the last segment of this path.
     */
    @NonNull
    public String getName() {
        // Last path segment is required.
        return Objects.requireNonNull(mDocumentFile.getName());
    }

    @Nullable
    public String getExtension() {
        String name = getName();
        int lastIndexOfDot = name.lastIndexOf('.');
        if (lastIndexOfDot == -1 || lastIndexOfDot + 1 == name.length()) {
            return null;
        }
        return name.substring(lastIndexOfDot + 1).toLowerCase(Locale.ROOT);
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
    @NonNull
    public String getType() {
        String type = getRealDocumentFile(mDocumentFile).getType();
        if (type == null) {
            type = PathContentInfo.fromExtension(this).getMimeType();
        }
        if (type == null) {
            type = "application/octet-stream";
        }
        return type;
    }

    /**
     * Return the content info of the path.
     * <p>
     * This is an expensive operation and should be done in a non-UI thread.
     */
    @NonNull
    public PathContentInfo getPathContentInfo() {
        return PathContentInfo.fromPath(this);
    }

    /**
     * Returns the length of this path in bytes. Returns 0 if the path does not
     * exist, or if the length is unknown. The result for a directory is not
     * defined.
     */
    @CheckResult
    public long length() {
        return getRealDocumentFile(mDocumentFile).length();
    }

    /**
     * Recreate this path if required.
     * <p>
     * This only recreates files and not directories in order to avoid potential mass destructive operation.
     *
     * @return {@code true} iff the path has been recreated.
     */
    @CheckResult
    public boolean recreate() {
        DocumentFile documentFile = getRealDocumentFile(mDocumentFile);
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
        displayName = Paths.getSanitizedPath(displayName, true);
        if (displayName == null) {
            throw new IOException("Empty display name.");
        }
        return createFileAsDirectChild(mContext, mDocumentFile, displayName, mimeType);
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
        displayName = Paths.getSanitizedPath(displayName, true);
        if (displayName == null) {
            throw new IOException("Empty display name.");
        }
        if (displayName.indexOf(File.separatorChar) != -1) {
            throw new IllegalArgumentException("Display name contains file separator.");
        }
        DocumentFile documentFile = getRealDocumentFile(mDocumentFile);
        if (!documentFile.isDirectory()) {
            throw new IOException("Current file is not a directory.");
        }
        checkVfs(Paths.appendPathSegment(documentFile.getUri(), displayName));
        DocumentFile file = documentFile.createDirectory(displayName);
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
        displayName = Paths.getSanitizedPath(displayName, true);
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
     * @throws IOException If the target is a mount point, or failed for any other reason.
     */
    @NonNull
    public Path createDirectoriesIfRequired(@NonNull String displayName) throws IOException {
        displayName = Paths.getSanitizedPath(displayName, true);
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
        DocumentFile file = createArbitraryDirectories(mDocumentFile, dirNames, dirNames.length);
        return new Path(mContext, file);
    }

    /**
     * Create all the non-existing directories under this directory. If mount
     * points encountered while iterating through the paths, it will try to
     * create a new directory under the last mount point.
     *
     * @param displayName Relative path to the target directory.
     * @return The newly created directory.
     * @throws IOException If the target exists, or it is a mount point, or failed for any other reason.
     */
    @NonNull
    public Path createDirectories(@NonNull String displayName) throws IOException {
        displayName = Paths.getSanitizedPath(displayName, true);
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
        DocumentFile file = createArbitraryDirectories(mDocumentFile, dirNames, dirNames.length - 1);
        // Special case for the last segment
        String lastSegment = dirNames[dirNames.length - 1];
        Path fsRoot = VirtualFileSystem.getFsRoot(Paths.appendPathSegment(file.getUri(), lastSegment));
        DocumentFile t = fsRoot != null ? fsRoot.mDocumentFile : file.findFile(lastSegment);
        if (t != null) {
            throw new IOException(t.getUri() + " already exists.");
        }
        t = file.createDirectory(lastSegment);
        if (t == null) {
            throw new IOException("Directory" + file.getUri() + File.separator + lastSegment + " could not be created.");
        }
        return new Path(mContext, t);
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
        return mDocumentFile.delete();
    }

    /**
     * Return the parent file of this document. If this is a mount point,
     * the parent is the parent of the mount point. For tree-documents,
     * the consistency of the parent file isn't guaranteed as the underlying
     * directory tree might be altered by another application.
     */
    @Nullable
    public Path getParentFile() {
        DocumentFile file = getRealDocumentFile(mDocumentFile).getParentFile();
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
        return findFileInternal(mDocumentFile, displayName) != null;
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
        DocumentFile nextPath = findFileInternal(mDocumentFile, displayName);
        if (nextPath == null) {
            throw new FileNotFoundException("Cannot find " + this + File.separatorChar + displayName);
        }
        return new Path(mContext, nextPath);
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
        displayName = Paths.getSanitizedPath(displayName, true);
        if (displayName == null) {
            throw new IOException("Empty display name.");
        }
        if (displayName.indexOf(File.separatorChar) != -1) {
            throw new IllegalArgumentException("Display name contains file separator.");
        }
        DocumentFile documentFile = getRealDocumentFile(mDocumentFile);
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
            return new Path(mContext, file);
        }
        file = documentFile.createFile(mimeType, displayName);
        if (file == null) {
            throw new IOException("Could not create " + documentFile.getUri() + File.separatorChar + nameWithExtension + " with type " + mimeType);
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
        displayName = Paths.getSanitizedPath(displayName, true);
        if (displayName == null) {
            throw new IOException("Empty display name.");
        }
        if (displayName.indexOf(File.separatorChar) != -1) {
            throw new IllegalArgumentException("Display name contains file separator.");
        }
        DocumentFile documentFile = getRealDocumentFile(mDocumentFile);
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
            return new Path(mContext, file);
        }
        file = documentFile.createDirectory(displayName);
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
        return getRealDocumentFile(mDocumentFile).exists();
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
        return getRealDocumentFile(mDocumentFile).isDirectory();
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
        return getRealDocumentFile(mDocumentFile).isFile();
    }

    /**
     * Whether the file is a virtual file i.e. it has no physical existence.
     *
     * @return {@code true} if this is a virtual file.
     */
    @CheckResult
    public boolean isVirtual() {
        return getRealDocumentFile(mDocumentFile).isVirtual();
    }

    /**
     * Whether the file is a symbolic link, only applicable for Java File API.
     *
     * @return {@code true} iff the file is accessed using Java File API and
     * is a symbolic link.
     */
    public boolean isSymbolicLink() {
        if (getRealDocumentFile(mDocumentFile) instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).isSymlink();
        }
        return false;
    }

    /**
     * Creates a new symbolic link named by this abstract pathname to a target file if and only if the pathname is a
     * physical file and is not yet exist.
     *
     * @param target the target of the symbolic link.
     * @return {@code true} if target did not exist and the link was successfully created, and {@code false} otherwise.
     */
    public boolean createNewSymbolicLink(String target) {
        if (getRealDocumentFile(mDocumentFile) instanceof ExtendedRawDocumentFile) {
            try {
                return Objects.requireNonNull(getFile()).createNewSymlink(target);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * Whether the file can be read.
     *
     * @return {@code true} if it can be read.
     */
    public boolean canRead() {
        return getRealDocumentFile(mDocumentFile).canRead();
    }

    /**
     * Whether the file can be written.
     *
     * @return {@code true} if it can be written.
     */
    public boolean canWrite() {
        return getRealDocumentFile(mDocumentFile).canWrite();
    }

    /**
     * Whether the file can be executed.
     *
     * @return {@code true} if it can be executed.
     */
    public boolean canExecute() {
        if (getRealDocumentFile(mDocumentFile) instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).canExecute();
        }
        return false;
    }

    public int getMode() {
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            try {
                return Objects.requireNonNull(getFile()).getMode();
            } catch (ErrnoException e) {
                return 0;
            }
        }
        if (mDocumentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile) mDocumentFile).getMode();
        }
        return 0;
    }

    public boolean setMode(int mode) {
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            try {
                Objects.requireNonNull(getFile()).setMode(mode);
                return true;
            } catch (ErrnoException e) {
                return false;
            }
        }
        if (mDocumentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile) mDocumentFile).setMode(mode);
        }
        return false;
    }

    @Nullable
    public UidGidPair getUidGid() {
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            try {
                return Objects.requireNonNull(getFile()).getUidGid();
            } catch (ErrnoException e) {
                return null;
            }
        }
        if (mDocumentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile) mDocumentFile).getUidGid();
        }
        return null;
    }

    public boolean setUidGid(UidGidPair uidGidPair) {
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            try {
                return Objects.requireNonNull(getFile()).setUidGid(uidGidPair.uid, uidGidPair.gid);
            } catch (ErrnoException e) {
                return false;
            }
        }
        if (mDocumentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile) mDocumentFile).setUidGid(uidGidPair);
        }
        return false;
    }

    @Nullable
    public String getSelinuxContext() {
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).getSelinuxContext();
        }
        return null;
    }

    public boolean setSelinuxContext(@Nullable String context) {
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            if (context == null) {
                return Objects.requireNonNull(getFile()).restoreSelinuxContext();
            }
            return Objects.requireNonNull(getFile()).setSelinuxContext(context);
        }
        return false;
    }

    /**
     * Whether the file is a mount point, thereby, is being overridden by another file system.
     *
     * @return {@code true} if this is a mount point.
     */
    public boolean isMountPoint() {
        return VirtualFileSystem.isMountPoint(getUri());
    }

    public boolean mkdir() {
        DocumentFile documentFile = getRealDocumentFile(mDocumentFile);
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
                    mDocumentFile = thisFile;
                    return true;
                }
            }
        }
        return false;
    }

    public boolean mkdirs() {
        DocumentFile documentFile = getRealDocumentFile(mDocumentFile);
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
                mDocumentFile = thisFile;
                return true;
            }
        }
        return false;
    }

    /**
     * Renames this file to {@code displayName}, both containing in the same directory.
     * <p>
     * Note that this method does <i>not</i> throw {@code IOException} on
     * failure. Callers must check the return value.
     * <p>
     * Some providers may need to create a new document to reflect the rename,
     * potentially with a different MIME type, so {@link #getUri()} and
     * {@link #getType()} may change to reflect the rename.
     * <p>
     * When renaming a directory, children previously enumerated through
     * {@link #listFiles()} may no longer be valid.
     *
     * @param displayName the new display name.
     * @return {@code true} on success. It returns {@code false} if the displayName is invalid or if it already exists.
     * @throws UnsupportedOperationException when working with a single document
     */
    public boolean renameTo(@NonNull String displayName) {
        displayName = Paths.getSanitizedPath(displayName, true);
        if (displayName == null) {
            // Empty display name
            return false;
        }
        if (displayName.contains(File.separator)) {
            // Display name must belong to the same directory.
            return false;
        }
        DocumentFile parent = mDocumentFile.getParentFile();
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
        Uri oldMountPoint = mDocumentFile.getUri();
        if (mDocumentFile.renameTo(displayName)) {
            if (VirtualFileSystem.getFileSystem(oldMountPoint) != null) {
                // Change mount point
                VirtualFileSystem.alterMountPoint(oldMountPoint, mDocumentFile.getUri());
            }
            return true;
        }
        return false;
    }

    /**
     * Same as {@link #moveTo(Path, boolean)} with override enabled
     */
    public boolean moveTo(@NonNull Path dest) {
        return moveTo(dest, true);
    }

    /**
     * Move the given path based on the following criteria:
     * <ol>
     *     <li>If both paths are physical (i.e. uses File API), use normal move behaviour
     *     <li>If one of the paths is virtual or the above fails, use special copy and delete operation
     * </ol>
     * <p>
     * Move behavior is as follows:
     * <ul>
     *     <li>If both are directories, move {@code this} inside {@code path}
     *     <li>If both are files, move {@code this} to {@code path} overriding it
     *     <li>If {@code this} is a file and {@code path} is a directory, move the file inside the directory
     *     <li>If {@code path} does not exist, it is created based on {@code this}.
     * </ul>
     *
     * @param path     Target file/directory which may or may not exist
     * @param override Whether to override the files in the destination
     * @return {@code true} on success and {@code false} on failure
     */
    public boolean moveTo(@NonNull Path path, boolean override) {
        DocumentFile source = getRealDocumentFile(mDocumentFile);
        DocumentFile dest = getRealDocumentFile(path.mDocumentFile);
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
                mDocumentFile = getRequiredRawDocument(dstFile.getAbsolutePath());
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
                    copyDirectory(mContext, source, newPath);
                    source.delete();
                    mDocumentFile = newPath;
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
                    copyDirectory(mContext, source, newPath);
                    source.delete();
                    mDocumentFile = newPath;
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
                copyFile(mContext, source, newPath);
                source.delete();
                mDocumentFile = newPath;
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
        DocumentFile source = getRealDocumentFile(mDocumentFile);
        DocumentFile dest = getRealDocumentFile(path.mDocumentFile);
        if (!source.exists()) {
            // Source itself does not exist.
            return null;
        }
        if (dest.exists() && !dest.canWrite()) {
            // There's no point is attempting to copy if the destination is read-only.
            return null;
        }
        if (dest.getUri().toString().startsWith(source.getUri().toString())) {
            // Destination cannot be the same or a subdirectory of source
            return null;
        }
        DocumentFile destParent = dest.getParentFile();
        if (source.isDirectory()) { // Source is a directory
            if (dest.isDirectory()) {
                // Destination is a directory: Apply copy source inside the dest
                DocumentFile newPath = dest.createDirectory(Objects.requireNonNull(source.getName()));
                if (newPath == null) {
                    // Couldn't create directory
                    return null;
                }
                try {
                    // Copy all the directory items to the new path
                    copyDirectory(mContext, source, newPath, override);
                    return new Path(mContext, newPath);
                } catch (IOException e) {
                    return null;
                }
            }
            if (!dest.exists()) {
                // Destination does not exist, simply create and copy
                // Make sure that parent exists, and it is a directory
                if (destParent == null || !destParent.isDirectory()) {
                    return null;
                }
                DocumentFile newPath = destParent.createDirectory(Objects.requireNonNull(dest.getName()));
                if (newPath == null) {
                    // Couldn't create directory or the directory is not empty
                    return null;
                }
                try {
                    // Copy all the directory items to the new path
                    copyDirectory(mContext, source, newPath, override);
                    return new Path(mContext, newPath);
                } catch (IOException e) {
                    return null;
                }
            }
            // Current path is a directory but target is not a directory
            return null;
        }
        if (source.isFile()) { // Source is a file
            DocumentFile newPath;
            if (dest.isDirectory()) {
                // Move the file inside the directory
                newPath = dest.createFile(DEFAULT_MIME, getName());
            } else if (dest.isFile()) {
                // Override the existing dest
                if (!override) {
                    // overriding is disabled
                    return null;
                }
                newPath = dest;
            } else if (destParent != null) {
                // File does not exist, create a new one
                newPath = destParent.createFile(DEFAULT_MIME, Objects.requireNonNull(dest.getName()));
            } else {
                // File does not exist, but nothing could be done about it
                return null;
            }
            if (newPath == null) {
                // For some reason, newPath could not be created
                return null;
            }
            try {
                // Copy the contents of the source file
                copyFile(mContext, source, newPath);
                return new Path(mContext, newPath);
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    private static void copyFile(@NonNull Context context, @NonNull DocumentFile src, @NonNull DocumentFile dst)
            throws IOException {
        copyFile(new Path(context, src), new Path(context, dst));
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
        copyDirectory(new Path(context, src), new Path(context, dst), override);
    }

    private static void copyDirectory(@NonNull Context context, @NonNull DocumentFile src, @NonNull DocumentFile dst)
            throws IOException {
        copyDirectory(new Path(context, src), new Path(context, dst), true);
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
        return mDocumentFile.lastModified();
    }

    public boolean setLastModified(long time) {
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            return Objects.requireNonNull(getFile()).setLastModified(time);
        }
        if (mDocumentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile) mDocumentFile).setLastModified(time);
        }
        return false;
    }

    public long lastAccess() {
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            try {
                return Objects.requireNonNull(getFile()).lastAccess();
            } catch (ErrnoException e) {
                return 0;
            }
        }
        if (mDocumentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile) mDocumentFile).lastAccess();
        }
        return 0;
    }

    public long creationTime() {
        if (mDocumentFile instanceof ExtendedRawDocumentFile) {
            try {
                return Objects.requireNonNull(getFile()).creationTime();
            } catch (ErrnoException e) {
                return 0;
            }
        }
        if (mDocumentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile) mDocumentFile).creationTime();
        }
        return 0;
    }

    @NonNull
    public Path[] listFiles() {
        // Get all file systems mounted at this Uri
        DocumentFile documentFile = getRealDocumentFile(mDocumentFile);
        VirtualFileSystem[] fileSystems = VirtualFileSystem.getFileSystemsAtUri(documentFile.getUri());
        HashMap<String, Uri> nameMountPointMap = new HashMap<>(fileSystems.length);
        for (VirtualFileSystem fs : fileSystems) {
            Uri mountPoint = Objects.requireNonNull(fs.getMountPoint());
            nameMountPointMap.put(mountPoint.getLastPathSegment(), mountPoint);
        }
        // List documents at this folder and remove mount points
        DocumentFile[] ss = documentFile.listFiles();
        List<Path> paths = new ArrayList<>(ss.length + fileSystems.length);
        for (DocumentFile s : ss) {
            if (nameMountPointMap.get(s.getName()) != null) {
                // Mount point exists, remove it from map
                nameMountPointMap.remove(s.getName());
            }
            paths.add(new Path(mContext, s));
        }
        // Add all the other mount points
        for (Uri mountPoint : nameMountPointMap.values()) {
            paths.add(new Path(mContext, mountPoint));
        }
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
            s.getName();
            if (filter == null || filter.accept(this, s.getName())) {
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
            s.getName();
            files.add(s.getName());
        }
        return files.toArray(new String[0]);
    }

    @NonNull
    public String[] listFileNames(@Nullable FileFilter filter) {
        Path[] ss = listFiles();
        ArrayList<String> files = new ArrayList<>();
        for (Path s : ss) {
            s.getName();
            if (filter == null || filter.accept(s)) {
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
            s.getName();
            if (filter == null || filter.accept(s, s.getName())) {
                files.add(s.getName());
            }
        }
        return files.toArray(new String[0]);
    }

    @Nullable
    public ParcelFileDescriptor openFileDescriptor(@NonNull String mode, @NonNull HandlerThread callbackThread)
            throws FileNotFoundException {
        DocumentFile documentFile = getRealDocumentFile(mDocumentFile);
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
        return mContext.getContentResolver().openFileDescriptor(documentFile.getUri(), mode);
    }

    public OutputStream openOutputStream() throws IOException {
        return openOutputStream(false);
    }

    @NonNull
    public OutputStream openOutputStream(boolean append) throws IOException {
        DocumentFile documentFile = getRealDocumentFile(mDocumentFile);
        if (documentFile instanceof ExtendedRawDocumentFile) {
            try {
                return Objects.requireNonNull(getFile()).newOutputStream(append);
            } catch (IOException e) {
                throw new IOException("Could not open file for writing: " + documentFile.getUri());
            }
        } else if (documentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile) documentFile).openOutputStream(append);
        }
        String mode = "w" + (append ? "a" : "t");
        OutputStream os = mContext.getContentResolver().openOutputStream(documentFile.getUri(), mode);
        if (os == null) {
            throw new IOException("Could not resolve Uri: " + documentFile.getUri());
        }
        return os;
    }

    @NonNull
    public InputStream openInputStream() throws IOException {
        DocumentFile documentFile = getRealDocumentFile(mDocumentFile);
        if (documentFile instanceof ExtendedRawDocumentFile) {
            try {
                return Objects.requireNonNull(getFile()).newInputStream();
            } catch (IOException e) {
                throw new IOException("Could not open file for reading: " + documentFile.getUri(), e);
            }
        } else if (documentFile instanceof VirtualDocumentFile) {
            return ((VirtualDocumentFile) documentFile).openInputStream();
        }
        InputStream is = mContext.getContentResolver().openInputStream(documentFile.getUri());
        if (is == null) {
            throw new IOException("Could not resolve Uri: " + documentFile.getUri());
        }
        return is;
    }

    public FileChannel openFileChannel(int mode) throws IOException {
        DocumentFile documentFile = getRealDocumentFile(mDocumentFile);
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

    public byte[] getContentAsBinary() {
        return getContentAsBinary(EmptyArray.BYTE);
    }

    public byte[] getContentAsBinary(byte[] emptyValue) {
        try (InputStream inputStream = openInputStream()) {
            return IoUtils.readFully(inputStream, -1, true);
        } catch (IOException e) {
            if (!(e.getCause() instanceof ErrnoException)) {
                // This isn't just another EACCESS exception
                e.printStackTrace();
            }
        }
        return emptyValue;
    }

    public String getContentAsString() {
        return getContentAsString("");
    }

    @Nullable
    @Contract("!null -> !null")
    public String getContentAsString(@Nullable String emptyValue) {
        try (InputStream inputStream = openInputStream()) {
            return new String(IoUtils.readFully(inputStream, -1, true), Charset.defaultCharset());
        } catch (IOException e) {
            if (!(e.getCause() instanceof ErrnoException)) {
                // This isn't just another EACCESS exception
                e.printStackTrace();
            }
        }
        return emptyValue;
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
        return new Path(context, file);
    }

    @Nullable
    private static DocumentFile findFileInternal(@NonNull DocumentFile documentFile, @NonNull String dirtyDisplayName) {
        String displayName = Paths.getSanitizedPath(dirtyDisplayName, true);
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
            documentFile = fsRoot != null ? fsRoot.mDocumentFile : documentFile.findFile(part);
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
        Uri parentUri = Paths.removeLastPathSegment(mountPoint);
        return new Path(context, parentUri).mDocumentFile;
    }

    @NonNull
    private static DocumentFile createArbitraryDirectories(@NonNull DocumentFile documentFile,
                                                           @NonNull String[] names,
                                                           int length) throws IOException {
        DocumentFile file = getRealDocumentFile(documentFile);
        for (int i = 0; i < length; ++i) {
            Path fsRoot = VirtualFileSystem.getFsRoot(Paths.appendPathSegment(file.getUri(), names[i]));
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

    @NonNull
    private static DocumentFile getRealDocumentFile(@NonNull DocumentFile documentFile) {
        Path fsRoot = VirtualFileSystem.getFsRoot(documentFile.getUri());
        if (fsRoot != null) {
            return fsRoot.mDocumentFile;
        }
        return documentFile;
    }

    private static void checkVfs(Uri uri) throws IOException {
        if (VirtualFileSystem.getFileSystem(uri) != null) {
            throw new IOException("Destination is a mount point.");
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
