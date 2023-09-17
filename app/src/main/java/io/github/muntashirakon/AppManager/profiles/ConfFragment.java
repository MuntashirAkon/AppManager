// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import io.github.muntashirakon.AppManager.R;

public class ConfFragment extends Fragment {
    private AppsProfileActivity mActivity;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = (AppsProfileActivity) requireActivity();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_container, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ProfileViewModel model = new ViewModelProvider(requireActivity()).get(ProfileViewModel.class);
        model.observeProfileLoaded().observe(getViewLifecycleOwner(), profileName -> {
            if (profileName == null) return;
            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container_view_tag, new ConfPreferences())
                    .commit();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mActivity.getSupportActionBar() != null) {
            mActivity.getSupportActionBar().setSubtitle(R.string.configurations);
        }
        mActivity.fab.hide();
    }
}
