// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileDescriptor;
import java.io.InterruptedIOException;
import java.io.SyncFailedException;

import io.github.muntashirakon.AppManager.utils.IOUtils;

import static android.system.OsConstants.EISDIR;
import static android.system.OsConstants.O_CREAT;
import static android.system.OsConstants.S_IRWXG;
import static android.system.OsConstants.S_IRWXU;
import static android.system.OsConstants.S_ISDIR;

public class FileDescriptorImpl extends IFileDescriptor.Stub {
    FileDescriptor fd;

    @NonNull
    public static IFileDescriptor getInstance(@NonNull String path, @NonNull String mode) throws ErrnoException {
        File file = new File(path);
        int flags = IOUtils.translateModeStringToPosix(mode);
        int fileMode = S_IRWXU | S_IRWXG;
        if (file.exists()) {
            try {
                fileMode = Os.lstat(path).st_mode & 0x1FF;
            } catch (ErrnoException ignore) {
            }
        } else if ((flags & O_CREAT) != 0) {
            String parent = file.getParent();
            if (parent != null) {
                try {
                    fileMode = Os.lstat(parent).st_mode & 0x1FF;
                } catch (ErrnoException ignore) {
                }
            }
        }
        return new FileDescriptorImpl(path, flags, fileMode);
    }

    private FileDescriptorImpl(String filePath, int flags, int mode) throws ErrnoException {
        fd = Os.open(filePath, flags, mode);
        if (S_ISDIR(Os.fstat(fd).st_mode)) {
            throw new ErrnoException("open", EISDIR);
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws RemoteException {
        try {
            return Os.read(fd, b, off, len);
        } catch (ErrnoException | InterruptedIOException e) {
            return rethrowAsRemoteException(e);
        }
    }

    @Override
    public int write(byte[] b, int off, int len) throws RemoteException {
        try {
            return Os.write(fd, b, off, len);
        } catch (ErrnoException | InterruptedIOException e) {
            return rethrowAsRemoteException(e);
        }
    }

    @Override
    public void sync() throws RemoteException {
        try {
            fd.sync();
        } catch (SyncFailedException e) {
            rethrowAsRemoteException(e);
        }
    }

    @Override
    public int available() {
        try {
            // On Android, available = total file size - current position
            long currPos = getFilePointer();
            long size = Os.fstat(fd).st_size;
            return (int) (size - currPos);
        } catch (ErrnoException | RemoteException ignore) {
        }
        return 0;
    }

    @Override
    public long seek(long pos) throws RemoteException {
        try {
            return Os.lseek(fd, pos, OsConstants.SEEK_SET);
        } catch (ErrnoException e) {
            return rethrowAsRemoteException(e);
        }
    }

    @Override
    public long getFilePointer() throws RemoteException {
        try {
            return Os.lseek(fd, 0, OsConstants.SEEK_CUR);
        } catch (ErrnoException e) {
            return rethrowAsRemoteException(e);
        }
    }

    @Override
    public long length() throws RemoteException {
        try {
            return Os.fstat(fd).st_size;
        } catch (ErrnoException e) {
            return rethrowAsRemoteException(e);
        }
    }

    @Override
    public void close() throws RemoteException {
        try {
            if (fd.valid()) Os.close(fd);
        } catch (ErrnoException e) {
            rethrowAsRemoteException(e);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (fd != null && fd.valid()) Os.close(fd);
    }

    private <T> T rethrowAsRemoteException(Exception e) throws RemoteException {
        RemoteException re = new RemoteException(e.getMessage());
        re.initCause(e);
        throw re;
    }
}
