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

import java.io.*;
import java.nio.channels.FileChannel;

public class ProxyInputStream extends InputStream {
    private final FileInputStream privateInputStream;

    public ProxyInputStream(File file) throws FileNotFoundException, RemoteException {
        if (file instanceof ProxyFile) {
            privateInputStream = ((ProxyFile) file).getInputStream();
        } else {
            privateInputStream = new FileInputStream(file);
        }
    }

    public ProxyInputStream(String file) throws IOException, RemoteException {
        this(new ProxyFile(file));
    }

    @Override
    public int read() throws IOException {
        return privateInputStream.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return privateInputStream.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return privateInputStream.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return privateInputStream.skip(n);
    }

    @Override
    public int available() throws IOException {
        return privateInputStream.available();
    }

    @Override
    public void close() throws IOException {
        privateInputStream.close();
    }

    public final FileDescriptor getFD() throws IOException {
        return privateInputStream.getFD();
    }

    public FileChannel getChannel() {
        return privateInputStream.getChannel();
    }

    @Override
    public synchronized void mark(int readlimit) {
        privateInputStream.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        privateInputStream.reset();
    }

    @Override
    public boolean markSupported() {
        return privateInputStream.markSupported();
    }
}
