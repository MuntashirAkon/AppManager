// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;

public class TextInputTextView extends TextInputEditText {
    public TextInputTextView(@NonNull Context context) {
        this(context, null);
    }

    public TextInputTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, androidx.appcompat.R.attr.editTextStyle);
    }

    public TextInputTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setKeyListener(null);
    }
}
