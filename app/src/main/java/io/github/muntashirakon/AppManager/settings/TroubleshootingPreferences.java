// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;

import java.util.Objects;

import io.github.muntashirakon.AppManager.R;

public class TroubleshootingPreferences extends PreferenceFragment {
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_troubleshooting, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        MainPreferencesViewModel model = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
        // Reload apps
        ((Preference) Objects.requireNonNull(findPreference("reload_apps")))
                .setOnPreferenceClickListener(preference -> {
                    model.reloadApps();
                    return true;
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.troubleshooting);
    }
}