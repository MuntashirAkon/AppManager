// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;

public class TextUtilsCompat {
    @Contract("null -> true")
    public static boolean isEmpty(@Nullable CharSequence text) {
        return TextUtils.isEmpty(text);
    }
}
