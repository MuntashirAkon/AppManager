// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.whatsnew;

import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

import io.github.muntashirakon.AppManager.R;

public class WhatsNewFragment extends Fragment {
    public static final String TAG = WhatsNewFragment.class.getSimpleName();
    private static final String ARG_NEW_PKG_INFO = "new_pkg";
    private static final String ARG_OLD_PKG_INFO = "old_pkg";

    @NonNull
    public static WhatsNewFragment getInstance(@NonNull PackageInfo newPkgInfo, @NonNull PackageInfo oldPkgInfo) {
        WhatsNewFragment dialog = new WhatsNewFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_NEW_PKG_INFO, newPkgInfo);
        args.putParcelable(ARG_OLD_PKG_INFO, oldPkgInfo);
        dialog.setArguments(args);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return View.inflate(requireContext(), R.layout.dialog_whats_new, null);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        WhatsNewDialogViewModel viewModel = new ViewModelProvider(this).get(WhatsNewDialogViewModel.class);
        PackageInfo newPkgInfo = Objects.requireNonNull(BundleCompat.getParcelable(requireArguments(), ARG_NEW_PKG_INFO, PackageInfo.class));
        PackageInfo oldPkgInfo = Objects.requireNonNull(BundleCompat.getParcelable(requireArguments(), ARG_OLD_PKG_INFO, PackageInfo.class));
        RecyclerView recyclerView = view.findViewById(android.R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        WhatsNewRecyclerAdapter adapter = new WhatsNewRecyclerAdapter(requireContext(), newPkgInfo.packageName);
        recyclerView.setAdapter(adapter);
        viewModel.getChangesLiveData().observe(getViewLifecycleOwner(), adapter::setAdapterList);
        viewModel.loadChanges(newPkgInfo, oldPkgInfo);
    }
}
