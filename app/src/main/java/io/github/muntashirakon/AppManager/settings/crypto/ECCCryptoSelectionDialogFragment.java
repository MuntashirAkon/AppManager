// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings.crypto;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.crypto.ECCCrypto;
import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.ScrollableDialogBuilder;

public class ECCCryptoSelectionDialogFragment extends DialogFragment {
    public static final String TAG = ECCCryptoSelectionDialogFragment.class.getSimpleName();

    public interface OnKeyPairUpdatedListener {
        @UiThread
        void keyPairUpdated(@Nullable KeyPair keyPair, @Nullable byte[] certificateBytes);
    }

    private FragmentActivity activity;
    private ScrollableDialogBuilder builder;
    @Nullable
    private OnKeyPairUpdatedListener listener;
    @Nullable
    private KeyStoreManager keyStoreManager;
    private final String targetAlias = ECCCrypto.ECC_KEY_ALIAS;

    public void setOnKeyPairUpdatedListener(OnKeyPairUpdatedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        activity = requireActivity();
        builder = new ScrollableDialogBuilder(activity)
                .setTitle(R.string.ecc)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.pref_import, null)
                .setNeutralButton(R.string.generate_key, null);
        new Thread(() -> {
            if (isDetached()) return;
            CharSequence info = getSigningInfo();
            activity.runOnUiThread(() -> builder.setMessage(info));
        }).start();
        AlertDialog alertDialog = builder.create();
        alertDialog.setOnShowListener(dialog -> {
            AlertDialog dialog1 = (AlertDialog) dialog;
            Button defaultOrOkButton = dialog1.getButton(AlertDialog.BUTTON_POSITIVE);
            Button importButton = dialog1.getButton(AlertDialog.BUTTON_NEGATIVE);
            Button generateButton = dialog1.getButton(AlertDialog.BUTTON_NEUTRAL);
            importButton.setOnClickListener(v -> {
                KeyPairImporterDialogFragment fragment = new KeyPairImporterDialogFragment();
                Bundle args = new Bundle();
                args.putString(KeyPairImporterDialogFragment.EXTRA_ALIAS, targetAlias);
                fragment.setArguments(args);
                fragment.setOnKeySelectedListener((keyPair) -> new Thread(() ->
                        new Thread(() -> addKeyPair(keyPair)).start()).start());
                fragment.show(getParentFragmentManager(), KeyPairImporterDialogFragment.TAG);
            });
            generateButton.setOnClickListener(v -> {
                KeyPairGeneratorDialogFragment fragment = new KeyPairGeneratorDialogFragment();
                Bundle args = new Bundle();
                args.putString(KeyPairGeneratorDialogFragment.EXTRA_KEY_TYPE, CryptoUtils.MODE_ECC);
                fragment.setArguments(args);
                fragment.setOnGenerateListener((keyPair) -> new Thread(() ->
                        addKeyPair(keyPair)).start());
                fragment.show(getParentFragmentManager(), KeyPairGeneratorDialogFragment.TAG);
            });
            defaultOrOkButton.setOnClickListener(v -> new Thread(() -> {
                try {
                    if (isDetached()) return;
                    activity.runOnUiThread(() -> UIUtils.displayShortToast(R.string.done));
                    keyPairUpdated();
                } catch (Exception e) {
                    Log.e(TAG, e);
                    activity.runOnUiThread(() -> UIUtils.displayLongToast(R.string.failed_to_save_key));
                } finally {
                    alertDialog.dismiss();
                }
            }).start());
        });
        return alertDialog;
    }

    @WorkerThread
    private CharSequence getSigningInfo() {
        KeyPair keyPair = getKeyPair();
        if (keyPair != null) {
            try {
                return PackageUtils.getSigningCertificateInfo(activity, (X509Certificate) keyPair.getCertificate());
            } catch (CertificateEncodingException e) {
                return getString(R.string.failed_to_load_key);
            }
        }
        return getString(R.string.key_not_set);
    }

    @WorkerThread
    private void addKeyPair(@Nullable KeyPair keyPair) {
        try {
            if (keyPair == null) {
                throw new Exception("Keypair can't be null.");
            }
            keyStoreManager = KeyStoreManager.getInstance();
            keyStoreManager.addKeyPair(targetAlias, keyPair, true);
            if (isDetached()) return;
            activity.runOnUiThread(() -> UIUtils.displayShortToast(R.string.done));
            keyPairUpdated();
            if (isDetached()) return;
            CharSequence info = getSigningInfo();
            activity.runOnUiThread(() -> builder.setMessage(info));
        } catch (Exception e) {
            Log.e(TAG, e);
            activity.runOnUiThread(() -> UIUtils.displayLongToast(R.string.failed_to_save_key));
        }
    }

    @WorkerThread
    private void keyPairUpdated() {
        try {
            KeyPair keyPair = getKeyPair();
            if (keyPair != null) {
                if (listener != null) {
                    byte[] bytes = keyPair.getCertificate().getEncoded();
                    activity.runOnUiThread(() -> listener.keyPairUpdated(keyPair, bytes));
                }
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, e);
        }
        if (listener != null) {
            activity.runOnUiThread(() -> listener.keyPairUpdated(null, null));
        }
    }

    @WorkerThread
    @Nullable
    private KeyPair getKeyPair() {
        try {
            keyStoreManager = KeyStoreManager.getInstance();
            if (keyStoreManager.containsKey(targetAlias)) {
                return keyStoreManager.getKeyPair(targetAlias);
            }
        } catch (Exception e) {
            Log.e(TAG, e);
        }
        return null;
    }
}
