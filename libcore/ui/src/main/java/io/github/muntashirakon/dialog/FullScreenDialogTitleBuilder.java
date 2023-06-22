// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
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
    private final Context mContext;
    @StringRes
    private int mTitleRes;
    @Nullable
    private CharSequence mTitle;
    @Nullable
    private DialogInterface.OnClickListener mOnCloseButtonClickListener;
    private int mCloseIconDescriptionRes = android.R.string.cancel;
    @Nullable
    private CharSequence mCloseIconDescription;
    @Nullable
    private Drawable mCloseButtonIcon;
    @Nullable
    private Drawable mPositiveButtonIcon;
    @StringRes
    private int mPositiveButtonTextRes;
    @Nullable
    private CharSequence mPositiveButtonText;
    private DialogInterface.OnClickListener mOnPositiveButtonClickListener;
    private boolean mExitOnButtonPress = true;

    public FullScreenDialogTitleBuilder(@NonNull Context context) {
        mContext = context;
    }

    public FullScreenDialogTitleBuilder setTitle(@Nullable CharSequence title) {
        mTitle = title;
        return this;
    }

    public FullScreenDialogTitleBuilder setTitle(@StringRes int titleId) {
        mTitleRes = titleId;
        return this;
    }

    public FullScreenDialogTitleBuilder setExitOnButtonPress(boolean exitOnButtonPress) {
        mExitOnButtonPress = exitOnButtonPress;
        return this;
    }

    public FullScreenDialogTitleBuilder setCloseButtonIcon(@Nullable Drawable closeButtonIcon) {
        mCloseButtonIcon = closeButtonIcon;
        return this;
    }

    public FullScreenDialogTitleBuilder setOnCloseButtonClickListener(@Nullable DialogInterface.OnClickListener listener) {
        mOnCloseButtonClickListener = listener;
        return this;
    }

    public FullScreenDialogTitleBuilder setCloseIconContentDescription(@Nullable CharSequence closeIconDescription) {
        mCloseIconDescription = closeIconDescription;
        return this;
    }

    public FullScreenDialogTitleBuilder setCloseIconContentDescription(@StringRes int strRes) {
        mCloseIconDescriptionRes = strRes;
        return this;
    }

    public FullScreenDialogTitleBuilder setPositiveButtonIcon(Drawable positiveButtonIcon) {
        mPositiveButtonIcon = positiveButtonIcon;
        return this;
    }

    public FullScreenDialogTitleBuilder setPositiveButtonText(@Nullable CharSequence text) {
        mPositiveButtonText = text;
        return this;
    }

    public FullScreenDialogTitleBuilder setPositiveButtonText(@StringRes int textId) {
        mPositiveButtonTextRes = textId;
        return this;
    }

    public FullScreenDialogTitleBuilder setOnPositiveButtonClickListener(DialogInterface.OnClickListener listener) {
        mOnPositiveButtonClickListener = listener;
        return this;
    }

    public View build(DialogInterface dialog) {
        View v = View.inflate(mContext, R.layout.dialog_title_toolbar, null);
        MaterialToolbar toolbar = v.findViewById(R.id.toolbar);
        if (mTitle != null) toolbar.setTitle(mTitle);
        else if (mTitleRes != 0) toolbar.setTitle(mTitleRes);
        if (mCloseButtonIcon != null) {
            toolbar.setNavigationIcon(mCloseButtonIcon);
        }
        // TODO: 28/1/22 Use Handler instead of lamda functions
        if (mOnCloseButtonClickListener != null) {
            toolbar.setNavigationOnClickListener(v1 -> {
                mOnCloseButtonClickListener.onClick(dialog, AlertDialog.BUTTON_NEGATIVE);
                if (mExitOnButtonPress) dialog.dismiss();
            });
        } else {
            toolbar.setNavigationOnClickListener(v1 -> dialog.dismiss());
        }
        if (mCloseIconDescription != null) {
            toolbar.setNavigationContentDescription(mCloseIconDescription);
        } else {
            toolbar.setNavigationContentDescription(mCloseIconDescriptionRes);
        }
        MaterialButton positiveButton = v.findViewById(android.R.id.button1);
        if (mOnPositiveButtonClickListener != null) {
            positiveButton.setOnClickListener(v1 -> {
                mOnPositiveButtonClickListener.onClick(dialog, AlertDialog.BUTTON_POSITIVE);
                if (mExitOnButtonPress) dialog.dismiss();
            });
        } else {
            positiveButton.setOnClickListener(v1 -> dialog.dismiss());
        }
        if (mPositiveButtonIcon != null) {
            // Set icon only, remove text
            positiveButton.setIcon(mPositiveButtonIcon);
            positiveButton.setIconPadding(0);
            positiveButton.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_TOP);
            positiveButton.setText(null);
            if (mPositiveButtonText != null) {
                positiveButton.setContentDescription(mPositiveButtonText);
            } else if (mPositiveButtonTextRes != 0) {
                positiveButton.setContentDescription(mContext.getString(mPositiveButtonTextRes));
            }
        } else if (mPositiveButtonText != null) {
            positiveButton.setText(mPositiveButtonText);
        } else if (mPositiveButtonTextRes != 0) {
            positiveButton.setText(mPositiveButtonTextRes);
        } else positiveButton.setVisibility(View.GONE);
        return v;
    }
}
