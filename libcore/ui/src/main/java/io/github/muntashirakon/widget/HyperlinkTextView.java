// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.google.android.material.textview.MaterialTextView;

public class HyperlinkTextView extends MaterialTextView {
    public HyperlinkTextView(Context context) {
        super(context);
    }

    public HyperlinkTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public HyperlinkTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Set default movement method to {@link LinkMovementMethod}
     *
     * @return Link movement method as the default movement method
     */
    @Override
    protected MovementMethod getDefaultMovementMethod() {
        return LinkMovementMethod.getInstance();
    }
}
