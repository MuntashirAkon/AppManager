// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings.crypto;

import android.app.Application;
import android.app.Dialog;
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

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.ScrollableDialogBuilder;

public class RSACryptoSelectionDialogFragment extends DialogFragment {
    public static final String TAG = RSACryptoSelectionDialogFragment.class.getSimpleName();

    private static final String EXTRA_ALIAS = "alias";

    @NonNull
    public static RSACryptoSelectionDialogFragment getInstance(@NonNull String alias) {
        RSACryptoSelectionDialogFragment fragment = new RSACryptoSelectionDialogFragment();
        Bundle args = new Bundle();
        args.putString(EXTRA_ALIAS, alias);
        fragment.setArguments(args);
        return fragment;
    }

    public interface OnKeyPairUpdatedListener {
        @UiThread
        void keyPairUpdated(@Nullable KeyPair keyPair, @Nullable byte[] certificateBytes);
    }

    @Nullable
    ScrollableDialogBuilder mBuilder;
    @Nullable
    private OnKeyPairUpdatedListener mListener;
    private String mTargetAlias;
    @Nullable
    private RSACryptoSelectionViewModel mModel;

    public void setOnKeyPairUpdatedListener(OnKeyPairUpdatedListener listener) {
        mListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mModel = new ViewModelProvider(this).get(RSACryptoSelectionViewModel.class);
        mModel.observeStatus().observe(this, status -> {
            if (status.second /* long toast */) {
                UIUtils.displayLongToast(status.first);
            } else {
                UIUtils.displayShortToast(status.first);
            }
        });
        mModel.observeKeyUpdated().observe(this, updatedKeyPair -> {
            if (mListener == null) return;
            mListener.keyPairUpdated(updatedKeyPair.first, updatedKeyPair.second);
        });
        mModel.observeSigningInfo().observe(this, keyPair -> {
            if (mBuilder != null) mBuilder.setMessage(getSigningInfo(keyPair));
        });
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mTargetAlias = requireArguments().getString(EXTRA_ALIAS);
        mBuilder = new ScrollableDialogBuilder(requireActivity())
                .setTitle(R.string.rsa)
                .setNegativeButton(R.string.pref_import, null)
                .setNeutralButton(R.string.generate_key, null)
                .setPositiveButton(R.string.ok, null);
        Objects.requireNonNull(mModel).loadSigningInfo(mTargetAlias);
        AlertDialog dialog = Objects.requireNonNull(mBuilder).create();
        dialog.setOnShowListener(dialog3 -> {
            AlertDialog dialog1 = (AlertDialog) dialog3;
            Button importButton = dialog1.getButton(AlertDialog.BUTTON_NEGATIVE);
            Button generateButton = dialog1.getButton(AlertDialog.BUTTON_NEUTRAL);
            importButton.setOnClickListener(v -> {
                KeyPairImporterDialogFragment fragment = new KeyPairImporterDialogFragment();
                Bundle args = new Bundle();
                args.putString(KeyPairImporterDialogFragment.EXTRA_ALIAS, mTargetAlias);
                fragment.setArguments(args);
                fragment.setOnKeySelectedListener(keyPair -> mModel.addKeyPair(mTargetAlias, keyPair));
                fragment.show(getParentFragmentManager(), KeyPairImporterDialogFragment.TAG);
            });
            generateButton.setOnClickListener(v -> {
                KeyPairGeneratorDialogFragment fragment = new KeyPairGeneratorDialogFragment();
                Bundle args = new Bundle();
                args.putString(KeyPairGeneratorDialogFragment.EXTRA_KEY_TYPE, CryptoUtils.MODE_RSA);
                fragment.setArguments(args);
                fragment.setOnGenerateListener(keyPair -> mModel.addKeyPair(mTargetAlias, keyPair));
                fragment.show(getParentFragmentManager(), KeyPairGeneratorDialogFragment.TAG);
            });
        });
        return dialog;
    }

    private CharSequence getSigningInfo(@Nullable KeyPair keyPair) {
        if (keyPair != null) {
            try {
                return PackageUtils.getSigningCertificateInfo(requireActivity(), (X509Certificate) keyPair.getCertificate());
            } catch (CertificateEncodingException e) {
                return getString(R.string.failed_to_load_key);
            }
        }
        return getString(R.string.key_not_set);
    }

    public static class RSACryptoSelectionViewModel extends AndroidViewModel {
        // StringRes, isLongToast
        private final MutableLiveData<Pair<Integer, Boolean>> status = new MutableLiveData<>();
        private final MutableLiveData<Pair<KeyPair, byte[]>> keyUpdated = new MutableLiveData<>();
        private final MutableLiveData<KeyPair> signingInfo = new MutableLiveData<>();

        public RSACryptoSelectionViewModel(@NonNull Application application) {
            super(application);
        }

        public LiveData<Pair<Integer, Boolean>> observeStatus() {
            return status;
        }

        public LiveData<Pair<KeyPair, byte[]>> observeKeyUpdated() {
            return keyUpdated;
        }

        public LiveData<KeyPair> observeSigningInfo() {
            return signingInfo;
        }

        @AnyThread
        public void loadSigningInfo(String targetAlias) {
            ThreadUtils.postOnBackgroundThread(() -> signingInfo.postValue(getKeyPair(targetAlias)));
        }

        @AnyThread
        private void addKeyPair(String targetAlias, @Nullable KeyPair keyPair) {
            ThreadUtils.postOnBackgroundThread(() -> {
                try {
                    if (keyPair == null) {
                        throw new Exception("Keypair can't be null.");
                    }
                    KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
                    keyStoreManager.addKeyPair(targetAlias, keyPair, true);
                    status.postValue(new Pair<>(R.string.done, false));
                    keyPairUpdated(targetAlias);
                    signingInfo.postValue(getKeyPair(targetAlias));
                } catch (Exception e) {
                    Log.e(TAG, e);
                    status.postValue(new Pair<>(R.string.failed_to_save_key, true));
                }
            });
        }

        @WorkerThread
        @Nullable
        private KeyPair getKeyPair(String targetAlias) {
            try {
                KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
                if (keyStoreManager.containsKey(targetAlias)) {
                    return keyStoreManager.getKeyPair(targetAlias);
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
