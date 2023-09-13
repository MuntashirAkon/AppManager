// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.google.android.material.button.MaterialButton;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;

class ActionItem {
    @StringRes
    private final int mTitleRes;
    @DrawableRes
    private final int mIconRes;
    private View.OnClickListener mOnClickListener;
    private View.OnLongClickListener mOnLongClickListener;

    public ActionItem(@StringRes int titleRes, @DrawableRes int iconRes) {
        mTitleRes = titleRes;
        mIconRes = iconRes;
    }

    public ActionItem setOnClickListener(View.OnClickListener clickListener) {
        mOnClickListener = clickListener;
        return this;
    }

    public ActionItem setOnLongClickListener(View.OnLongClickListener longClickListener) {
        mOnLongClickListener = longClickListener;
        return this;
    }

    public MaterialButton toActionButton(@NonNull Context context, @NonNull ViewGroup parent) {
        MaterialButton button = (MaterialButton) LayoutInflater.from(context).inflate(R.layout.item_app_info_action, parent, false);
        button.setBackgroundTintList(ColorStateList.valueOf(ColorCodes.getListItemColor1(context)));
        button.setText(mTitleRes);
        button.setIconResource(mIconRes);
        if (mOnClickListener != null) {
            button.setOnClickListener(mOnClickListener);
        }
        if (mOnLongClickListener != null) {
            button.setOnLongClickListener(mOnLongClickListener);
        }
        return button;
    }
}
