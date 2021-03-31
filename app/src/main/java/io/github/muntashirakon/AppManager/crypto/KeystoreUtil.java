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

import android.content.Context;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.Calendar;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.security.auth.x500.X500Principal;

/**
 * Created by xcelder1 on 11/7/16.
 */
public class KeystoreUtil {

    KeyStore keyStore;
    KeyPair keyPair;
    static final String ALIAS = "am_keystore"; //Enter your alias here (only a name for the key pair instance)
    static final String KEY_ALGORITHM_RSA = "RSA";
    static final String KEYSTORE_NAME = "AndroidKeyStore";
    private static final String ALGORITHM = "RSA/ECB/PKCS1Padding";
    private static final String ALGORITHM_M = "RSA/None/PKCS1Padding";
    private static final String PROVIDER = "AndroidOpenSSL";
    private static final String PROVIDER_M = "AndroidKeyStoreBCWorkaround";

    private static KeystoreUtil instance;

    private static KeystoreUtil getInstance(Context context) {
        if (instance == null) {
            try {
                instance = new KeystoreUtil(context);
            } catch (KeystoreManagerException e) {
                e.printStackTrace();
            }
        }
        return instance;
    }

    @SuppressWarnings("deprecation")
    public KeystoreUtil(@NonNull Context context) throws KeystoreManagerException {
        try {
            keyStore = KeyStore.getInstance(KEYSTORE_NAME);
            keyStore.load(null);
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            throw new KeystoreManagerException(e);
        }
        try {
            // Create new key if needed
            if (!keyStore.containsAlias(ALIAS)) {
                KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM_RSA, KEYSTORE_NAME);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                            ALIAS,
                            KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_ENCRYPT)
                            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                            .build();
                    generator.initialize(spec);
                } else {
                    Calendar start = Calendar.getInstance();
                    Calendar end = Calendar.getInstance();
                    end.add(Calendar.YEAR, 20);
                    KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context)
                            .setAlias(ALIAS)
                            .setSubject(new X500Principal("CN=App Manager"))
                            .setSerialNumber(BigInteger.ONE)
                            .setStartDate(start.getTime())
                            .setEndDate(end.getTime())
                            .build();
                    generator.initialize(spec);
                }

                keyPair = generator.generateKeyPair();
            }

        } catch (Exception e) {
            throw new KeystoreManagerException(e);
        }
    }

    @NonNull
    public static CipherOutputStream createCipherOutputStream(@NonNull OutputStream out, @NonNull Context context)
            throws NoSuchPaddingException, NoSuchAlgorithmException, NoSuchProviderException,
            KeyStoreException, UnrecoverableEntryException, InvalidKeyException {
        KeyStore keyStore = getInstance(context).keyStore;
        KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(ALIAS, null);
        PublicKey publicKey = privateKeyEntry.getCertificate().getPublicKey();
        Cipher input;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            input = Cipher.getInstance(ALGORITHM_M, PROVIDER_M);
        } else input = Cipher.getInstance(ALGORITHM, PROVIDER);
        input.init(Cipher.ENCRYPT_MODE, publicKey);
        return new CipherOutputStream(out, input);
    }

    @NonNull
    public static CipherInputStream createCipherInputStream(@NonNull InputStream in, @NonNull Context context)
            throws UnrecoverableEntryException, NoSuchAlgorithmException, KeyStoreException, NoSuchProviderException,
            NoSuchPaddingException, InvalidKeyException {
        KeyStore keyStore = getInstance(context).keyStore;
        KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(ALIAS, null);
        PrivateKey privateKey = privateKeyEntry.getPrivateKey();
        Cipher output;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
            output = Cipher.getInstance(ALGORITHM_M, PROVIDER_M);
        else
            output = Cipher.getInstance(ALGORITHM, PROVIDER);
        output.init(Cipher.DECRYPT_MODE, privateKey);
        return new CipherInputStream(in, output);
    }

    /**
     * @return A KeyPair with an asymmetric pair of public and private keys generated by the KeyStore instance that was decided with the Alias you have chosen
     */
    public KeyPair getKeyPair() throws KeystoreManagerException {
        try {
            KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(ALIAS, null);
            return new KeyPair(privateKeyEntry.getCertificate().getPublicKey(), privateKeyEntry.getPrivateKey());
        } catch (NoSuchAlgorithmException | UnrecoverableEntryException | KeyStoreException e) {
            throw new KeystoreManagerException(e);
        }
    }
}