/*
 * Copyright (C) 2020 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.profiles;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupDialogFragment;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.types.TextInputDialogBuilder;
import io.github.muntashirakon.AppManager.utils.TextUtils;

public class ConfPreferences extends PreferenceFragmentCompat {
    AppsProfileActivity activity;
    private ProfileViewModel model;

    @ProfileMetaManager.ProfileState
    private final List<String> states = Arrays.asList(ProfileMetaManager.STATE_ON, ProfileMetaManager.STATE_OFF);
    private String[] components;
    private String[] app_ops;
    private String[] permissions;
    private ProfileMetaManager.Profile.BackupInfo backupInfo;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_profile_config, rootKey);
        getPreferenceManager().setPreferenceDataStore(new ConfDataStore());
        activity = (AppsProfileActivity) requireActivity();
        if (activity.model == null) {
            // ViewModel should never be null.
            // If it's null, it means that we're on the wrong Fragment
            return;
        }
        model = this.activity.model;
        // Set state
        Preference statePref = Objects.requireNonNull(findPreference("state"));
        final String[] statesL = new String[]{
                getString(R.string.on),
                getString(R.string.off)
        };
        statePref.setTitle(getString(R.string.process_state, statesL[states.indexOf(model.getState())]));
        statePref.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.profile_state)
                    .setSingleChoiceItems(statesL, states.indexOf(model.getState()), (dialog, which) -> {
                        model.setState(states.get(which));
                        statePref.setTitle(getString(R.string.process_state, statesL[which]));
                        dialog.dismiss();
                    })
                    .show();
            return true;
        });
        // Set components
        Preference componentsPref = Objects.requireNonNull(findPreference("components"));
        updateComponentsPref(componentsPref);
        componentsPref.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(activity, R.string.input_signatures)
                    .setTitle(R.string.components)
                    .setInputText(components == null ? "" : TextUtils.join(" ", components))
                    .setHelperText(R.string.input_signatures_description)
                    .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                        if (!TextUtils.isEmpty(inputText)) {
                            String[] newComponents = inputText.toString().split("\\s+");
                            model.setComponents(newComponents);
                        } else model.setComponents(null);
                        updateComponentsPref(componentsPref);
                    })
                    .setNegativeButton(R.string.disable, (dialog, which, inputText, isChecked) -> {
                        model.setComponents(null);
                        updateComponentsPref(componentsPref);
                    })
                    .show();
            return true;
        });
        // Set app ops
        Preference appOpsPref = Objects.requireNonNull(findPreference("app_ops"));
        updateAppOpsPref(appOpsPref);
        appOpsPref.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(activity, R.string.input_app_ops)
                    .setTitle(R.string.app_ops)
                    .setInputText(app_ops == null ? "" : TextUtils.join(" ", app_ops))
                    .setHelperText(R.string.input_app_ops_description)
                    .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                        if (!TextUtils.isEmpty(inputText)) {
                            String[] newAppOps = inputText.toString().split("\\s+");
                            model.setAppOps(newAppOps);
                        } else model.setAppOps(null);
                        updateAppOpsPref(appOpsPref);
                    })
                    .setNegativeButton(R.string.disable, (dialog, which, inputText, isChecked) -> {
                        model.setAppOps(null);
                        updateAppOpsPref(appOpsPref);
                    })
                    .show();
            return true;
        });
        // Set permissions
        Preference permissionsPref = Objects.requireNonNull(findPreference("permissions"));
        updatePermissionsPref(permissionsPref);
        permissionsPref.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(activity, R.string.input_permissions)
                    .setTitle(R.string.declared_permission)
                    .setInputText(permissions == null ? "" : TextUtils.join(" ", permissions))
                    .setHelperText(R.string.input_permissions_description)
                    .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                        if (!TextUtils.isEmpty(inputText)) {
                            String[] newPermissions = inputText.toString().split("\\s+");
                            model.setPermissions(newPermissions);
                        } else model.setPermissions(null);
                        updatePermissionsPref(permissionsPref);
                    })
                    .setNegativeButton(R.string.disable, (dialog, which, inputText, isChecked) -> {
                        model.setPermissions(null);
                        updatePermissionsPref(permissionsPref);
                    })
                    .show();
            return true;
        });
        Preference backupDataPref = Objects.requireNonNull(findPreference("backup_data"));
        backupInfo = model.getBackupInfo();
        backupDataPref.setSummary(backupInfo != null ? R.string.enabled : R.string.disabled_app);
        backupDataPref.setOnPreferenceClickListener(preference -> {
            View view = activity.getLayoutInflater().inflate(R.layout.dialog_profile_backup_restore, null);
            final Spinner spinner = view.findViewById(R.id.type_selector_spinner);
            final AtomicInteger backupMode = new AtomicInteger(BackupDialogFragment.MODE_BACKUP);
            final BackupFlags flags;
            if (backupInfo != null) flags = new BackupFlags(backupInfo.flags);
            else flags = BackupFlags.fromPref();
            final AtomicInteger backupFlags = new AtomicInteger(flags.getFlags());
            ArrayAdapter<CharSequence> spinnerAdapter = new ArrayAdapter<>(activity,
                    android.R.layout.simple_spinner_dropdown_item, new CharSequence[]{
                    getString(R.string.backup),
                    getString(R.string.restore)
            });
            spinner.setAdapter(spinnerAdapter);
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    backupMode.set(position == 0 ? BackupDialogFragment.MODE_BACKUP : BackupDialogFragment.MODE_RESTORE);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            view.findViewById(R.id.dialog_button).setOnClickListener(v -> new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.backup_options)
                    .setMultiChoiceItems(R.array.backup_flags, flags.flagsToCheckedItems(),
                            (dialog, flag, isChecked) -> {
                                if (isChecked) flags.addFlag(flag);
                                else flags.removeFlag(flag);
                            })
                    .setPositiveButton(R.string.save, (dialog, which) -> backupFlags.set(flags.getFlags()))
                    .setNegativeButton(R.string.cancel, null)
                    .show());
            final TextInputEditText editText = view.findViewById(android.R.id.input);
            if (backupInfo != null) {
                editText.setText(backupInfo.name);
                spinner.setSelection(backupInfo.mode == BackupDialogFragment.MODE_BACKUP ? 0 : 1);
            }
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.backup_restore)
                    .setView(view)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        if (backupInfo == null) {
                            backupInfo = new ProfileMetaManager.Profile.BackupInfo();
                        }
                        CharSequence backupName = editText.getText();
                        backupInfo.flags = backupFlags.get();
                        backupInfo.mode = backupMode.get();
                        backupInfo.name = TextUtils.isEmpty(backupName) ? null : backupName.toString();
                        model.setBackupInfo(backupInfo);
                        backupDataPref.setSummary(R.string.enabled);
                    })
                    .setNegativeButton(R.string.disable, (dialog, which) -> {
                        model.setBackupInfo(backupInfo = null);
                        backupDataPref.setSummary(R.string.disabled_app);
                    })
                    .show();
            return true;
        });
        // Set export rules
        Preference exportRulesPref = Objects.requireNonNull(findPreference("export_rules"));
        int rulesCount = RulesTypeSelectionDialogFragment.types.length;
        List<Integer> checkedItems = new ArrayList<>(rulesCount);
        List<Integer> selectedRules = updateExportRulesPref(exportRulesPref);
        for (int i = 0; i < rulesCount; ++i) checkedItems.add(1 << i);
        exportRulesPref.setOnPreferenceClickListener(preference -> {
            new SearchableMultiChoiceDialogBuilder<>(activity, checkedItems, R.array.rule_types)
                    .setTitle(R.string.options)
                    .hideSearchBar(true)
                    .setSelections(selectedRules)
                    .setPositiveButton(R.string.ok, (dialog, which, selectedItems) -> {
                        int value = 0;
                        for (int item : selectedItems) value |= item;
                        if (value != 0) {
                            model.setExportRules(value);
                        } else model.setExportRules(null);
                        selectedRules.clear();
                        selectedRules.addAll(updateExportRulesPref(exportRulesPref));
                    })
                    .setNegativeButton(R.string.disable, (dialog, which, selectedItems) -> {
                        model.setExportRules(null);
                        selectedRules.clear();
                        selectedRules.addAll(updateExportRulesPref(exportRulesPref));
                    })
                    .show();
            return true;
        });
        // Set others
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("disable")))
                .setChecked(model.getBoolean("disable", false));
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("force_stop")))
                .setChecked(model.getBoolean("force_stop", false));
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("clear_cache")))
                .setChecked(model.getBoolean("clear_cache", false));
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("clear_data")))
                .setChecked(model.getBoolean("clear_data", false));
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("block_trackers")))
                .setChecked(model.getBoolean("block_trackers", false));
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("backup_apk")))
                .setChecked(model.getBoolean("backup_apk", false));
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("allow_routine")))
                .setChecked(model.getBoolean("allow_routine", false));
    }

    @NonNull
    private List<Integer> updateExportRulesPref(Preference pref) {
        Integer rules = model.getExportRules();
        List<Integer> selectedRules = new ArrayList<>();
        if (rules == null || rules == 0) pref.setSummary(R.string.disabled_app);
        else {
            List<String> selectedRulesStr = new ArrayList<>();
            int i = 0;
            while (rules != 0) {
                int flag = (rules & (~(1 << i)));
                if (flag != rules) {
                    selectedRulesStr.add(RulesTypeSelectionDialogFragment.types[i].toString());
                    rules = flag;
                    selectedRules.add(1 << i);
                }
                ++i;
            }
            pref.setSummary(TextUtils.join(", ", selectedRulesStr));
        }
        return selectedRules;
    }

    private void updateComponentsPref(Preference pref) {
        components = model.getComponents();
        if (components == null || components.length == 0) pref.setSummary(R.string.disabled_app);
        else {
            pref.setSummary(TextUtils.join(", ", components));
        }
    }

    private void updateAppOpsPref(Preference pref) {
        app_ops = model.getAppOps();
        if (app_ops == null || app_ops.length == 0) pref.setSummary(R.string.disabled_app);
        else {
            pref.setSummary(TextUtils.join(", ", app_ops));
        }
    }

    private void updatePermissionsPref(Preference pref) {
        permissions = model.getPermissions();
        if (permissions == null || permissions.length == 0) pref.setSummary(R.string.disabled_app);
        else {
            pref.setSummary(TextUtils.join(", ", permissions));
        }
    }

    public class ConfDataStore extends PreferenceDataStore {
        @Override
        public void putBoolean(@NonNull String key, boolean value) {
            model.putBoolean(key, value);
        }

        @Override
        public boolean getBoolean(@NonNull String key, boolean defValue) {
            return model.getBoolean(key, defValue);
        }
    }
}
