// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.github.muntashirakon.io.Path;

public interface Crypto extends Closeable {
    @WorkerThread
    void encrypt(@NonNull Path[] files) throws IOException;

    @WorkerThread
    void encrypt(@NonNull InputStream unencryptedStream, @NonNull OutputStream encryptedStream) throws IOException;

    @WorkerThread
    void decrypt(@NonNull Path[] files) throws IOException;

    @WorkerThread
    void decrypt(@NonNull InputStream encryptedStream, @NonNull OutputStream unencryptedStream) throws IOException;

    @NonNull
    Path[] getNewFiles();

    @Override
    void close();
}
