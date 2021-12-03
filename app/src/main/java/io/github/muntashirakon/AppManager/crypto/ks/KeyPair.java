// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks;

import androidx.core.util.Pair;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;

import javax.security.auth.DestroyFailedException;

public class KeyPair extends Pair<PrivateKey, Certificate> {
    public KeyPair(PrivateKey first, Certificate second) {
        super(first, second);
    }

    public PrivateKey getPrivateKey() {
        return first;
    }

    public PublicKey getPublicKey() {
        return second.getPublicKey();
    }

    public Certificate getCertificate() {
        return second;
    }

    public void destroy() throws DestroyFailedException {
        try {
            first.destroy();
        } catch (NoSuchMethodError ignore) {
        }
    }
}
