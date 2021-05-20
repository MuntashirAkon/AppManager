// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.os.RemoteException;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import io.github.muntashirakon.AppManager.IRemoteFileWriter;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;

public class ProxyOutputStream extends OutputStream {
    @Nullable
    private final FileOutputStream privateOutputStream;
    @Nullable
    private final IRemoteFileWriter fileWriter;

    @WorkerThread
    public ProxyOutputStream(File file) throws FileNotFoundException, RemoteException {
        if (file instanceof ProxyFile && LocalServer.isAMServiceAlive()) {
            privateOutputStream = null;
            fileWriter = ((ProxyFile) file).getFileWriter();
        } else {
            privateOutputStream = new FileOutputStream(file);
            fileWriter = null;
        }
    }

    public ProxyOutputStream(String file) throws FileNotFoundException, RemoteException {
        this(new ProxyFile(file));
    }

    @Override
    public void write(int b) throws IOException {
        if (fileWriter != null) {
            try {
                fileWriter.write0(b);
            } catch (RemoteException e) {
                throw new IOException(e);
            }
        } else if (privateOutputStream != null) privateOutputStream.write(b);
        else throw new IOException("Invalid stream");
    }

    @Override
    public void write(byte[] b) throws IOException {
        if (fileWriter != null) {
            try {
                fileWriter.write1(b);
            } catch (RemoteException e) {
                throw new IOException(e);
            }
        } else if (privateOutputStream != null) privateOutputStream.write(b);
        else throw new IOException("Invalid stream");
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (fileWriter != null) {
            try {
                fileWriter.write2(b, off, len);
            } catch (RemoteException e) {
                throw new IOException(e);
            }
        } else if (privateOutputStream != null) privateOutputStream.write(b, off, len);
        else throw new IOException("Invalid stream");
    }

    @Override
    public void flush() throws IOException {
        if (fileWriter != null) {
            try {
                fileWriter.flush();
            } catch (RemoteException e) {
                throw new IOException(e.getMessage());
            }
        } else if (privateOutputStream != null) privateOutputStream.flush();
        else throw new IOException("Invalid stream");
    }

    public void sync() throws IOException {
        if (fileWriter != null) {
            try {
                fileWriter.sync();
            } catch (RemoteException e) {
                throw new IOException(e.getMessage());
            }
        } else if (privateOutputStream != null) privateOutputStream.getFD().sync();
        else throw new IOException("Invalid stream");
    }

    @Override
    public void close() throws IOException {
        if (fileWriter != null) {
            try {
                fileWriter.close();
            } catch (RemoteException e) {
                throw new IOException(e.getMessage());
            }
        } else if (privateOutputStream != null) privateOutputStream.close();
        else throw new IOException("Invalid stream");
    }
}
