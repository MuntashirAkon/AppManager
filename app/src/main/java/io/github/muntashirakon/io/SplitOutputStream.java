// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class SplitOutputStream extends OutputStream {
    private static final long MAX_BYTES_WRITTEN = 1024 * 1024 * 1024;  // 1GB

    private final List<OutputStream> outputStreams = new ArrayList<>(1);
    private final List<Path> files = new ArrayList<>(1);
    private int currentIndex = -1;
    private long bytesWritten;
    private final long maxBytesPerFile;
    private final String baseName;
    private final Path basePath;

    public SplitOutputStream(@NonNull Path basePath, @NonNull String baseName) {
        this(basePath, baseName, MAX_BYTES_WRITTEN);
    }

    public SplitOutputStream(@NonNull Path basePath, @NonNull String baseName, long maxBytesPerFile) {
        this.basePath = basePath;
        this.baseName = baseName;
        this.maxBytesPerFile = maxBytesPerFile;
        this.bytesWritten = maxBytesPerFile;
    }

    public List<Path> getFiles() {
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
        for (OutputStream stream : outputStreams) {
            stream.flush();
        }
    }

    @WorkerThread
    @Override
    public void close() throws IOException {
        for (OutputStream stream : outputStreams) {
            stream.close();
        }
    }

    @WorkerThread
    private void checkCurrentStream(int nextBytesSize) throws IOException {
        if (bytesWritten + nextBytesSize > maxBytesPerFile) {
            // Need to create a new stream
            Path newFile = getNextFile();
            files.add(newFile);
            outputStreams.add(newFile.openOutputStream());
            ++currentIndex;
            bytesWritten = 0;
        }
    }

    @NonNull
    private Path getNextFile() throws IOException {
        return basePath.createNewFile(baseName + "." + (currentIndex + 1), null);
    }
}
