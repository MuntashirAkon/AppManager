// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.button.MaterialButton;

import io.github.muntashirakon.ui.R;

public class DialogTitleBuilder {
    @NonNull
    private final Context mContext;
    @StringRes
    private int mTitleRes;
    @Nullable
    private CharSequence mTitle;
    private boolean mTitleSelectable;
    @StringRes
    private int mSubtitleRes;
    @Nullable
    private CharSequence mSubtitle;
    private boolean mSubtitleSelectable;
    @DrawableRes
    private int mStartIconRes;
    @Nullable
    private Drawable mStartIcon;
    @DrawableRes
    private int mEndIconRes;
    @Nullable
    private Drawable mEndIcon;
    @Nullable
    private View.OnClickListener mEndIconClickListener;
    @StringRes
    private int mEndIconContentDescriptionRes;
    @Nullable
    private CharSequence mEndIconContentDescription;

    public DialogTitleBuilder(@NonNull Context context) {
        mContext = context;
    }

    public DialogTitleBuilder setTitle(@Nullable CharSequence title) {
        mTitle = title;
        return this;
    }

    public DialogTitleBuilder setTitle(@StringRes int titleRes) {
        mTitleRes = titleRes;
        return this;
    }

    public DialogTitleBuilder setTitleSelectable(boolean titleSelectable) {
        mTitleSelectable = titleSelectable;
        return this;
    }

    public DialogTitleBuilder setSubtitle(@Nullable CharSequence subtitle) {
        mSubtitle = subtitle;
        return this;
    }

    public DialogTitleBuilder setSubtitle(@StringRes int subtitleRes) {
        mSubtitleRes = subtitleRes;
        return this;
    }

    public DialogTitleBuilder setSubtitleSelectable(boolean subtitleSelectable) {
        mSubtitleSelectable = subtitleSelectable;
        return this;
    }

    public DialogTitleBuilder setStartIcon(@Nullable Drawable startIcon) {
        mStartIcon = startIcon;
        return this;
    }

    public DialogTitleBuilder setStartIcon(@DrawableRes int startIconRes) {
        mStartIconRes = startIconRes;
        return this;
    }

    public DialogTitleBuilder setEndIcon(@Nullable Drawable endIcon, @Nullable View.OnClickListener listener) {
        mEndIcon = endIcon;
        mEndIconClickListener = listener;
        return this;
    }

    public DialogTitleBuilder setEndIcon(@DrawableRes int endIconRes, @Nullable View.OnClickListener listener) {
        mEndIconRes = endIconRes;
        mEndIconClickListener = listener;
        return this;
    }

    public DialogTitleBuilder setEndIconContentDescription(@Nullable CharSequence endIconContentDescription) {
        mEndIconContentDescription = endIconContentDescription;
        return this;
    }

    public DialogTitleBuilder setEndIconContentDescription(@StringRes int endIconContentDescriptionRes) {
        mEndIconContentDescriptionRes = endIconContentDescriptionRes;
        return this;
    }

    public View build() {
        View v = View.inflate(mContext, R.layout.dialog_title_with_two_icons, null);
        TextView title = v.findViewById(R.id.title);
        TextView subtitle = v.findViewById(R.id.subtitle);
        ImageView startIcon = v.findViewById(R.id.icon);
        MaterialButton endIcon = v.findViewById(R.id.action);
        // Set title
        if (mTitle != null) title.setText(mTitle);
        else if (mTitleRes != 0) title.setText(mTitleRes);
        title.setTextIsSelectable(mTitleSelectable);
        // Set subtitle or hide
        if (mSubtitle == null && mSubtitleRes == 0) subtitle.setVisibility(View.GONE);
        else if (mSubtitle != null) subtitle.setText(mSubtitle);
        else subtitle.setText(mSubtitleRes);
        subtitle.setTextIsSelectable(mSubtitleSelectable);
        // Set start icon or hide
        if (mStartIcon == null && mStartIconRes == 0) startIcon.setVisibility(View.GONE);
        else if (mStartIcon != null) startIcon.setImageDrawable(mStartIcon);
        else startIcon.setImageResource(mStartIconRes);
        // Set end icon or hide
        if (mEndIcon == null && mEndIconRes == 0) endIcon.setVisibility(View.GONE);
        else if (mEndIcon != null) endIcon.setIcon(mEndIcon);
        else endIcon.setIconResource(mEndIconRes);
        if (mEndIconClickListener != null) {
            endIcon.setOnClickListener(mEndIconClickListener);
        }
        if (mEndIconContentDescription != null) {
            endIcon.setContentDescription(mEndIconContentDescription);
        } else if (mEndIconContentDescriptionRes != 0) {
            endIcon.setContentDescription(mContext.getText(mEndIconContentDescriptionRes));
        }
        return v;
    }
}
