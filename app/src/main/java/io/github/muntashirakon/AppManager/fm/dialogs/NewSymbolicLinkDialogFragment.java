// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.dialogs;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.lang.ref.WeakReference;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.lifecycle.SoftInputLifeCycleObserver;

public class NewSymbolicLinkDialogFragment extends DialogFragment {
    public static final String TAG = NewSymbolicLinkDialogFragment.class.getSimpleName();

    public interface OnCreateNewLinkInterface {
        void onCreate(@NonNull String prefix, @Nullable String extension, @NonNull String targetPath);
    }

    @NonNull
    public static NewSymbolicLinkDialogFragment getInstance(@Nullable OnCreateNewLinkInterface createNewLinkInterface) {
        NewSymbolicLinkDialogFragment fragment = new NewSymbolicLinkDialogFragment();
        fragment.setOnCreateNewLinkInterface(createNewLinkInterface);
        return fragment;
    }

    @Nullable
    private OnCreateNewLinkInterface mOnCreateNewLinkInterface;
    private View mDialogView;
    private TextInputEditText mNameField;
    private TextInputLayout mTargetPathLayout;
    private TextInputEditText mTargetPathField;
    private boolean mValidPath = false;

    private final TextWatcher mPathValidator = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (TextUtils.isEmpty(s)) {
                return;
            }
            Path targetPath = Paths.get(s.toString());
            mValidPath = targetPath.getFile() != null && targetPath.exists();
            if (!mValidPath) {
                mTargetPathLayout.setError(getText(R.string.invalid_target_path));
            } else {
                mTargetPathLayout.setError(null);
            }
        }
    };

    @SuppressLint("SetTextI18n")
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_new_symlink, null);
        mNameField = mDialogView.findViewById(R.id.name);
        mNameField.setText("New link");
        mNameField.selectAll();
        mTargetPathField = mDialogView.findViewById(R.id.target_file);
        mTargetPathLayout = mDialogView.findViewById(R.id.target_file_layout);
        mTargetPathField.addTextChangedListener(mPathValidator);
        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.create_new_symbolic_link)
                .setView(mDialogView)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    Editable name = mNameField.getText();
                    Editable targetPath = mTargetPathField.getText();
                    if (!TextUtils.isEmpty(name) && mValidPath && mOnCreateNewLinkInterface != null) {
                        String newName = name.toString();
                        String prefix = Paths.trimPathExtension(newName);
                        String extension = Paths.getPathExtension(newName, false);
                        mOnCreateNewLinkInterface.onCreate(prefix, extension, Objects.requireNonNull(targetPath).toString());
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
        getLifecycle().addObserver(new SoftInputLifeCycleObserver(new WeakReference<>(mNameField)));
    }

    public void setOnCreateNewLinkInterface(@Nullable OnCreateNewLinkInterface createNewLinkInterface) {
        mOnCreateNewLinkInterface = createNewLinkInterface;
    }
}
