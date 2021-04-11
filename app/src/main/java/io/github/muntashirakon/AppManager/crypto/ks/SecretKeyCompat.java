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

import androidx.annotation.NonNull;

import java.lang.reflect.Field;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.DestroyFailedException;

import io.github.muntashirakon.AppManager.utils.Utils;

public class SecretKeyCompat {
    static final Field KEY;

    static {
        Field key = null;
        try {
            //noinspection JavaReflectionMemberAccess
            key = SecretKeySpec.class.getDeclaredField("key");
            key.setAccessible(true);
        } catch (NoSuchFieldException | SecurityException ignored) {
        }

        KEY = key;
    }

    public static void destroy(@NonNull SecretKey secretKey) throws DestroyFailedException {
        // We might want to use the SecretKeySpec#destroy() but it doesn't work either
        if (KEY != null && secretKey instanceof SecretKeySpec) {
            try {
                byte[] key = (byte[]) KEY.get(secretKey);
                if (key != null) {
                    Utils.clearBytes(key);
                }
                KEY.set(secretKey, null);
            } catch (IllegalAccessException | IllegalArgumentException e) {
                DestroyFailedException dfe = new DestroyFailedException(e.toString());
                dfe.setStackTrace(e.getStackTrace());
                throw dfe;
            }
        }
    }
}