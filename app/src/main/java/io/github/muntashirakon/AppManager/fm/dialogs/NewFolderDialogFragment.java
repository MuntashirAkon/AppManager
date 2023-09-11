// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.dialogs;

import android.annotation.SuppressLint;
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
import io.github.muntashirakon.lifecycle.SoftInputLifeCycleObserver;

public class NewFolderDialogFragment extends DialogFragment {
    public static final String TAG = NewFolderDialogFragment.class.getSimpleName();

    public interface OnCreateNewFolderInterface {
        void onCreate(@NonNull String name);
    }

    @NonNull
    public static NewFolderDialogFragment getInstance(@Nullable OnCreateNewFolderInterface createNewFolderInterface) {
        NewFolderDialogFragment fragment = new NewFolderDialogFragment();
        fragment.setOnCreateNewFolderInterface(createNewFolderInterface);
        return fragment;
    }

    @Nullable
    private OnCreateNewFolderInterface mOnCreateNewFolderInterface;
    private View mDialogView;
    private TextInputEditText mEditText;

    @SuppressLint("SetTextI18n")
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_rename, null);
        mEditText = mDialogView.findViewById(R.id.rename);
        mEditText.setText("New folder");
        mEditText.selectAll();
        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.create_new_folder)
                .setView(mDialogView)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    Editable editable = mEditText.getText();
                    if (!TextUtils.isEmpty(editable) && mOnCreateNewFolderInterface != null) {
                        mOnCreateNewFolderInterface.onCreate(editable.toString());
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

    public void setOnCreateNewFolderInterface(@Nullable OnCreateNewFolderInterface createNewFolderInterface) {
        mOnCreateNewFolderInterface = createNewFolderInterface;
    }
}
