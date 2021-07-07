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

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.compat.StorageManagerCompat;
import io.github.muntashirakon.AppManager.ipc.IPCUtils;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.users.Users;

/**
 * Provide an interface to {@link File} and {@link DocumentFile} with basic functionalities.
 */
public class Storage {
    public static final String TAG = Storage.class.getSimpleName();

    private final Context context;
    @NonNull
    private final DocumentFile documentFile;

    public Storage(@NonNull Context context, @NonNull File fileLocation) {
        this.context = context;
        this.documentFile = new ProxyDocumentFile(fileLocation);
    }

    public Storage(@NonNull Context context, @NonNull DocumentFile documentFile) {
        this.context = context;
        this.documentFile = documentFile;
    }

    public Storage(@NonNull Context context, @NonNull Uri uri) throws FileNotFoundException {
        this.context = context;
        DocumentFile documentFile;
        switch (uri.getScheme()) {
            case ContentResolver.SCHEME_CONTENT:
                documentFile = DocumentFile.fromTreeUri(context, uri);
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

    public Storage(@NonNull Context context, @NonNull UriPermission uriPermission) throws FileNotFoundException {
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

    public String getFilePath() throws UnsupportedOperationException {
        if (documentFile instanceof ProxyDocumentFile) {
            String path = documentFile.getUri().getPath();
            if (path.startsWith("/storage/emulated/" + Users.myUserId())) {
                return path;
            }
        }
        throw new UnsupportedOperationException("File cannot be accessed this way.");
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
                ((ProxyDocumentFile) documentFile).getFile().createNewFile();
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

    @CheckResult
    public boolean delete() {
        return documentFile.delete();
    }

    @Nullable
    public Storage getParentFile() {
        DocumentFile file = documentFile.getParentFile();
        return file == null ? null : new Storage(context, file);
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

    public boolean mkdir() {
        return true;
    }

    public boolean mkdirs() {
        return true;
    }

    public boolean renameTo(@NonNull String name) {
        // TODO: 9/2/21 Unreliable
        return documentFile.renameTo(name);
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
        File f = new File(documentFile.getUri().getPath());
        for (Uri name : names) {
            if (filter.accept(f, name.getLastPathSegment())) {
                v.add(name);
            }
        }
        return v.toArray(new Uri[0]);
    }

    @NonNull
    public Storage[] listFiles() {
        DocumentFile[] ss = listDocumentFiles();
        int n = ss.length;
        Storage[] fs = new Storage[n];
        for (int i = 0; i < n; i++) {
            fs[i] = new Storage(context, ss[i]);
        }
        return fs;
    }

    @NonNull
    public Storage[] listFiles(@Nullable FileFilter filter) {
        DocumentFile[] ss = listDocumentFiles();
        ArrayList<Storage> files = new ArrayList<>();
        for (DocumentFile s : ss) {
            if ((filter == null) || filter.accept(new File(s.getUri().getPath())))
                files.add(new Storage(context, s));
        }
        return files.toArray(new Storage[0]);
    }

    @NonNull
    public Storage[] listFiles(@Nullable FilenameFilter filter) {
        DocumentFile[] ss = listDocumentFiles();
        ArrayList<Storage> files = new ArrayList<>();
        File f = new File(documentFile.getUri().getPath());
        for (DocumentFile s : ss) {
            if ((filter == null) || filter.accept(f, s.getName()))
                files.add(new Storage(context, s));
        }
        return files.toArray(new Storage[0]);
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
            File file = ((ProxyDocumentFile) documentFile).getFile();
            int modeBits = ParcelFileDescriptor.parseMode(mode);
            if (file instanceof ProxyFile && LocalServer.isAMServiceAlive()) {
                try {
                    return StorageManagerCompat.openProxyFileDescriptor(modeBits, new StorageCallback(
                            file.getAbsolutePath(), mode, callbackThread));
                } catch (RemoteException | IOException e) {
                    Log.e(TAG, e);
                    throw new FileNotFoundException(e.getMessage());
                }
            } else {
                try {
                    return StorageManagerCompat.openProxyFileDescriptor(modeBits, new StorageCallback(
                            FileDescriptorImpl.getInstance(file.getAbsolutePath(), mode), callbackThread));
                } catch (IOException | ErrnoException e) {
                    Log.e(TAG, e);
                    throw new FileNotFoundException(e.getMessage());
                }
            }
        }
        return context.getContentResolver().openFileDescriptor(documentFile.getUri(), mode);
    }

    @Nullable
    public OutputStream openOutputStream(@NonNull String mode) throws FileNotFoundException, RemoteException {
        if (documentFile instanceof ProxyDocumentFile) {
            File file = ((ProxyDocumentFile) documentFile).getFile();
            return new ProxyOutputStream(file);
        }
        return context.getContentResolver().openOutputStream(documentFile.getUri(), mode);
    }

    @Nullable
    public InputStream openInputStream() throws IOException {
        if (documentFile instanceof ProxyDocumentFile) {
            File file = ((ProxyDocumentFile) documentFile).getFile();
            return new ProxyInputStream(file);
        }
        return context.getContentResolver().openInputStream(documentFile.getUri());
    }

    @NonNull
    private DocumentFile[] listDocumentFiles() {
        return documentFile.listFiles();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Storage)) return false;
        Storage storage = (Storage) o;
        return documentFile.getUri().equals(storage.documentFile.getUri());
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentFile.getUri());
    }

    private static class StorageCallback extends StorageManagerCompat.ProxyFileDescriptorCallbackCompat {
        private final IFileDescriptor fd;

        private StorageCallback(String path, String mode, HandlerThread thread) throws RemoteException {
            super(thread);
            Log.d(TAG, "Mode: " + mode);
            try {
                fd = IPCUtils.getAmService().getFD(path, mode);
            } catch (RemoteException e) {
                thread.quitSafely();
                throw e;
            }
        }

        private StorageCallback(IFileDescriptor fd, HandlerThread thread) {
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
