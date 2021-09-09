// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import io.github.muntashirakon.AppManager.R;

public class DialogTitleBuilder {
    @NonNull
    private final Context context;
    @StringRes
    private int titleRes;
    @Nullable
    private CharSequence title;
    @StringRes
    private int subtitleRes;
    @Nullable
    private CharSequence subtitle;
    @DrawableRes
    private int startIconRes;
    @Nullable
    private Drawable startIcon;
    @DrawableRes
    private int endIconRes;
    @Nullable
    private Drawable endIcon;
    @Nullable
    private View.OnClickListener endIconClickListener;

    public DialogTitleBuilder(@NonNull Context context) {
        this.context = context;
    }

    public DialogTitleBuilder setTitle(@Nullable CharSequence title) {
        this.title = title;
        return this;
    }

    public DialogTitleBuilder setTitle(@StringRes int titleRes) {
        this.titleRes = titleRes;
        return this;
    }

    public DialogTitleBuilder setSubtitle(@Nullable CharSequence subtitle) {
        this.subtitle = subtitle;
        return this;
    }

    public DialogTitleBuilder setSubtitle(@StringRes int subtitleRes) {
        this.subtitleRes = subtitleRes;
        return this;
    }

    public DialogTitleBuilder setStartIcon(@Nullable Drawable startIcon) {
        this.startIcon = startIcon;
        return this;
    }

    public DialogTitleBuilder setStartIcon(@DrawableRes int startIconRes) {
        this.startIconRes = startIconRes;
        return this;
    }

    public DialogTitleBuilder setEndIcon(@Nullable Drawable endIcon, @Nullable View.OnClickListener listener) {
        this.endIcon = endIcon;
        this.endIconClickListener = listener;
        return this;
    }

    public DialogTitleBuilder setEndIcon(@DrawableRes int endIconRes, @Nullable View.OnClickListener listener) {
        this.endIconRes = endIconRes;
        this.endIconClickListener = listener;
        return this;
    }

    public View build() {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        @SuppressLint("InflateParams")
        View v = inflater.inflate(R.layout.dialog_title_with_two_icons, null);
        TextView title = v.findViewById(R.id.title);
        TextView subtitle = v.findViewById(R.id.subtitle);
        ImageView startIcon = v.findViewById(R.id.icon);
        ImageView endIcon = v.findViewById(R.id.icon_2);
        // Set title
        if (this.title != null) title.setText(this.title);
        else if (this.titleRes != 0) title.setText(this.titleRes);
        // Set subtitle or hide
        if (this.subtitle == null && this.subtitleRes == 0) subtitle.setVisibility(View.GONE);
        else if (this.subtitle != null) subtitle.setText(this.subtitle);
        else subtitle.setText(this.subtitleRes);
        // Set start icon or hide
        if (this.startIcon == null && this.startIconRes == 0) startIcon.setVisibility(View.GONE);
        else if (this.startIcon != null) startIcon.setImageDrawable(this.startIcon);
        else startIcon.setImageResource(this.startIconRes);
        // Set end icon or hide
        if (this.endIcon == null && this.endIconRes == 0) endIcon.setVisibility(View.GONE);
        else if (this.endIcon != null) endIcon.setImageDrawable(this.endIcon);
        else endIcon.setImageResource(this.endIconRes);
        if (this.endIconClickListener != null) endIcon.setOnClickListener(this.endIconClickListener);
        return v;
    }
}
