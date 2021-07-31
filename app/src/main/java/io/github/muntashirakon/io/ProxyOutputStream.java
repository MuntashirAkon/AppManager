// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.os.RemoteException;
import android.system.ErrnoException;

import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import io.github.muntashirakon.AppManager.ipc.IPCUtils;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;

import static io.github.muntashirakon.AppManager.utils.ExUtils.rethrowAsIOException;

public class ProxyOutputStream extends OutputStream {
    private final IFileDescriptor fd;

    public ProxyOutputStream(String file) throws IOException {
        this(new ProxyFile(file), false);
    }

    public ProxyOutputStream(String file, boolean append) throws IOException {
        this(new ProxyFile(file), append);
    }

    public ProxyOutputStream(File file) throws IOException {
        this(file, false);
    }

    @WorkerThread
    public ProxyOutputStream(File file, boolean append) throws IOException {
        String mode = "w" + (append ? "a" : "t");
        try {
            if (file == null || (file.exists() && !file.canWrite())) {
                throw new IOException("The file cannot be opened for writing.");
            }
            if (file instanceof ProxyFile && LocalServer.isAMServiceAlive()) {
                fd = IPCUtils.getAmService().getFD(file.getAbsolutePath(), mode);
                if (fd == null) {
                    throw new IOException("Returned no file descriptor from the remote service.");
                }
            } else {
                fd = FileDescriptorImpl.getInstance(file.getAbsolutePath(), mode);
            }
        } catch (ErrnoException | RemoteException | SecurityException e) {
            throw ExUtils.<IOException>rethrowAsIOException(e);
        }
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        ArrayUtils.throwsIfOutOfBounds(b.length, off, len);
        if (len == 0) {
            return;
        }
        try {
            while (len > 0) {
                int bytesWritten = fd.write(b, off, len);
                len -= bytesWritten;
                off += bytesWritten;
            }
        } catch (RemoteException e) {
            throw new IOException(e);
        }
    }

    /**
     * Since getFd().sync() doesn't exist
     */
    public void sync() throws IOException {
        try {
            fd.sync();
        } catch (RemoteException e) {
            rethrowAsIOException(e);
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
    protected void finalize() throws Throwable {
        if (fd != null) {
            fd.close();
        }
    }
}
