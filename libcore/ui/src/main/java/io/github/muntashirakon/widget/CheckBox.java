// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.content.Context;
import android.util.AttributeSet;

import com.google.android.material.checkbox.MaterialCheckBox;

public class CheckBox extends MaterialCheckBox {
    private OnCheckedChangeListener mListener;

    public CheckBox(final Context context) {
        super(context);
    }

    public CheckBox(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public CheckBox(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setOnCheckedChangeListener(final OnCheckedChangeListener listener) {
        mListener = listener;
        super.setOnCheckedChangeListener(listener);
    }

    public void setChecked(final boolean checked, final boolean triggerListener) {
        if (!triggerListener) {
            super.setOnCheckedChangeListener(null);
            super.setChecked(checked);
            super.setOnCheckedChangeListener(mListener);
            return;
        }
        super.setChecked(checked);
    }

    public void toggle(boolean triggerListener) {
        if (!triggerListener) {
            super.setOnCheckedChangeListener(null);
            super.toggle();
            super.setOnCheckedChangeListener(mListener);
            return;
        }
        super.toggle();
    }
}