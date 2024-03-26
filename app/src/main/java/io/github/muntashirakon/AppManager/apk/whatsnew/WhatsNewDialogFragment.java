// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.whatsnew;

import android.app.Dialog;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

import io.github.muntashirakon.AppManager.R;

public class WhatsNewDialogFragment extends DialogFragment {
    public static final String TAG = WhatsNewDialogFragment.class.getSimpleName();
    private static final String ARG_NEW_PKG_INFO = "new_pkg";
    private static final String ARG_OLD_PKG_INFO = "old_pkg";

    @NonNull
    public static WhatsNewDialogFragment getInstance(@NonNull PackageInfo newPkgInfo, @NonNull PackageInfo oldPkgInfo) {
        WhatsNewDialogFragment dialog = new WhatsNewDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_NEW_PKG_INFO, newPkgInfo);
        args.putParcelable(ARG_OLD_PKG_INFO, oldPkgInfo);
        dialog.setArguments(args);
        return dialog;
    }

    private WhatsNewRecyclerAdapter mAdapter;
    private PackageInfo mNewPkgInfo;
    private PackageInfo mOldPkgInfo;
    private View mDialogView;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mDialogView = View.inflate(requireContext(), R.layout.dialog_whats_new, null);
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.whats_new)
                .setView(mDialogView)
                .setNegativeButton(R.string.ok, null)
                .create();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return mDialogView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        WhatsNewDialogViewModel viewModel = new ViewModelProvider(this).get(WhatsNewDialogViewModel.class);
        mNewPkgInfo = Objects.requireNonNull(BundleCompat.getParcelable(requireArguments(), ARG_NEW_PKG_INFO, PackageInfo.class));
        mOldPkgInfo = Objects.requireNonNull(BundleCompat.getParcelable(requireArguments(), ARG_OLD_PKG_INFO, PackageInfo.class));
        RecyclerView recyclerView = mDialogView.findViewById(android.R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mAdapter = new WhatsNewRecyclerAdapter(requireContext(), mNewPkgInfo.packageName);
        recyclerView.setAdapter(mAdapter);
        viewModel.getChangesLiveData().observe(this, mAdapter::setAdapterList);
        viewModel.loadChanges(mNewPkgInfo, mOldPkgInfo);
    }

    @Override
    public void show(@NonNull FragmentManager manager, @Nullable String tag) {
        FragmentTransaction ft = manager.beginTransaction();
        ft.add(this, tag);
        ft.commitAllowingStateLoss();
    }
}
