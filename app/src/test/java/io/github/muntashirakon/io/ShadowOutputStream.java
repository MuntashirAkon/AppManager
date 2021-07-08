// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

@Implements(ProxyOutputStream.class)
public class ShadowOutputStream extends OutputStream {
    private FileOutputStream os;

    @Implementation
    public void __constructor__(String file) throws FileNotFoundException {
        os = new FileOutputStream(file);
    }

    @Implementation
    public void __constructor__(String file, boolean append) throws FileNotFoundException {
        os = new FileOutputStream(file, append);
    }

    @Implementation
    public void __constructor__(File file) throws FileNotFoundException {
        os = new FileOutputStream(file);
    }

    @Implementation
    public void __constructor__(File file, boolean append) throws FileNotFoundException {
        os = new FileOutputStream(file, append);
    }

    @Implementation
    @Override
    public void write(int b) throws IOException {
        os.write(b);
    }

    @Implementation
    @Override
    public void write(byte[] b) throws IOException {
        os.write(b);
    }

    @Implementation
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        os.write(b, off, len);
    }

    @Implementation
    public void sync() throws IOException {
        os.getFD().sync();
    }

    @Implementation
    @Override
    public void close() throws IOException {
        os.close();
    }
}
