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
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.security.auth.DestroyFailedException;

import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.io.ProxyFile;
import io.github.muntashirakon.io.ProxyInputStream;
import io.github.muntashirakon.io.ProxyOutputStream;

public class AESCrypto implements Crypto {
    public static final String TAG = "AESCrypto";

    public static final String AES_EXT = ".aes";

    public static final String AES_KEY_ALIAS = "backup_aes";

    private static final String AES_GCM_CIPHER_TYPE = "AES/GCM/NoPadding";
    public static final int GCM_IV_LENGTH = 12; // in bytes

    private final byte[] iv;
    private final SecretKey secretKey;
    private final Cipher cipher;
    private final List<File> newFiles = new ArrayList<>();

    public AESCrypto(byte[] iv) throws CryptoException {
        this.iv = iv;
        try {
            KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
            this.secretKey = (SecretKey) keyStoreManager.getKey(AES_KEY_ALIAS, null);
        } catch (Exception e) {
            throw new CryptoException(e);
        }
        try {
            this.cipher = Cipher.getInstance(AES_GCM_CIPHER_TYPE);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new CryptoException(e);
        }
    }

    @WorkerThread
    @Override
    public boolean encrypt(@NonNull File[] files) {
        return handleFiles(Cipher.ENCRYPT_MODE, files);
    }

    @Override
    public void encrypt(@NonNull InputStream unencryptedStream, @NonNull OutputStream encryptedStream)
            throws IOException, InvalidAlgorithmParameterException, InvalidKeyException {
        // Init cipher
        GCMParameterSpec spec = new GCMParameterSpec(secretKey.getEncoded().length, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
        // Convert unencrypted stream to encrypted stream
        try (OutputStream cipherOS = new CipherOutputStream(encryptedStream, cipher)) {
            IOUtils.copy(unencryptedStream, cipherOS);
        }
    }

    @WorkerThread
    @Override
    public boolean decrypt(@NonNull File[] files) {
        return handleFiles(Cipher.DECRYPT_MODE, files);
    }

    @Override
    public void decrypt(@NonNull InputStream encryptedStream, @NonNull OutputStream unencryptedStream)
            throws IOException, InvalidAlgorithmParameterException, InvalidKeyException {
        // Init cipher
        GCMParameterSpec spec = new GCMParameterSpec(secretKey.getEncoded().length, iv);
        // Convert encrypted stream to unencrypted stream
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        try (InputStream cipherIS = new CipherInputStream(encryptedStream, cipher)) {
            IOUtils.copy(cipherIS, unencryptedStream);
        }
    }

    @WorkerThread
    private boolean handleFiles(int mode, @NonNull File[] files) {
        newFiles.clear();
        if (files.length > 0) {  // files is never null here
            // Init cipher
            try {
                GCMParameterSpec spec = new GCMParameterSpec(secretKey.getEncoded().length, iv);
                cipher.init(mode, secretKey, spec);
            } catch (InvalidAlgorithmParameterException | InvalidKeyException e) {
                Log.e(TAG, "Error initializing cipher", e);
                return false;
            }
            // Encrypt/decrypt files
            for (File file : files) {
                File outputFilename;
                if (mode == Cipher.DECRYPT_MODE) {
                    outputFilename = new ProxyFile(file.getAbsolutePath().substring(0, file.getAbsolutePath().lastIndexOf(AES_EXT)));
                } else outputFilename = new ProxyFile(file.getAbsolutePath() + AES_EXT);
                newFiles.add(outputFilename);
                Log.i(TAG, "Input: " + file + "\nOutput: " + outputFilename);
                try (InputStream is = new ProxyInputStream(file);
                     OutputStream os = new ProxyOutputStream(outputFilename)) {
                    if (mode == Cipher.ENCRYPT_MODE) {
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
                if (mode == Cipher.ENCRYPT_MODE) {
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
            secretKey.destroy();
        } catch (DestroyFailedException e) {
            e.printStackTrace();
        }
    }
}
