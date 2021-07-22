// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.util;

import android.content.Context;

import androidx.annotation.NonNull;

public interface LocalizedString {
    @NonNull
    CharSequence toLocalizedString(@NonNull Context context);
}
