// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.util.UiUtils;

public abstract class PreferenceFragment extends PreferenceFragmentCompat {
    public static final String PREF_KEY = "key";

    @Nullable
    private String prefKey;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            prefKey = requireArguments().getString(PREF_KEY);
            requireArguments().remove(PREF_KEY);
        }
        // https://github.com/androidx/androidx/blob/androidx-main/preference/preference/res/layout/preference_recyclerview.xml
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setFitsSystemWindows(true);
        recyclerView.setClipToPadding(false);
        UiUtils.applyWindowInsetsAsPaddingNoTop(recyclerView);
    }

    @Override
    public void onStart() {
        super.onStart();
        updateUi();
    }

    public void setPrefKey(@Nullable String prefKey) {
        this.prefKey = prefKey;
        updateUi();
    }

    @SuppressLint("RestrictedApi")
    private void updateUi() {
        if (prefKey != null) {
            Preference prefToNavigate = findPreference(prefKey);
            if (prefToNavigate != null) {
                prefToNavigate.performClick();
            }
        }
    }
}
