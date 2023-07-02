// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.view;

import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.progressindicator.BaseProgressIndicator;

public final class ProgressIndicatorCompat {
    public static <T extends BaseProgressIndicator<?>> void setVisibility(@Nullable T progressIndicator, boolean visible) {
        if (progressIndicator == null) {
            return;
        }
        if (visible) {
            progressIndicator.show();
        } else {
            progressIndicator.hide();
            progressIndicator.setVisibility(View.GONE);
        }
    }
}
