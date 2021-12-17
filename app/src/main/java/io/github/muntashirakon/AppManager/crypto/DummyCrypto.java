// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto;

import androidx.annotation.NonNull;

import java.io.InputStream;
import java.io.OutputStream;

import io.github.muntashirakon.io.Path;

public class DummyCrypto implements Crypto {
    Path[] newFiles;

    @Override
    public void encrypt(@NonNull Path[] files) {
        // Have to return new files to be processed further
        newFiles = files;
    }

    @Override
    public void encrypt(@NonNull InputStream unencryptedStream, @NonNull OutputStream encryptedStream) {
        // Do nothing since both are the same stream
    }

    @Override
    public void decrypt(@NonNull Path[] files) {
        // The new files will be deleted, so don't send
        newFiles = null;
    }

    @Override
    public void decrypt(@NonNull InputStream encryptedStream, @NonNull OutputStream unencryptedStream) {
        // Do nothing since both are the same stream
    }

    @NonNull
    @Override
    public Path[] getNewFiles() {
        if (newFiles == null) return new Path[0];
        return newFiles;
    }

    @Override
    public void close() {
    }
}
