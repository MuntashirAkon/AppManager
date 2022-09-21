// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.transition.MaterialSharedAxis;

import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.crypto.auth.AuthManagerActivity;
import io.github.muntashirakon.AppManager.utils.AppPref;

public class PrivacyPreferences extends PreferenceFragment {
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_privacy, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        // Screen lock
        SwitchPreferenceCompat screenLock = Objects.requireNonNull(findPreference("enable_screen_lock"));
        screenLock.setChecked(AppPref.getBoolean(AppPref.PrefKey.PREF_ENABLE_SCREEN_LOCK_BOOL));
        // Authorization Management
        ((Preference) Objects.requireNonNull(findPreference("auth_manager")))
                .setOnPreferenceClickListener(preference -> {
                    startActivity(new Intent(requireContext(), AuthManagerActivity.class));
                    return true;
                });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, true));
        setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, false));
    }

    @Override
    public int getTitle() {
        return R.string.pref_privacy;
    }
}
