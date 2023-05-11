// SPDX-License-Identifier: Apache-2.0 AND GPL-3-or-later

package io.github.muntashirakon.AppManager.crypto.ks;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Calendar;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.DestroyFailedException;
import javax.security.auth.x500.X500Principal;

import io.github.muntashirakon.AppManager.logs.Log;

// Copyright 2021 Muntashir Al-Islam
// Copyright 2018 New Vector Ltd
public class CompatUtil {
    private static final String TAG = CompatUtil.class.getSimpleName();
    private static final String ANDROID_KEY_STORE_PROVIDER = "AndroidKeyStore";
    private static final String AES_GCM_CIPHER_TYPE = "AES/GCM/NoPadding";
    private static final int AES_GCM_KEY_SIZE_IN_BITS = 128;
    private static final int AES_GCM_IV_LENGTH = 12;
    private static final String AES_LOCAL_PROTECTION_KEY_ALIAS = "aes_local_protection";

    private static final String RSA_WRAP_LOCAL_PROTECTION_KEY_ALIAS = "rsa_wrap_local_protection";
    private static final String RSA_WRAP_CIPHER_TYPE = "RSA/NONE/PKCS1Padding";
    private static final String AES_WRAPPED_PROTECTION_KEY_SHARED_PREFERENCE = "aes_wrapped_local_protection";

    private static final String SHARED_KEY_ANDROID_VERSION_WHEN_KEY_HAS_BEEN_GENERATED =
            "android_version_when_key_has_been_generated";

    private static SecureRandom sPrng;

    /**
     * Returns the AES key used for local storage encryption/decryption with AES/GCM.
     * The key is created if it does not exist already in the keystore.
     * From Marshmallow, this key is generated and operated directly from the android keystore.
     * From KitKat and before Marshmallow, this key is stored in the application shared preferences
     * wrapped by a RSA key generated and operated directly from the android keystore.
     *
     * @param context the context holding the application shared preferences
     */
    @SuppressWarnings({"deprecation", "InlinedApi"})
    @NonNull
    private static synchronized SecretKeyAndVersion getAesGcmLocalProtectionKey(@NonNull Context context)
            throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException,
            NoSuchProviderException, InvalidAlgorithmParameterException, NoSuchPaddingException,
            InvalidKeyException, IllegalBlockSizeException, UnrecoverableKeyException {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE_PROVIDER);
        keyStore.load(null);

        Log.i(TAG, "Loading local protection key");
        SharedPreferences sharedPreferences = context.getSharedPreferences("keystore", Context.MODE_PRIVATE);
        // Get the version of Android when the key has been generated, default to the current version of the system.
        // In the latter case, the key will be generated.
        int androidVersionWhenTheKeyHasBeenGenerated = sharedPreferences.getInt(
                SHARED_KEY_ANDROID_VERSION_WHEN_KEY_HAS_BEEN_GENERATED, Build.VERSION.SDK_INT);

        // Check if there's a key in the Android keystore (M and later)
        if (keyStore.containsAlias(AES_LOCAL_PROTECTION_KEY_ALIAS)) {
            Log.i(TAG, "AES local protection key found in keystore");
            SecretKey secretKey = (SecretKey) keyStore.getKey(AES_LOCAL_PROTECTION_KEY_ALIAS, null);
            if (secretKey == null) {
                throw new KeyStoreException("Could not load AES local protection key from keystore");
            }
            return new SecretKeyAndVersion(secretKey, androidVersionWhenTheKeyHasBeenGenerated);
        }

        // Check if a key has been created on version < M (such as, in case of an OS upgrade)
        SecretKey secretKey = readKeyApiL(sharedPreferences, keyStore);
        if (secretKey != null) {
            return new SecretKeyAndVersion(secretKey, androidVersionWhenTheKeyHasBeenGenerated);
        }

        // Otherwise generate key
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.i(TAG, "Generating AES key with keystore");
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEY_STORE_PROVIDER);
            generator.init(new KeyGenParameterSpec.Builder(AES_LOCAL_PROTECTION_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setKeySize(AES_GCM_KEY_SIZE_IN_BITS)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            secretKey = generator.generateKey();

            sharedPreferences.edit()
                    .putInt(SHARED_KEY_ANDROID_VERSION_WHEN_KEY_HAS_BEEN_GENERATED, Build.VERSION.SDK_INT)
                    .apply();
            return new SecretKeyAndVersion(secretKey, androidVersionWhenTheKeyHasBeenGenerated);
        }

        Log.i(TAG, "Generating RSA key pair with keystore");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", ANDROID_KEY_STORE_PROVIDER);
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        end.add(Calendar.YEAR, 10);

        generator.initialize(new android.security.KeyPairGeneratorSpec.Builder(context)
                .setAlgorithmParameterSpec(new RSAKeyGenParameterSpec(2048,
                        RSAKeyGenParameterSpec.F4))
                .setAlias(RSA_WRAP_LOCAL_PROTECTION_KEY_ALIAS)
                .setSubject(new X500Principal("CN=App Manager"))
                .setStartDate(start.getTime())
                .setEndDate(end.getTime())
                .setSerialNumber(BigInteger.ONE)
                .build());
        KeyPair keyPair = generator.generateKeyPair();

        Log.i(TAG, "Generating wrapped AES key");

        byte[] aesKeyRaw = new byte[AES_GCM_KEY_SIZE_IN_BITS / Byte.SIZE];
        getPrng().nextBytes(aesKeyRaw);
        secretKey = new SecretKeySpec(aesKeyRaw, "AES");

        Cipher cipher = Cipher.getInstance(RSA_WRAP_CIPHER_TYPE);
        cipher.init(Cipher.WRAP_MODE, keyPair.getPublic());
        byte[] wrappedAesKey = cipher.wrap(secretKey);

        sharedPreferences.edit()
                .putString(AES_WRAPPED_PROTECTION_KEY_SHARED_PREFERENCE,
                        Base64.encodeToString(wrappedAesKey, 0))
                .putInt(SHARED_KEY_ANDROID_VERSION_WHEN_KEY_HAS_BEEN_GENERATED, Build.VERSION.SDK_INT)
                .apply();

        return new SecretKeyAndVersion(secretKey, androidVersionWhenTheKeyHasBeenGenerated);
    }

    /**
     * Read the key, which may have been stored when the OS was < M
     *
     * @param sharedPreferences shared pref
     * @param keyStore          key store
     * @return the key if it exists or null
     */
    @Nullable
    private static SecretKey readKeyApiL(@NonNull SharedPreferences sharedPreferences, @NonNull KeyStore keyStore)
            throws KeyStoreException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            UnrecoverableKeyException {
        String wrappedAesKeyString = sharedPreferences.getString(AES_WRAPPED_PROTECTION_KEY_SHARED_PREFERENCE, null);
        if (wrappedAesKeyString != null && keyStore.containsAlias(RSA_WRAP_LOCAL_PROTECTION_KEY_ALIAS)) {
            Log.i(TAG, "RSA + wrapped AES local protection keys found in keystore");
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(RSA_WRAP_LOCAL_PROTECTION_KEY_ALIAS, null);
            byte[] wrappedAesKey = Base64.decode(wrappedAesKeyString, 0);
            Cipher cipher = Cipher.getInstance(RSA_WRAP_CIPHER_TYPE);
            cipher.init(Cipher.UNWRAP_MODE, privateKey);
            return (SecretKey) cipher.unwrap(wrappedAesKey, "AES", Cipher.SECRET_KEY);
        }
        // Key does not exist
        return null;
    }

    /**
     * Returns the unique SecureRandom instance shared for all local storage encryption operations.
     */
    @NonNull
    private static SecureRandom getPrng() {
        if (sPrng == null) {
            sPrng = new SecureRandom();
        }
        return sPrng;
    }

    /**
     * Encrypt the given data
     *
     * @param unencryptedData The data to be encrypted
     * @param context         The context holding the application shared preferences
     */
    @NonNull
    public static AesEncryptedData getEncryptedData(@NonNull byte[] unencryptedData, @NonNull Context context)
            throws InvalidAlgorithmParameterException, UnrecoverableKeyException, NoSuchPaddingException,
            IllegalBlockSizeException, CertificateException, KeyStoreException, NoSuchAlgorithmException,
            IOException, NoSuchProviderException, InvalidKeyException, BadPaddingException, DestroyFailedException {
        SecretKeyAndVersion keyAndVersion = getAesGcmLocalProtectionKey(context);

        Cipher cipher = Cipher.getInstance(AES_GCM_CIPHER_TYPE);
        byte[] iv;

        if (keyAndVersion.getAndroidVersionWhenTheKeyHasBeenGenerated() >= Build.VERSION_CODES.M) {
            cipher.init(Cipher.ENCRYPT_MODE, keyAndVersion.getSecretKey(), getPrng());
            iv = cipher.getIV();
        } else {
            iv = new byte[AES_GCM_IV_LENGTH];
            getPrng().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keyAndVersion.getSecretKey(), new IvParameterSpec(iv));
        }

        if (iv.length != AES_GCM_IV_LENGTH) {
            throw new InvalidAlgorithmParameterException("Invalid IV length " + iv.length);
        }

        byte[] encryptedData = cipher.doFinal(unencryptedData);
        SecretKeyCompat.destroy(keyAndVersion.getSecretKey());
        return new AesEncryptedData(iv, encryptedData);
    }

    /**
     * Decrypt given data.
     *
     * @param context       The context holding the application shared preferences
     * @param encryptedData Data to be decrypted
     * @return Decrypted data.
     */
    @NonNull
    public static byte[] decryptData(@NonNull Context context, @NonNull byte[] encryptedData)
            throws NoSuchPaddingException, NoSuchAlgorithmException, CertificateException,
            InvalidKeyException, KeyStoreException, UnrecoverableKeyException, IllegalBlockSizeException,
            NoSuchProviderException, InvalidAlgorithmParameterException, IOException, ShortBufferException, BadPaddingException {
        ByteBuffer encryptedBuffer = ByteBuffer.wrap(encryptedData);
        int iv_len = encryptedBuffer.get();
        if (iv_len != AES_GCM_IV_LENGTH) {
            throw new InvalidAlgorithmParameterException("Invalid IV length " + iv_len);
        }

        byte[] iv = new byte[AES_GCM_IV_LENGTH];
        encryptedBuffer.get(iv);

        Cipher cipher = Cipher.getInstance(AES_GCM_CIPHER_TYPE);

        SecretKeyAndVersion keyAndVersion = getAesGcmLocalProtectionKey(context);

        AlgorithmParameterSpec spec;

        if (keyAndVersion.getAndroidVersionWhenTheKeyHasBeenGenerated() >= Build.VERSION_CODES.M) {
            spec = new GCMParameterSpec(AES_GCM_KEY_SIZE_IN_BITS, iv);
        } else {
            spec = new IvParameterSpec(iv);
        }

        cipher.init(Cipher.DECRYPT_MODE, keyAndVersion.getSecretKey(), spec);

        ByteBuffer decryptedBuffer = ByteBuffer.allocate(cipher.getOutputSize(encryptedBuffer.remaining()));
        cipher.doFinal(encryptedBuffer, decryptedBuffer);
        return decryptedBuffer.array();
    }
}