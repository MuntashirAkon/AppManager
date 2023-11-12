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

    private final List<OutputStream> mOutputStreams = new ArrayList<>(1);
    private final List<Path> mFiles = new ArrayList<>(1);
    private int mCurrentIndex = -1;
    private long mBytesWritten;
    private final long mMaxBytesPerFile;
    private final String mBaseName;
    private final Path mBasePath;

    public SplitOutputStream(@NonNull Path basePath, @NonNull String baseName) {
        this(basePath, baseName, MAX_BYTES_WRITTEN);
    }

    public SplitOutputStream(@NonNull Path basePath, @NonNull String baseName, long maxBytesPerFile) {
        mBasePath = basePath;
        mBaseName = baseName;
        mMaxBytesPerFile = maxBytesPerFile;
        mBytesWritten = maxBytesPerFile;
    }

    public List<Path> getFiles() {
        return mFiles;
    }

    @WorkerThread
    @Override
    public void write(int b) throws IOException {
        checkCurrentStream(1);
        mOutputStreams.get(mCurrentIndex).write(b);
        ++mBytesWritten;
    }

    @WorkerThread
    @Override
    public void write(@NonNull byte[] b) throws IOException {
        checkCurrentStream(b.length);
        mOutputStreams.get(mCurrentIndex).write(b);
        mBytesWritten += b.length;
    }

    @WorkerThread
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkCurrentStream(len);
        mOutputStreams.get(mCurrentIndex).write(b, off, len);
        mBytesWritten += len;
    }

    @WorkerThread
    @Override
    public void flush() throws IOException {
        for (OutputStream stream : mOutputStreams) {
            stream.flush();
        }
    }

    @WorkerThread
    @Override
    public void close() throws IOException {
        for (OutputStream stream : mOutputStreams) {
            stream.close();
        }
    }

    @WorkerThread
    private void checkCurrentStream(int nextBytesSize) throws IOException {
        if (mBytesWritten + nextBytesSize > mMaxBytesPerFile) {
            // Need to create a new stream
            Path newFile = getNextFile();
            mFiles.add(newFile);
            mOutputStreams.add(newFile.openOutputStream());
            ++mCurrentIndex;
            mBytesWritten = 0;
        }
    }

    @NonNull
    private Path getNextFile() throws IOException {
        return mBasePath.createNewFile(mBaseName + "." + (mCurrentIndex + 1), null);
    }
}
