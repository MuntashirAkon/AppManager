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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.security.auth.DestroyFailedException;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.IOUtils;

public class AESCrypto implements Crypto {
    public static final String TAG = "AESCrypto";

    public static final String AES_EXT = ".aes";

    public static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    public static final String AES_KEY_ALIAS = "aes";

    private static final String AES_GCM_CIPHER_TYPE = "AES/GCM/NoPadding";
    public static final int GCM_IV_LENGTH = 12; // in bytes

    private final byte[] iv;
    private final SecretKey secretKey;
    private final Cipher cipher;
    private final List<File> newFiles = new ArrayList<>();

    public AESCrypto(byte[] iv) throws CryptoException {
        this.iv = iv;
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEY_STORE);
            ks.load(null);
            this.secretKey = (SecretKey) ks.getKey(AES_KEY_ALIAS, null);
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException | UnrecoverableEntryException e) {
            throw new CryptoException(e);
        }
        try {
            this.cipher = Cipher.getInstance(AES_GCM_CIPHER_TYPE);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new CryptoException(e);
        }
    }

    @Override
    public boolean encrypt(@NonNull File[] files) {
        return handleFiles(Cipher.ENCRYPT_MODE, files);
    }

    @Override
    public boolean decrypt(@NonNull File[] files) {
        return handleFiles(Cipher.DECRYPT_MODE, files);
    }

    private boolean handleFiles(int mode, @NonNull File[] files) {
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
                    outputFilename = new File(file.getAbsolutePath().substring(0, file.getAbsolutePath().lastIndexOf(AES_EXT)));
                } else outputFilename = new File(file.getAbsolutePath() + AES_EXT);
                newFiles.add(outputFilename);
                Log.i(TAG, "Input: " + file + "\nOutput: " + outputFilename);
                try (FileInputStream is = new FileInputStream(file);
                     FileOutputStream os = new FileOutputStream(outputFilename)) {
                    if (mode == Cipher.ENCRYPT_MODE) {
                        OutputStream cipherOS = new CipherOutputStream(os, cipher);
                        IOUtils.copy(is, os);
                        cipherOS.close();
                    } else {  // Cipher.DECRYPT_MODE
                        InputStream cipherIS = new CipherInputStream(is, cipher);
                        IOUtils.copy(cipherIS, os);
                        cipherIS.close();
                    }
                } catch (IOException e) {
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
