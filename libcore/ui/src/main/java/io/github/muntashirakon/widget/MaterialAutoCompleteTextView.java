// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.internal.ThemeEnforcement;

import io.github.muntashirakon.ui.R;
import io.github.muntashirakon.view.AutoCompleteTextViewCompat;

public class MaterialAutoCompleteTextView extends com.google.android.material.textfield.MaterialAutoCompleteTextView {
    public MaterialAutoCompleteTextView(@NonNull Context context) {
        this(context, null);
    }

    public MaterialAutoCompleteTextView(@NonNull Context context, @Nullable AttributeSet attributeSet) {
        this(context, attributeSet, com.google.android.material.R.attr.autoCompleteTextViewStyle);
    }

    @SuppressLint("RestrictedApi")
    public MaterialAutoCompleteTextView(@NonNull Context context, @Nullable AttributeSet attributeSet, int defStyleAttr) {
        super(context, attributeSet, defStyleAttr);

        context = getContext();

        TypedArray attributes = ThemeEnforcement.obtainStyledAttributes(
                context,
                attributeSet,
                com.google.android.material.R.styleable.MaterialAutoCompleteTextView,
                defStyleAttr,
                com.google.android.material.R.style.Widget_AppCompat_AutoCompleteTextView);

        Drawable popupListSelector = attributes.getDrawable(R.styleable.MaterialAutoCompleteTextView_android_dropDownSelector);
        if (popupListSelector != null) {
            AutoCompleteTextViewCompat.setListSelectorMaterial(this, popupListSelector);
        }
        attributes.recycle();
    }
}
