// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.chip.Chip;

import io.github.muntashirakon.AppManager.R;

class TagItem {
    @StringRes
    private int mTextRes;
    @Nullable
    private CharSequence mText;
    @ColorInt
    private int mColor;
    private boolean mColorSet = false;
    private View.OnClickListener mOnClickListener;

    public TagItem setTextRes(@StringRes int textRes) {
        mTextRes = textRes;
        return this;
    }

    public TagItem setText(@Nullable CharSequence text) {
        mText = text;
        return this;
    }

    public TagItem setColor(@ColorInt int color) {
        mColor = color;
        mColorSet = true;
        return this;
    }

    public TagItem setOnClickListener(View.OnClickListener clickListener) {
        mOnClickListener = clickListener;
        return this;
    }

    public Chip toChip(@NonNull Context context, @NonNull ViewGroup parent) {
        Chip chip = (Chip) LayoutInflater.from(context).inflate(R.layout.item_chip, parent, false);
        if (mTextRes != 0) {
            chip.setText(mTextRes);
        } else chip.setText(mText);
        if (mColorSet) {
            chip.setChipBackgroundColor(ColorStateList.valueOf(mColor));
        }
        if (mOnClickListener != null) {
            chip.setOnClickListener(mOnClickListener);
        }
        return chip;
    }
}
