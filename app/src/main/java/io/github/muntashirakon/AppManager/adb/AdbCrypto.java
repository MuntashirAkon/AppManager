/*
 * Copyright (c) 2021 Muntashir Al-Islam
 * Copyright (c) 2016 Anton Tananaev
 * Copyright (c) 2013 Cameron Gutman
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

package io.github.muntashirakon.AppManager.adb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;

/**
 * This class encapsulates the ADB cryptography functions and provides
 * an interface for the storage and retrieval of keys.
 */
class AdbCrypto {

    /**
     * An RSA keypair encapsulated by the AdbCrypto object
     */
    private KeyPair keyPair;

    /**
     * The base 64 conversion interface to use
     */
    private AdbBase64 base64;

    /**
     * The ADB RSA key length in bits
     */
    public static final int KEY_LENGTH_BITS = 2048;

    /**
     * The ADB RSA key length in bytes
     */
    public static final int KEY_LENGTH_BYTES = KEY_LENGTH_BITS / 8;

    /**
     * The ADB RSA key length in words
     */
    public static final int KEY_LENGTH_WORDS = KEY_LENGTH_BYTES / 4;

    /**
     * The RSA signature padding as an int array
     */
    public static final int[] SIGNATURE_PADDING_AS_INT = new int[] {
        0x00, 0x01, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x00,
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00,
        0x04, 0x14
    };

    /**
     * The RSA signature padding as a byte array
     */
    public static byte[] SIGNATURE_PADDING;

    static {
        SIGNATURE_PADDING = new byte[SIGNATURE_PADDING_AS_INT.length];

        for (int i = 0; i < SIGNATURE_PADDING.length; i++)
            SIGNATURE_PADDING[i] = (byte) SIGNATURE_PADDING_AS_INT[i];
    }

    /**
     * Converts a standard RSAPublicKey object to the special ADB format
     *
     * @param pubkey RSAPublicKey object to convert
     * @return Byte array containing the converted RSAPublicKey object
     */
    private static byte[] convertRsaPublicKeyToAdbFormat(RSAPublicKey pubkey) {
        /*
         * ADB literally just saves the RSAPublicKey struct to a file.
         *
         * typedef struct RSAPublicKey {
         * int len; // Length of n[] in number of uint32_t
         * uint32_t n0inv;  // -1 / n[0] mod 2^32
         * uint32_t n[RSANUMWORDS]; // modulus as little endian array
         * uint32_t rr[RSANUMWORDS]; // R^2 as little endian array
         * int exponent; // 3 or 65537
         * } RSAPublicKey;
         */

        /* ------ This part is a Java-ified version of RSA_to_RSAPublicKey from adb_host_auth.c ------ */
        BigInteger r32, r, rr, rem, n, n0inv;

        r32 = BigInteger.ZERO.setBit(32);
        n = pubkey.getModulus();
        r = BigInteger.ZERO.setBit(KEY_LENGTH_WORDS * 32);
        rr = r.modPow(BigInteger.valueOf(2), n);
        rem = n.remainder(r32);
        n0inv = rem.modInverse(r32);

        int[] myN = new int[KEY_LENGTH_WORDS];
        int[] myRr = new int[KEY_LENGTH_WORDS];
        BigInteger[] res;
        for (int i = 0; i < KEY_LENGTH_WORDS; i++) {
            res = rr.divideAndRemainder(r32);
            rr = res[0];
            rem = res[1];
            myRr[i] = rem.intValue();

            res = n.divideAndRemainder(r32);
            n = res[0];
            rem = res[1];
            myN[i] = rem.intValue();
        }

        /* ------------------------------------------------------------------------------------------- */

        ByteBuffer bbuf = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);


        bbuf.putInt(KEY_LENGTH_WORDS);
        bbuf.putInt(n0inv.negate().intValue());
        for (int i : myN)
            bbuf.putInt(i);
        for (int i : myRr)
            bbuf.putInt(i);

        bbuf.putInt(pubkey.getPublicExponent().intValue());
        return bbuf.array();
    }

    /**
     * Creates a new AdbCrypto object from a key pair loaded from files.
     *
     * @param base64     Implementation of base 64 conversion interface required by ADB
     * @param privateKey File containing the RSA private key
     * @param publicKey  File containing the RSA public key
     * @return New AdbCrypto object
     * @throws IOException              If the files cannot be read
     * @throws NoSuchAlgorithmException If an RSA key factory cannot be found
     * @throws InvalidKeySpecException  If a PKCS8 or X509 key spec cannot be found
     */
    public static AdbCrypto loadAdbKeyPair(AdbBase64 base64, File privateKey, File publicKey) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        AdbCrypto crypto = new AdbCrypto();

        int privKeyLength = (int) privateKey.length();
        int pubKeyLength = (int) publicKey.length();
        byte[] privKeyBytes = new byte[privKeyLength];
        byte[] pubKeyBytes = new byte[pubKeyLength];

        FileInputStream privIn = new FileInputStream(privateKey);
        FileInputStream pubIn = new FileInputStream(publicKey);

        privIn.read(privKeyBytes);
        pubIn.read(pubKeyBytes);

        privIn.close();
        pubIn.close();

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privKeyBytes);
        EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(pubKeyBytes);

        crypto.keyPair = new KeyPair(keyFactory.generatePublic(publicKeySpec),
                keyFactory.generatePrivate(privateKeySpec));
        crypto.base64 = base64;

        return crypto;
    }

    /**
     * Creates a new AdbCrypto object from a key pair loaded from files.
     *
     * @param base64     Implementation of base 64 conversion interface required by ADB
     * @param keyPair    RSA key pair
     * @return New AdbCrypto object
     */
    public static AdbCrypto loadAdbKeyPair(AdbBase64 base64, KeyPair keyPair) {
        AdbCrypto crypto = new AdbCrypto();

        crypto.keyPair = keyPair;
        crypto.base64 = base64;

        return crypto;
    }

    /**
     * Creates a new AdbCrypto object by generating a new key pair.
     *
     * @param base64 Implementation of base 64 conversion interface required by ADB
     * @return A new AdbCrypto object
     * @throws NoSuchAlgorithmException If an RSA key factory cannot be found
     */
    public static AdbCrypto generateAdbKeyPair(AdbBase64 base64) throws NoSuchAlgorithmException {
        AdbCrypto crypto = new AdbCrypto();

        KeyPairGenerator rsaKeyPg = KeyPairGenerator.getInstance("RSA");
        rsaKeyPg.initialize(KEY_LENGTH_BITS);

        crypto.keyPair = rsaKeyPg.genKeyPair();
        crypto.base64 = base64;

        return crypto;
    }

    /**
     * Signs the ADB SHA1 payload with the private key of this object.
     *
     * @param payload SHA1 payload to sign
     * @return Signed SHA1 payload
     * @throws GeneralSecurityException If signing fails
     */
    public byte[] signAdbTokenPayload(byte[] payload) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("RSA/ECB/NoPadding");

        c.init(Cipher.ENCRYPT_MODE, keyPair.getPrivate());

        c.update(SIGNATURE_PADDING);

        return c.doFinal(payload);
    }

    /**
     * Gets the RSA public key in ADB format.
     *
     * @return Byte array containing the RSA public key in ADB format.
     */
    public byte[] getAdbPublicKeyPayload() {
        byte[] convertedKey = convertRsaPublicKeyToAdbFormat((RSAPublicKey) keyPair.getPublic());

        /* The key is base64 encoded with a user@host suffix and terminated with a NUL */

        String keyString = base64.encodeToString(convertedKey) + " unknown@unknown" + '\0';
        return keyString.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Saves the AdbCrypto's key pair to the specified files.
     *
     * @param privateKey The file to store the encoded private key
     * @param publicKey  The file to store the encoded public key
     * @throws IOException If the files cannot be written
     */
    public void saveAdbKeyPair(File privateKey, File publicKey) throws IOException {
        FileOutputStream privOut = new FileOutputStream(privateKey);
        FileOutputStream pubOut = new FileOutputStream(publicKey);

        privOut.write(keyPair.getPrivate().getEncoded());
        pubOut.write(keyPair.getPublic().getEncoded());

        privOut.close();
        pubOut.close();
    }

}
