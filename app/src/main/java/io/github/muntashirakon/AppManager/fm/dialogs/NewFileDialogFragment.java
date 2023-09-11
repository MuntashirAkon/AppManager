// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.lang.ref.WeakReference;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.adapters.SelectedArrayAdapter;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.lifecycle.SoftInputLifeCycleObserver;
import io.github.muntashirakon.widget.MaterialSpinner;

public class NewFileDialogFragment extends DialogFragment {
    public static final String TAG = NewFileDialogFragment.class.getSimpleName();

    public interface OnCreateNewFileInterface {
        void onCreate(@NonNull String prefix, @Nullable String extension, @NonNull String template);
    }

    @NonNull
    public static NewFileDialogFragment getInstance(@Nullable OnCreateNewFileInterface createNewFileInterface) {
        NewFileDialogFragment fragment = new NewFileDialogFragment();
        fragment.setOnCreateNewFileInterface(createNewFileInterface);
        return fragment;
    }

    private static final int TYPE_TEXT = 0;
    private static final int TYPE_PDF = 1;
    private static final int TYPE_DOCS = 2;
    private static final int TYPE_SHEET = 3;
    private static final int TYPE_PRESENTATION = 4;
    private static final int TYPE_DB = 5;

    private static final String[] TYPE_LABELS = new String[]{
            "Text",                         // TYPE_TEXT
            "PDF",                          // TYPE_PDF
            "Document (.docx, .odt)",       // TYPE_DOCS
            "Sheet (.xlsx, .ods)",          // TYPE_SHEET
            "Presentation (.ppt, .odp)",    // TYPE_PRESENTATION
            "Database",                     // TYPE_DB
    };

    private static final String[] TYPE_DEFAULT_EXTENSIONS = new String[]{
            "txt",      // TYPE_TEXT
            "pdf",      // TYPE_PDF
            "docx",     // TYPE_DOCS
            "xlsx",     // TYPE_SHEET
            "pptx",    // TYPE_PRESENTATION
            "db",      // TYPE_DB
    };

    @Nullable
    private OnCreateNewFileInterface mOnCreateNewFileInterface;
    private View mDialogView;
    private TextInputEditText mEditText;
    private int mType;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_new_file, null);
        mEditText = mDialogView.findViewById(R.id.name);
        String name = "New file.txt";
        mEditText.setText(name);
        handleFilename(name, null);
        MaterialSpinner spinner = mDialogView.findViewById(R.id.type_selector_spinner);
        ArrayAdapter<CharSequence> spinnerAdapter = new SelectedArrayAdapter<>(requireContext(),
                io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item, TYPE_LABELS);
        spinner.setAdapter(spinnerAdapter);
        spinner.setSelection(TYPE_TEXT);
        spinner.setOnItemClickListener((parent, view, position, id) -> {
            if (mType != position) {
                mType = position;
                handleFilename(mEditText.getText(), TYPE_DEFAULT_EXTENSIONS[mType]);
            }
        });
        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.create_new_file)
                .setView(mDialogView)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    Editable editable = mEditText.getText();
                    if (!TextUtils.isEmpty(editable) && mOnCreateNewFileInterface != null) {
                        String newName = editable.toString();
                        String prefix = Paths.trimPathExtension(newName);
                        String extension = Paths.getPathExtension(newName, false);
                        String template = getFileTemplateFromTypeAndExtension(mType, extension);
                        mOnCreateNewFileInterface.onCreate(prefix, extension, template);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return mDialogView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        getLifecycle().addObserver(new SoftInputLifeCycleObserver(new WeakReference<>(mEditText)));
    }

    public void setOnCreateNewFileInterface(@Nullable OnCreateNewFileInterface createNewFileInterface) {
        mOnCreateNewFileInterface = createNewFileInterface;
    }

    private void handleFilename(@Nullable CharSequence charSequence, @Nullable String newExtension) {
        if (charSequence == null) {
            return;
        }
        String name = charSequence.toString();
        int lastIndex = name.lastIndexOf('.');
        if (newExtension != null && lastIndex != -1) {
            // Change extension before setting selection
            name = name.substring(0, lastIndex) + "." + newExtension;
            mEditText.setText(name);
            lastIndex = name.lastIndexOf('.');
        }
        if (lastIndex != -1 || lastIndex == name.length() - 1) {
            mEditText.setSelection(0, lastIndex);
        } else {
            mEditText.selectAll();
        }
    }

    @NonNull
    private static String getFileTemplateFromTypeAndExtension(int type, @Nullable String extension) {
        String prefix = "blank";
        switch (type) {
            default:
            case TYPE_TEXT:
                return prefix + ".txt";
            case TYPE_PDF:
                return prefix + ".pdf";
            case TYPE_DOCS:
                return prefix + ("odt".equals(extension) ? ".odt" : ".docx");
            case TYPE_SHEET:
                return prefix + ("ods".equals(extension) ? ".ods" : ".xlsx");
            case TYPE_PRESENTATION:
                return prefix + ("odp".equals(extension) ? ".odp" : ".pptx");
            case TYPE_DB:
                return prefix + ".db";
        }
    }
}
