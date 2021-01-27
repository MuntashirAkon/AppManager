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

package io.github.muntashirakon.io;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class SplitOutputStream extends OutputStream {
    private static final long MAX_BYTES_WRITTEN = 1024*1024*1024;  // 1GB

    private final List<ProxyOutputStream> outputStreams = new ArrayList<>(1);
    private int currentIndex = -1;
    private long bytesWritten;
    private final long maxBytesPerFile;
    private final String baseFile;

    public SplitOutputStream(@NonNull String baseFile) {
        this(baseFile, MAX_BYTES_WRITTEN);
    }

    public SplitOutputStream(@NonNull File baseFile) {
        this(baseFile.getAbsolutePath());
    }

    public SplitOutputStream(@NonNull String baseFile, long maxBytesPerFile) {
        this.baseFile = baseFile;
        this.maxBytesPerFile = maxBytesPerFile;
        this.bytesWritten = maxBytesPerFile;
    }

    public SplitOutputStream(@NonNull File baseFile, long maxBytesPerFile) {
        this(baseFile.getAbsolutePath(), maxBytesPerFile);
    }

    @Override
    public void write(int b) throws IOException {
        checkCurrentStream(1);
        outputStreams.get(currentIndex).write(b);
        ++bytesWritten;
    }

    @Override
    public void write(@NonNull byte[] b) throws IOException {
        checkCurrentStream(b.length);
        outputStreams.get(currentIndex).write(b);
        bytesWritten += b.length;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkCurrentStream(len);
        outputStreams.get(currentIndex).write(b, off, len);
        bytesWritten += len;
    }

    @Override
    public void flush() throws IOException {
        for (ProxyOutputStream stream : outputStreams) {
            stream.flush();
        }
    }

    @Override
    public void close() throws IOException {
        for (ProxyOutputStream stream : outputStreams) {
            stream.close();
        }
    }

    private void checkCurrentStream(int nextBytesSize) throws IOException {
        if (bytesWritten + nextBytesSize > maxBytesPerFile) {
            // Need to create a new stream
            try {
                outputStreams.add(new ProxyOutputStream(getNextFile()));
                ++currentIndex;
                bytesWritten = 0;
            } catch (Throwable th) {
                throw new IOException(th);
            }
        }
    }

    @NonNull
    private File getNextFile() {
        return new ProxyFile(baseFile + "." + (currentIndex + 1));
    }
}
