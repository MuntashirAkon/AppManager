// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.AppPref;

public class AppearancePreferences extends PreferenceFragment {
    private static final List<Integer> THEME_CONST = Arrays.asList(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES);
    private static final List<Integer> LAYOUT_ORIENTATION_CONST = Arrays.asList(
            View.LAYOUT_DIRECTION_LOCALE,
            View.LAYOUT_DIRECTION_LTR,
            View.LAYOUT_DIRECTION_RTL);

    private int currentTheme;
    private int currentLayoutOrientation;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_appearance, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        // App theme
        final String[] themes = getResources().getStringArray(R.array.themes);
        currentTheme = AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_INT);
        Preference appTheme = Objects.requireNonNull(findPreference("app_theme"));
        appTheme.setSummary(themes[THEME_CONST.indexOf(currentTheme)]);
        appTheme.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.select_theme)
                    .setSingleChoiceItems(themes, THEME_CONST.indexOf(currentTheme),
                            (dialog, which) -> currentTheme = THEME_CONST.get(which))
                    .setPositiveButton(R.string.apply, (dialog, which) -> {
                        if (AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_INT) != currentTheme) {
                            AppPref.set(AppPref.PrefKey.PREF_APP_THEME_INT, currentTheme);
                            AppCompatDelegate.setDefaultNightMode(currentTheme);
                            appTheme.setSummary(themes[THEME_CONST.indexOf(currentTheme)]);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Black theme/custom theme
        SwitchPreferenceCompat fullBlackTheme = Objects.requireNonNull(findPreference("app_theme_pure_black"));
        fullBlackTheme.setVisible(BuildConfig.DEBUG);
        fullBlackTheme.setChecked(AppPref.isPureBlackTheme());
        fullBlackTheme.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = (boolean) newValue;
            AppPref.setPureBlackTheme(enabled);
            ActivityCompat.recreate(requireActivity());
            return true;
        });
        // Layout orientation
        final String[] layoutOrientations = getResources().getStringArray(R.array.layout_orientations);
        currentLayoutOrientation = AppPref.getLayoutOrientation();
        Preference layoutOrientation = Objects.requireNonNull(findPreference("layout_orientation"));
        layoutOrientation.setSummary(layoutOrientations[LAYOUT_ORIENTATION_CONST.indexOf(currentLayoutOrientation)]);
        layoutOrientation.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.pref_layout_orientation)
                    .setSingleChoiceItems(layoutOrientations, LAYOUT_ORIENTATION_CONST.indexOf(currentLayoutOrientation),
                            (dialog, which) -> currentLayoutOrientation = LAYOUT_ORIENTATION_CONST.get(which))
                    .setPositiveButton(R.string.apply, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_LAYOUT_ORIENTATION_INT, currentLayoutOrientation);
                        ActivityCompat.recreate(requireActivity());
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Enable/disable features
        FeatureController fc = FeatureController.getInstance();
        ((Preference) Objects.requireNonNull(findPreference("enabled_features")))
                .setOnPreferenceClickListener(preference -> {
                    new MaterialAlertDialogBuilder(requireActivity())
                            .setTitle(R.string.enable_disable_features)
                            .setMultiChoiceItems(FeatureController.getFormattedFlagNames(requireActivity()),
                                    fc.flagsToCheckedItems(),
                                    (dialog, index, isChecked) -> fc.modifyState(FeatureController.featureFlags.get(index), isChecked))
                            .setNegativeButton(R.string.close, null)
                            .show();
                    return true;
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.pref_cat_appearance);
    }
}
