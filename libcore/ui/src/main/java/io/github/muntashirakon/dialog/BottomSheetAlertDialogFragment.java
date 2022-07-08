// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.method.MovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.CallSuper;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.LinearLayoutCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

import io.github.muntashirakon.ui.R;

public class BottomSheetAlertDialogFragment extends CapsuleBottomSheetDialogFragment {
    protected static final String ARG_TITLE = "title";
    protected static final String ARG_SUBTITLE = "subtitle";
    protected static final String ARG_MESSAGE = "message";

    @NonNull
    protected static Bundle getArgs(@Nullable CharSequence title, @Nullable CharSequence subtitle,
                                    @Nullable CharSequence message) {
        Bundle args = new Bundle();
        args.putCharSequence(ARG_TITLE, title);
        args.putCharSequence(ARG_SUBTITLE, subtitle);
        args.putCharSequence(ARG_MESSAGE, message);
        return args;
    }

    private LinearLayoutCompat mMessageContainer;
    private MaterialTextView mMessageView;
    private RelativeLayout mActionContainer;
    private MaterialButton mActionPrimary;
    private MaterialButton mActionSecondary;
    private MaterialButton mActionMore;
    private DialogTitleBuilder mDialogTitleBuilder;

    @NonNull
    @Override
    public final View initRootView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_bottom_sheet_alert, container, false);
        mDialogTitleBuilder = new DialogTitleBuilder(view.getContext());
        mMessageContainer = view.findViewById(R.id.container);
        mMessageView = view.findViewById(android.R.id.text1);
        mActionContainer = view.findViewById(R.id.action_container);
        mActionPrimary = view.findViewById(R.id.action_primary);
        mActionSecondary = view.findViewById(R.id.action_secondary);
        mActionMore = view.findViewById(R.id.action_more);
        return view;
    }

    @CallSuper
    @Override
    public void onBodyInitialized(@NonNull View bodyView, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args != null) {
            CharSequence title = args.getCharSequence(ARG_TITLE);
            CharSequence subtitle = args.getCharSequence(ARG_SUBTITLE);
            CharSequence message = args.getCharSequence(ARG_MESSAGE);
            if (title != null) {
                setTitle(title);
            }
            if (subtitle != null) {
                setSubtitle(subtitle);
            }
            if (message != null) {
                setMessage(message);
            }
        }
    }

    public void setTitle(@StringRes int title) {
        mDialogTitleBuilder.setTitle(title);
        setHeader(mDialogTitleBuilder.build());
    }

    public void setTitle(@Nullable CharSequence title) {
        mDialogTitleBuilder.setTitle(title);
        setHeader(mDialogTitleBuilder.build());
    }

    public void setSubtitle(@StringRes int subtitle) {
        mDialogTitleBuilder.setSubtitle(subtitle);
        setHeader(mDialogTitleBuilder.build());
    }

    public void setSubtitle(@Nullable CharSequence subtitle) {
        mDialogTitleBuilder.setSubtitle(subtitle);
        setHeader(mDialogTitleBuilder.build());
    }

    public void setStartIcon(@DrawableRes int drawable) {
        mDialogTitleBuilder.setStartIcon(drawable);
        setHeader(mDialogTitleBuilder.build());
    }

    public void setStartIcon(@Nullable Drawable drawable) {
        mDialogTitleBuilder.setStartIcon(drawable);
        setHeader(mDialogTitleBuilder.build());
    }

    public void setEndIcon(@DrawableRes int drawable, @StringRes int contentDescription, @Nullable View.OnClickListener clickListener) {
        mDialogTitleBuilder.setEndIcon(drawable, clickListener);
        mDialogTitleBuilder.setEndIconContentDescription(contentDescription);
        setHeader(mDialogTitleBuilder.build());
    }

    public void setEndIcon(@Nullable Drawable drawable, @Nullable CharSequence contentDescription, @Nullable View.OnClickListener clickListener) {
        mDialogTitleBuilder.setEndIcon(drawable, clickListener);
        mDialogTitleBuilder.setEndIconContentDescription(contentDescription);
        setHeader(mDialogTitleBuilder.build());
    }

    public void setMessage(@StringRes int message) {
        mMessageView.setText(message);
    }

    public void setMessage(@Nullable CharSequence message) {
        mMessageView.setText(message);
    }

    public void setMessageIsSelectable(boolean selectable) {
        mMessageView.setTextIsSelectable(selectable);
    }

    public void setMessageMovementMethod(MovementMethod movementMethod) {
        mMessageView.setMovementMethod(movementMethod);
    }

    public void setPrimaryAction(@StringRes int title, @Nullable View.OnClickListener onClickListener) {
        if (mActionContainer.getVisibility() == View.GONE) {
            mActionContainer.setVisibility(View.VISIBLE);
        }
        if (mActionPrimary.getVisibility() == View.GONE) {
            mActionPrimary.setVisibility(View.VISIBLE);
        }
        mActionPrimary.setText(title);
        mActionPrimary.setOnClickListener(onClickListener);
    }

    public void setPrimaryAction(@Nullable CharSequence title, @Nullable View.OnClickListener onClickListener) {
        if (mActionContainer.getVisibility() == View.GONE) {
            mActionContainer.setVisibility(View.VISIBLE);
        }
        if (mActionPrimary.getVisibility() == View.GONE) {
            mActionPrimary.setVisibility(View.VISIBLE);
        }
        mActionPrimary.setText(title);
        mActionPrimary.setOnClickListener(onClickListener);
    }

    public void setSecondaryAction(@StringRes int title, @Nullable View.OnClickListener onClickListener) {
        if (mActionContainer.getVisibility() == View.GONE) {
            mActionContainer.setVisibility(View.VISIBLE);
        }
        if (mActionSecondary.getVisibility() == View.GONE) {
            mActionSecondary.setVisibility(View.VISIBLE);
        }
        mActionSecondary.setText(title);
        mActionSecondary.setOnClickListener(onClickListener);
    }

    public void setSecondaryAction(@Nullable CharSequence title, @Nullable View.OnClickListener onClickListener) {
        if (mActionContainer.getVisibility() == View.GONE) {
            mActionContainer.setVisibility(View.VISIBLE);
        }
        if (mActionSecondary.getVisibility() == View.GONE) {
            mActionSecondary.setVisibility(View.VISIBLE);
        }
        mActionSecondary.setText(title);
        mActionSecondary.setOnClickListener(onClickListener);
    }

    public void prependView(View view, LinearLayoutCompat.LayoutParams layoutParams) {
        mMessageContainer.addView(view, 0, layoutParams);
    }

    public void appendView(View view, ViewGroup.LayoutParams layoutParams) {
        mMessageContainer.addView(view, layoutParams);
    }
}
