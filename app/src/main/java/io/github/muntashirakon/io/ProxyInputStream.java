// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.os.RemoteException;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import io.github.muntashirakon.AppManager.IRemoteFileReader;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;

public class ProxyInputStream extends InputStream {
    @Nullable
    private final FileInputStream privateInputStream;
    @Nullable
    private final IRemoteFileReader fileReader;

    @WorkerThread
    public ProxyInputStream(File file) throws FileNotFoundException, RemoteException {
        if (file instanceof ProxyFile && LocalServer.isAMServiceAlive()) {
            privateInputStream = null;
            fileReader = ((ProxyFile) file).getFileReader();
        } else {
            privateInputStream = new FileInputStream(file);
            fileReader = null;
        }
    }

    @WorkerThread
    public ProxyInputStream(String file) throws IOException, RemoteException {
        this(new ProxyFile(file));
    }

    @Override
    public int read() throws IOException {
        if (fileReader != null) {
            try {
                return fileReader.read0();
            } catch (RemoteException e) {
                throw new IOException(e);
            }
        } else if (privateInputStream != null) return privateInputStream.read();
        throw new IOException("Invalid stream.");
    }

    @Override
    public int read(byte[] b) throws IOException {
        if (fileReader != null) {
            try {
                return fileReader.read1(b);
            } catch (RemoteException e) {
                throw new IOException(e);
            }
        } else if (privateInputStream != null) return privateInputStream.read(b);
        throw new IOException("Invalid stream.");
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (fileReader != null) {
            try {
                return fileReader.read2(b, off, len);
            } catch (RemoteException e) {
                throw new IOException(e);
            }
        } else if (privateInputStream != null) return privateInputStream.read(b, off, len);
        throw new IOException("Invalid stream.");
    }

    @Override
    public long skip(long n) throws IOException {
        if (fileReader != null) {
            try {
                return fileReader.skip(n);
            } catch (RemoteException e) {
                throw new IOException(e);
            }
        } else if (privateInputStream != null) return privateInputStream.skip(n);
        throw new IOException("Invalid stream.");
    }

    @Override
    public int available() throws IOException {
        if (fileReader != null) {
            try {
                return fileReader.available();
            } catch (RemoteException e) {
                throw new IOException(e);
            }
        } else if (privateInputStream != null) return privateInputStream.available();
        throw new IOException("Invalid stream.");
    }

    @Override
    public void close() throws IOException {
        if (fileReader != null) {
            try {
                fileReader.close();
            } catch (RemoteException e) {
                throw new IOException(e);
            }
        } else if (privateInputStream != null) {
            privateInputStream.close();
        } else throw new IOException("Invalid stream.");
    }

    @Override
    public synchronized void mark(int readlimit) {
        if (fileReader != null) {
            try {
                fileReader.mark(readlimit);
            } catch (RemoteException ignore) {
            }
        } else if (privateInputStream != null) {
            privateInputStream.mark(readlimit);
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        if (fileReader != null) {
            try {
                fileReader.reset();
            } catch (RemoteException e) {
                throw new IOException(e);
            }
        } else if (privateInputStream != null) {
            privateInputStream.reset();
        } else throw new IOException("Invalid stream.");
    }

    @Override
    public boolean markSupported() {
        if (fileReader != null) {
            try {
                return fileReader.markSupported();
            } catch (RemoteException ignore) {
                return false;
            }
        }
        if (privateInputStream != null) return privateInputStream.markSupported();
        return false;
    }
}
