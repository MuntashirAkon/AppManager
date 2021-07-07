// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.os.RemoteException;
import android.system.ErrnoException;

import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import io.github.muntashirakon.AppManager.ipc.IPCUtils;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;

import static io.github.muntashirakon.AppManager.utils.ExUtils.rethrowAsIOException;

public class ProxyInputStream extends InputStream {
    private final IFileDescriptor fd;
    private final byte[] scratch = new byte[1];

    @WorkerThread
    public ProxyInputStream(File file) throws IOException {
        String mode = "r";
        try {
            if (file instanceof ProxyFile && LocalServer.isAMServiceAlive()) {
                fd = IPCUtils.getAmService().getFD(file.getAbsolutePath(), mode);
            } else {
                fd = FileDescriptorImpl.getInstance(file.getAbsolutePath(), mode);
            }
        } catch (ErrnoException | RemoteException e) {
            throw ExUtils.<IOException>rethrowAsIOException(e);
        }
    }

    @WorkerThread
    public ProxyInputStream(String file) throws IOException, RemoteException {
        this(new ProxyFile(file));
    }

    @Override
    public int read() throws IOException {
        return (read(scratch, 0, 1) != -1) ? scratch[0] & 0xff : -1;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        ArrayUtils.throwsIfOutOfBounds(b.length, off, len);
        if (len == 0) {
            return 0;
        }
        try {
            int readCount = fd.read(b, off, len);
            if (readCount == 0) {
                return -1;
            }
            return readCount;
        } catch (RemoteException e) {
            return rethrowAsIOException(e);
        }
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 0) {
            return 0;
        }
        try {
            long pos = fd.getFilePointer();
            long len = fd.length();
            long newpos = pos + n;
            if (newpos > len) {
                newpos = len;
            }
            fd.seek(newpos);
            // return the actual number of bytes skipped
            return (int) (newpos - pos);
        } catch (RemoteException e) {
            return rethrowAsIOException(e);
        }
    }

    @Override
    public int available() throws IOException {
        try {
            return fd.available();
        } catch (RemoteException e) {
            return rethrowAsIOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            fd.close();
        } catch (RemoteException e) {
            rethrowAsIOException(e);
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        try {
            fd.seek(0);
        } catch (RemoteException e) {
            rethrowAsIOException(e);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (fd != null) fd.close();
    }
}
