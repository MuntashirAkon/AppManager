/*
 * Copyright (c) 2021 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.ipc;

import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import aosp.libcore.util.HexEncoding;
import io.github.muntashirakon.AppManager.IRemoteFileReader;

class RemoteFileReaderImpl extends IRemoteFileReader.Stub {
    private final FileInputStream fis;

    public RemoteFileReaderImpl(File file) throws FileNotFoundException {
        fis = new FileInputStream(file);
    }

    @Override
    public int read0() throws RemoteException {
        try {
            return fis.read();
        } catch (IOException e) {
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public int read1(byte[] b) throws RemoteException {
        try {
            return fis.read(b);
        } catch (IOException e) {
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public int read2(byte[] b, int off, int len) throws RemoteException {
        try {
            return fis.read(b, off, len);
        } catch (IOException e) {
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public long skip(long n) throws RemoteException {
        try {
            return fis.skip(n);
        } catch (IOException e) {
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public int available() throws RemoteException {
        try {
            return fis.available();
        } catch (IOException e) {
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public void close() throws RemoteException {
        try {
            fis.close();
        } catch (IOException e) {
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public void mark(int readlimit) {
        fis.mark(readlimit);
    }

    @Override
    public void reset() throws RemoteException {
        try {
            fis.reset();
        } catch (IOException e) {
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public boolean markSupported() {
        return fis.markSupported();
    }
}
