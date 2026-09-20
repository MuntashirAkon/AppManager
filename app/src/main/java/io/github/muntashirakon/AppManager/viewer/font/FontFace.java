// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.font;

import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

final class FontFace {
    @NonNull
    final Typeface typeface;
    final int ttcIndex;
    final int weight;
    final int style;
    @Nullable
    final String variationSettings;

    FontFace(@NonNull Typeface typeface, int ttcIndex, int weight, int style,
             @Nullable String variationSettings) {
        this.typeface = typeface;
        this.ttcIndex = ttcIndex;
        this.weight = weight;
        this.style = style;
        this.variationSettings = variationSettings;
    }
}
