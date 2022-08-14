// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.signing.Signer;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.ScrollableDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

public class InstallerPreferences extends PreferenceFragment {
    private static final int[] installLocationNames = new int[]{
            R.string.auto,  // PackageInfo.INSTALL_LOCATION_AUTO
            R.string.install_location_internal_only,  // PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY
            R.string.install_location_prefer_external,  // PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL
    };

    private SettingsActivity activity;
    private PackageManager pm;
    private String installerApp;
    private Preference installerAppPref;
    private MainPreferencesViewModel model;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_installer, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        model = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
        activity = (SettingsActivity) requireActivity();
        pm = activity.getPackageManager();
        // Display users in installer
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("installer_display_users")))
                .setChecked(AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_DISPLAY_USERS_BOOL));
        // Set installation locations
        Preference installLocationPref = Objects.requireNonNull(findPreference("installer_install_location"));
        int installLocation = AppPref.getInt(AppPref.PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT);
        installLocationPref.setSummary(installLocationNames[installLocation]);
        installLocationPref.setOnPreferenceClickListener(preference -> {
            CharSequence[] installLocationTexts = new CharSequence[installLocationNames.length];
            for (int i = 0; i < installLocationNames.length; ++i) {
                installLocationTexts[i] = getString(installLocationNames[i]);
            }
            int choice = AppPref.getInt(AppPref.PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT);
            new MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.install_location)
                    .setSingleChoiceItems(installLocationTexts, choice, (dialog, newInstallLocation) -> {
                        AppPref.set(AppPref.PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT, newInstallLocation);
                        installLocationPref.setSummary(installLocationNames[newInstallLocation]);
                    })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> {
                        // Revert
                        AppPref.set(AppPref.PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT, installLocation);
                        installLocationPref.setSummary(installLocationNames[installLocation]);
                    })
                    .show();
            return true;
        });
        // Set installer app
        installerAppPref = Objects.requireNonNull(findPreference("installer_installer_app"));
        installerApp = AppPref.getString(AppPref.PrefKey.PREF_INSTALLER_INSTALLER_APP_STR);
        installerAppPref.setSummary(PackageUtils.getPackageLabel(pm, installerApp));
        installerAppPref.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.installer_app)
                    .setMessage(R.string.installer_app_message)
                    .setPositiveButton(R.string.choose, (dialog1, which1) -> {
                        activity.progressIndicator.show();
                        model.loadPackageNameLabelPair();
                    })
                    .setNegativeButton(R.string.specify_custom_name, (dialog, which) ->
                            new TextInputDialogBuilder(requireActivity(), R.string.installer_app)
                                    .setTitle(R.string.installer_app)
                                    .setInputText(installerApp)
                                    .setPositiveButton(R.string.ok, (dialog1, which1, inputText, isChecked) -> {
                                        if (inputText == null) return;
                                        installerApp = inputText.toString().trim();
                                        AppPref.set(AppPref.PrefKey.PREF_INSTALLER_INSTALLER_APP_STR, installerApp);
                                        installerAppPref.setSummary(PackageUtils.getPackageLabel(pm, installerApp));
                                    })
                                    .setNegativeButton(R.string.cancel, null)
                                    .show())
                    .setNeutralButton(R.string.reset_to_default, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_INSTALLER_INSTALLER_APP_STR, installerApp = BuildConfig.APPLICATION_ID);
                        installerAppPref.setSummary(PackageUtils.getPackageLabel(pm, installerApp));
                    })
                    .show();
            return true;
        });
        // Sign apk before installing
        SwitchPreferenceCompat signApk = Objects.requireNonNull(findPreference("installer_sign_apk"));
        signApk.setChecked(AppPref.canSignApk());
        signApk.setOnPreferenceChangeListener((preference, enabled) -> {
            if ((boolean) enabled && !Signer.canSign()) {
                new ScrollableDialogBuilder(requireActivity())
                        .setTitle(R.string.pref_sign_apk_no_signing_key)
                        .setMessage(R.string.pref_sign_apk_error_signing_key_not_added)
                        .enableAnchors()
                        .setPositiveButton(R.string.add, (dialog, which, isChecked) -> {
                            Intent intent = new Intent()
                                    .setData(Uri.parse("app-manager://settings/apk_signing_prefs/signing_keys"));
                            startActivity(intent);
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                return false;
            }
            return true;
        });
        // Display changes
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("installer_display_changes")))
                .setChecked(AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_DISPLAY_CHANGES_BOOL));
        // Block trackers
        SwitchPreferenceCompat blockTrackersPref = Objects.requireNonNull(findPreference("installer_block_trackers"));
        blockTrackersPref.setVisible(Ops.isRoot());
        blockTrackersPref.setChecked(AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_BLOCK_TRACKERS_BOOL));
        // Running installer in the background
        SwitchPreferenceCompat backgroundPref = Objects.requireNonNull(findPreference("installer_always_on_background"));
        backgroundPref.setVisible(Utils.canDisplayNotification(requireContext()));
        backgroundPref.setChecked(AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_ALWAYS_ON_BACKGROUND_BOOL));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Observe installer app selection
        model.getPackageNameLabelPairLiveData().observe(getViewLifecycleOwner(), this::displayInstallerAppSelectionDialog);
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.installer);
    }

    public void displayInstallerAppSelectionDialog(@NonNull List<Pair<String, CharSequence>> appInfo) {
        ArrayList<String> items = new ArrayList<>(appInfo.size());
        ArrayList<CharSequence> itemNames = new ArrayList<>(appInfo.size());
        for (Pair<String, CharSequence> pair : appInfo) {
            items.add(pair.first);
            itemNames.add(new SpannableStringBuilder(pair.second)
                    .append("\n")
                    .append(getSecondaryText(requireContext(), getSmallerText(pair.first))));
        }
        int selectedApp = itemNames.indexOf(installerApp);
        activity.progressIndicator.hide();
        new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.installer_app)
                .setSingleChoiceItems(itemNames.toArray(new CharSequence[0]),
                        selectedApp, (dialog, which) -> installerApp = items.get(which))
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    AppPref.set(AppPref.PrefKey.PREF_INSTALLER_INSTALLER_APP_STR, installerApp);
                    installerAppPref.setSummary(PackageUtils.getPackageLabel(pm, installerApp));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
