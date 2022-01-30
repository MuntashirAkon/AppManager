// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import io.github.muntashirakon.ui.R;

@SuppressWarnings("UnusedReturnValue")
public class FullScreenDialogTitleBuilder {
    @NonNull
    private final Context context;
    @StringRes
    private int titleRes;
    @Nullable
    private CharSequence title;
    @Nullable
    private DialogInterface.OnClickListener onCloseButtonClickListener;
    private int closeIconDescriptionRes = android.R.string.cancel;
    @Nullable
    private CharSequence closeIconDescription;
    @Nullable
    private Drawable closeButtonIcon;
    @Nullable
    private Drawable positiveButtonIcon;
    @StringRes
    private int positiveButtonTextRes;
    @Nullable
    private CharSequence positiveButtonText;
    private DialogInterface.OnClickListener onPositiveButtonClickListener;
    private boolean exitOnButtonPress = true;

    public FullScreenDialogTitleBuilder(@NonNull Context context) {
        this.context = context;
    }

    public FullScreenDialogTitleBuilder setTitle(@Nullable CharSequence title) {
        this.title = title;
        return this;
    }

    public FullScreenDialogTitleBuilder setTitle(@StringRes int titleId) {
        this.titleRes = titleId;
        return this;
    }

    public FullScreenDialogTitleBuilder setExitOnButtonPress(boolean exitOnButtonPress) {
        this.exitOnButtonPress = exitOnButtonPress;
        return this;
    }

    public FullScreenDialogTitleBuilder setCloseButtonIcon(@Nullable Drawable closeButtonIcon) {
        this.closeButtonIcon = closeButtonIcon;
        return this;
    }

    public FullScreenDialogTitleBuilder setOnCloseButtonClickListener(@Nullable DialogInterface.OnClickListener listener) {
        this.onCloseButtonClickListener = listener;
        return this;
    }

    public FullScreenDialogTitleBuilder setCloseIconContentDescription(@Nullable CharSequence closeIconDescription) {
        this.closeIconDescription = closeIconDescription;
        return this;
    }

    public FullScreenDialogTitleBuilder setCloseIconContentDescription(@StringRes int strRes) {
        this.closeIconDescriptionRes = strRes;
        return this;
    }

    public FullScreenDialogTitleBuilder setPositiveButtonIcon(Drawable positiveButtonIcon) {
        this.positiveButtonIcon = positiveButtonIcon;
        return this;
    }

    public FullScreenDialogTitleBuilder setPositiveButtonText(@Nullable CharSequence text) {
        this.positiveButtonText = text;
        return this;
    }

    public FullScreenDialogTitleBuilder setPositiveButtonText(@StringRes int textId) {
        this.positiveButtonTextRes = textId;
        return this;
    }

    public FullScreenDialogTitleBuilder setOnPositiveButtonClickListener(DialogInterface.OnClickListener listener) {
        this.onPositiveButtonClickListener = listener;
        return this;
    }

    public View build(DialogInterface dialog) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        @SuppressLint("InflateParams")
        View v = inflater.inflate(R.layout.dialog_title_toolbar, null);
        MaterialToolbar toolbar = v.findViewById(R.id.toolbar);
        if (title != null) toolbar.setTitle(title);
        else if (titleRes != 0) toolbar.setTitle(titleRes);
        if (closeButtonIcon != null) {
            toolbar.setNavigationIcon(closeButtonIcon);
        }
        // TODO: 28/1/22 Use Handler instead of lamda functions
        if (onCloseButtonClickListener != null) {
            toolbar.setNavigationOnClickListener(v1 -> {
                onCloseButtonClickListener.onClick(dialog, AlertDialog.BUTTON_NEGATIVE);
                if (exitOnButtonPress) dialog.dismiss();
            });
        } else {
            toolbar.setNavigationOnClickListener(v1 -> dialog.dismiss());
        }
        if (closeIconDescription != null) {
            toolbar.setNavigationContentDescription(closeIconDescription);
        } else {
            toolbar.setNavigationContentDescription(closeIconDescriptionRes);
        }
        MaterialButton positiveButton = v.findViewById(android.R.id.button1);
        if (onPositiveButtonClickListener != null) {
            positiveButton.setOnClickListener(v1 -> {
                onPositiveButtonClickListener.onClick(dialog, AlertDialog.BUTTON_POSITIVE);
                if (exitOnButtonPress) dialog.dismiss();
            });
        } else {
            positiveButton.setOnClickListener(v1 -> dialog.dismiss());
        }
        if (positiveButtonIcon != null) {
            // Set icon only, remove text
            positiveButton.setIcon(positiveButtonIcon);
            positiveButton.setIconPadding(0);
            positiveButton.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_TOP);
            positiveButton.setText(null);
            if (positiveButtonText != null) {
                positiveButton.setContentDescription(positiveButtonText);
            } else if (positiveButtonTextRes != 0) {
                positiveButton.setContentDescription(context.getString(positiveButtonTextRes));
            }
        } else if (positiveButtonText != null) {
            positiveButton.setText(positiveButtonText);
        } else if (positiveButtonTextRes != 0) {
            positiveButton.setText(positiveButtonTextRes);
        } else positiveButton.setVisibility(View.GONE);
        return v;
    }
}
