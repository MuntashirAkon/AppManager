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
import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;

import java.io.*;
import java.nio.channels.FileChannel;

public class ProxyOutputStream extends OutputStream {
    @NonNull
    private final FileOutputStream privateOutputStream;

    public ProxyOutputStream(File file) throws FileNotFoundException, RemoteException {
        if (file instanceof ProxyFile && LocalServer.isAMServiceAlive()) {
            privateOutputStream = ((ProxyFile) file).getOutputStream();
        } else {
            privateOutputStream = new FileOutputStream(file);
        }
    }

    @Override
    public void write(int b) throws IOException {
        privateOutputStream.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        privateOutputStream.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        privateOutputStream.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        privateOutputStream.flush();
    }

    public final FileDescriptor getFD() throws IOException {
        return privateOutputStream.getFD();
    }

    public FileChannel getChannel() {
        return privateOutputStream.getChannel();
    }

    @Override
    public void close() throws IOException {
        privateOutputStream.close();
    }
}
