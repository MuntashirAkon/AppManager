// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog;

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

import io.github.muntashirakon.ui.R;
import io.github.muntashirakon.adapters.AnyFilterArrayAdapter;
import io.github.muntashirakon.adapters.NoFilterArrayAdapter;

@SuppressWarnings("unused")
public class TextInputDropdownDialogBuilder {
    @NonNull
    private final FragmentActivity mActivity;
    @NonNull
    private final TextInputLayout mMainInputLayout;
    @NonNull
    private final AutoCompleteTextView mMainInput;
    @NonNull
    private final TextInputLayout mAuxiliaryInputLayout;
    @NonNull
    private final AutoCompleteTextView mAuxiliaryInput;
    @NonNull
    private final MaterialCheckBox mCheckBox;
    @NonNull
    private final MaterialAlertDialogBuilder mBuilder;

    public interface OnClickListener {
        void onClick(DialogInterface dialog, int which, @Nullable Editable inputText, boolean isChecked);
    }

    @SuppressLint("InflateParams")
    public TextInputDropdownDialogBuilder(@NonNull FragmentActivity activity, @NonNull CharSequence inputTextLabel) {
        mActivity = activity;
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_text_input_dropdown, null);
        // Main input layout: always visible
        mMainInputLayout = view.findViewById(android.R.id.text1);
        mMainInputLayout.setHint(inputTextLabel);
        mMainInputLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
        mMainInput = view.findViewById(android.R.id.input);
        // Auxiliary input layout: visible on demand
        mAuxiliaryInputLayout = view.findViewById(android.R.id.text2);
        mAuxiliaryInput = view.findViewById(android.R.id.custom);
        mAuxiliaryInputLayout.setVisibility(View.GONE);
        // Checkbox: visible on demand
        mCheckBox = view.findViewById(android.R.id.checkbox);
        mCheckBox.setVisibility(View.GONE);
        mBuilder = new MaterialAlertDialogBuilder(activity).setView(view);
    }

    @SuppressLint("InflateParams")
    public TextInputDropdownDialogBuilder(@NonNull FragmentActivity activity, @StringRes int inputTextLabel) {
        this(activity, activity.getText(inputTextLabel));
    }

    public TextInputDropdownDialogBuilder setTitle(@Nullable View title) {
        mBuilder.setCustomTitle(title);
        return this;
    }

    public TextInputDropdownDialogBuilder setTitle(@Nullable CharSequence title) {
        mBuilder.setTitle(title);
        return this;
    }

    public TextInputDropdownDialogBuilder setTitle(@StringRes int title) {
        mBuilder.setTitle(title);
        return this;
    }

    public <T> TextInputDropdownDialogBuilder setDropdownItems(List<T> items, int choice, boolean filterable) {
        ArrayAdapter<T> adapter;
        if (filterable) {
            adapter = new AnyFilterArrayAdapter<>(mActivity, R.layout.auto_complete_dropdown_item, items);
        } else {
            adapter = new NoFilterArrayAdapter<>(mActivity, R.layout.auto_complete_dropdown_item, items);
        }
        mMainInput.setAdapter(adapter);
        if (choice >= 0) {
            T selectedItem = adapter.getItem(choice);
            mMainInput.setText(selectedItem == null ? "" : selectedItem.toString());
        }
        mMainInputLayout.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
        return this;
    }

    public <T> TextInputDropdownDialogBuilder setAuxiliaryInput(@NonNull CharSequence inputLabel,
                                                                @Nullable CharSequence helperText,
                                                                @Nullable CharSequence inputText,
                                                                @Nullable List<T> dropdownItems,
                                                                boolean isEnabled) {
        mAuxiliaryInputLayout.setVisibility(View.VISIBLE);
        mAuxiliaryInputLayout.setHint(inputLabel);
        mAuxiliaryInputLayout.setHelperText(helperText);
        mAuxiliaryInput.setText(inputText);
        if (dropdownItems != null) {
            ArrayAdapter<T> adapter = new NoFilterArrayAdapter<>(mActivity, R.layout.auto_complete_dropdown_item, dropdownItems);
            mAuxiliaryInput.setAdapter(adapter);
            mAuxiliaryInputLayout.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
        } else {
            mAuxiliaryInputLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
        }
        if (isEnabled)
            mAuxiliaryInput.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        else mAuxiliaryInput.setInputType(InputType.TYPE_NULL);
        return this;
    }

    public <T> TextInputDropdownDialogBuilder setAuxiliaryInput(@StringRes int inputLabel,
                                                                @Nullable @StringRes Integer helperText,
                                                                @Nullable @StringRes Integer inputText,
                                                                @Nullable List<T> dropdownItems,
                                                                boolean isEnabled) {
        return setAuxiliaryInput(mActivity.getString(inputLabel),
                helperText == null ? null : mActivity.getText(helperText),
                inputText == null ? null : mActivity.getText(inputText),
                dropdownItems, isEnabled);
    }

    public TextInputDropdownDialogBuilder setAuxiliaryInputLabel(@Nullable CharSequence inputText) {
        mAuxiliaryInputLayout.setVisibility(View.VISIBLE);
        mAuxiliaryInputLayout.setHint(inputText);
        return this;
    }

    public TextInputDropdownDialogBuilder setAuxiliaryInputLabel(@StringRes int inputText) {
        mAuxiliaryInputLayout.setVisibility(View.VISIBLE);
        mAuxiliaryInputLayout.setHint(inputText);
        return this;
    }

    public TextInputDropdownDialogBuilder setAuxiliaryInputHelperText(@Nullable CharSequence helperText) {
        mAuxiliaryInputLayout.setHelperText(helperText);
        return this;
    }

    public TextInputDropdownDialogBuilder setAuxiliaryInputHelperText(@StringRes int helperText) {
        mAuxiliaryInputLayout.setHelperText(mActivity.getText(helperText));
        return this;
    }

    public TextInputDropdownDialogBuilder setAuxiliaryInputText(@Nullable CharSequence inputText) {
        mAuxiliaryInput.setText(inputText);
        return this;
    }

    public TextInputDropdownDialogBuilder setAuxiliaryInputText(@StringRes int inputText) {
        mAuxiliaryInput.setText(inputText);
        return this;
    }

    @Nullable
    public Editable getAuxiliaryInput() {
        return mAuxiliaryInput.getText();
    }

    @Nullable
    public Editable getInputText() {
        return mMainInput.getText();
    }

    public TextInputDropdownDialogBuilder setEnable(boolean enable) {
        if (enable)
            mMainInput.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        else mMainInput.setInputType(InputType.TYPE_NULL);
        return this;
    }

    public TextInputDropdownDialogBuilder setInputText(@Nullable CharSequence inputText) {
        mMainInput.setText(inputText);
        return this;
    }

    public TextInputDropdownDialogBuilder setInputText(@StringRes int inputText) {
        mMainInput.setText(inputText);
        return this;
    }

    public TextInputDropdownDialogBuilder setHelperText(@Nullable CharSequence helperText) {
        mMainInputLayout.setHelperText(helperText);
        return this;
    }

    public TextInputDropdownDialogBuilder setHelperText(@StringRes int helperText) {
        mMainInputLayout.setHelperText(mActivity.getText(helperText));
        return this;
    }

    public TextInputDropdownDialogBuilder setCheckboxLabel(@Nullable CharSequence checkboxLabel) {
        if (checkboxLabel != null) {
            mCheckBox.setVisibility(View.VISIBLE);
            mCheckBox.setText(checkboxLabel);
        } else mCheckBox.setVisibility(View.GONE);
        return this;
    }

    public TextInputDropdownDialogBuilder setCheckboxLabel(@StringRes int checkboxLabel) {
        if (checkboxLabel != 0) {
            mCheckBox.setVisibility(View.VISIBLE);
            mCheckBox.setText(checkboxLabel);
        } else mCheckBox.setVisibility(View.GONE);
        return this;
    }

    public TextInputDropdownDialogBuilder setPositiveButton(@StringRes int textId, @Nullable OnClickListener listener) {
        mBuilder.setPositiveButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mMainInput.getText(), mCheckBox.isChecked());
        });
        return this;
    }

    public TextInputDropdownDialogBuilder setPositiveButton(@NonNull CharSequence text, @Nullable OnClickListener listener) {
        mBuilder.setPositiveButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mMainInput.getText(), mCheckBox.isChecked());
        });
        return this;
    }

    public TextInputDropdownDialogBuilder setNegativeButton(@StringRes int textId, @Nullable OnClickListener listener) {
        mBuilder.setNegativeButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mMainInput.getText(), mCheckBox.isChecked());
        });
        return this;
    }

    public TextInputDropdownDialogBuilder setNegativeButton(@NonNull CharSequence text, @Nullable OnClickListener listener) {
        mBuilder.setNegativeButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mMainInput.getText(), mCheckBox.isChecked());
        });
        return this;
    }

    public TextInputDropdownDialogBuilder setNeutralButton(@StringRes int textId, @Nullable OnClickListener listener) {
        mBuilder.setNeutralButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mMainInput.getText(), mCheckBox.isChecked());
        });
        return this;
    }

    public TextInputDropdownDialogBuilder setNeutralButton(@NonNull CharSequence text, @Nullable OnClickListener listener) {
        mBuilder.setNeutralButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mMainInput.getText(), mCheckBox.isChecked());
        });
        return this;
    }

    public AlertDialog create() {
        return mBuilder.create();
    }

    public void show() {
        create().show();
    }
}
