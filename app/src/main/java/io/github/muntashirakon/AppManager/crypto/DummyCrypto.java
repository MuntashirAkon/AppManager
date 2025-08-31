// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto;

import androidx.annotation.NonNull;

import java.io.InputStream;
import java.io.OutputStream;

import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.io.Path;

public class DummyCrypto implements Crypto {
    @NonNull
    @Override
    public String getModeName() {
        return CryptoUtils.MODE_NO_ENCRYPTION;
    }

    @Override
    public void encrypt(@NonNull Path[] inputFiles, @NonNull Path[] outputFiles) {
        // Do nothing since both are the same set of files
    }

    @Override
    public void encrypt(@NonNull InputStream unencryptedStream, @NonNull OutputStream encryptedStream) {
        // Do nothing since both are the same stream
    }

    @Override
    public void decrypt(@NonNull Path[] inputFiles, @NonNull Path[] outputFiles) {
        // Do nothing since both are the same set of files
    }

    @Override
    public void decrypt(@NonNull InputStream encryptedStream, @NonNull OutputStream unencryptedStream) {
        // Do nothing since both are the same stream
    }

    @Override
    public void close() {
        // Nothing to close
    }
}
