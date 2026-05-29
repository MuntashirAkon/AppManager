// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public class AuthUtils {
    private static final SecureRandom RANDOM = new SecureRandom();

    public static byte[] generateNonce() {
        byte[] nonce = new byte[32];
        RANDOM.nextBytes(nonce);
        return nonce;
    }

    public static byte[] calculateHmac(String token, byte[] data) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IOException("Failed to calculate HMAC", e);
        }
    }
}