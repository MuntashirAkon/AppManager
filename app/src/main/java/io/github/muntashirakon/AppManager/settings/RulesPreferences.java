// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.DialogTitleBuilder;

public class RulesPreferences extends PreferenceFragmentCompat {
    private final String[] blockingMethods = new String[]{
            ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW_DISABLE,
            ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW,
            ComponentRule.COMPONENT_TO_BE_DISABLED};

    private final Integer[] blockingMethodTitles = new Integer[]{
            R.string.intent_firewall_and_disable,
            R.string.intent_firewall,
            R.string.disable
    };

    private final Integer[] blockingMethodDescriptions = new Integer[]{
            R.string.pref_intent_firewall_and_disable_description,
            R.string.pref_intent_firewall_description,
            R.string.pref_disable_description
    };

    private SettingsActivity activity;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_rules);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        activity = (SettingsActivity) requireActivity();
        // Default component blocking method
        Preference defaultBlockingMethod = Objects.requireNonNull(findPreference("default_blocking_method"));
        AtomicInteger csIdx = new AtomicInteger(ArrayUtils.indexOf(blockingMethods, AppPref.getDefaultComponentStatus()));
        if (csIdx.get() != -1) {
            defaultBlockingMethod.setSummary(blockingMethodTitles[csIdx.get()]);
        }
        defaultBlockingMethod.setOnPreferenceClickListener(preference -> {
            CharSequence[] itemDescription = new CharSequence[blockingMethods.length];
            for (int i = 0; i < blockingMethods.length; ++i) {
                itemDescription[i] = UIUtils.getStyledKeyValue(
                        activity,
                        getString(blockingMethodTitles[i]),
                        UIUtils.getSecondaryText(activity, UIUtils.getSmallerText(getString(blockingMethodDescriptions[i]))),
                        "\n");
            }
            new MaterialAlertDialogBuilder(activity)
                    .setCustomTitle(new DialogTitleBuilder(activity)
                            .setTitle(R.string.pref_default_blocking_method)
                            .setSubtitle(R.string.pref_default_blocking_method_description)
                            .build())
                    .setSingleChoiceItems(itemDescription, csIdx.get(), (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_DEFAULT_BLOCKING_METHOD_STR, blockingMethods[which]);
                        defaultBlockingMethod.setSummary(blockingMethodTitles[which]);
                        csIdx.set(which);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.close, null)
                    .show();
            return true;
        });
        // Global blocking enabled
        final SwitchPreferenceCompat gcb = Objects.requireNonNull(findPreference("global_blocking_enabled"));
        gcb.setChecked(AppPref.isGlobalBlockingEnabled());
        gcb.setOnPreferenceChangeListener((preference, isEnabled) -> {
            if (AppPref.isRootEnabled() && (boolean) isEnabled) {
                new Thread(() -> {
                    // Apply all rules immediately if GCB is true
                    synchronized (gcb) {
                        ComponentsBlocker.applyAllRules(activity, Users.myUserId());
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

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.rules);
    }
}
