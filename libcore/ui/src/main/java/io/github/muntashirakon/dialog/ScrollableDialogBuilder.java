// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.text.util.LinkifyCompat;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textview.MaterialTextView;

import io.github.muntashirakon.ui.R;

@SuppressWarnings("unused")
public class ScrollableDialogBuilder {
    @NonNull
    private final MaterialTextView mMessage;
    @NonNull
    private final MaterialCheckBox mCheckBox;
    @NonNull
    private final AlertDialogBuilder mBuilder;

    public interface OnClickListener {
        void onClick(DialogInterface dialog, int which, boolean isChecked);
    }

    public ScrollableDialogBuilder(@NonNull Context context, @Nullable CharSequence message, boolean fullScreen) {
        View view = View.inflate(context, R.layout.dialog_scrollable_text_view, null);
        mMessage = view.findViewById(android.R.id.content);
        mMessage.setText(message);
        mCheckBox = view.findViewById(android.R.id.checkbox);
        mCheckBox.setVisibility(View.GONE);
        mBuilder = new AlertDialogBuilder(context, fullScreen).setView(view);
    }

    public ScrollableDialogBuilder(@NonNull Context context, @Nullable CharSequence message) {
        this(context, message, false);
    }

    public ScrollableDialogBuilder(@NonNull Context context, boolean fullScreen) {
        this(context, null, fullScreen);
    }

    public ScrollableDialogBuilder(@NonNull Context context) {
        this(context, null);
    }

    public ScrollableDialogBuilder(@NonNull Context context, @StringRes int inputTextLabel) {
        this(context, context.getText(inputTextLabel));
    }

    public ScrollableDialogBuilder setTitle(@Nullable View title) {
        mBuilder.setCustomTitle(title);
        return this;
    }

    public ScrollableDialogBuilder setTitle(@Nullable CharSequence title) {
        mBuilder.setTitle(title);
        return this;
    }

    public ScrollableDialogBuilder setTitle(@StringRes int title) {
        mBuilder.setTitle(title);
        return this;
    }

    public ScrollableDialogBuilder setMessage(@StringRes int message) {
        mMessage.setText(message);
        return this;
    }

    public ScrollableDialogBuilder setMessage(@Nullable CharSequence message) {
        mMessage.setText(message);
        return this;
    }

    public ScrollableDialogBuilder setSelectable(boolean selectable) {
        mMessage.setTextIsSelectable(selectable);
        return this;
    }

    public ScrollableDialogBuilder enableAnchors() {
        mMessage.setMovementMethod(LinkMovementMethod.getInstance());
        return this;
    }

    public ScrollableDialogBuilder linkify(@LinkifyCompat.LinkifyMask int mask) {
        LinkifyCompat.addLinks(mMessage, mask);
        return this;
    }

    public ScrollableDialogBuilder linkifyAll() {
        return linkify(Linkify.ALL);
    }

    public ScrollableDialogBuilder setCheckboxLabel(@Nullable CharSequence checkboxLabel) {
        if (checkboxLabel != null) {
            mCheckBox.setVisibility(View.VISIBLE);
            mCheckBox.setText(checkboxLabel);
        } else mCheckBox.setVisibility(View.GONE);
        return this;
    }

    public ScrollableDialogBuilder setCheckboxLabel(@StringRes int checkboxLabel) {
        if (checkboxLabel != 0) {
            mCheckBox.setVisibility(View.VISIBLE);
            mCheckBox.setText(checkboxLabel);
        } else mCheckBox.setVisibility(View.GONE);
        return this;
    }

    public ScrollableDialogBuilder setPositiveButton(@StringRes int textId, @Nullable OnClickListener listener) {
        mBuilder.setPositiveButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mCheckBox.isChecked());
        });
        return this;
    }

    public ScrollableDialogBuilder setPositiveButton(@NonNull CharSequence text, @Nullable OnClickListener listener) {
        mBuilder.setPositiveButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mCheckBox.isChecked());
        });
        return this;
    }

    public ScrollableDialogBuilder setNegativeButton(@StringRes int textId, @Nullable OnClickListener listener) {
        mBuilder.setNegativeButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mCheckBox.isChecked());
        });
        return this;
    }

    public ScrollableDialogBuilder setNegativeButton(@NonNull CharSequence text, @Nullable OnClickListener listener) {
        mBuilder.setNegativeButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mCheckBox.isChecked());
        });
        return this;
    }

    public ScrollableDialogBuilder setNeutralButton(@StringRes int textId, @Nullable OnClickListener listener) {
        mBuilder.setNeutralButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mCheckBox.isChecked());
        });
        return this;
    }

    public ScrollableDialogBuilder setNeutralButton(@NonNull CharSequence text, @Nullable OnClickListener listener) {
        mBuilder.setNeutralButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mCheckBox.isChecked());
        });
        return this;
    }

    @NonNull
    public AlertDialog create() {
        return mBuilder.create();
    }

    public void show() {
        create().show();
    }
}
