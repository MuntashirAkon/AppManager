// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.DeviceInfo2;
import io.github.muntashirakon.util.UiUtils;

public class AboutDeviceFragment extends Fragment {
    private MainPreferencesViewModel mModel;
    private TextView mTextView;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mModel = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.about_device);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        NestedScrollView view = (NestedScrollView) inflater.inflate(io.github.muntashirakon.ui.R.layout.dialog_scrollable_text_view, container, false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.setScrollIndicators(0);
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
            mlp.topMargin = 0;
            view.setLayoutParams(mlp);
        }
        boolean secondary = false;
        if (getArguments() != null) {
            secondary = requireArguments().getBoolean(PreferenceFragment.PREF_SECONDARY);
            requireArguments().remove(PreferenceFragment.PREF_KEY);
            requireArguments().remove(PreferenceFragment.PREF_SECONDARY);
        }
        if (secondary) {
            UiUtils.applyWindowInsetsAsPadding(view, false, true, false, true);
        } else UiUtils.applyWindowInsetsAsPaddingNoTop(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mTextView = view.findViewById(android.R.id.content);
        view.findViewById(android.R.id.checkbox).setVisibility(View.GONE);
        mModel.getDeviceInfo().observe(getViewLifecycleOwner(), deviceInfo ->
                mTextView.setText(deviceInfo.toLocalizedString(requireActivity())));
        mModel.loadDeviceInfo(new DeviceInfo2(requireActivity()));
    }
}
