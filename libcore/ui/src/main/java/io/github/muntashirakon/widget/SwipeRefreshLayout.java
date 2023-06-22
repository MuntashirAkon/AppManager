// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.internal.ThemeEnforcement;
import com.google.android.material.theme.overlay.MaterialThemeOverlay;

import io.github.muntashirakon.ui.R;

public class SwipeRefreshLayout extends androidx.swiperefreshlayout.widget.SwipeRefreshLayout {
    private static final int DEFAULT_STYLE_RES = R.style.Widget_AppTheme_SwipeRefreshLayout;

    public SwipeRefreshLayout(@NonNull Context context) {
        this(context, null);
    }

    public SwipeRefreshLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeRefreshLayout(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes final int defStyleAttr) {
        this(context, attrs, defStyleAttr, DEFAULT_STYLE_RES);
    }

    public SwipeRefreshLayout(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes final int defStyleAttr,
                              @StyleRes final int defStyleRes) {
        super(MaterialThemeOverlay.wrap(context, attrs, defStyleAttr, DEFAULT_STYLE_RES), attrs);

        // Ensures that we are using the correctly themed context rather than the context that was
        // passed in.
        context = getContext();

        // Loads additional attributes for view level.
        @SuppressLint("RestrictedApi")
        TypedArray a = ThemeEnforcement.obtainStyledAttributes(context, attrs, R.styleable.SwipeRefreshLayout,
                defStyleAttr, defStyleRes);
        try {
            setProgressBackgroundColorSchemeColor(a.getColor(R.styleable.SwipeRefreshLayout_progressBackgroundColor,
                    MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, -1)));
            setColorSchemeColors(loadIndicatorColors(context, a));
        } finally {
            a.recycle();
        }
    }

    @NonNull
    private static int[] loadIndicatorColors(@NonNull Context context, @NonNull TypedArray typedArray) {
        if (!typedArray.hasValue(R.styleable.SwipeRefreshLayout_indicatorColor)) {
            // Uses theme primary color for indicator if not provided in the attribute set.
            return new int[]{MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, -1)};
        }

        TypedValue indicatorColorValue = typedArray.peekValue(R.styleable.SwipeRefreshLayout_indicatorColor);

        if (indicatorColorValue.type != TypedValue.TYPE_REFERENCE) {
            return new int[]{typedArray.getColor(R.styleable.SwipeRefreshLayout_indicatorColor, -1)};
        }

        int[] indicatorColors = context.getResources().getIntArray(typedArray.getResourceId(
                R.styleable.SwipeRefreshLayout_indicatorColor, -1));
        if (indicatorColors.length == 0) {
            throw new IllegalArgumentException(
                    "indicatorColors cannot be empty when indicatorColor is not used.");
        }
        return indicatorColors;
    }
}
