// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.crypto.ks;

import androidx.core.util.Pair;

import javax.crypto.SecretKey;

/**
 * Tuple which contains the secret key and the version of Android when the key has been generated
 */
// Copyright 2018 New Vector Ltd
public class SecretKeyAndVersion extends Pair<SecretKey, Integer> {
    /**
     * @param secretKey                                The key
     * @param androidVersionWhenTheKeyHasBeenGenerated The android version when the key has been generated
     */
    public SecretKeyAndVersion(SecretKey secretKey, int androidVersionWhenTheKeyHasBeenGenerated) {
        super(secretKey, androidVersionWhenTheKeyHasBeenGenerated);
    }

    /**
     * Get the key
     *
     * @return The key
     */
    public SecretKey getSecretKey() {
        return first;
    }

    /**
     * Get the android version when the key has been generated
     *
     * @return The android version when the key has been generated
     */
    public int getAndroidVersionWhenTheKeyHasBeenGenerated() {
        return second;
    }
}