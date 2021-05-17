// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

interface IRemoteFileReader {
    int read0();
    int read1(out byte[] b);
    int read2(inout byte[] b, int off, int len);
    long skip(long n);
    int available();
    void close();
    void mark(int readlimit);
    void reset();
    boolean markSupported();
}
