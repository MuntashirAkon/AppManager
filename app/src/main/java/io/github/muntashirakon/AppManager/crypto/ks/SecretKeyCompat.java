// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.DestroyFailedException;

import io.github.muntashirakon.AppManager.utils.Utils;

@SuppressLint("SoonBlockedPrivateApi")
public final class SecretKeyCompat {
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