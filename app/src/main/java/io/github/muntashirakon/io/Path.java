// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.content.ContentResolver;
import android.content.Context;
import android.content.UriPermission;
import android.net.Uri;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.OsConstants;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.documentfile.provider.ProxyDocumentFile;
import androidx.documentfile.provider.ZipDocumentFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.compat.StorageManagerCompat;
import io.github.muntashirakon.AppManager.ipc.IPCUtils;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;

/**
 * Provide an interface to {@link File} and {@link DocumentFile} with basic functionalities.
 */
public class Path {
    public static final String TAG = Path.class.getSimpleName();

    private final Context context;
    @NonNull
    private DocumentFile documentFile;

    public Path(@NonNull Context context, @NonNull File fileLocation) {
        this.context = context;
        this.documentFile = new ProxyDocumentFile(fileLocation);
    }

    public Path(@NonNull Context context, @NonNull ZipFile zipFile, @Nullable String path) {
        this.context = context;
        this.documentFile = new ZipDocumentFile(zipFile, path);
    }

    public Path(@NonNull Context context, @NonNull DocumentFile documentFile) {
        this.context = context;
        this.documentFile = documentFile;
    }

    public Path(@NonNull Context context, @NonNull Uri treeUri) throws FileNotFoundException {
        this(context, treeUri, true);
    }

    public Path(@NonNull Context context, @NonNull Uri uri, boolean isTreeUri) throws FileNotFoundException {
        this.context = context;
        DocumentFile documentFile;
        switch (uri.getScheme()) {
            case ContentResolver.SCHEME_CONTENT:
                documentFile = isTreeUri ? DocumentFile.fromTreeUri(context, uri) : DocumentFile.fromSingleUri(context, uri);
                // For tree uri, the requested Uri is not always the same as the generated uri.
                // So, make sure to navigate to the correct uri
                if (isTreeUri && documentFile != null) {
                    String diff = FileUtils.getRelativePath(uri.getPath(), documentFile.getUri().getPath(), File.separator);
                    String[] files = diff.split("/");
                    for (String file : files) {
                        if (documentFile != null) {
                            if (file.equals("..")) {
                                // Tree uri doesn't always support go back
                                DocumentFile parent = documentFile.getParentFile();
                                if (parent != null) documentFile = parent;
                                continue;
                            }
                            documentFile = documentFile.findFile(file);
                        }
                    }
                }
                break;
            case ContentResolver.SCHEME_FILE:
                documentFile = new ProxyDocumentFile(new ProxyFile(uri.getPath()));
                break;
            default:
                throw new IllegalArgumentException("Unsupported uri " + uri);
        }
        if (documentFile == null) throw new FileNotFoundException("Uri " + uri + " does not belong to any document.");
        this.documentFile = documentFile;
    }

    public Path(@NonNull Context context, @NonNull UriPermission uriPermission) throws FileNotFoundException {
        this.context = context;
        DocumentFile documentFile = DocumentFile.fromTreeUri(context, uriPermission.getUri());
        if (documentFile == null)
            throw new FileNotFoundException("Uri " + uriPermission + " does not belong to any document.");
        this.documentFile = documentFile;
    }

    public String getName() {
        return documentFile.getName();
    }

    public Uri getUri() {
        return documentFile.getUri();
    }

    @Nullable
    public File getFile() {
        if (documentFile instanceof ProxyDocumentFile) {
            return ((ProxyDocumentFile) documentFile).getFile();
        }
        return null;
    }

    @Nullable
    public String getFilePath() {
        if (documentFile instanceof ProxyDocumentFile) {
            return documentFile.getUri().getPath();
        }
        return null;
    }

    @Nullable
    public String getRealFilePath() throws IOException {
        if (documentFile instanceof ProxyDocumentFile) {
            return Objects.requireNonNull(getFile()).getCanonicalPath();
        }
        return null;
    }

    public String getType() {
        return documentFile.getType();
    }

    @CheckResult
    public long length() {
        return documentFile.length();
    }

    @CheckResult
    public boolean createNewFile() {
        if (exists()) return true;
        if (documentFile instanceof ProxyDocumentFile) {
            try {
                Objects.requireNonNull(getFile()).createNewFile();
                return true;
            } catch (IOException e) {
                return false;
            }
        }
        try (OutputStream ignore = context.getContentResolver().openOutputStream(documentFile.getUri())) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @NonNull
    public Path createNewFile(@NonNull String displayName, @Nullable String mimeType) throws IOException {
        return createNewFileInternal(context, documentFile, displayName, mimeType);
    }

    @CheckResult
    @NonNull
    public Path createNewDirectory(@NonNull String displayName) throws IOException {
        displayName = normalizeFilename(displayName);
        DocumentFile file = documentFile.createDirectory(displayName);
        if (file == null) throw new IOException("Could not create directory named " + displayName);
        return new Path(context, file);
    }

    public Path createNewFileRecursive(@NonNull String displayName, @Nullable String mimeType) throws IOException {
        displayName = normalizeFilename(displayName);
        String[] names = displayName.split("/");
        DocumentFile file = documentFile;
        int dirCount = names.length - 1;
        for (int i = 0; i < dirCount; ++i) {
            DocumentFile t = file.findFile(names[i]);
            if (t == null) t = file.createDirectory(names[i]);
            else if (!t.isDirectory()) {
                throw new IOException(names[i] + " exists and it is not a directory.");
            }
            if (t == null) {
                throw new IOException("Could not create directory " + this + File.separatorChar + names[i]);
            }
            file = t;
        }
        return createNewFileInternal(context, file, names[dirCount], mimeType);
    }

    @CheckResult
    @NonNull
    public Path createDirectories(@NonNull String displayName) throws IOException {
        displayName = normalizeFilename(displayName);
        String[] dirNames = displayName.split("/");
        DocumentFile file = documentFile;
        for (String dirName : dirNames) {
            DocumentFile t = file.findFile(dirName);
            if (t == null) t = file.createDirectory(dirName);
            else if (!t.isDirectory()) {
                throw new IOException(dirName + " exists and it is not a directory.");
            }
            if (t == null) {
                throw new IOException("Could not create directory " + this + File.separatorChar + dirName);
            }
            file = t;
        }
        return new Path(context, file);
    }

    public boolean delete() {
        documentFile.delete();
        return !exists();
    }

    @Nullable
    public Path getParentFile() {
        DocumentFile file = documentFile.getParentFile();
        return file == null ? null : new Path(context, file);
    }

    public boolean hasFile(@NonNull String displayName) {
        // TODO: 14/10/21 Investigate whether display name with `/` works
        return documentFile.findFile(normalizeFilename(displayName)) != null;
    }

    @NonNull
    public Path findFile(@NonNull String displayName) throws FileNotFoundException {
        displayName = normalizeFilename(displayName);
        DocumentFile file = documentFile.findFile(displayName);
        if (file == null) throw new FileNotFoundException("Cannot find " + this + File.separatorChar + displayName);
        return new Path(context, file);
    }

    public Path findOrCreateFile(@NonNull String displayName, @Nullable String mimeType) throws IOException {
        displayName = normalizeFilename(displayName);
        DocumentFile file = documentFile.findFile(displayName);
        if (file == null) {
            return createNewFile(displayName, mimeType);
        }
        return new Path(context, file);
    }

    public Path findOrCreateDirectory(@NonNull String displayName) throws IOException {
        displayName = normalizeFilename(displayName);
        DocumentFile file = documentFile.findFile(displayName);
        if (file == null) {
            return createNewDirectory(displayName);
        }
        return new Path(context, file);
    }

    @CheckResult
    public boolean exists() {
        return documentFile.exists();
    }

    @CheckResult
    public boolean isDirectory() {
        return documentFile.isDirectory();
    }

    @CheckResult
    public boolean isFile() {
        return documentFile.isFile();
    }

    @CheckResult
    public boolean isVirtual() {
        return documentFile.isVirtual();
    }

    public boolean isSymbolicLink() throws IOException {
        if (documentFile instanceof ProxyDocumentFile) {
            try {
                FileStatus lstat = ProxyFiles.lstat(Objects.requireNonNull(getFile()));
                // https://github.com/win32ports/unistd_h/blob/master/unistd.h
                return OsConstants.S_ISLNK(lstat.st_mode);
            } catch (ErrnoException | RemoteException e) {
                return ExUtils.rethrowAsIOException(e);
            }
        }
        return false;
    }

    public boolean mkdir() {
        if (exists()) return true;
        if (documentFile instanceof ProxyDocumentFile) {
            return ((ProxyDocumentFile) documentFile).getFile().mkdir();
        } else {
            DocumentFile parent = documentFile.getParentFile();
            if (parent != null) {
                DocumentFile thisFile = parent.createDirectory(getName());
                if (thisFile != null) {
                    documentFile = thisFile;
                    return true;
                }
            }
        }
        return false;
    }

    public boolean mkdirs() {
        if (exists()) return true;
        if (documentFile instanceof ProxyDocumentFile) {
            return ((ProxyDocumentFile) documentFile).getFile().mkdirs();
        }
        // For others, directory can't be created recursively as parent must exist
        return mkdir();
    }

    public boolean renameTo(@NonNull String displayName) {
        displayName = normalizeFilename(displayName);
        if (displayName.contains(File.separator)) {
            // display name must belong to the same directory.
            return false;
        }
        return documentFile.renameTo(displayName);
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
        if (path.exists() && !path.canWrite()) {
            // There's no point is attempting to move if the destination is read-only
            return false;
        }
        if (documentFile instanceof ProxyDocumentFile && path.documentFile instanceof ProxyDocumentFile) {
            // Try using the default option
            File src = ((ProxyDocumentFile) documentFile).getFile();
            File dst = ((ProxyDocumentFile) path.documentFile).getFile();
            if (src.renameTo(dst)) {
                documentFile = path.documentFile;
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
                    documentFile = newPath.documentFile;
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
                documentFile = newPath.documentFile;
                return true;
            } catch (IOException e) {
                return false;
            }
        }
    }

    private static void copyFile(@NonNull Path src, @NonNull Path dst) throws IOException {
        FileUtils.copy(src, dst);
    }

    // Copy directory content
    private static void copyDirectory(@NonNull Path src, @NonNull Path dst) throws IOException {
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
        return documentFile.lastModified();
    }

    @NonNull
    public Uri[] list() {
        DocumentFile[] children = listDocumentFiles();
        Uri[] files = new Uri[children.length];
        for (int i = 0; i < children.length; ++i) {
            files[i] = children[i].getUri();
        }
        return files;
    }

    @NonNull
    public Uri[] list(@Nullable FilenameFilter filter) {
        Uri[] names = list();
        if (filter == null) {
            return names;
        }
        List<Uri> v = new ArrayList<>();
        for (Uri name : names) {
            if (filter.accept(this, name.getLastPathSegment())) {
                v.add(name);
            }
        }
        return v.toArray(new Uri[0]);
    }

    @NonNull
    public Path[] listFiles() {
        DocumentFile[] ss = listDocumentFiles();
        int n = ss.length;
        Path[] fs = new Path[n];
        for (int i = 0; i < n; i++) {
            fs[i] = new Path(context, ss[i]);
        }
        return fs;
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
        DocumentFile[] ss = listDocumentFiles();
        ArrayList<Path> files = new ArrayList<>();
        for (DocumentFile s : ss) {
            if ((filter == null) || filter.accept(this, s.getName()))
                files.add(new Path(context, s));
        }
        return files.toArray(new Path[0]);
    }

    public boolean canRead() {
        return documentFile.canRead();
    }

    public boolean canWrite() {
        return documentFile.canWrite();
    }

    @Nullable
    public ParcelFileDescriptor openFileDescriptor(@NonNull String mode, @NonNull HandlerThread callbackThread)
            throws FileNotFoundException {
        if (documentFile instanceof ProxyDocumentFile) {
            File file = Objects.requireNonNull(getFile());
            int modeBits = ParcelFileDescriptor.parseMode(mode);
            if (file instanceof ProxyFile && LocalServer.isAMServiceAlive()) {
                try {
                    return StorageManagerCompat.openProxyFileDescriptor(modeBits, new ProxyStorageCallback(
                            file.getAbsolutePath(), mode, callbackThread));
                } catch (RemoteException | IOException e) {
                    Log.e(TAG, e);
                    throw new FileNotFoundException(e.getMessage());
                }
            } else {
                try {
                    return StorageManagerCompat.openProxyFileDescriptor(modeBits, new ProxyStorageCallback(
                            FileDescriptorImpl.getInstance(file.getAbsolutePath(), mode), callbackThread));
                } catch (IOException | ErrnoException e) {
                    Log.e(TAG, e);
                    throw new FileNotFoundException(e.getMessage());
                }
            }
        } else if (documentFile instanceof ZipDocumentFile) {
            if (!documentFile.isFile()) return null;
            int modeBits = ParcelFileDescriptor.parseMode(mode);
            if ((modeBits & ParcelFileDescriptor.MODE_READ_ONLY) == 0) {
                throw new FileNotFoundException("Read-only file");
            }
            try {
                return StorageManagerCompat.openProxyFileDescriptor(modeBits,
                        new ZipStorageCallback((ZipDocumentFile) documentFile, callbackThread));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return context.getContentResolver().openFileDescriptor(documentFile.getUri(), mode);
    }

    public OutputStream openOutputStream() throws IOException {
        return openOutputStream(false);
    }

    @NonNull
    public OutputStream openOutputStream(boolean append) throws IOException {
        if (documentFile instanceof ProxyDocumentFile) {
            return new ProxyOutputStream(Objects.requireNonNull(getFile()), append);
        } else if (documentFile instanceof ZipDocumentFile) {
            throw new IOException("Unsupported operation");
        }
        String mode = "w" + (append ? "a" : "t");
        OutputStream os = context.getContentResolver().openOutputStream(documentFile.getUri(), mode);
        if (os != null) return os;
        throw new IOException("Content provider has crashed");
    }

    @NonNull
    public InputStream openInputStream() throws IOException {
        if (documentFile instanceof ProxyDocumentFile) {
            return new ProxyInputStream(Objects.requireNonNull(getFile()));
        } else if (documentFile instanceof ZipDocumentFile) {
            return ((ZipDocumentFile) documentFile).openInputStream();
        }
        InputStream is = context.getContentResolver().openInputStream(documentFile.getUri());
        if (is != null) return is;
        throw new IOException("Content provider has crashed");
    }

    @NonNull
    private DocumentFile[] listDocumentFiles() {
        return documentFile.listFiles();
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
        return documentFile.getUri().equals(path.documentFile.getUri());
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentFile.getUri());
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
    private static Path createNewFileInternal(@NonNull Context context,
                                              @NonNull DocumentFile documentFile,
                                              @NonNull String displayName,
                                              @Nullable String mimeType) throws IOException {
        displayName = normalizeFilename(displayName);
        DocumentFile f = documentFile.findFile(displayName);
        if (f != null) {
            if (f.isDirectory()) {
                throw new IOException("Directory cannot be converted to file");
            }
            // Delete the file if it exists
            f.delete();
        }
        if (mimeType == null) {
            // Generic binary file
            mimeType = "application/octet-stream";
        }
        DocumentFile file = documentFile.createFile(mimeType, displayName);
        if (file == null) {
            throw new IOException("Could not create " + documentFile.getUri() + File.separatorChar + displayName + " with type " + mimeType);
        }
        return new Path(context, file);
    }

    @NonNull
    private static String normalizeFilename(@NonNull String filename) {
        // Filename can contain `./` prefix which has to be removed
        return filename.startsWith("./") ? filename.substring(2) : filename;
    }

    private static class ZipStorageCallback extends StorageManagerCompat.ProxyFileDescriptorCallbackCompat {
        private final InputStream is;

        public ZipStorageCallback(ZipDocumentFile document, HandlerThread callbackThread) throws IOException {
            super(callbackThread);
            is = document.openInputStream();
        }

        @Override
        public long onGetSize() throws ErrnoException {
            try {
                return is.available();
            } catch (IOException e) {
                throw new ErrnoException(e.getMessage(), OsConstants.EBADF);
            }
        }

        @Override
        public int onRead(long offset, int size, byte[] data) throws ErrnoException {
            try {
                return is.read(data, (int) offset, size);
            } catch (IOException e) {
                throw new ErrnoException(e.getMessage(), OsConstants.EBADF);
            }
        }

        @Override
        protected void onRelease() {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected void finalize() throws Throwable {
            is.close();
        }
    }

    private static class ProxyStorageCallback extends StorageManagerCompat.ProxyFileDescriptorCallbackCompat {
        private final IFileDescriptor fd;

        private ProxyStorageCallback(String path, String mode, HandlerThread thread) throws RemoteException {
            super(thread);
            Log.d(TAG, "Mode: " + mode);
            try {
                fd = IPCUtils.getAmService().getFD(path, mode);
            } catch (RemoteException e) {
                thread.quitSafely();
                throw e;
            }
        }

        private ProxyStorageCallback(IFileDescriptor fd, HandlerThread thread) {
            super(thread);
            this.fd = fd;
        }

        @Override
        public long onGetSize() throws ErrnoException {
            try {
                return fd.available();
            } catch (RemoteException e) {
                throw new ErrnoException(e.getMessage(), OsConstants.EBADF);
            }
        }

        @Override
        public int onRead(long offset, int size, byte[] data) throws ErrnoException {
            try {
                return fd.read(data, (int) offset, size);
            } catch (RemoteException e) {
                throw new ErrnoException(e.getMessage(), OsConstants.EBADF);
            }
        }

        @Override
        public int onWrite(long offset, int size, byte[] data) throws ErrnoException {
            try {
                return fd.write(data, (int) offset, size);
            } catch (RemoteException e) {
                throw new ErrnoException(e.getMessage(), OsConstants.EBADF);
            }
        }

        @Override
        public void onFsync() throws ErrnoException {
            try {
                fd.sync();
            } catch (RemoteException e) {
                throw new ErrnoException(e.getMessage(), OsConstants.EBADF);
            }
        }

        @Override
        protected void onRelease() {
            try {
                fd.close();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected void finalize() throws Throwable {
            fd.close();
        }
    }
}
