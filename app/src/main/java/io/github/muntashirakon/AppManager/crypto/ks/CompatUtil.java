// SPDX-License-Identifier: Apache-2.0 AND GPL-3-or-later

package io.github.muntashirakon.AppManager.crypto.ks;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
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
import javax.crypto.CipherInputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.DestroyFailedException;
import javax.security.auth.x500.X500Principal;

import io.github.muntashirakon.AppManager.logs.Log;

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

    private static SecretKeyAndVersion sSecretKeyAndVersion;
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
    private static synchronized SecretKeyAndVersion getAesGcmLocalProtectionKey(@NonNull Context context)
            throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException,
            NoSuchProviderException, InvalidAlgorithmParameterException, NoSuchPaddingException,
            InvalidKeyException, IllegalBlockSizeException, UnrecoverableKeyException {
        if (sSecretKeyAndVersion == null) {
            final KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE_PROVIDER);
            keyStore.load(null);

            Log.i(TAG, "Loading local protection key");

            SecretKey key;

            final SharedPreferences sharedPreferences = context.getSharedPreferences("keystore",
                    Context.MODE_PRIVATE);
            // Get the version of Android when the key has been generated, default to the current version of the system.
            // In this case, the key will be generated
            final int androidVersionWhenTheKeyHasBeenGenerated = sharedPreferences.getInt(
                    SHARED_KEY_ANDROID_VERSION_WHEN_KEY_HAS_BEEN_GENERATED, Build.VERSION.SDK_INT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (keyStore.containsAlias(AES_LOCAL_PROTECTION_KEY_ALIAS)) {
                    Log.i(TAG, "AES local protection key found in keystore");
                    key = (SecretKey) keyStore.getKey(AES_LOCAL_PROTECTION_KEY_ALIAS, null);
                } else {
                    // Check if a key has been created on version < M (in case of OS upgrade)
                    key = readKeyApiL(sharedPreferences, keyStore);

                    if (key == null) {
                        Log.i(TAG, "Generating AES key with keystore");
                        final KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                                ANDROID_KEY_STORE_PROVIDER);
                        generator.init(new KeyGenParameterSpec.Builder(AES_LOCAL_PROTECTION_KEY_ALIAS,
                                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setKeySize(AES_GCM_KEY_SIZE_IN_BITS)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .build());
                        key = generator.generateKey();

                        sharedPreferences.edit()
                                .putInt(SHARED_KEY_ANDROID_VERSION_WHEN_KEY_HAS_BEEN_GENERATED, Build.VERSION.SDK_INT)
                                .apply();
                    }
                }
            } else {
                key = readKeyApiL(sharedPreferences, keyStore);

                if (key == null) {
                    Log.i(TAG, "Generating RSA key pair with keystore");
                    final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA",
                            ANDROID_KEY_STORE_PROVIDER);
                    final Calendar start = Calendar.getInstance();
                    final Calendar end = Calendar.getInstance();
                    end.add(Calendar.YEAR, 10);

                    generator.initialize(new KeyPairGeneratorSpec.Builder(context)
                            .setAlgorithmParameterSpec(new RSAKeyGenParameterSpec(2048,
                                    RSAKeyGenParameterSpec.F4))
                            .setAlias(RSA_WRAP_LOCAL_PROTECTION_KEY_ALIAS)
                            .setSubject(new X500Principal("CN=App Manager"))
                            .setStartDate(start.getTime())
                            .setEndDate(end.getTime())
                            .setSerialNumber(BigInteger.ONE)
                            .build());
                    final KeyPair keyPair = generator.generateKeyPair();

                    Log.i(TAG, "Generating wrapped AES key");

                    final byte[] aesKeyRaw = new byte[AES_GCM_KEY_SIZE_IN_BITS / Byte.SIZE];
                    getPrng().nextBytes(aesKeyRaw);
                    key = new SecretKeySpec(aesKeyRaw, "AES");

                    final Cipher cipher = Cipher.getInstance(RSA_WRAP_CIPHER_TYPE);
                    cipher.init(Cipher.WRAP_MODE, keyPair.getPublic());
                    byte[] wrappedAesKey = cipher.wrap(key);

                    sharedPreferences.edit()
                            .putString(AES_WRAPPED_PROTECTION_KEY_SHARED_PREFERENCE,
                                    Base64.encodeToString(wrappedAesKey, 0))
                            .putInt(SHARED_KEY_ANDROID_VERSION_WHEN_KEY_HAS_BEEN_GENERATED, Build.VERSION.SDK_INT)
                            .apply();
                }
            }

            sSecretKeyAndVersion = new SecretKeyAndVersion(key, androidVersionWhenTheKeyHasBeenGenerated);
        }

        return sSecretKeyAndVersion;
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
        final String wrappedAesKeyString = sharedPreferences.getString(AES_WRAPPED_PROTECTION_KEY_SHARED_PREFERENCE, null);
        if (wrappedAesKeyString != null && keyStore.containsAlias(RSA_WRAP_LOCAL_PROTECTION_KEY_ALIAS)) {
            Log.i(TAG, "RSA + wrapped AES local protection keys found in keystore");
            final PrivateKey privateKey = (PrivateKey) keyStore.getKey(RSA_WRAP_LOCAL_PROTECTION_KEY_ALIAS, null);
            final byte[] wrappedAesKey = Base64.decode(wrappedAesKeyString, 0);
            final Cipher cipher = Cipher.getInstance(RSA_WRAP_CIPHER_TYPE);
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
        final SecretKeyAndVersion keyAndVersion = getAesGcmLocalProtectionKey(context);
        if (keyAndVersion == null || keyAndVersion.getSecretKey() == null) {
            throw new KeyStoreException();
        }

        final Cipher cipher = Cipher.getInstance(AES_GCM_CIPHER_TYPE);
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
     * Create a CipherInputStream instance.
     * Warning, if `in` is not an encrypted stream, it's up to the caller to close and reopen `in`, because the stream has been read.
     *
     * @param in      The input stream
     * @param context The context holding the application shared preferences
     * @return The created InputStream, or null if the InputStream `in`  does not contain encrypted data
     */
    @NonNull
    public static CipherInputStream createCipherInputStream(@NonNull InputStream in, @NonNull Context context)
            throws NoSuchPaddingException, NoSuchAlgorithmException, CertificateException,
            InvalidKeyException, KeyStoreException, UnrecoverableKeyException, IllegalBlockSizeException,
            NoSuchProviderException, InvalidAlgorithmParameterException, IOException {
        final int iv_len = in.read();
        if (iv_len != AES_GCM_IV_LENGTH) {
            throw new InvalidAlgorithmParameterException("Invalid IV length " + iv_len);
        }

        final byte[] iv = new byte[AES_GCM_IV_LENGTH];
        in.read(iv);

        final Cipher cipher = Cipher.getInstance(AES_GCM_CIPHER_TYPE);

        final SecretKeyAndVersion keyAndVersion = getAesGcmLocalProtectionKey(context);
        if (keyAndVersion == null || keyAndVersion.getSecretKey() == null) {
            throw new KeyStoreException();
        }

        AlgorithmParameterSpec spec;

        if (keyAndVersion.getAndroidVersionWhenTheKeyHasBeenGenerated() >= Build.VERSION_CODES.M) {
            spec = new GCMParameterSpec(AES_GCM_KEY_SIZE_IN_BITS, iv);
        } else {
            spec = new IvParameterSpec(iv);
        }

        cipher.init(Cipher.DECRYPT_MODE, keyAndVersion.getSecretKey(), spec);

        return new CipherInputStream(in, cipher);
    }
}