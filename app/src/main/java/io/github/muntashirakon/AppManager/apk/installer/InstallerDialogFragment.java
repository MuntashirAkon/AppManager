// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.dialog.DialogTitleBuilder;

public class InstallerDialogFragment extends DialogFragment {
    public static final String TAG = InstallerDialogFragment.class.getSimpleName();

    public interface FragmentStartedCallback {
        void onStart(@NonNull InstallerDialogFragment fragment, @NonNull AlertDialog dialog);
    }

    private FragmentStartedCallback mFragmentStartedCallback;
    private View mDialogView;
    private DialogTitleBuilder mTitleBuilder;

    public void setFragmentStartedCallback(FragmentStartedCallback fragmentStartedCallback) {
        mFragmentStartedCallback = fragmentStartedCallback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mDialogView = View.inflate(requireContext(), R.layout.dialog_installer, null);
        mTitleBuilder = new DialogTitleBuilder(requireContext());
        View titleView = mTitleBuilder.build();
        return new MaterialAlertDialogBuilder(requireContext())
                .setCustomTitle(titleView)
                .setView(mDialogView)
                .setPositiveButton(" ", null)
                .setNegativeButton(" ", null)
                .setNeutralButton(" ", null)
                .setCancelable(false)
                .create();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return mDialogView;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mFragmentStartedCallback != null) {
            mFragmentStartedCallback.onStart(this, (AlertDialog) requireDialog());
        }
    }

    public View getDialogView() {
        return mDialogView;
    }

    public DialogTitleBuilder getTitleBuilder() {
        return mTitleBuilder;
    }
}