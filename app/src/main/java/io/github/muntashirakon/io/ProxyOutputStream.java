/*
 * Copyright (C) 2021 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
