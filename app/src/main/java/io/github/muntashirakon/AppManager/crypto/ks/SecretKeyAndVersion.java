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

import javax.crypto.SecretKey;

/**
 * Tuple which contains the secret key and the version of Android when the key has been generated
 */
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