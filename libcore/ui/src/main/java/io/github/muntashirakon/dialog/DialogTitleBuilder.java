// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.button.MaterialButton;

import io.github.muntashirakon.ui.R;
import io.github.muntashirakon.util.UiUtils;

public class DialogTitleBuilder {
    @NonNull
    private final Context mContext;
    private final View mView;
    private final TextView mTitle;
    private final TextView mSubtitle;
    private final ImageView mStartIcon;
    private final MaterialButton mEndIcon;
    private final int mIconTopPadding;

    public DialogTitleBuilder(@NonNull Context context) {
        mContext = context;
        mView = View.inflate(mContext, R.layout.dialog_title_with_two_icons, null);
        mTitle = mView.findViewById(R.id.title);
        mSubtitle = mView.findViewById(R.id.subtitle);
        mSubtitle.setVisibility(View.GONE);
        mStartIcon = mView.findViewById(R.id.icon);
        mStartIcon.setVisibility(View.GONE);
        mEndIcon = mView.findViewById(R.id.action);
        mEndIcon.setVisibility(View.GONE);
        mIconTopPadding = UiUtils.dpToPx(context, 12);
    }

    public DialogTitleBuilder setTitle(@Nullable CharSequence title) {
        mTitle.setText(title);
        return this;
    }

    public DialogTitleBuilder setTitle(@StringRes int titleRes) {
        mTitle.setText(titleRes);
        return this;
    }

    public DialogTitleBuilder setTitleSelectable(boolean titleSelectable) {
        mTitle.setTextIsSelectable(titleSelectable);
        return this;
    }

    public DialogTitleBuilder setSubtitle(@Nullable CharSequence subtitle) {
        toggleVisibility(mSubtitle, subtitle != null);
        updateIconPadding(subtitle != null);
        mSubtitle.setText(subtitle);
        return this;
    }

    public DialogTitleBuilder setSubtitle(@StringRes int subtitleRes) {
        toggleVisibility(mSubtitle, subtitleRes != 0);
        updateIconPadding(subtitleRes != 0);
        mSubtitle.setText(subtitleRes);
        return this;
    }

    public DialogTitleBuilder setSubtitleSelectable(boolean subtitleSelectable) {
        mSubtitle.setTextIsSelectable(subtitleSelectable);
        return this;
    }

    public DialogTitleBuilder setStartIcon(@Nullable Drawable startIcon) {
        toggleVisibility(mStartIcon, startIcon != null);
        mStartIcon.setImageDrawable(startIcon);
        return this;
    }

    public DialogTitleBuilder setStartIcon(@DrawableRes int startIconRes) {
        toggleVisibility(mStartIcon, startIconRes != 0);
        mStartIcon.setImageResource(startIconRes);
        return this;
    }

    public DialogTitleBuilder setEndIcon(@Nullable Drawable endIcon, @Nullable View.OnClickListener listener) {
        toggleVisibility(mEndIcon, endIcon != null);
        mEndIcon.setIcon(endIcon);
        mEndIcon.setOnClickListener(listener);
        return this;
    }

    public DialogTitleBuilder setEndIcon(@DrawableRes int endIconRes, @Nullable View.OnClickListener listener) {
        toggleVisibility(mEndIcon, endIconRes != 0);
        mEndIcon.setIconResource(endIconRes);
        mEndIcon.setOnClickListener(listener);
        return this;
    }

    public DialogTitleBuilder setEndIconContentDescription(@Nullable CharSequence endIconContentDescription) {
        mEndIcon.setContentDescription(endIconContentDescription);
        return this;
    }

    public DialogTitleBuilder setEndIconContentDescription(@StringRes int endIconContentDescriptionRes) {
        mEndIcon.setContentDescription(mContext.getText(endIconContentDescriptionRes));
        return this;
    }

    public View build() {
        return mView;
    }

    private void toggleVisibility(@NonNull View v, boolean show) {
        boolean isShown = v.isShown();
        if (show) {
            if (!isShown) {
                v.setVisibility(View.VISIBLE);
            }
        } else if (isShown) {
            v.setVisibility(View.GONE);
        }
    }

    private void updateIconPadding(boolean set) {
        ((FrameLayout) mStartIcon.getParent()).setPadding(0, set ? mIconTopPadding : 0, 0, 0);
    }
}
