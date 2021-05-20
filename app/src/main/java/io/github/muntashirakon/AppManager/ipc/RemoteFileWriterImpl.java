// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.os.RemoteException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import io.github.muntashirakon.AppManager.IRemoteFileWriter;

class RemoteFileWriterImpl extends IRemoteFileWriter.Stub {
    private final FileOutputStream fos;

    public RemoteFileWriterImpl(File file) throws FileNotFoundException {
        fos = new FileOutputStream(file);
    }

    @Override
    public void write0(int b) throws RemoteException {
        try {
            fos.write(b);
        } catch (IOException e) {
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public void write1(byte[] b) throws RemoteException {
        try {
            fos.write(b);
        } catch (IOException e) {
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public void write2(byte[] b, int off, int len) throws RemoteException {
        try {
            fos.write(b, off, len);
        } catch (IOException e) {
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public void flush() throws RemoteException {
        try {
            fos.flush();
        } catch (IOException e) {
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public void sync() throws RemoteException {
        try {
            fos.getFD().sync();
        } catch (IOException e) {
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public void close() throws RemoteException {
        try {
            fos.close();
        } catch (IOException e) {
            throw new RemoteException(e.getMessage());
        }
    }
}
