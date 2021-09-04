// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Bundle;
import android.os.UserHandleHidden;
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
                        ComponentsBlocker.applyAllRules(activity, UserHandleHidden.myUserId());
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
                            int[] userHandles = Users.getUsersIds();
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
