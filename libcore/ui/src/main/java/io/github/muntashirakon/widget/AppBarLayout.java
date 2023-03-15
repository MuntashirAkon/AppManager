// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import com.google.android.material.internal.ViewUtils;

public class AppBarLayout extends com.google.android.material.appbar.AppBarLayout {
    public AppBarLayout(@NonNull Context context) {
        this(context, null);
    }

    public AppBarLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.appBarLayoutStyle);
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("RestrictedApi")
    public AppBarLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ViewUtils.doOnApplyWindowInsets(this, (view, insets, initialPadding) -> {
            if (!ViewCompat.getFitsSystemWindows(view)) {
                return insets;
            }
            initialPadding.top += insets.getSystemWindowInsetTop();
            boolean isRtl = ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL;
            int systemWindowInsetLeft = insets.getSystemWindowInsetLeft();
            int systemWindowInsetRight = insets.getSystemWindowInsetRight();
            initialPadding.start += isRtl ? systemWindowInsetRight : systemWindowInsetLeft;
            initialPadding.end += isRtl ? systemWindowInsetLeft : systemWindowInsetRight;
            initialPadding.applyToView(view);
            return insets;
        });
    }
}
