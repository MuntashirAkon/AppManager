// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

interface IFileDescriptor {
    int read(inout byte[] b, int off, int len);
    int write(in byte[] b, int off, int len);
    void sync();
    int available();
    void close();
}
