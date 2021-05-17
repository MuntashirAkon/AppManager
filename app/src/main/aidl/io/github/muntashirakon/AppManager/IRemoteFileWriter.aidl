// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

interface IRemoteFileWriter {
    void write0(int b);
    void write1(in byte[] b);
    void write2(in byte[] b, int off, int len);
    void flush();
    void sync();
    void close();
}
