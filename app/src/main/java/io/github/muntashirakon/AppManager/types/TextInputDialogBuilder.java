/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.types;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.view.View;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import io.github.muntashirakon.AppManager.R;

public class TextInputDialogBuilder {
    @NonNull
    private final FragmentActivity activity;
    @NonNull
    private final TextInputLayout textInputLayout;
    @NonNull
    private final TextInputEditText editText;
    @NonNull
    private final MaterialCheckBox checkBox;
    @NonNull
    private final MaterialAlertDialogBuilder builder;

    public interface OnClickListener {
        void onClick(DialogInterface dialog, int which, @Nullable CharSequence inputText, boolean isChecked);
    }

    @SuppressLint("InflateParams")
    public TextInputDialogBuilder(@NonNull FragmentActivity activity, @NonNull CharSequence inputTextLabel) {
        this.activity = activity;
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_text_input, null);
        this.textInputLayout = view.findViewById(android.R.id.text1);
        this.textInputLayout.setHint(inputTextLabel);
        this.editText = view.findViewById(android.R.id.input);
        this.checkBox = view.findViewById(android.R.id.checkbox);
        this.checkBox.setVisibility(View.GONE);
        this.builder = new MaterialAlertDialogBuilder(activity).setView(view);
    }

    @SuppressLint("InflateParams")
    public TextInputDialogBuilder(@NonNull FragmentActivity activity, @StringRes int inputTextLabel) {
        this(activity, activity.getText(inputTextLabel));
    }

    public TextInputDialogBuilder setTitle(@Nullable CharSequence title) {
        builder.setTitle(title);
        return this;
    }

    public TextInputDialogBuilder setTitle(@StringRes int title) {
        builder.setTitle(title);
        return this;
    }

    public TextInputDialogBuilder setInputText(@Nullable CharSequence inputText) {
        editText.setText(inputText);
        return this;
    }

    public TextInputDialogBuilder setInputText(@StringRes int inputText) {
        editText.setText(inputText);
        return this;
    }

    public TextInputDialogBuilder setHelperText(@Nullable CharSequence helperText) {
        textInputLayout.setHelperText(helperText);
        return this;
    }

    public TextInputDialogBuilder setHelperText(@StringRes int helperText) {
        textInputLayout.setHelperText(activity.getText(helperText));
        return this;
    }

    public TextInputDialogBuilder setCheckboxLabel(@Nullable CharSequence checkboxLabel) {
        if (checkboxLabel != null) {
            checkBox.setVisibility(View.VISIBLE);
            checkBox.setText(checkboxLabel);
        } else checkBox.setVisibility(View.GONE);
        return this;
    }

    public TextInputDialogBuilder setCheckboxLabel(@StringRes int checkboxLabel) {
        if (checkboxLabel != 0) {
            checkBox.setVisibility(View.VISIBLE);
            checkBox.setText(checkboxLabel);
        } else checkBox.setVisibility(View.GONE);
        return this;
    }

    public TextInputDialogBuilder setPositiveButton(@StringRes int textId, OnClickListener listener) {
        builder.setPositiveButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, editText.getText(), checkBox.isChecked());
        });
        return this;
    }

    public TextInputDialogBuilder setPositiveButton(@NonNull CharSequence text, OnClickListener listener) {
        builder.setPositiveButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, editText.getText(), checkBox.isChecked());
        });
        return this;
    }

    public TextInputDialogBuilder setNegativeButton(@StringRes int textId, OnClickListener listener) {
        builder.setNegativeButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, editText.getText(), checkBox.isChecked());
        });
        return this;
    }

    public TextInputDialogBuilder setNegativeButton(@NonNull CharSequence text, OnClickListener listener) {
        builder.setNegativeButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, editText.getText(), checkBox.isChecked());
        });
        return this;
    }

    public TextInputDialogBuilder setNeutralButton(@StringRes int textId, OnClickListener listener) {
        builder.setNeutralButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, editText.getText(), checkBox.isChecked());
        });
        return this;
    }

    public TextInputDialogBuilder setNeutralButton(@NonNull CharSequence text, OnClickListener listener) {
        builder.setNeutralButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, editText.getText(), checkBox.isChecked());
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
