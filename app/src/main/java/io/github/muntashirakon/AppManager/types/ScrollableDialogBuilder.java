// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.text.util.Linkify;
import android.view.View;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.text.util.LinkifyCompat;
import androidx.fragment.app.FragmentActivity;
import io.github.muntashirakon.AppManager.R;

public class ScrollableDialogBuilder {
    @NonNull
    private final MaterialTextView message;
    @NonNull
    private final MaterialCheckBox checkBox;
    @NonNull
    private final MaterialAlertDialogBuilder builder;

    public interface OnClickListener {
        void onClick(DialogInterface dialog, int which, boolean isChecked);
    }

    @SuppressLint("InflateParams")
    public ScrollableDialogBuilder(@NonNull FragmentActivity activity, @Nullable CharSequence message) {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_scrollable_text_view, null);
        this.message = view.findViewById(android.R.id.content);
        this.message.setText(message);
        this.checkBox = view.findViewById(android.R.id.checkbox);
        this.checkBox.setVisibility(View.GONE);
        this.builder = new MaterialAlertDialogBuilder(activity).setView(view);
    }

    @SuppressLint("InflateParams")
    public ScrollableDialogBuilder(@NonNull FragmentActivity activity) {
        this(activity, null);
    }

    @SuppressLint("InflateParams")
    public ScrollableDialogBuilder(@NonNull FragmentActivity activity, @StringRes int inputTextLabel) {
        this(activity, activity.getText(inputTextLabel));
    }

    public ScrollableDialogBuilder setTitle(@Nullable CharSequence title) {
        builder.setTitle(title);
        return this;
    }

    public ScrollableDialogBuilder setTitle(@StringRes int title) {
        builder.setTitle(title);
        return this;
    }

    public ScrollableDialogBuilder setMessage(@StringRes int message) {
        this.message.setText(message);
        return this;
    }

    public ScrollableDialogBuilder setMessage(@Nullable CharSequence message) {
        this.message.setText(message);
        return this;
    }

    public ScrollableDialogBuilder setSelectable(boolean selectable) {
        this.message.setTextIsSelectable(selectable);
        return this;
    }

    public ScrollableDialogBuilder linkify(@LinkifyCompat.LinkifyMask int mask){
        LinkifyCompat.addLinks(message, mask);
        return this;
    }

    public ScrollableDialogBuilder linkifyAll(){
        return linkify(Linkify.ALL);
    }

    public ScrollableDialogBuilder setCheckboxLabel(@Nullable CharSequence checkboxLabel) {
        if (checkboxLabel != null) {
            checkBox.setVisibility(View.VISIBLE);
            checkBox.setText(checkboxLabel);
        } else checkBox.setVisibility(View.GONE);
        return this;
    }

    public ScrollableDialogBuilder setCheckboxLabel(@StringRes int checkboxLabel) {
        if (checkboxLabel != 0) {
            checkBox.setVisibility(View.VISIBLE);
            checkBox.setText(checkboxLabel);
        } else checkBox.setVisibility(View.GONE);
        return this;
    }

    public ScrollableDialogBuilder setPositiveButton(@StringRes int textId, OnClickListener listener) {
        builder.setPositiveButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, checkBox.isChecked());
        });
        return this;
    }

    public ScrollableDialogBuilder setPositiveButton(@NonNull CharSequence text, OnClickListener listener) {
        builder.setPositiveButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, checkBox.isChecked());
        });
        return this;
    }

    public ScrollableDialogBuilder setNegativeButton(@StringRes int textId, OnClickListener listener) {
        builder.setNegativeButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, checkBox.isChecked());
        });
        return this;
    }

    public ScrollableDialogBuilder setNegativeButton(@NonNull CharSequence text, OnClickListener listener) {
        builder.setNegativeButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, checkBox.isChecked());
        });
        return this;
    }

    public ScrollableDialogBuilder setNeutralButton(@StringRes int textId, OnClickListener listener) {
        builder.setNeutralButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, checkBox.isChecked());
        });
        return this;
    }

    public ScrollableDialogBuilder setNeutralButton(@NonNull CharSequence text, OnClickListener listener) {
        builder.setNeutralButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, checkBox.isChecked());
        });
        return this;
    }

    public AlertDialog create() {
        return builder.create();
    }

    public void show() {
        create().show();
    }
}
