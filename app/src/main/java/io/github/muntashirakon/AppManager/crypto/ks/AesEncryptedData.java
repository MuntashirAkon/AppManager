// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks;

import androidx.core.util.Pair;

class AesEncryptedData extends Pair<byte[], byte[]> {
    /**
     * Constructor for a Pair.
     *
     * @param iv            the iv object in the Pair
     * @param encryptedData the encryptedData object in the pair
     */
    public AesEncryptedData(byte[] iv, byte[] encryptedData) {
        super(iv, encryptedData);
    }

    public byte[] getIv() {
        return first;
    }

    public byte[] getEncryptedData() {
        return second;
    }
}
