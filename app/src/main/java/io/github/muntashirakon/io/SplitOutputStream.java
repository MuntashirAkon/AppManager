// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class SplitOutputStream extends OutputStream {
    private static final long MAX_BYTES_WRITTEN = 1024*1024*1024;  // 1GB

    private final List<ProxyOutputStream> outputStreams = new ArrayList<>(1);
    private final List<File> files = new ArrayList<>(1);
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

    public List<File> getFiles() {
        return files;
    }

    @WorkerThread
    @Override
    public void write(int b) throws IOException {
        checkCurrentStream(1);
        outputStreams.get(currentIndex).write(b);
        ++bytesWritten;
    }

    @WorkerThread
    @Override
    public void write(@NonNull byte[] b) throws IOException {
        checkCurrentStream(b.length);
        outputStreams.get(currentIndex).write(b);
        bytesWritten += b.length;
    }

    @WorkerThread
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkCurrentStream(len);
        outputStreams.get(currentIndex).write(b, off, len);
        bytesWritten += len;
    }

    @WorkerThread
    @Override
    public void flush() throws IOException {
        for (ProxyOutputStream stream : outputStreams) {
            stream.flush();
        }
    }

    @WorkerThread
    @Override
    public void close() throws IOException {
        for (ProxyOutputStream stream : outputStreams) {
            stream.close();
        }
    }

    @WorkerThread
    private void checkCurrentStream(int nextBytesSize) throws IOException {
        if (bytesWritten + nextBytesSize > maxBytesPerFile) {
            // Need to create a new stream
            try {
                File newFile = getNextFile();
                files.add(newFile);
                outputStreams.add(new ProxyOutputStream(newFile));
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
