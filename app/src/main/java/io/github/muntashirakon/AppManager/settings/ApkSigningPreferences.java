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

import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.signing.SigSchemes;
import io.github.muntashirakon.AppManager.apk.signing.Signer;
import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.crypto.KeyPairGeneratorDialogFragment;
import io.github.muntashirakon.AppManager.types.ScrollableDialogBuilder;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public class ApkSigningPreferences extends PreferenceFragmentCompat {
    public static final String TAG = "ApkSigningPreferences";
    private SettingsActivity activity;
    @Nullable
    private KeyStoreManager keyStoreManager;
    @Nullable
    private Certificate certificate;

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
                    .setTitle(R.string.signature_schemes)
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
        Preference customSig = Objects.requireNonNull(findPreference("signing_keys"));
        new Thread(() -> updateSigningPref(customSig)).start();
        customSig.setOnPreferenceClickListener(preference -> {
            ScrollableDialogBuilder builder = new ScrollableDialogBuilder(activity)
                    .setTitle(R.string.signing_keys)
                    .setPositiveButton(R.string.pref_import, null)
                    .setNegativeButton(R.string.generate_key, null)
                    .setNeutralButton(R.string.use_default, null)
                    .setMessage(getSigningInfo());
            AlertDialog alertDialog = builder.create();
            alertDialog.setOnShowListener(dialog -> {
                AlertDialog dialog1 = (AlertDialog) dialog;
                Button importButton = dialog1.getButton(AlertDialog.BUTTON_POSITIVE);
                Button generateButton = dialog1.getButton(AlertDialog.BUTTON_NEGATIVE);
                Button defaultButton = dialog1.getButton(AlertDialog.BUTTON_NEUTRAL);
                importButton.setOnClickListener(v -> {
                    // TODO: 4/4/21 Import key from JKS/PKCS12/BKS or PK8
                });
                generateButton.setOnClickListener(v -> {
                    KeyPairGeneratorDialogFragment fragment = new KeyPairGeneratorDialogFragment();
                    fragment.setOnGenerateListener((password, keyPair) -> new Thread(() -> {
                        try {
                            if (keyPair == null) {
                                throw new Exception("Keypair can't be null.");
                            }
                            keyStoreManager = KeyStoreManager.getInstance();
                            keyStoreManager.addKeyPair(Signer.SIGNING_KEY_ALIAS, keyPair, password, true);
                            if (password != null) Utils.clearChars(password);
                            if (isDetached()) return;
                            activity.runOnUiThread(() -> UIUtils.displayShortToast(R.string.done));
                            updateSigningPref(customSig);
                            activity.runOnUiThread(() -> builder.setMessage(getSigningInfo()));
                        } catch (Exception e) {
                            Log.e(TAG, e);
                            activity.runOnUiThread(() -> UIUtils.displayLongToast(R.string.failed_to_save_key));
                        }
                    }).start());
                    fragment.show(getParentFragmentManager(), KeyPairGeneratorDialogFragment.TAG);
                });
                defaultButton.setOnClickListener(v -> new Thread(() -> {
                    try {
                        keyStoreManager = KeyStoreManager.getInstance();
                        if (keyStoreManager.containsKey(Signer.SIGNING_KEY_ALIAS)) {
                            keyStoreManager.removeItem(Signer.SIGNING_KEY_ALIAS);
                        }
                        if (isDetached()) return;
                        activity.runOnUiThread(() -> UIUtils.displayShortToast(R.string.done));
                        updateSigningPref(customSig);
                    } catch (Exception e) {
                        Log.e(TAG, e);
                        activity.runOnUiThread(() -> UIUtils.displayLongToast(R.string.failed_to_save_key));
                    } finally {
                        alertDialog.dismiss();
                    }
                }).start());
            });
            alertDialog.show();
            return true;
        });
    }

    public CharSequence getSigningInfo() {
        if (certificate != null) {
            try {
                return PackageUtils.getSigningCertificateInfo(activity, (X509Certificate) certificate);
            } catch (CertificateEncodingException e) {
                return getString(R.string.failed_to_load_signing_key);
            }
        }
        return getString(R.string.default_signing_key_used);
    }

    public void updateSigningPref(Preference preference) {
        try {
            keyStoreManager = KeyStoreManager.getInstance();
            if (keyStoreManager.containsKey(Signer.SIGNING_KEY_ALIAS)) {
                KeyPair keyPair = keyStoreManager.getKeyPair(Signer.SIGNING_KEY_ALIAS, null);
                if (keyPair != null) {
                    certificate = keyPair.getCertificate();
                    String hash = DigestUtils.getHexDigest(DigestUtils.SHA_256, certificate.getEncoded());
                    try {
                        keyPair.destroy();
                    } catch (Exception ignore) {
                    }
                    activity.runOnUiThread(() -> preference.setSummary(hash));
                    return;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e);
        }
        activity.runOnUiThread(() -> preference.setSummary(R.string.signing_key_not_set));
    }
}
