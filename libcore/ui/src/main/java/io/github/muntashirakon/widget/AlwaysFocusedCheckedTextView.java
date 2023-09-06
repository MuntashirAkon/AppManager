// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatCheckedTextView;

/**
 * Same as {@link AppCompatCheckedTextView} except that it's always on focus which fixes issues related to marquee text.
 */
public class AlwaysFocusedCheckedTextView extends AppCompatCheckedTextView {
    public AlwaysFocusedCheckedTextView(@NonNull Context context) {
        super(context);
    }

    public AlwaysFocusedCheckedTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AlwaysFocusedCheckedTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (focused) {
            super.onFocusChanged(true, direction, previouslyFocusedRect);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (hasWindowFocus) {
            super.onWindowFocusChanged(true);
        }
    }

    @Override
    public boolean isFocused() {
        return true;
    }
}
