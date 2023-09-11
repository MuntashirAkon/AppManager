// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.dialogs;

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

public class RenameDialogFragment extends DialogFragment {
    public static final String TAG = RenameDialogFragment.class.getSimpleName();

    public interface OnRenameFilesInterface {
        void onRename(@NonNull String prefix, @Nullable String extension);
    }

    private static final String ARG_NAME = "name";

    @NonNull
    public static RenameDialogFragment getInstance(@Nullable String name,
                                                   @Nullable OnRenameFilesInterface renameFilesInterface) {
        RenameDialogFragment fragment = new RenameDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_NAME, name);
        fragment.setArguments(args);
        fragment.setOnRenameFilesInterface(renameFilesInterface);
        return fragment;
    }

    @Nullable
    private OnRenameFilesInterface mOnRenameFilesInterface;
    private View mDialogView;
    private TextInputEditText mEditText;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        String name = getArguments() != null ? requireArguments().getString(ARG_NAME) : null;
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_rename, null);
        mEditText = mDialogView.findViewById(R.id.rename);
        mEditText.setText(name);
        if (name != null) {
            int lastIndex = name.lastIndexOf('.');
            if (lastIndex != -1 || lastIndex == name.length() - 1) {
                mEditText.setSelection(0, lastIndex);
            } else {
                mEditText.selectAll();
            }
        }
        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.rename)
                .setView(mDialogView)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    Editable editable = mEditText.getText();
                    if (!TextUtils.isEmpty(editable) && mOnRenameFilesInterface != null) {
                        String newName = editable.toString();
                        String prefix = Paths.trimPathExtension(newName);
                        String extension = Paths.getPathExtension(newName, false);
                        mOnRenameFilesInterface.onRename(prefix, extension);
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

    public void setOnRenameFilesInterface(@Nullable OnRenameFilesInterface renameFilesInterface) {
        mOnRenameFilesInterface = renameFilesInterface;
    }
}
