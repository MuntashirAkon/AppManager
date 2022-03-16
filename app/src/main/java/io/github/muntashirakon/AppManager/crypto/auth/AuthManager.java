// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.auth;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.crypto.RandomChar;
import io.github.muntashirakon.AppManager.utils.AppPref;

public final class AuthManager {
    public static final int AUTH_KEY_SIZE = 24;

    @NonNull
    public static String getKey() {
        return AppPref.getString(AppPref.PrefKey.PREF_AUTHORIZATION_KEY_STR);
    }

    public static void setKey(@NonNull String key) {
        AppPref.set(AppPref.PrefKey.PREF_AUTHORIZATION_KEY_STR, key);
    }

    @NonNull
    public static String generateKey() {
        char[] authKey = new char[AUTH_KEY_SIZE];
        new RandomChar().nextChars(authKey);
        return new String(authKey);
    }
}
