// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandleHidden;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.transition.MaterialSharedAxis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.signing.Signer;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.ScrollableDialogBuilder;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.preference.TopSwitchPreference;

public class InstallerPreferences extends PreferenceFragment {
    public static final Integer[] INSTALL_LOCATIONS = new Integer[] {
            PackageInfo.INSTALL_LOCATION_AUTO,
            PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY,
            PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL
    };
    public static final int[] INSTALL_LOCATION_NAMES = new int[]{
            R.string.auto,  // PackageInfo.INSTALL_LOCATION_AUTO
            R.string.install_location_internal_only,  // PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY
            R.string.install_location_prefer_external,  // PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL
    };

    private SettingsActivity mActivity;
    private PackageManager mPm;
    private String mInstallerApp;
    private Preference mInstallerAppPref;
    private MainPreferencesViewModel mModel;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_installer, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        mModel = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
        mActivity = (SettingsActivity) requireActivity();
        mPm = mActivity.getPackageManager();
        boolean isInstallerEnabled = FeatureController.isInstallerEnabled();
        PreferenceCategory catGeneral = requirePreference("cat_general");
        PreferenceCategory catAdvanced = requirePreference("cat_advanced");
        TopSwitchPreference useInstaller = requirePreference("use_installer");
        useInstaller.setChecked(isInstallerEnabled);
        useInstaller.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean isEnabled = (boolean) newValue;
            enablePrefs(isEnabled, catGeneral, catAdvanced);
            FeatureController.getInstance().modifyState(FeatureController.FEAT_INSTALLER, isEnabled);
            return true;
        });
        enablePrefs(isInstallerEnabled, catGeneral, catAdvanced);

        // Set installation locations
        Preference installLocationPref = Objects.requireNonNull(findPreference("installer_install_location"));
        installLocationPref.setSummary(INSTALL_LOCATION_NAMES[Prefs.Installer.getInstallLocation()]);
        installLocationPref.setOnPreferenceClickListener(preference -> {
            CharSequence[] installLocationTexts = new CharSequence[INSTALL_LOCATION_NAMES.length];
            for (int i = 0; i < INSTALL_LOCATION_NAMES.length; ++i) {
                installLocationTexts[i] = getString(INSTALL_LOCATION_NAMES[i]);
            }
            int defaultChoice = Prefs.Installer.getInstallLocation();
            new SearchableSingleChoiceDialogBuilder<>(requireActivity(), INSTALL_LOCATIONS, installLocationTexts)
                    .setTitle(R.string.install_location)
                    .setSelection(defaultChoice)
                    .setPositiveButton(R.string.save, (dialog, which, newInstallLocation) -> {
                        Objects.requireNonNull(newInstallLocation);
                        Prefs.Installer.setInstallLocation(newInstallLocation);
                        installLocationPref.setSummary(INSTALL_LOCATION_NAMES[newInstallLocation]);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Set installer app
        mInstallerAppPref = Objects.requireNonNull(findPreference("installer_installer_app"));
        mInstallerAppPref.setEnabled(SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES));
        mInstallerApp = Prefs.Installer.getInstallerPackageName();
        mInstallerAppPref.setSummary(PackageUtils.getPackageLabel(mPm, mInstallerApp));
        mInstallerAppPref.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.installer_app)
                    .setMessage(R.string.installer_app_message)
                    .setPositiveButton(R.string.choose, (dialog1, which1) -> {
                        mActivity.progressIndicator.show();
                        mModel.loadPackageNameLabelPair();
                    })
                    .setNegativeButton(R.string.specify_custom_name, (dialog, which) ->
                            new TextInputDialogBuilder(requireActivity(), R.string.installer_app)
                                    .setTitle(R.string.installer_app)
                                    .setInputText(mInstallerApp)
                                    .setInputInputType(InputType.TYPE_CLASS_TEXT)
                                    .setInputImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING)
                                    .setPositiveButton(R.string.ok, (dialog1, which1, inputText, isChecked) -> {
                                        if (inputText == null) return;
                                        mInstallerApp = inputText.toString().trim();
                                        Prefs.Installer.setInstallerPackageName(mInstallerApp);
                                        mInstallerAppPref.setSummary(PackageUtils.getPackageLabel(mPm, mInstallerApp));
                                    })
                                    .setNegativeButton(R.string.cancel, null)
                                    .show())
                    .setNeutralButton(R.string.reset_to_default, (dialog, which) -> {
                        Prefs.Installer.setInstallerPackageName(mInstallerApp = BuildConfig.APPLICATION_ID);
                        mInstallerAppPref.setSummary(PackageUtils.getPackageLabel(mPm, mInstallerApp));
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
                            Intent intent = new Intent(Intent.ACTION_VIEW)
                                    .setData(Uri.parse("app-manager://settings/apk_signing_prefs/signing_keys"));
                            startActivity(intent);
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                return false;
            }
            return true;
        });
        SwitchPreferenceCompat forceDexOpt = Objects.requireNonNull(findPreference("installer_force_dex_opt"));
        forceDexOpt.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        forceDexOpt.setChecked(Prefs.Installer.forceDexOpt());
        // Display changes
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("installer_display_changes")))
                .setChecked(Prefs.Installer.displayChanges());
        // Block trackers
        SwitchPreferenceCompat blockTrackersPref = Objects.requireNonNull(findPreference("installer_block_trackers"));
        blockTrackersPref.setVisible(SelfPermissions.canModifyAppComponentStates(UserHandleHidden.myUserId(), null, true));
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
        mModel.getPackageNameLabelPairLiveData().observe(getViewLifecycleOwner(), this::displayInstallerAppSelectionDialog);
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
        mActivity.progressIndicator.hide();
        new SearchableSingleChoiceDialogBuilder<>(requireActivity(), items, itemNames)
                .setTitle(R.string.installer_app)
                .setSelection(mInstallerApp)
                .setPositiveButton(R.string.save, (dialog, which, selectedInstallerApp) -> {
                    if (selectedInstallerApp != null) {
                        mInstallerApp = selectedInstallerApp;
                        Prefs.Installer.setInstallerPackageName(mInstallerApp);
                        mInstallerAppPref.setSummary(PackageUtils.getPackageLabel(mPm, mInstallerApp));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
