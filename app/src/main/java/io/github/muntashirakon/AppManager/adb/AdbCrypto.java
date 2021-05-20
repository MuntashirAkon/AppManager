// SPDX-License-Identifier: BSD-3-Clause AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.util.Base64;

import androidx.annotation.NonNull;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.Cipher;

import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;

// Copyright 2013 Cameron Gutman
class AdbCrypto {
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
     * @param publicKey RSAPublicKey object to convert
     * @param name      Name without null terminator
     * @return Byte array containing the converted RSAPublicKey object
     */
    @NonNull
    public static byte[] getAdbFormattedRsaPublicKey(@NonNull RSAPublicKey publicKey, @NonNull String name) {
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
        n = publicKey.getModulus();
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

        ByteBuffer buffer = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(KEY_LENGTH_WORDS);
        buffer.putInt(n0inv.negate().intValue());
        for (int i : myN) buffer.putInt(i);
        for (int i : myRr) buffer.putInt(i);

        buffer.putInt(publicKey.getPublicExponent().intValue());

        byte[] convertedKey = Base64.encode(buffer.array(), Base64.NO_WRAP);

        /* The key is base64 encoded with a user@host suffix and terminated with a NUL */
        byte[] nameBytes = (' ' + name + '\u0000').getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[convertedKey.length + nameBytes.length];
        System.arraycopy(convertedKey, 0, payload, 0, convertedKey.length);
        System.arraycopy(nameBytes, 0, payload, convertedKey.length, nameBytes.length);
        return payload;
    }

    /**
     * Signs the ADB SHA1 payload with the private key of this object.
     *
     * @param payload SHA1 payload to sign
     * @return Signed SHA1 payload
     * @throws GeneralSecurityException If signing fails
     */
    public static byte[] signAdbTokenPayload(@NonNull KeyPair keyPair, byte[] payload) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("RSA/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, keyPair.getPrivateKey());
        c.update(SIGNATURE_PADDING);
        return c.doFinal(payload);
    }

    private AdbCrypto() {
    }
}
