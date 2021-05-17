// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.utils.IOUtils;

public class DummyCrypto implements Crypto {
    File[] newFiles;

    @Override
    public boolean encrypt(@NonNull File[] files) {
        // Have to return new files to be processed further
        newFiles = files;
        return true;
    }

    @Override
    public void encrypt(@NonNull InputStream unencryptedStream, @NonNull OutputStream encryptedStream) throws IOException {
        // Do nothing since both are the same stream
    }

    @Override
    public boolean decrypt(@NonNull File[] files) {
        // The new files will be deleted, so don't send
        newFiles = null;
        return true;
    }

    @Override
    public void decrypt(@NonNull InputStream encryptedStream, @NonNull OutputStream unencryptedStream) throws IOException {
        // Do nothing since both are the same stream
    }

    @NonNull
    @Override
    public File[] getNewFiles() {
        if (newFiles == null) return new File[0];
        return newFiles;
    }

    @Override
    public void close() {
    }
}
