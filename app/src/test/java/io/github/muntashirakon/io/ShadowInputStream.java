// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

@Implements(ProxyInputStream.class)
public class ShadowInputStream extends InputStream {
    private FileInputStream is;

    @Implementation
    public void __constructor__(File file) throws FileNotFoundException {
        is = new FileInputStream(file);
    }

    @Implementation
    public void __constructor__(String file) throws FileNotFoundException {
        is = new FileInputStream(file);
    }

    @Implementation
    @Override
    public int read() throws IOException {
        return is.read();
    }

    @Implementation
    @Override
    public int read(byte[] b) throws IOException {
        return is.read(b, 0, b.length);
    }

    @Implementation
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return is.read(b, off, len);
    }

    @Implementation
    @Override
    public long skip(long n) throws IOException {
        return is.skip(n);
    }

    @Implementation
    @Override
    public int available() throws IOException {
        return is.available();
    }

    @Implementation
    @Override
    public void close() throws IOException {
        is.close();
    }

    @Implementation
    @Override
    public synchronized void reset() throws IOException {
        is.reset();
    }

    @Implementation
    @Override
    protected void finalize() throws Throwable {
        is.close();
    }
}
