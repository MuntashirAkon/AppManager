// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.Nullable;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.preference.DefaultAlertPreference;
import io.github.muntashirakon.preference.TopSwitchPreference;

public class VirusTotalPreferences extends PreferenceFragment {
    private MainPreferencesViewModel mModel;

    @Override
    public int getTitle() {
        return R.string.virus_total;
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle bundle, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_virus_total, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        mModel = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
        boolean hasInternet = FeatureController.isInternetEnabled();
        boolean isVtEnabled = FeatureController.isVirusTotalEnabled();
        String apiKey = Prefs.VirusTotal.getApiKey();
        TopSwitchPreference useVtPref = requirePreference("use_vt");
        DefaultAlertPreference infoNoInternetPref = requirePreference("info_no_internet");
        Preference vtApiKeyPref = requirePreference("virus_total_api_key");
        SwitchPreferenceCompat promptBeforeUploadPref = requirePreference("virus_total_prompt_before_uploading");
        DefaultAlertPreference infoPref = requirePreference("info");
        // Set values
        useVtPref.setEnabled(hasInternet);
        useVtPref.setChecked(isVtEnabled);
        useVtPref.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean isEnabled = (boolean) newValue;
            enablePrefs(isEnabled, vtApiKeyPref, promptBeforeUploadPref);
            FeatureController.getInstance().modifyState(FeatureController.FEAT_VIRUS_TOTAL, isEnabled);
            return true;
        });
        infoNoInternetPref.setVisible(!hasInternet);
        enablePrefs(isVtEnabled, vtApiKeyPref, promptBeforeUploadPref);
        if (apiKey != null) {
            vtApiKeyPref.setSummary(apiKey);
        } else {
            vtApiKeyPref.setSummary(R.string.key_not_set);
        }
        vtApiKeyPref.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(requireContext(), null)
                    .setTitle(R.string.pref_vt_apikey)
                    .setInputText(Prefs.VirusTotal.getApiKey())
                    .setInputTypeface(Typeface.MONOSPACE)
                    .setInputInputType(InputType.TYPE_CLASS_TEXT)
                    .setInputImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.save, (dialog, which, inputText, isChecked) -> {
                        String newApiKey = !TextUtils.isEmpty(inputText) ? inputText.toString() : null;
                        Prefs.VirusTotal.setApiKey(newApiKey);
                        if (newApiKey != null) {
                            vtApiKeyPref.setSummary(newApiKey);
                        } else {
                            vtApiKeyPref.setSummary(R.string.key_not_set);
                        }
                    })
                    .show();
            return true;
        });
        promptBeforeUploadPref.setChecked(Prefs.VirusTotal.promptBeforeUpload());
        infoPref.setSummary(getString(R.string.pref_vt_apikey_description) + "\n\n" + getString(R.string.vt_disclaimer));
    }
}
