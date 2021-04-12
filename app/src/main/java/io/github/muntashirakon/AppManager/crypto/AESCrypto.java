/*
 * Copyright (C) 2020 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.crypto;

import android.os.RemoteException;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.io.CipherInputStream;
import org.bouncycastle.crypto.io.CipherOutputStream;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;
import javax.security.auth.DestroyFailedException;

import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.crypto.ks.SecretKeyCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.io.ProxyFile;
import io.github.muntashirakon.io.ProxyInputStream;
import io.github.muntashirakon.io.ProxyOutputStream;

public class AESCrypto implements Crypto {
    public static final String TAG = "AESCrypto";

    public static final String AES_EXT = ".aes";

    public static final String AES_KEY_ALIAS = "backup_aes";

    public static final int GCM_IV_LENGTH = 12; // in bytes

    private final SecretKey secretKey;
    private final GCMBlockCipher cipher;
    private final AEADParameters spec;
    @CryptoUtils.Mode
    private final String parentMode;
    private final List<File> newFiles = new ArrayList<>();

    public AESCrypto(@NonNull byte[] iv) throws CryptoException {
        this(iv, CryptoUtils.MODE_AES, null);
    }

    protected AESCrypto(@NonNull byte[] iv, @NonNull @CryptoUtils.Mode String mode, @Nullable byte[] encryptedAesKey)
            throws CryptoException {
        this.parentMode = mode;
        if (parentMode.equals(CryptoUtils.MODE_AES)) {
            try {
                KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
                this.secretKey = keyStoreManager.getSecretKey(AES_KEY_ALIAS, null);
                if (this.secretKey == null) {
                    throw new CryptoException("No SecretKey with alias " + AES_KEY_ALIAS);
                }
            } catch (Exception e) {
                throw new CryptoException(e);
            }
        } else if (parentMode.equals(CryptoUtils.MODE_RSA)) {
            // Hybrid encryption using RSA
            if (encryptedAesKey == null) {
                // No encryption key provided, generate one
                this.secretKey = RSACrypto.generateAesKey();
            } else {
                // Encryption key provided
                this.secretKey = RSACrypto.decryptAesKey(encryptedAesKey);
            }
        } else {
            throw new CryptoException("Unsupported mode " + parentMode);
        }
        this.spec = new AEADParameters(new KeyParameter(secretKey.getEncoded()), secretKey.getEncoded().length, iv);
        this.cipher = new GCMBlockCipher(new AESEngine());
    }

    @Nullable
    protected byte[] getEncryptedAesKey() {
        try {
            if (parentMode.equals(CryptoUtils.MODE_RSA)) {
                return RSACrypto.encryptAesKey(secretKey);
            }
        } catch (CryptoException e) {
            Log.e(TAG, e);
        }
        return null;
    }

    @WorkerThread
    @Override
    public boolean encrypt(@NonNull File[] files) {
        return handleFiles(true, files);
    }

    @Override
    public void encrypt(@NonNull InputStream unencryptedStream, @NonNull OutputStream encryptedStream)
            throws IOException, InvalidAlgorithmParameterException, InvalidKeyException {
        // Init cipher
        cipher.init(true, spec);
        // Convert unencrypted stream to encrypted stream
        try (OutputStream cipherOS = new CipherOutputStream(encryptedStream, cipher)) {
            IOUtils.copy(unencryptedStream, cipherOS);
        }
    }

    @WorkerThread
    @Override
    public boolean decrypt(@NonNull File[] files) {
        return handleFiles(false, files);
    }

    @Override
    public void decrypt(@NonNull InputStream encryptedStream, @NonNull OutputStream unencryptedStream)
            throws IOException, InvalidAlgorithmParameterException, InvalidKeyException {
        // Init cipher
        cipher.init(false, spec);
        // Convert encrypted stream to unencrypted stream
        try (InputStream cipherIS = new CipherInputStream(encryptedStream, cipher)) {
            IOUtils.copy(cipherIS, unencryptedStream);
        }
    }

    @WorkerThread
    private boolean handleFiles(boolean forEncryption, @NonNull File[] files) {
        newFiles.clear();
        if (files.length > 0) {  // files is never null here
            // Init cipher
            cipher.init(forEncryption, spec);
            // Get desired extension
            String ext = CryptoUtils.getExtension(parentMode);
            // Encrypt/decrypt files
            for (File file : files) {
                File outputFilename;
                if (!forEncryption) {
                    outputFilename = new ProxyFile(file.getAbsolutePath().substring(0, file.getAbsolutePath().lastIndexOf(ext)));
                } else outputFilename = new ProxyFile(file.getAbsolutePath() + ext);
                newFiles.add(outputFilename);
                Log.i(TAG, "Input: " + file + "\nOutput: " + outputFilename);
                try (InputStream is = new ProxyInputStream(file);
                     OutputStream os = new ProxyOutputStream(outputFilename)) {
                    if (forEncryption) {
                        try (OutputStream cipherOS = new CipherOutputStream(os, cipher)) {
                            IOUtils.copy(is, cipherOS);
                        }
                    } else {  // Cipher.DECRYPT_MODE
                        try (InputStream cipherIS = new CipherInputStream(is, cipher)) {
                            IOUtils.copy(cipherIS, os);
                        }
                    }
                } catch (IOException | RemoteException e) {
                    Log.e(TAG, "Error: " + e.toString(), e);
                    return false;
                }
                // Delete unencrypted file
                if (forEncryption) {
                    if (!file.delete()) {
                        Log.e(TAG, "Couldn't delete old file " + file);
                        return false;
                    }
                }
            }
            // Total success
        } else {
            Log.d(TAG, "No files to de/encrypt");
        }
        return true;
    }

    @AnyThread
    @NonNull
    @Override
    public File[] getNewFiles() {
        return newFiles.toArray(new File[0]);
    }

    @Override
    public void close() {
        try {
            SecretKeyCompat.destroy(secretKey);
        } catch (DestroyFailedException e) {
            e.printStackTrace();
        }
    }
}
