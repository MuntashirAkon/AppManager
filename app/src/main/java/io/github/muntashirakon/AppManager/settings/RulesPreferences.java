/*
 * Copyright (c) 2021 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.settings;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;

public class RulesPreferences extends PreferenceFragmentCompat {
    SettingsActivity activity;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_rules);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        activity = (SettingsActivity) requireActivity();
        // Global blocking enabled
        final SwitchPreferenceCompat gcb = Objects.requireNonNull(findPreference("global_blocking_enabled"));
        gcb.setChecked(AppPref.isGlobalBlockingEnabled());
        gcb.setOnPreferenceChangeListener((preference, isEnabled) -> {
            if (AppPref.isRootEnabled() && (boolean) isEnabled) {
                new Thread(() -> {
                    // Apply all rules immediately if GCB is true
                    synchronized (gcb) {
                        ComponentsBlocker.applyAllRules(activity, Users.getCurrentUserHandle());
                    }
                }).start();
            }
            return true;
        });
        // Import/export rules
        ((Preference) Objects.requireNonNull(findPreference("import_export_rules"))).setOnPreferenceClickListener(preference -> {
            new ImportExportRulesDialogFragment().show(getParentFragmentManager(), ImportExportRulesDialogFragment.TAG);
            return true;
        });
        // Remove all rules
        ((Preference) Objects.requireNonNull(findPreference("remove_all_rules"))).setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.pref_remove_all_rules)
                    .setMessage(R.string.are_you_sure)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        activity.progressIndicator.show();
                        new Thread(() -> {
                            int[] userHandles = Users.getUsersHandles();
                            List<String> packages = ComponentUtils.getAllPackagesWithRules();
                            for (int userHandle : userHandles) {
                                for (String packageName : packages) {
                                    ComponentUtils.removeAllRules(packageName, userHandle);
                                }
                            }
                            activity.runOnUiThread(() -> {
                                if (isDetached()) return;
                                activity.progressIndicator.hide();
                                Toast.makeText(activity, R.string.the_operation_was_successful, Toast.LENGTH_SHORT).show();
                            });
                        }).start();
                    })
                    .setNegativeButton(R.string.no, null)
                    .show();
            return true;
        });
    }
}
