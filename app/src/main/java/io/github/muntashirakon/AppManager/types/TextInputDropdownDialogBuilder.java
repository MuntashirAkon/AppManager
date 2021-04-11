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
import android.text.Editable;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

import io.github.muntashirakon.AppManager.R;

public class TextInputDropdownDialogBuilder {
    @NonNull
    private final FragmentActivity activity;
    @NonNull
    private final TextInputLayout mainInputLayout;
    @NonNull
    private final AutoCompleteTextView mainInput;
    @NonNull
    private final TextInputLayout auxiliaryInputLayout;
    @NonNull
    private final AutoCompleteTextView auxiliaryInput;
    @NonNull
    private final MaterialCheckBox checkBox;
    @NonNull
    private final MaterialAlertDialogBuilder builder;

    public interface OnClickListener {
        void onClick(DialogInterface dialog, int which, @Nullable Editable inputText, boolean isChecked);
    }

    @SuppressLint("InflateParams")
    public TextInputDropdownDialogBuilder(@NonNull FragmentActivity activity, @NonNull CharSequence inputTextLabel) {
        this.activity = activity;
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_text_input_dropdown, null);
        // Main input layout: always visible
        this.mainInputLayout = view.findViewById(android.R.id.text1);
        this.mainInputLayout.setHint(inputTextLabel);
        this.mainInputLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
        this.mainInput = view.findViewById(android.R.id.input);
        // Auxiliary input layout: visible on demand
        this.auxiliaryInputLayout = view.findViewById(android.R.id.text2);
        this.auxiliaryInput = view.findViewById(android.R.id.custom);
        this.auxiliaryInputLayout.setVisibility(View.GONE);
        // Checkbox: visible on demand
        this.checkBox = view.findViewById(android.R.id.checkbox);
        this.checkBox.setVisibility(View.GONE);
        this.builder = new MaterialAlertDialogBuilder(activity).setView(view);
    }

    @SuppressLint("InflateParams")
    public TextInputDropdownDialogBuilder(@NonNull FragmentActivity activity, @StringRes int inputTextLabel) {
        this(activity, activity.getText(inputTextLabel));
    }

    public TextInputDropdownDialogBuilder setTitle(@Nullable CharSequence title) {
        builder.setTitle(title);
        return this;
    }

    public TextInputDropdownDialogBuilder setTitle(@StringRes int title) {
        builder.setTitle(title);
        return this;
    }

    public <T> TextInputDropdownDialogBuilder setDropdownItems(List<T> items, boolean filterable) {
        ArrayAdapter<T> adapter;
        if (filterable) {
            adapter = new AnyFilterArrayAdapter<>(activity, R.layout.item_checked_text_view, items);
        } else {
            adapter = new NoFilterArrayAdapter<>(activity, R.layout.item_checked_text_view, items);
        }
        mainInput.setAdapter(adapter);
        mainInputLayout.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
        return this;
    }

    public <T> TextInputDropdownDialogBuilder setAuxiliaryInput(@NonNull CharSequence inputLabel,
                                                                @Nullable CharSequence helperText,
                                                                @Nullable CharSequence inputText,
                                                                @Nullable List<T> dropdownItems,
                                                                boolean isEnabled) {
        auxiliaryInputLayout.setVisibility(View.VISIBLE);
        auxiliaryInputLayout.setHint(inputLabel);
        auxiliaryInputLayout.setHelperText(helperText);
        auxiliaryInput.setText(inputText);
        if (dropdownItems != null) {
            ArrayAdapter<T> adapter = new NoFilterArrayAdapter<>(activity, R.layout.item_checked_text_view, dropdownItems);
            auxiliaryInput.setAdapter(adapter);
            auxiliaryInputLayout.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
        } else {
            auxiliaryInputLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
        }
        if (isEnabled) auxiliaryInput.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        else auxiliaryInput.setInputType(InputType.TYPE_NULL);
        return this;
    }

    public <T> TextInputDropdownDialogBuilder setAuxiliaryInput(@StringRes int inputLabel,
                                                                @Nullable @StringRes Integer helperText,
                                                                @Nullable @StringRes Integer inputText,
                                                                @Nullable List<T> dropdownItems,
                                                                boolean isEnabled) {
        return setAuxiliaryInput(activity.getString(inputLabel),
                helperText == null ? null : activity.getText(helperText),
                inputText == null ? null : activity.getText(inputText),
                dropdownItems, isEnabled);
    }

    public TextInputDropdownDialogBuilder setAuxiliaryInputLabel(CharSequence inputText) {
        auxiliaryInputLayout.setVisibility(View.VISIBLE);
        auxiliaryInputLayout.setHint(inputText);
        return this;
    }

    public TextInputDropdownDialogBuilder setAuxiliaryInputLabel(@StringRes int inputText) {
        auxiliaryInputLayout.setVisibility(View.VISIBLE);
        auxiliaryInputLayout.setHint(inputText);
        return this;
    }

    public TextInputDropdownDialogBuilder setAuxiliaryInputHelperText(CharSequence helperText) {
        auxiliaryInputLayout.setHelperText(helperText);
        return this;
    }

    public TextInputDropdownDialogBuilder setAuxiliaryInputHelperText(@StringRes int helperText) {
        auxiliaryInputLayout.setHelperText(activity.getText(helperText));
        return this;
    }

    public TextInputDropdownDialogBuilder setAuxiliaryInputText(CharSequence inputText) {
        auxiliaryInput.setText(inputText);
        return this;
    }

    public TextInputDropdownDialogBuilder setAuxiliaryInputText(@StringRes int inputText) {
        auxiliaryInput.setText(inputText);
        return this;
    }

    @Nullable
    public Editable getAuxiliaryInput() {
        return auxiliaryInput.getText();
    }

    @Nullable
    public Editable getInputText() {
        return mainInput.getText();
    }

    public TextInputDropdownDialogBuilder setEnable(boolean enable) {
        if (enable) mainInput.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        else mainInput.setInputType(InputType.TYPE_NULL);
        return this;
    }

    public TextInputDropdownDialogBuilder setInputText(@Nullable CharSequence inputText) {
        mainInput.setText(inputText);
        return this;
    }

    public TextInputDropdownDialogBuilder setInputText(@StringRes int inputText) {
        mainInput.setText(inputText);
        return this;
    }

    public TextInputDropdownDialogBuilder setHelperText(@Nullable CharSequence helperText) {
        mainInputLayout.setHelperText(helperText);
        return this;
    }

    public TextInputDropdownDialogBuilder setHelperText(@StringRes int helperText) {
        mainInputLayout.setHelperText(activity.getText(helperText));
        return this;
    }

    public TextInputDropdownDialogBuilder setCheckboxLabel(@Nullable CharSequence checkboxLabel) {
        if (checkboxLabel != null) {
            checkBox.setVisibility(View.VISIBLE);
            checkBox.setText(checkboxLabel);
        } else checkBox.setVisibility(View.GONE);
        return this;
    }

    public TextInputDropdownDialogBuilder setCheckboxLabel(@StringRes int checkboxLabel) {
        if (checkboxLabel != 0) {
            checkBox.setVisibility(View.VISIBLE);
            checkBox.setText(checkboxLabel);
        } else checkBox.setVisibility(View.GONE);
        return this;
    }

    public TextInputDropdownDialogBuilder setPositiveButton(@StringRes int textId, OnClickListener listener) {
        builder.setPositiveButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mainInput.getText(), checkBox.isChecked());
        });
        return this;
    }

    public TextInputDropdownDialogBuilder setPositiveButton(@NonNull CharSequence text, OnClickListener listener) {
        builder.setPositiveButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mainInput.getText(), checkBox.isChecked());
        });
        return this;
    }

    public TextInputDropdownDialogBuilder setNegativeButton(@StringRes int textId, OnClickListener listener) {
        builder.setNegativeButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mainInput.getText(), checkBox.isChecked());
        });
        return this;
    }

    public TextInputDropdownDialogBuilder setNegativeButton(@NonNull CharSequence text, OnClickListener listener) {
        builder.setNegativeButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mainInput.getText(), checkBox.isChecked());
        });
        return this;
    }

    public TextInputDropdownDialogBuilder setNeutralButton(@StringRes int textId, OnClickListener listener) {
        builder.setNeutralButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mainInput.getText(), checkBox.isChecked());
        });
        return this;
    }

    public TextInputDropdownDialogBuilder setNeutralButton(@NonNull CharSequence text, OnClickListener listener) {
        builder.setNeutralButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mainInput.getText(), checkBox.isChecked());
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
