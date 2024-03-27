// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.Nullable;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.transition.MaterialSharedAxis;

import java.io.File;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.fm.FmActivity;
import io.github.muntashirakon.AppManager.fm.FmUtils;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;

public class FileManagerPreferences extends PreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_file_manager, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        // Display in launcher
        SwitchPreferenceCompat displayInLauncherPref = Objects.requireNonNull(findPreference("fm_display_in_launcher"));
        displayInLauncherPref.setChecked(Prefs.FileManager.displayInLauncher());
        displayInLauncherPref.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean isChecked = (boolean) newValue;
            int newState = isChecked ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            ComponentName componentName = new ComponentName(BuildConfig.APPLICATION_ID, FmActivity.LAUNCHER_ALIAS);
            requireContext().getPackageManager().setComponentEnabledSetting(componentName, newState, PackageManager.DONT_KILL_APP);
            return true;
        });
        // Remember last opened path
        SwitchPreferenceCompat filesRememberLastPathPref = Objects.requireNonNull(findPreference("fm_remember_last_path"));
        filesRememberLastPathPref.setChecked(Prefs.FileManager.isRememberLastOpenedPath());
        // Set home
        Preference setHomePrefs = Objects.requireNonNull(findPreference("fm_home"));
        setHomePrefs.setSummary(FmUtils.getDisplayablePath(Prefs.FileManager.getHome()));
        setHomePrefs.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(requireContext(), null)
                    .setTitle(R.string.pref_set_home)
                    .setInputText(FmUtils.getDisplayablePath(Prefs.FileManager.getHome()))
                    .setInputInputType(InputType.TYPE_CLASS_TEXT)
                    .setInputImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                        if (TextUtils.isEmpty(inputText)) {
                            return;
                        }
                        String newHome = inputText.toString();
                        Uri uri;
                        if (newHome.startsWith(File.separator)) {
                            uri = new Uri.Builder().scheme(ContentResolver.SCHEME_FILE).path(newHome).build();
                        } else uri = Uri.parse(newHome);
                        Prefs.FileManager.setHome(uri);
                        setHomePrefs.setSummary(FmUtils.getDisplayablePath(uri));
                    })
                    .show();
            return true;
        });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, true));
        setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, false));
    }

    @Override
    public int getTitle() {
        return R.string.files;
    }
}
