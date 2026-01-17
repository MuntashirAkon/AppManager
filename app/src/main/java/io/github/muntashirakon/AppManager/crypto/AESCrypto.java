// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.io.CipherInputStream;
import org.bouncycastle.crypto.io.CipherOutputStream;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.modes.GCMModeCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.crypto.SecretKey;
import javax.security.auth.DestroyFailedException;

import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.crypto.ks.SecretKeyCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;

public class AESCrypto implements Crypto {
    public static final String TAG = "AESCrypto";

    public static final String AES_EXT = ".aes";
    public static final String AES_KEY_ALIAS = "backup_aes";
    public static final int GCM_IV_SIZE_BYTES = 12;
    public static final int MAC_SIZE_BITS_OLD = 32;
    public static final int MAC_SIZE_BITS = 128;

    private final SecretKey mSecretKey;
    private final byte[] mIv;
    @CryptoUtils.Mode
    private final String mParentMode;

    private int mMacSizeBits = MAC_SIZE_BITS;

    public AESCrypto(@NonNull byte[] iv) throws CryptoException {
        this(iv, CryptoUtils.MODE_AES, null);
    }

    @NonNull
    @Override
    public String getModeName() {
        return mParentMode;
    }

    protected AESCrypto(@NonNull byte[] iv, @NonNull @CryptoUtils.Mode String mode, @Nullable byte[] encryptedAesKey)
            throws CryptoException {
        mIv = iv;
        mParentMode = mode;
        switch (mParentMode) {
            case CryptoUtils.MODE_AES:
                try {
                    KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
                    mSecretKey = keyStoreManager.getSecretKey(AES_KEY_ALIAS);
                    if (mSecretKey == null) {
                        throw new CryptoException("No SecretKey with alias " + AES_KEY_ALIAS);
                    }
                } catch (Exception e) {
                    throw new CryptoException(e);
                }
                break;
            case CryptoUtils.MODE_RSA:
                // Hybrid encryption using RSA
                if (encryptedAesKey == null) {
                    // No encryption key provided, generate one
                    mSecretKey = RSACrypto.generateAesKey();
                } else {
                    // Encryption key provided
                    mSecretKey = RSACrypto.decryptAesKey(encryptedAesKey);
                }
                break;
            case CryptoUtils.MODE_ECC:
                // Hybrid encryption using ECC
                if (encryptedAesKey == null) {
                    // No encryption key provided, generate one
                    mSecretKey = ECCCrypto.generateAesKey();
                } else {
                    // Encryption key provided
                    mSecretKey = ECCCrypto.decryptAesKey(encryptedAesKey);
                }
                break;
            default:
                throw new CryptoException("Unsupported mode " + mParentMode);
        }
    }

    public void setMacSizeBits(int macSizeBits) {
        if (macSizeBits == MAC_SIZE_BITS || macSizeBits == MAC_SIZE_BITS_OLD) {
            mMacSizeBits = macSizeBits;
        }
    }

    @NonNull
    private AEADParameters getParams() {
        // We need to generate it dynamically due to MAC size issues
        return new AEADParameters(new KeyParameter(mSecretKey.getEncoded()), mMacSizeBits, mIv);
    }

    @CallSuper
    @NonNull
    protected byte[] getEncryptedAesKey() throws CryptoException {
        if (mParentMode.equals(CryptoUtils.MODE_RSA)) {
            return RSACrypto.encryptAesKey(mSecretKey);
        }
        if (mParentMode.equals(CryptoUtils.MODE_ECC)) {
            return ECCCrypto.encryptAesKey(mSecretKey);
        }
        // Invalid mode
        throw new CryptoException("Not in RSA or ECC mode");
    }

    @WorkerThread
    @Override
    public void encrypt(@NonNull Path[] inputFiles, @NonNull Path[] outputFiles) throws IOException {
        handleFiles(true, inputFiles, outputFiles);
    }

    @Override
    public void encrypt(@NonNull InputStream unencryptedStream, @NonNull OutputStream encryptedStream)
            throws IOException {
        // Init cipher
        GCMModeCipher cipher = GCMBlockCipher.newInstance(AESEngine.newInstance());
        cipher.init(true, getParams());
        // Convert unencrypted stream to encrypted stream
        try (OutputStream cipherOS = new CipherOutputStream(encryptedStream, cipher)) {
            IoUtils.copy(unencryptedStream, cipherOS);
        }
    }

    @WorkerThread
    @Override
    public void decrypt(@NonNull Path[] inputFiles, @NonNull Path[] outputFiles) throws IOException {
        handleFiles(false, inputFiles, outputFiles);
    }

    @Override
    public void decrypt(@NonNull InputStream encryptedStream, @NonNull OutputStream unencryptedStream)
            throws IOException {
        // Init cipher
        GCMModeCipher cipher = GCMBlockCipher.newInstance(AESEngine.newInstance());
        cipher.init(false, getParams());
        // Convert encrypted stream to unencrypted stream
        try (InputStream cipherIS = new CipherInputStream(encryptedStream, cipher)) {
            IoUtils.copy(cipherIS, unencryptedStream);
        }
    }

    @WorkerThread
    private void handleFiles(boolean forEncryption, @NonNull Path[] inputFiles, @NonNull Path[] outputFiles) throws IOException {
        // `files` is never null here
        if (inputFiles.length == 0) {
            Log.d(TAG, "No files to de/encrypt");
            return;
        }
        if (inputFiles.length != outputFiles.length) {
            throw new IOException("The number of input and output files are not the same.");
        }
        // Init cipher
        GCMModeCipher cipher = GCMBlockCipher.newInstance(AESEngine.newInstance());
        cipher.init(forEncryption, getParams());
        // Encrypt/decrypt files
        for (int i = 0; i < inputFiles.length; i++) {
            Path inputPath = inputFiles[i];
            Path outputPath = outputFiles[i];
            Log.i(TAG, "Input: %s\nOutput: %s", inputPath, outputPath);
            try (InputStream is = inputPath.openInputStream();
                 OutputStream os = outputPath.openOutputStream()) {
                if (forEncryption) {
                    try (OutputStream cipherOS = new CipherOutputStream(os, cipher)) {
                        IoUtils.copy(is, cipherOS);
                    }
                } else {  // Cipher.DECRYPT_MODE
                    try (InputStream cipherIS = new CipherInputStream(is, cipher)) {
                        IoUtils.copy(cipherIS, os);
                    }
                }
            }
        }
        // Total success
    }

    @Override
    public void close() {
        try {
            SecretKeyCompat.destroy(mSecretKey);
        } catch (DestroyFailedException e) {
            e.printStackTrace();
        }
    }
}
