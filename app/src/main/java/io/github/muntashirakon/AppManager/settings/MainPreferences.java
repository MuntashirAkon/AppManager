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
import android.os.Bundle;
import android.text.Spanned;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.text.HtmlCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.types.FullscreenDialog;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;

public class MainPreferences extends PreferenceFragmentCompat {
    private static final List<Integer> themeConst = Arrays.asList(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES);

    SettingsActivity activity;
    private int currentTheme;
    private String currentLang;
    private int currentCompression;

    @SuppressWarnings("ConstantConditions")
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        activity = (SettingsActivity) requireActivity();
        // Custom locale
        currentLang = (String) AppPref.get(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR);
        final String[] languages = getResources().getStringArray(R.array.languages);
        final String[] langKeys = getResources().getStringArray(R.array.languages_key);
        Preference locale = findPreference("custom_locale");
        locale.setSummary(getString(R.string.current_language, languages[ArrayUtils.indexOf(langKeys, currentLang)]));
        locale.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.choose_language)
                    .setSingleChoiceItems(languages, ArrayUtils.indexOf(langKeys, currentLang),
                            (dialog, which) -> currentLang = langKeys[which])
                    .setPositiveButton(R.string.apply, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR, currentLang);
                        activity.recreate();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // App theme
        final String[] themes = getResources().getStringArray(R.array.themes);
        currentTheme = (int) AppPref.get(AppPref.PrefKey.PREF_APP_THEME_INT);
        Preference appTheme = findPreference("app_theme");
        appTheme.setSummary(getString(R.string.current_theme, themes[themeConst.indexOf(currentTheme)]));
        appTheme.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.select_theme)
                    .setSingleChoiceItems(themes, themeConst.indexOf(currentTheme),
                            (dialog, which) -> currentTheme = themeConst.get(which))
                    .setPositiveButton(R.string.apply, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_APP_THEME_INT, currentTheme);
                        AppCompatDelegate.setDefaultNightMode(currentTheme);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // App usage permission toggle
        SwitchPreferenceCompat usageEnabled = findPreference("usage_access_enabled");
        usageEnabled.setChecked((boolean) AppPref.get(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL));
        // Root mode enabled
        SwitchPreferenceCompat rootEnabled = findPreference("root_mode_enabled");
        rootEnabled.setChecked(AppPref.isRootEnabled());
        rootEnabled.setOnPreferenceChangeListener((preference, isEnabled) -> {
            // Reset runner
            Runner.getInstance();
            // Change server type based on root status
            LocalServer.updateConfig();
            return true;
        });
        // Global blocking enabled
        SwitchPreferenceCompat gcb = findPreference("global_blocking_enabled");
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
        findPreference("import_export_rules").setOnPreferenceClickListener(preference -> {
            new ImportExportDialogFragment().show(getParentFragmentManager(), ImportExportDialogFragment.TAG);
            return true;
        });
        // Remove all rules
        findPreference("remove_all_rules").setOnPreferenceClickListener(preference -> {
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
        // Backup compression method
        String[] tarTypes = MetadataManager.TAR_TYPES;
        String[] readableTarTypes = new String[]{"GZip", "BZip2"};
        currentCompression = ArrayUtils.indexOf(tarTypes, AppPref.get(AppPref.PrefKey.PREF_BACKUP_COMPRESSION_METHOD_STR));
        Preference compressionMethod = findPreference("backup_compression_method");
        compressionMethod.setSummary(getString(R.string.compression_method, readableTarTypes[currentCompression == -1 ? 0 : currentCompression]));
        compressionMethod.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.pref_compression_method)
                    .setSingleChoiceItems(readableTarTypes, currentCompression,
                            (dialog, which) -> currentCompression = which)
                    .setPositiveButton(R.string.save, (dialog, which) ->
                            AppPref.set(AppPref.PrefKey.PREF_BACKUP_COMPRESSION_METHOD_STR, tarTypes[currentCompression]))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Backup flags
        BackupFlags flags = BackupFlags.fromPref();
        findPreference("backup_flags").setOnPreferenceClickListener(preference -> {
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
        // OpenPGP Provider
        findPreference("open_pgp_provider").setOnPreferenceClickListener(preference -> {
            new OpenPgpKeySelectionDialogFragment().show(getParentFragmentManager(), OpenPgpKeySelectionDialogFragment.TAG);
            return true;
        });
        // About
        findPreference("about").setOnPreferenceClickListener(preference -> {
            @SuppressLint("InflateParams")
            View view = getLayoutInflater().inflate(R.layout.dialog_about, null);
            ((TextView) view.findViewById(R.id.version)).setText(String.format(Locale.ROOT,
                    "%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
            new FullscreenDialog(activity).setTitle(R.string.about).setView(view).show();
            return true;
        });
        // Changelog
        findPreference("changelog").setOnPreferenceClickListener(preference -> {
            new Thread(() -> {
                final Spanned spannedChangelog = HtmlCompat.fromHtml(IOUtils.getContentFromAssets(activity, "changelog.html"), HtmlCompat.FROM_HTML_MODE_COMPACT);
                activity.runOnUiThread(() -> {
                    View view = getLayoutInflater().inflate(R.layout.dialog_changelog, null);
                    ((MaterialTextView) view.findViewById(R.id.content)).setText(spannedChangelog);
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.changelog)
                            .setView(view)
                            .setNegativeButton(R.string.ok, null)
                            .show();
                });
            }).start();
            return true;
        });
    }
}
