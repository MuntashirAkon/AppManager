// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings.crypto;

import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    @Nullable
    ScrollableDialogBuilder builder;
    @Nullable
    private OnKeyPairUpdatedListener listener;
    private String targetAlias;
    private boolean allowDefault;
    @Nullable
    private RSACryptoSelectionViewModel model;

    public void setOnKeyPairUpdatedListener(OnKeyPairUpdatedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        model = new ViewModelProvider(this).get(RSACryptoSelectionViewModel.class);
        model.observeStatus().observe(this, status -> {
            if (status.second /* long toast */) {
                UIUtils.displayLongToast(status.first);
            } else {
                UIUtils.displayShortToast(status.first);
            }
        });
        model.observeKeyUpdated().observe(this, updatedKeyPair -> {
            if (listener == null) return;
            listener.keyPairUpdated(updatedKeyPair.first, updatedKeyPair.second);
        });
        model.observeSigningInfo().observe(this, signingInfo -> {
            if (builder != null) builder.setMessage(signingInfo);
        });
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        targetAlias = requireArguments().getString(EXTRA_ALIAS);
        allowDefault = requireArguments().getBoolean(EXTRA_ALLOW_DEFAULT, false);
        builder = new ScrollableDialogBuilder(requireActivity())
                .setTitle(R.string.rsa)
                .setNegativeButton(R.string.pref_import, null)
                .setNeutralButton(R.string.generate_key, null)
                .setPositiveButton(allowDefault ? R.string.use_default : R.string.ok, (dialog, which, isChecked) -> {
                    if (allowDefault && model != null) {
                        model.addDefaultKeyPair(targetAlias);
                    }
                });
        Objects.requireNonNull(model).loadSigningInfo(targetAlias, allowDefault);
        AlertDialog dialog = Objects.requireNonNull(builder).create();
        dialog.setOnShowListener(dialog3 -> {
            AlertDialog dialog1 = (AlertDialog) dialog3;
            Button importButton = dialog1.getButton(AlertDialog.BUTTON_NEGATIVE);
            Button generateButton = dialog1.getButton(AlertDialog.BUTTON_NEUTRAL);
            importButton.setOnClickListener(v -> {
                KeyPairImporterDialogFragment fragment = new KeyPairImporterDialogFragment();
                Bundle args = new Bundle();
                args.putString(KeyPairImporterDialogFragment.EXTRA_ALIAS, targetAlias);
                fragment.setArguments(args);
                fragment.setOnKeySelectedListener((password, keyPair) -> model.addKeyPair(targetAlias, allowDefault,
                        password, keyPair));
                fragment.show(getParentFragmentManager(), KeyPairImporterDialogFragment.TAG);
            });
            generateButton.setOnClickListener(v -> {
                KeyPairGeneratorDialogFragment fragment = new KeyPairGeneratorDialogFragment();
                fragment.setOnGenerateListener((password, keyPair) -> model.addKeyPair(targetAlias, allowDefault,
                        password, keyPair));
                fragment.show(getParentFragmentManager(), KeyPairGeneratorDialogFragment.TAG);
            });
        });
        return dialog;
    }

    public static class RSACryptoSelectionViewModel extends AndroidViewModel {
        private final ExecutorService executor = Executors.newFixedThreadPool(2);
        // StringRes, isLongToast
        private final MutableLiveData<Pair<Integer, Boolean>> status = new MutableLiveData<>();
        private final MutableLiveData<Pair<KeyPair, byte[]>> keyUpdated = new MutableLiveData<>();
        private final MutableLiveData<CharSequence> signingInfo = new MutableLiveData<>();

        public RSACryptoSelectionViewModel(@NonNull Application application) {
            super(application);
        }

        @Override
        protected void onCleared() {
            super.onCleared();
            executor.shutdown();
        }

        public LiveData<Pair<Integer, Boolean>> observeStatus() {
            return status;
        }

        public LiveData<Pair<KeyPair, byte[]>> observeKeyUpdated() {
            return keyUpdated;
        }

        public LiveData<CharSequence> observeSigningInfo() {
            return signingInfo;
        }

        @AnyThread
        public void loadSigningInfo(String targetAlias, boolean allowDefault) {
            executor.submit(() -> {
                CharSequence info = getSigningInfo(targetAlias, allowDefault);
                signingInfo.postValue(info);
            });
        }

        @WorkerThread
        private CharSequence getSigningInfo(String targetAlias, boolean allowDefault) {
            Context ctx = getApplication();
            KeyPair keyPair = getKeyPair(targetAlias);
            if (keyPair != null) {
                try {
                    return PackageUtils.getSigningCertificateInfo(ctx, (X509Certificate) keyPair.getCertificate());
                } catch (CertificateEncodingException e) {
                    return ctx.getString(R.string.failed_to_load_key);
                }
            }
            return ctx.getString(allowDefault ? R.string.default_key_used : R.string.key_not_set);
        }

        @AnyThread
        private void addDefaultKeyPair(String targetAlias) {
            executor.submit(() -> {
                try {
                    KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
                    if (keyStoreManager.containsKey(targetAlias)) {
                        keyStoreManager.removeItem(targetAlias);
                    }
                    status.postValue(new Pair<>(R.string.done, false));
                    keyPairUpdated(targetAlias);
                } catch (Exception e) {
                    Log.e(TAG, e);
                    status.postValue(new Pair<>(R.string.failed_to_save_key, false));
                }
            });
        }

        @AnyThread
        private void addKeyPair(String targetAlias, boolean allowDefault, @Nullable char[] password, @Nullable KeyPair keyPair) {
            executor.submit(() -> {
                try {
                    if (keyPair == null) {
                        throw new Exception("Keypair can't be null.");
                    }
                    KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
                    keyStoreManager.addKeyPair(targetAlias, keyPair, password, true);
                    if (password != null) Utils.clearChars(password);
                    status.postValue(new Pair<>(R.string.done, false));
                    keyPairUpdated(targetAlias);
                    CharSequence info = getSigningInfo(targetAlias, allowDefault);
                    signingInfo.postValue(info);
                } catch (Exception e) {
                    Log.e(TAG, e);
                    status.postValue(new Pair<>(R.string.failed_to_save_key, true));
                } finally {
                    if (password != null) Utils.clearChars(password);
                }
            });
        }

        @WorkerThread
        @Nullable
        private KeyPair getKeyPair(String targetAlias) {
            try {
                KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
                if (keyStoreManager.containsKey(targetAlias)) {
                    return keyStoreManager.getKeyPair(targetAlias, null);
                }
            } catch (Exception e) {
                Log.e(TAG, e);
            }
            return null;
        }

        @WorkerThread
        private void keyPairUpdated(String targetAlias) {
            try {
                KeyPair keyPair = getKeyPair(targetAlias);
                if (keyPair != null) {
                    byte[] bytes = keyPair.getCertificate().getEncoded();
                    keyUpdated.postValue(new Pair<>(keyPair, bytes));
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, e);
            }
            keyUpdated.postValue(new Pair<>(null, null));
        }
    }
}
