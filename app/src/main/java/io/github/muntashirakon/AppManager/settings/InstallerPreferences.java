// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

import android.content.Intent;
import android.content.pm.PackageInfo;
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
import com.google.android.material.transition.MaterialSharedAxis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.signing.Signer;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.ScrollableDialogBuilder;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;

public class InstallerPreferences extends PreferenceFragment {
    private static final Integer[] installLocations = new Integer[] {
            PackageInfo.INSTALL_LOCATION_AUTO,
            PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY,
            PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL
    };
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
                .setChecked(Prefs.Installer.displayUsers());
        // Set installation locations
        Preference installLocationPref = Objects.requireNonNull(findPreference("installer_install_location"));
        installLocationPref.setSummary(installLocationNames[Prefs.Installer.getInstallLocation()]);
        installLocationPref.setOnPreferenceClickListener(preference -> {
            CharSequence[] installLocationTexts = new CharSequence[installLocationNames.length];
            for (int i = 0; i < installLocationNames.length; ++i) {
                installLocationTexts[i] = getString(installLocationNames[i]);
            }
            int defaultChoice = Prefs.Installer.getInstallLocation();
            new SearchableSingleChoiceDialogBuilder<>(requireActivity(), installLocations, installLocationTexts)
                    .setTitle(R.string.install_location)
                    .setSelection(defaultChoice)
                    .setPositiveButton(R.string.save, (dialog, which, newInstallLocation) -> {
                        Objects.requireNonNull(newInstallLocation);
                        Prefs.Installer.setInstallLocation(newInstallLocation);
                        installLocationPref.setSummary(installLocationNames[newInstallLocation]);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Set installer app
        installerAppPref = Objects.requireNonNull(findPreference("installer_installer_app"));
        installerApp = Prefs.Installer.getInstallerPackageName();
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
                                        Prefs.Installer.setInstallerPackageName(installerApp);
                                        installerAppPref.setSummary(PackageUtils.getPackageLabel(pm, installerApp));
                                    })
                                    .setNegativeButton(R.string.cancel, null)
                                    .show())
                    .setNeutralButton(R.string.reset_to_default, (dialog, which) -> {
                        Prefs.Installer.setInstallerPackageName(installerApp = BuildConfig.APPLICATION_ID);
                        installerAppPref.setSummary(PackageUtils.getPackageLabel(pm, installerApp));
                    })
                    .show();
            return true;
        });
        // Sign apk before installing
        SwitchPreferenceCompat signApk = Objects.requireNonNull(findPreference("installer_sign_apk"));
        signApk.setChecked(Prefs.Installer.canSignApk());
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
                .setChecked(Prefs.Installer.displayChanges());
        // Block trackers
        SwitchPreferenceCompat blockTrackersPref = Objects.requireNonNull(findPreference("installer_block_trackers"));
        blockTrackersPref.setVisible(Ops.isRoot());
        blockTrackersPref.setChecked(Prefs.Installer.blockTrackers());
        // Running installer in the background
        SwitchPreferenceCompat backgroundPref = Objects.requireNonNull(findPreference("installer_always_on_background"));
        backgroundPref.setVisible(Utils.canDisplayNotification(requireContext()));
        backgroundPref.setChecked(Prefs.Installer.installInBackground());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Observe installer app selection
        model.getPackageNameLabelPairLiveData().observe(getViewLifecycleOwner(), this::displayInstallerAppSelectionDialog);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, true));
        setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, false));
    }

    @Override
    public int getTitle() {
        return R.string.installer;
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
        activity.progressIndicator.hide();
        new SearchableSingleChoiceDialogBuilder<>(requireActivity(), items, itemNames)
                .setTitle(R.string.installer_app)
                .setSelection(installerApp)
                .setPositiveButton(R.string.save, (dialog, which, selectedInstallerApp) -> {
                    if (selectedInstallerApp != null) {
                        installerApp = selectedInstallerApp;
                        Prefs.Installer.setInstallerPackageName(installerApp);
                        installerAppPref.setSummary(PackageUtils.getPackageLabel(pm, installerApp));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
