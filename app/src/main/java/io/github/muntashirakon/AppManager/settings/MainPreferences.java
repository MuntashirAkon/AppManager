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

package io.github.muntashirakon.AppManager.settings;

import android.annotation.SuppressLint;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Spanned;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.yariksoffice.lingver.Lingver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.collection.ArrayMap;
import androidx.core.app.ActivityCompat;
import androidx.core.text.HtmlCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.types.FullscreenDialog;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class MainPreferences extends PreferenceFragmentCompat {
    private static final List<Integer> THEME_CONST = Arrays.asList(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES);
    private static final List<String> MODE_NAMES = Arrays.asList(
            Runner.MODE_AUTO,
            Runner.MODE_ROOT,
            Runner.MODE_ADB,
            Runner.MODE_NO_ROOT);
    @StringRes
    private static final int[] encryptionNames = new int[]{
            R.string.none,
            R.string.aes,
            R.string.rsa,
            R.string.ecc,
            R.string.open_pgp_provider
    };

    private static final int[] installLocationNames = new int[]{
            R.string.auto,  // PackageInfo.INSTALL_LOCATION_AUTO
            R.string.install_location_internal_only,  // PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY
            R.string.install_location_prefer_external,  // PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL
    };

    SettingsActivity activity;
    private int currentTheme;
    private String currentLang;
    @Runner.Mode
    private String currentMode;
    private int currentCompression;
    private String installerApp;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        activity = (SettingsActivity) requireActivity();
        // Custom locale
        currentLang = (String) AppPref.get(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR);
        ArrayMap<String, Locale> locales = LangUtils.getAppLanguages(activity);
        final CharSequence[] languages = getLanguagesL(locales);
        Preference locale = Objects.requireNonNull(findPreference("custom_locale"));
        locale.setSummary(getString(R.string.current_language, languages[locales.indexOfKey(currentLang)]));
        locale.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.choose_language)
                    .setSingleChoiceItems(languages, locales.indexOfKey(currentLang),
                            (dialog, which) -> currentLang = locales.keyAt(which))
                    .setPositiveButton(R.string.apply, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR, currentLang);
                        Lingver.getInstance().setLocale(activity, LangUtils.getLocaleByLanguage(activity));
                        ActivityCompat.recreate(activity);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // App theme
        final String[] themes = getResources().getStringArray(R.array.themes);
        currentTheme = (int) AppPref.get(AppPref.PrefKey.PREF_APP_THEME_INT);
        Preference appTheme = Objects.requireNonNull(findPreference("app_theme"));
        appTheme.setSummary(getString(R.string.current_theme, themes[THEME_CONST.indexOf(currentTheme)]));
        appTheme.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.select_theme)
                    .setSingleChoiceItems(themes, THEME_CONST.indexOf(currentTheme),
                            (dialog, which) -> currentTheme = THEME_CONST.get(which))
                    .setPositiveButton(R.string.apply, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_APP_THEME_INT, currentTheme);
                        AppCompatDelegate.setDefaultNightMode(currentTheme);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Mode of operation
        Preference mode = Objects.requireNonNull(findPreference("mode_of_operations"));
        final String[] modes = getResources().getStringArray(R.array.modes);
        currentMode = (String) AppPref.get(AppPref.PrefKey.PREF_MODE_OF_OPS_STR);
        mode.setSummary(getString(R.string.current_mode, modes[MODE_NAMES.indexOf(currentMode)]));
        mode.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.pref_mode_of_operations)
                    .setSingleChoiceItems(modes, MODE_NAMES.indexOf(currentMode),
                            (dialog, which) -> currentMode = MODE_NAMES.get(which))
                    .setPositiveButton(R.string.apply, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_MODE_OF_OPS_STR, currentMode);
                        mode.setSummary(getString(R.string.current_mode, modes[MODE_NAMES.indexOf(currentMode)]));
                        new Thread(() -> RunnerUtils.setModeOfOps(activity)).start();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // App usage permission toggle
        SwitchPreferenceCompat usageEnabled = Objects.requireNonNull(findPreference("usage_access_enabled"));
        usageEnabled.setChecked((boolean) AppPref.get(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL));
        // Global blocking enabled
        SwitchPreferenceCompat gcb = Objects.requireNonNull(findPreference("global_blocking_enabled"));
        gcb.setChecked(AppPref.isGlobalBlockingEnabled());
        gcb.setOnPreferenceChangeListener((preference, isEnabled) -> {
            if (AppPref.isRootEnabled() && (boolean) isEnabled) {
                // Apply all rules immediately if GCB is true
                int userHandle = Users.getCurrentUserHandle();
                ComponentsBlocker.applyAllRules(activity, userHandle);
            }
            return true;
        });
        // Import/export rules
        ((Preference) Objects.requireNonNull(findPreference("import_export_rules"))).setOnPreferenceClickListener(preference -> {
            new ImportExportDialogFragment().show(getParentFragmentManager(), ImportExportDialogFragment.TAG);
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
                            // TODO: Remove for all users
                            int userHandle = Users.getCurrentUserHandle();
                            List<String> packages = ComponentUtils.getAllPackagesWithRules();
                            for (String packageName : packages) {
                                ComponentUtils.removeAllRules(packageName, userHandle);
                            }
                            activity.runOnUiThread(() -> {
                                activity.progressIndicator.hide();
                                Toast.makeText(activity, R.string.the_operation_was_successful, Toast.LENGTH_SHORT).show();
                            });
                        }).start();
                    })
                    .setNegativeButton(R.string.no, null)
                    .show();
            return true;
        });
        // Display users in installer
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("installer_display_users")))
                .setChecked((boolean) AppPref.get(AppPref.PrefKey.PREF_INSTALLER_DISPLAY_USERS_BOOL));
        // Set install locations
        Preference installLocationPref = Objects.requireNonNull(findPreference("installer_install_location"));
        int installLocation = (int) AppPref.get(AppPref.PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT);
        installLocationPref.setSummary(installLocationNames[installLocation]);
        installLocationPref.setOnPreferenceClickListener(preference -> {
            CharSequence[] installLocationTexts = new CharSequence[installLocationNames.length];
            for (int i = 0; i < installLocationNames.length; ++i) {
                installLocationTexts[i] = getString(installLocationNames[i]);
            }
            int choice = (int) AppPref.get(AppPref.PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT);
            new MaterialAlertDialogBuilder(activity)
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
        Preference installerAppPref = Objects.requireNonNull(findPreference("installer_installer_app"));
        PackageManager pm = activity.getPackageManager();
        installerApp = (String) AppPref.get(AppPref.PrefKey.PREF_INSTALLER_INSTALLER_APP_STR);
        installerAppPref.setSummary(PackageUtils.getPackageLabel(pm, installerApp));
        installerAppPref.setOnPreferenceClickListener(preference -> {
            activity.progressIndicator.show();
            new Thread(() -> {
                // List apps
                List<PackageInfo> packageInfoList = pm.getInstalledPackages(0);
                ArrayList<String> items = new ArrayList<>(packageInfoList.size());
                ArrayList<CharSequence> itemNames = new ArrayList<>(packageInfoList.size());
                for (PackageInfo info : packageInfoList) {
                    items.add(info.packageName);
                    itemNames.add(info.applicationInfo.loadLabel(pm));
                }
                int selectedApp = itemNames.indexOf(installerApp);
                activity.runOnUiThread(() -> {
                    activity.progressIndicator.hide();
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.installer_app)
                            .setSingleChoiceItems(itemNames.toArray(new CharSequence[0]),
                                    selectedApp, (dialog, which) -> installerApp = items.get(which))
                            .setPositiveButton(R.string.ok, (dialog, which) -> {
                                AppPref.set(AppPref.PrefKey.PREF_INSTALLER_INSTALLER_APP_STR, installerApp);
                                installerAppPref.setSummary(PackageUtils.getPackageLabel(pm, installerApp));
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });
            }).start();
            return true;
        });
        // Sign apk before install
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("installer_sign_apk")))
                .setChecked((boolean) AppPref.get(AppPref.PrefKey.PREF_INSTALLER_SIGN_APK_BOOL));
        // Backup compression method
        String[] tarTypes = MetadataManager.TAR_TYPES;
        String[] readableTarTypes = new String[]{"GZip", "BZip2"};
        currentCompression = ArrayUtils.indexOf(tarTypes, AppPref.get(AppPref.PrefKey.PREF_BACKUP_COMPRESSION_METHOD_STR));
        Preference compressionMethod = Objects.requireNonNull(findPreference("backup_compression_method"));
        compressionMethod.setSummary(getString(R.string.compression_method, readableTarTypes[currentCompression == -1 ? 0 : currentCompression]));
        compressionMethod.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.pref_compression_method)
                    .setSingleChoiceItems(readableTarTypes, currentCompression,
                            (dialog, which) -> currentCompression = which)
                    .setPositiveButton(R.string.save, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_BACKUP_COMPRESSION_METHOD_STR, tarTypes[currentCompression]);
                        compressionMethod.setSummary(getString(R.string.compression_method, readableTarTypes[currentCompression == -1 ? 0 : currentCompression]));
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Backup flags
        BackupFlags flags = BackupFlags.fromPref();
        ((Preference) Objects.requireNonNull(findPreference("backup_flags"))).setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.backup_options)
                    .setMultiChoiceItems(R.array.backup_flags, flags.flagsToCheckedItems(),
                            (dialog, flag, isChecked) -> {
                                if (isChecked) flags.addFlag(flag);
                                else flags.removeFlag(flag);
                            })
                    .setPositiveButton(R.string.save, (dialog, which) ->
                            AppPref.set(AppPref.PrefKey.PREF_BACKUP_FLAGS_INT, flags.getFlags()))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Encryption
        ((Preference) Objects.requireNonNull(findPreference("encryption"))).setOnPreferenceClickListener(preference -> {
            CharSequence[] encryptionNamesText = new CharSequence[encryptionNames.length];
            for (int i = 0; i < encryptionNames.length; ++i) {
                encryptionNamesText[i] = getString(encryptionNames[i]);
            }
            int choice = encModeToIndex((String) AppPref.get(AppPref.PrefKey.PREF_ENCRYPTION_STR));
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.encryption)
                    .setSingleChoiceItems(encryptionNamesText, choice, (dialog, which) -> {
                        String encryptionMode = indexToEncMode(which);
                        switch (encryptionMode) {
                            case CryptoUtils.MODE_NO_ENCRYPTION:
                                AppPref.set(AppPref.PrefKey.PREF_ENCRYPTION_STR, encryptionMode);
                                break;
                            case CryptoUtils.MODE_AES:
                            case CryptoUtils.MODE_RSA:
                            case CryptoUtils.MODE_ECC:
                                // TODO(12/11/20): Implement encryption options
                                Toast.makeText(activity, "Not implemented yet.", Toast.LENGTH_SHORT).show();
                                break;
                            case CryptoUtils.MODE_OPEN_PGP:
                                AppPref.set(AppPref.PrefKey.PREF_ENCRYPTION_STR, encryptionMode);
                                new OpenPgpKeySelectionDialogFragment().show(getParentFragmentManager(), OpenPgpKeySelectionDialogFragment.TAG);
                        }
                    })
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return true;
        });
        // About
        ((Preference) Objects.requireNonNull(findPreference("about"))).setOnPreferenceClickListener(preference -> {
            @SuppressLint("InflateParams")
            View view = getLayoutInflater().inflate(R.layout.dialog_about, null);
            ((TextView) view.findViewById(R.id.version)).setText(String.format(Locale.ROOT,
                    "%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
            new FullscreenDialog(activity).setTitle(R.string.about).setView(view).show();
            return true;
        });
        // Changelog
        ((Preference) Objects.requireNonNull(findPreference("changelog"))).setOnPreferenceClickListener(preference -> {
            new Thread(() -> {
                final Spanned spannedChangelog = HtmlCompat.fromHtml(IOUtils.getContentFromAssets(activity, "changelog.html"), HtmlCompat.FROM_HTML_MODE_COMPACT);
                activity.runOnUiThread(() ->
                        UIUtils.getDialogWithScrollableTextView(activity, spannedChangelog, true)
                                .setTitle(R.string.changelog)
                                .setNegativeButton(R.string.ok, null)
                                .show());
            }).start();
            return true;
        });
    }

    @CryptoUtils.Mode
    private String indexToEncMode(int index) {
        switch (index) {
            default:
            case 0:
                return CryptoUtils.MODE_NO_ENCRYPTION;
            case 1:
                return CryptoUtils.MODE_AES;
            case 2:
                return CryptoUtils.MODE_RSA;
            case 3:
                return CryptoUtils.MODE_ECC;
            case 4:
                return CryptoUtils.MODE_OPEN_PGP;
        }
    }

    private int encModeToIndex(@NonNull @CryptoUtils.Mode String mode) {
        switch (mode) {
            default:
            case CryptoUtils.MODE_NO_ENCRYPTION:
                return 0;
            case CryptoUtils.MODE_AES:
                return 1;
            case CryptoUtils.MODE_RSA:
                return 2;
            case CryptoUtils.MODE_ECC:
                return 3;
            case CryptoUtils.MODE_OPEN_PGP:
                return 4;
        }
    }

    @NonNull
    private CharSequence[] getLanguagesL(@NonNull ArrayMap<String, Locale> locales) {
        CharSequence[] localesL = new CharSequence[locales.size()];
        Locale locale;
        for (int i = 0; i < locales.size(); ++i) {
            locale = locales.valueAt(i);
            if (LangUtils.LANG_AUTO.equals(locales.keyAt(i))) {
                localesL[i] = activity.getString(R.string.auto);
            } else localesL[i] = locale.getDisplayName(locale);
        }
        return localesL;
    }
}
