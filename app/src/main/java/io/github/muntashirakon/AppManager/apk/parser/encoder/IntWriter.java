// SPDX-License-Identifier: MIT

package io.github.muntashirakon.AppManager.apk.parser.encoder;

import java.io.IOException;
import java.io.OutputStream;

// Copyright 2015 Roy
public class IntWriter {
    private final OutputStream os;
    private int pos = 0;

    public IntWriter(OutputStream os) {
        this.os = os;
    }

    public void write(byte b) throws IOException {
        os.write(b);
        pos += 1;
    }

    public void write(short s) throws IOException {
        os.write(s & 0xff);
        os.write((s >>> 8) & 0xff);
        pos += 2;
    }

    public void write(char x) throws IOException {
        write((short) x);
    }

    public void write(int x) throws IOException {
        os.write(x & 0xff);
        x >>>= 8;
        os.write(x & 0xff);
        x >>>= 8;
        os.write(x & 0xff);
        x >>>= 8;
        os.write(x & 0xff);
        pos += 4;
    }

    public void writePlaceHolder(int len, String name) throws IOException {
        os.write(new byte[len]);
    }

    public void close() throws IOException {
        os.close();
    }

    public int getPos() {
        return pos;
    }
}
