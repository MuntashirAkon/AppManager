// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto;

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
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.Path;

public class AESCrypto implements Crypto {
    public static final String TAG = "AESCrypto";

    public static final String AES_EXT = ".aes";

    public static final String AES_KEY_ALIAS = "backup_aes";

    public static final int GCM_IV_LENGTH = 12; // in bytes

    private final SecretKey secretKey;
    private final AEADParameters spec;
    @CryptoUtils.Mode
    private final String parentMode;
    private final List<Path> newFiles = new ArrayList<>();

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
    public boolean encrypt(@NonNull Path[] files) {
        return handleFiles(true, files);
    }

    @Override
    public void encrypt(@NonNull InputStream unencryptedStream, @NonNull OutputStream encryptedStream)
            throws IOException, InvalidAlgorithmParameterException, InvalidKeyException {
        // Init cipher
        GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
        cipher.init(true, spec);
        // Convert unencrypted stream to encrypted stream
        try (OutputStream cipherOS = new CipherOutputStream(encryptedStream, cipher)) {
            FileUtils.copy(unencryptedStream, cipherOS);
        }
    }

    @WorkerThread
    @Override
    public boolean decrypt(@NonNull Path[] files) {
        return handleFiles(false, files);
    }

    @Override
    public void decrypt(@NonNull InputStream encryptedStream, @NonNull OutputStream unencryptedStream)
            throws IOException, InvalidAlgorithmParameterException, InvalidKeyException {
        // Init cipher
        GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
        cipher.init(false, spec);
        // Convert encrypted stream to unencrypted stream
        try (InputStream cipherIS = new CipherInputStream(encryptedStream, cipher)) {
            FileUtils.copy(cipherIS, unencryptedStream);
        }
    }

    @WorkerThread
    private boolean handleFiles(boolean forEncryption, @NonNull Path[] files) {
        newFiles.clear();
        if (files.length > 0) {  // files is never null here
            // Init cipher
            GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
            cipher.init(forEncryption, spec);
            // Get desired extension
            String ext = CryptoUtils.getExtension(parentMode);
            // Encrypt/decrypt files
            for (Path inputPath : files) {
                Path parent = inputPath.getParentFile();
                if (parent == null) {
                    Log.e(TAG, "Parent file cannot be null.");
                    return false;
                }
                String outputFilename;
                if (!forEncryption) {
                    outputFilename = inputPath.getName().substring(0, inputPath.getName().lastIndexOf(ext));
                } else outputFilename = inputPath.getName() + ext;
                try {
                    Path outputPath = parent.createNewFile(outputFilename, null);
                    newFiles.add(outputPath);
                    Log.i(TAG, "Input: " + inputPath + "\nOutput: " + outputPath);
                    try (InputStream is = inputPath.openInputStream();
                         OutputStream os = outputPath.openOutputStream()) {
                        if (forEncryption) {
                            try (OutputStream cipherOS = new CipherOutputStream(os, cipher)) {
                                FileUtils.copy(is, cipherOS);
                            }
                        } else {  // Cipher.DECRYPT_MODE
                            try (InputStream cipherIS = new CipherInputStream(is, cipher)) {
                                FileUtils.copy(cipherIS, os);
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error: " + e.getMessage(), e);
                    return false;
                }
                // Delete unencrypted file
                if (forEncryption) {
                    if (!inputPath.delete()) {
                        Log.e(TAG, "Couldn't delete old file " + inputPath);
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
    public Path[] getNewFiles() {
        return newFiles.toArray(new Path[0]);
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
