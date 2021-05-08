/*
 * Copyright (c) 2021 Muntashir Al-Islam
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
import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.types.ScrollableDialogBuilder;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public class RSACryptoSelectionDialogFragment extends DialogFragment {
    public static final String TAG = "RSACryptoSelectionDialogFragment";

    public static final String EXTRA_ALIAS = "alias";
    public static final String EXTRA_ALLOW_DEFAULT = "show_default";

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
    private String targetAlias;
    private boolean allowDefault;

    public void setOnKeyPairUpdatedListener(OnKeyPairUpdatedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        activity = requireActivity();
        targetAlias = requireArguments().getString(EXTRA_ALIAS);
        allowDefault = requireArguments().getBoolean(EXTRA_ALLOW_DEFAULT, false);
        builder = new ScrollableDialogBuilder(activity)
                .setTitle(R.string.rsa)
                .setNegativeButton(R.string.pref_import, null)
                .setNeutralButton(R.string.generate_key, null);
        new Thread(() -> {
            if (isDetached()) return;
            CharSequence info = getSigningInfo();
            activity.runOnUiThread(() -> builder.setMessage(info));
        }).start();
        if (allowDefault) {
            builder.setPositiveButton(R.string.use_default, null);
        } else {
            builder.setPositiveButton(R.string.ok, null);
        }
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
                fragment.setOnKeySelectedListener((password, keyPair) -> new Thread(() ->
                        new Thread(() -> addKeyPair(password, keyPair)).start()).start());
                fragment.show(getParentFragmentManager(), KeyPairImporterDialogFragment.TAG);
            });
            generateButton.setOnClickListener(v -> {
                KeyPairGeneratorDialogFragment fragment = new KeyPairGeneratorDialogFragment();
                fragment.setOnGenerateListener((password, keyPair) -> new Thread(() ->
                        addKeyPair(password, keyPair)).start());
                fragment.show(getParentFragmentManager(), KeyPairGeneratorDialogFragment.TAG);
            });
            defaultOrOkButton.setOnClickListener(v -> new Thread(() -> {
                try {
                    if (allowDefault) {
                        keyStoreManager = KeyStoreManager.getInstance();
                        if (keyStoreManager.containsKey(targetAlias)) {
                            keyStoreManager.removeItem(targetAlias);
                        }
                    }
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
        return getString(allowDefault ? R.string.default_key_used : R.string.key_not_set);
    }

    @WorkerThread
    private void addKeyPair(@Nullable char[] password, @Nullable KeyPair keyPair) {
        try {
            if (keyPair == null) {
                throw new Exception("Keypair can't be null.");
            }
            keyStoreManager = KeyStoreManager.getInstance();
            keyStoreManager.addKeyPair(targetAlias, keyPair, password, true);
            if (password != null) Utils.clearChars(password);
            if (isDetached()) return;
            activity.runOnUiThread(() -> UIUtils.displayShortToast(R.string.done));
            keyPairUpdated();
            if (isDetached()) return;
            CharSequence info = getSigningInfo();
            activity.runOnUiThread(() -> builder.setMessage(info));
        } catch (Exception e) {
            Log.e(TAG, e);
            activity.runOnUiThread(() -> UIUtils.displayLongToast(R.string.failed_to_save_key));
        } finally {
            if (password != null) Utils.clearChars(password);
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
                return keyStoreManager.getKeyPair(targetAlias, null);
            }
        } catch (Exception e) {
            Log.e(TAG, e);
        }
        return null;
    }

}
