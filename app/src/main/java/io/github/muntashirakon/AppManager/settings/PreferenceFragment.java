// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.util.UiUtils;

public abstract class PreferenceFragment extends PreferenceFragmentCompat {
    public static final String PREF_KEY = "key";
    public static final String PREF_SECONDARY = "secondary";

    @Nullable
    private String mPrefKey;

    @CallSuper
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        boolean secondary = false;
        if (getArguments() != null) {
            mPrefKey = requireArguments().getString(PREF_KEY);
            secondary = requireArguments().getBoolean(PREF_SECONDARY);
            requireArguments().remove(PREF_KEY);
            requireArguments().remove(PREF_SECONDARY);
        }
        // https://github.com/androidx/androidx/blob/29fe2b988bba2b56c0ff406d4edf1060e9154c82/preference/preference/src/main/java/androidx/preference/PreferenceFragment.java#L548
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setFitsSystemWindows(true);
        recyclerView.setClipToPadding(false);
        if (secondary) {
            if (this instanceof MainPreferences) {
                UiUtils.applyWindowInsetsAsPadding(recyclerView, false, true, true, false);
            } else {
                UiUtils.applyWindowInsetsAsPadding(recyclerView, false, true, false, true);
            }
        } else UiUtils.applyWindowInsetsAsPaddingNoTop(recyclerView);

        // Use M3 design for preference cards
        float m3CornerRadius;
        float m3CornerRadiusInner;
        TypedValue typedValue = new TypedValue();
        if (requireContext().getTheme().resolveAttribute(io.github.muntashirakon.ui.R.attr.listItemCornerRadius, typedValue, true)) {
            m3CornerRadius = typedValue.getDimension(getResources().getDisplayMetrics());
        } else {
            throw new RuntimeException("?attr/listItemCornerRadius not defined.");
        }
        if (requireContext().getTheme().resolveAttribute(io.github.muntashirakon.ui.R.attr.listItemCornerRadiusInner, typedValue, true)) {
            m3CornerRadiusInner = typedValue.getDimension(getResources().getDisplayMetrics());
        } else {
            throw new RuntimeException("?attr/listItemCornerRadiusInner not defined.");
        }

        // Apply the decorator with the specific radius
        recyclerView.addItemDecoration(new M3PreferenceGroupDecoration(m3CornerRadius, m3CornerRadiusInner));

        // Remove old style line dividers
        setDivider(null);
    }

    @CallSuper
    @Override
    public void onStart() {
        requireActivity().setTitle(getTitle());
        super.onStart();
        updateUi();
    }

    @StringRes
    public abstract int getTitle();

    public void setPrefKey(@Nullable String prefKey) {
        mPrefKey = prefKey;
        updateUi();
    }

    public <T extends androidx.preference.Preference> T requirePreference(CharSequence key) {
        return Objects.requireNonNull(findPreference(key));
    }

    protected void enablePrefs(boolean enable, Preference... prefs) {
        if (prefs == null) {
            return;
        }
        for (Preference pref : prefs) {
            pref.setEnabled(enable);
        }
    }

    @SuppressLint("RestrictedApi")
    private void updateUi() {
        if (mPrefKey != null) {
            Preference prefToNavigate = findPreference(mPrefKey);
            if (prefToNavigate != null) {
                scrollToPreference(prefToNavigate);
                if (prefToNavigate.getFragment() != null) {
                    prefToNavigate.performClick();
                }
            }
        }
    }
}
