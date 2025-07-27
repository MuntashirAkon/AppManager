// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

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
import com.google.android.material.textfield.TextInputLayout;

import java.lang.ref.WeakReference;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;
import io.github.muntashirakon.adapters.SelectedArrayAdapter;
import io.github.muntashirakon.lifecycle.SoftInputLifeCycleObserver;
import io.github.muntashirakon.view.TextInputLayoutCompat;
import io.github.muntashirakon.widget.MaterialSpinner;

public class NewProfileDialogFragment extends DialogFragment {
    public static final String TAG = NewProfileDialogFragment.class.getSimpleName();

    public interface OnCreateNewProfileInterface {
        void onCreateNewProfile(@NonNull String newProfileName, @BaseProfile.ProfileType int type);
    }

    @NonNull
    public static NewProfileDialogFragment getInstance(@Nullable OnCreateNewProfileInterface createNewProfileInterface) {
        NewProfileDialogFragment fragment = new NewProfileDialogFragment();
        fragment.setOnCreateNewProfileInterface(createNewProfileInterface);
        return fragment;
    }

    @Nullable
    private OnCreateNewProfileInterface mOnCreateNewProfileInterface;
    private View mDialogView;
    private TextInputEditText mEditText;
    private int mType;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_new_file, null);
        mEditText = mDialogView.findViewById(R.id.name);
        String name = "Untitled profile";
        mEditText.setText(name);
        mEditText.selectAll();
        TextInputLayout editTextLayout = TextInputLayoutCompat.fromTextInputEditText(mEditText);
        editTextLayout.setHelperText(requireContext().getText(R.string.input_profile_name_description));
        MaterialSpinner spinner = mDialogView.findViewById(R.id.type_selector_spinner);
        ArrayAdapter<CharSequence> spinnerAdapter = SelectedArrayAdapter.createFromResource(requireContext(),
                R.array.profile_types, io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item);
        if (!BuildConfig.DEBUG) {
            spinner.setVisibility(View.GONE);
        }
        spinner.setAdapter(spinnerAdapter);
        spinner.setSelection(BaseProfile.PROFILE_TYPE_APPS);
        spinner.setOnItemClickListener((parent, view, position, id) -> {
            if (mType != position) {
                mType = position;
            }
        });
        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.new_profile)
                .setView(mDialogView)
                .setPositiveButton(R.string.go, (dialog, which) -> {
                    Editable editable = mEditText.getText();
                    if (!TextUtils.isEmpty(editable) && mOnCreateNewProfileInterface != null) {
                        mOnCreateNewProfileInterface.onCreateNewProfile(editable.toString(), mType);
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

    public void setOnCreateNewProfileInterface(@Nullable OnCreateNewProfileInterface createNewProfileInterface) {
        mOnCreateNewProfileInterface = createNewProfileInterface;
    }
}
