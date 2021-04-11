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
        first.destroy();
    }
}
