// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Bundle;

import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.security.cert.Certificate;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.signing.SigSchemes;
import io.github.muntashirakon.AppManager.apk.signing.Signer;
import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.crypto.RSACryptoSelectionDialogFragment;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.DigestUtils;

public class ApkSigningPreferences extends PreferenceFragment {
    public static final String TAG = "ApkSigningPreferences";
    private SettingsActivity activity;
    private Preference customSig;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_signature, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
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
        customSig = Objects.requireNonNull(findPreference("signing_keys"));
        new Thread(this::updateSigningPref).start();
        customSig.setOnPreferenceClickListener(preference -> {
            RSACryptoSelectionDialogFragment fragment = RSACryptoSelectionDialogFragment.getInstance(Signer.SIGNING_KEY_ALIAS);
            fragment.setOnKeyPairUpdatedListener((keyPair, certificateBytes) -> {
                if (keyPair != null && certificateBytes != null) {
                    String hash = DigestUtils.getHexDigest(DigestUtils.SHA_256, certificateBytes);
                    try {
                        keyPair.destroy();
                    } catch (Exception ignore) {
                    }
                    activity.runOnUiThread(() -> customSig.setSummary(hash));
                } else {
                    customSig.setSummary(R.string.key_not_set);
                }
            });
            fragment.show(getParentFragmentManager(), RSACryptoSelectionDialogFragment.TAG);
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.apk_signing);
    }

    private void updateSigningPref() {
        try {
            KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
            if (keyStoreManager.containsKey(Signer.SIGNING_KEY_ALIAS)) {
                KeyPair keyPair = keyStoreManager.getKeyPair(Signer.SIGNING_KEY_ALIAS);
                if (keyPair != null) {
                    Certificate certificate = keyPair.getCertificate();
                    String hash = DigestUtils.getHexDigest(DigestUtils.SHA_256, certificate.getEncoded());
                    try {
                        keyPair.destroy();
                    } catch (Exception ignore) {
                    }
                    activity.runOnUiThread(() -> customSig.setSummary(hash));
                    return;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e);
        }
        activity.runOnUiThread(() -> customSig.setSummary(R.string.key_not_set));
    }
}
