// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.transition.MaterialSharedAxis;

import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.signing.SigSchemes;
import io.github.muntashirakon.AppManager.apk.signing.Signer;
import io.github.muntashirakon.AppManager.settings.crypto.RSACryptoSelectionDialogFragment;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.dialog.SearchableFlagsDialogBuilder;

public class ApkSigningPreferences extends PreferenceFragment {
    public static final String TAG = "ApkSigningPreferences";

    private SettingsActivity mActivity;
    private Preference mCustomSigPref;
    private MainPreferencesViewModel mModel;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_signature, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        mModel = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
        mActivity = (SettingsActivity) requireActivity();
        // Set signature schemes
        Preference sigSchemes = Objects.requireNonNull(findPreference("signature_schemes"));
        final SigSchemes sigSchemeFlags = Prefs.Signing.getSigSchemes();
        sigSchemes.setOnPreferenceClickListener(preference -> {
            new SearchableFlagsDialogBuilder<>(mActivity, sigSchemeFlags.getAllItems(), R.array.sig_schemes, sigSchemeFlags.getFlags())
                    .setTitle(R.string.app_signing_signature_schemes)
                    .setPositiveButton(R.string.save, (dialog, which, selections) -> {
                        int flags = 0;
                        for (int flag : selections) {
                            flags |= flag;
                        }
                        sigSchemeFlags.setFlags(flags);
                        Prefs.Signing.setSigSchemes(flags);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.reset_to_default, (dialog, which, selections) -> {
                        sigSchemeFlags.setFlags(SigSchemes.DEFAULT_SCHEMES);
                        Prefs.Signing.setSigSchemes(SigSchemes.DEFAULT_SCHEMES);
                    })
                    .show();
            return true;
        });
        mCustomSigPref = Objects.requireNonNull(findPreference("signing_keys"));
        mCustomSigPref.setOnPreferenceClickListener(preference -> {
            RSACryptoSelectionDialogFragment fragment = RSACryptoSelectionDialogFragment.getInstance(Signer.SIGNING_KEY_ALIAS);
            fragment.setOnKeyPairUpdatedListener((keyPair, certificateBytes) -> {
                if (keyPair != null && certificateBytes != null) {
                    String hash = DigestUtils.getHexDigest(DigestUtils.SHA_256, certificateBytes);
                    try {
                        keyPair.destroy();
                    } catch (Exception ignore) {
                    }
                    mCustomSigPref.setSummary(hash);
                } else {
                    mCustomSigPref.setSummary(R.string.key_not_set);
                }
            });
            fragment.show(getParentFragmentManager(), RSACryptoSelectionDialogFragment.TAG);
            return true;
        });
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("zip_align")))
                .setChecked(Prefs.Signing.zipAlign());
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, true));
        setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, false));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mModel.getSigningKeySha256HashLiveData().observe(getViewLifecycleOwner(), hash -> {
            if (hash != null) {
                mCustomSigPref.setSummary(hash);
            } else {
                mCustomSigPref.setSummary(R.string.key_not_set);
            }
        });
        mModel.loadSigningKeySha256Hash();
    }

    @Override
    public int getTitle() {
        return R.string.apk_signing;
    }
}
