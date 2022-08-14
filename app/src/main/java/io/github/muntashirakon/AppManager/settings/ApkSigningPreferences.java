// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.signing.SigSchemes;
import io.github.muntashirakon.AppManager.apk.signing.Signer;
import io.github.muntashirakon.AppManager.settings.crypto.RSACryptoSelectionDialogFragment;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.DigestUtils;

public class ApkSigningPreferences extends PreferenceFragment {
    public static final String TAG = "ApkSigningPreferences";
    private SettingsActivity activity;
    private Preference customSigPref;
    private MainPreferencesViewModel model;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_signature, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        model = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
        activity = (SettingsActivity) requireActivity();
        // Set signature schemes
        Preference sigSchemes = Objects.requireNonNull(findPreference("signature_schemes"));
        final SigSchemes sigSchemeFlags = SigSchemes.fromPref();
        sigSchemes.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.app_signing_signature_schemes)
                    .setMultiChoiceItems(R.array.sig_schemes, sigSchemeFlags.flagsToCheckedItems(), (dialog, which, isChecked) -> {
                        if (isChecked) sigSchemeFlags.addFlag(which);
                        else sigSchemeFlags.removeFlag(which);
                    })
                    .setPositiveButton(R.string.save, (dialog, which) ->
                            AppPref.set(AppPref.PrefKey.PREF_SIGNATURE_SCHEMES_INT, sigSchemeFlags.getFlags()))
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.reset_to_default, (dialog, which) ->
                            AppPref.set(AppPref.PrefKey.PREF_SIGNATURE_SCHEMES_INT, sigSchemeFlags.getDefaultFlags()))
                    .show();
            return true;
        });
        customSigPref = Objects.requireNonNull(findPreference("signing_keys"));
        customSigPref.setOnPreferenceClickListener(preference -> {
            RSACryptoSelectionDialogFragment fragment = RSACryptoSelectionDialogFragment.getInstance(Signer.SIGNING_KEY_ALIAS);
            fragment.setOnKeyPairUpdatedListener((keyPair, certificateBytes) -> {
                if (keyPair != null && certificateBytes != null) {
                    String hash = DigestUtils.getHexDigest(DigestUtils.SHA_256, certificateBytes);
                    try {
                        keyPair.destroy();
                    } catch (Exception ignore) {
                    }
                    customSigPref.setSummary(hash);
                } else {
                    customSigPref.setSummary(R.string.key_not_set);
                }
            });
            fragment.show(getParentFragmentManager(), RSACryptoSelectionDialogFragment.TAG);
            return true;
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        model.getSigningKeySha256HashLiveData().observe(getViewLifecycleOwner(), hash -> {
            if (hash != null) {
                customSigPref.setSummary(hash);
            } else {
                customSigPref.setSummary(R.string.key_not_set);
            }
        });
        model.loadSigningKeySha256Hash();
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.apk_signing);
    }
}
