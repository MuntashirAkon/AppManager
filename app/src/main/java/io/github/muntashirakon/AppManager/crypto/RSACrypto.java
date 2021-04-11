/*
 * Copyright (c) 2021 Muntashir Al-Islam
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;

public class RSACrypto extends AESCrypto {
    public static final String TAG = "RSACrypto";

    public static final String RSA_EXT = ".rsa";

    public static final String RSA_KEY_ALIAS = "backup_rsa";

    private static final String RSA_CIPHER_TYPE = "RSA/NONE/OAEPPadding";  // 42 bytes padding

    private static final int AES_KEY_SIZE_BITS = 256;

    public RSACrypto(@NonNull byte[] iv, @Nullable byte[] encryptedAesKey) throws CryptoException {
        // This class extends AES crypto as RSA uses hybrid encryption
        super(iv, CryptoUtils.MODE_RSA, encryptedAesKey);
    }

    @Nullable
    public byte[] getEncryptedAesKey() {
        return super.getEncryptedAesKey();
    }

    @NonNull
    static SecretKey generateAesKey() {
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[AES_KEY_SIZE_BITS/8];
        random.nextBytes(key);
        return new SecretKeySpec(key, "AES");
    }

    @NonNull
    static SecretKey decryptAesKey(@NonNull byte[] encryptedAesKey) throws CryptoException {
        // We only have 32/64 bytes AES key with either 256 or 512 bytes minus 42 bytes of data,
        // so it should work without issues
        KeyPair keyPair;
        try {
            KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
            keyPair = keyStoreManager.getKeyPair(RSA_KEY_ALIAS, null);
            if (keyPair == null) {
                throw new CryptoException("No KeyPair with alias " + RSA_KEY_ALIAS);
            }
        } catch (Exception e) {
            throw new CryptoException(e);
        }
        try {
            Cipher cipher = Cipher.getInstance(RSA_CIPHER_TYPE);
            cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivateKey());
            return new SecretKeySpec(cipher.doFinal(encryptedAesKey), "AES");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | BadPaddingException
                | IllegalBlockSizeException e) {
            throw new CryptoException(e);
        }
    }

    @NonNull
    static byte[] encryptAesKey(@NonNull SecretKey key) throws CryptoException {
        // We only have 32/64 bytes AES key with either 256 or 512 bytes minus 42 bytes of data,
        // so it should work without issues
        KeyPair keyPair;
        try {
            KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
            keyPair = keyStoreManager.getKeyPair(RSA_KEY_ALIAS, null);
            if (keyPair == null) {
                throw new CryptoException("No KeyPair with alias " + RSA_KEY_ALIAS);
            }
        } catch (Exception e) {
            throw new CryptoException(e);
        }
        try {
            Cipher cipher = Cipher.getInstance(RSA_CIPHER_TYPE);
            cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublicKey());
            return cipher.doFinal(key.getEncoded());
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | BadPaddingException
                | IllegalBlockSizeException e) {
            throw new CryptoException(e);
        }
    }
}
