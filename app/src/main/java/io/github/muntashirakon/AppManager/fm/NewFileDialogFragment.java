// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.lang.ref.WeakReference;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.lifecycle.SoftInputLifeCycleObserver;

public class NewFileDialogFragment extends DialogFragment {
    public static final String TAG = NewFileDialogFragment.class.getSimpleName();

    public interface OnCreateNewFileInterface {
        void onCreate(@NonNull String prefix, @Nullable String extension);
    }

    @NonNull
    public static NewFileDialogFragment getInstance(@Nullable OnCreateNewFileInterface createNewFileInterface) {
        NewFileDialogFragment fragment = new NewFileDialogFragment();
        fragment.setOnCreateNewFileInterface(createNewFileInterface);
        return fragment;
    }

    @Nullable
    private OnCreateNewFileInterface mOnCreateNewFileInterface;
    private View mDialogView;
    private TextInputEditText mEditText;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_rename, null);
        mEditText = mDialogView.findViewById(R.id.rename);
        String name = "New file.txt";
        mEditText.setText(name);
        handleFilename(name);
        // TODO: 26/6/23 Add options to set other kind of files
        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.create_new_file)
                .setView(mDialogView)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    Editable editable = mEditText.getText();
                    if (!TextUtils.isEmpty(editable) && mOnCreateNewFileInterface != null) {
                        String newName = editable.toString();
                        String prefix = Paths.trimPathExtension(newName);
                        String extension = Paths.getPathExtension(newName, false);
                        mOnCreateNewFileInterface.onCreate(prefix, extension);
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

    private void handleFilename(@Nullable String name) {
        if (name == null) {
            return;
        }
        int lastIndex = name.lastIndexOf('.');
        if (lastIndex != -1 || lastIndex == name.length() - 1) {
            mEditText.setSelection(0, lastIndex);
        } else {
            mEditText.selectAll();
        }
    }
}
