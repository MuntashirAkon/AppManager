// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto;

import androidx.annotation.NonNull;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

public class RandomChar {
    public static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String LOWERCASE = UPPERCASE.toLowerCase(Locale.ROOT);
    public static final String DIGITS = "0123456789";
    public static final String ALPHA_NUMERIC = UPPERCASE + LOWERCASE + DIGITS;

    private final Random mRandom;
    private final char[] mSymbols;

    public RandomChar() {
        this(new SecureRandom());
    }

    public RandomChar(@NonNull Random random) {
        this(random, ALPHA_NUMERIC);
    }

    public RandomChar(@NonNull Random random, @NonNull String symbols) {
        if (symbols.length() < 2) throw new IllegalArgumentException();
        mRandom = Objects.requireNonNull(random);
        mSymbols = symbols.toCharArray();
    }

    public void nextChars(@NonNull char[] chars) {
        for (int idx = 0; idx < chars.length; ++idx) {
            chars[idx] = mSymbols[mRandom.nextInt(mSymbols.length)];
        }
    }

    public char nextChar() {
        return mSymbols[mRandom.nextInt(mSymbols.length)];
    }
}