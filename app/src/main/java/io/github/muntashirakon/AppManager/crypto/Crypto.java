// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

import io.github.muntashirakon.io.Path;

public interface Crypto extends Closeable {
    @WorkerThread
    boolean encrypt(@NonNull Path[] files);

    @WorkerThread
    void encrypt(@NonNull InputStream unencryptedStream, @NonNull OutputStream encryptedStream)
            throws IOException, GeneralSecurityException;

    @WorkerThread
    boolean decrypt(@NonNull Path[] files);

    @WorkerThread
    void decrypt(@NonNull InputStream encryptedStream, @NonNull OutputStream unencryptedStream)
            throws IOException, GeneralSecurityException;

    @NonNull
    Path[] getNewFiles();

    @Override
    void close();
}
