// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.annotation.SuppressLint;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public abstract class PreferenceFragment extends PreferenceFragmentCompat {
    public static final String PREF_KEY = "key";

    @SuppressLint("RestrictedApi")
    @Override
    public void onStart() {
        super.onStart();
        String prefKey = getArguments() != null ? requireArguments().getString(PREF_KEY) : null;
        if (prefKey != null) {
            Preference prefToNavigate = findPreference(prefKey);
            if (prefToNavigate != null) {
                prefToNavigate.performClick();
            }
            requireArguments().remove(PREF_KEY);
        }
    }
}
