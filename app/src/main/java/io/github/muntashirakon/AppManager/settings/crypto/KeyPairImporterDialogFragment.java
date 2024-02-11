// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings.crypto;

import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.KeyListener;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreUtils;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.BetterActivityResult;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.adapters.SelectedArrayAdapter;
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder;
import io.github.muntashirakon.widget.MaterialSpinner;

public class KeyPairImporterDialogFragment extends DialogFragment {
    public static final String TAG = "KeyPairImporterDialogFragment";

    public static final String EXTRA_ALIAS = "alias";

    public interface OnKeySelectedListener {
        void onKeySelected(@Nullable KeyPair keyPair);
    }

    @Nullable
    private OnKeySelectedListener mListener;
    private FragmentActivity mActivity;
    private TextInputLayout mKsPassOrPk8Layout;
    private EditText mKsPassOrPk8;
    private KeyListener mKeyListener;
    private TextInputLayout mKsLocationOrPemLayout;
    private EditText mKsLocationOrPem;
    @KeyStoreUtils.KeyType
    private int mKeyType;
    @Nullable
    private Uri mKsOrPemFile;
    @Nullable
    private Uri mPk8File;
    private final BetterActivityResult<String, Uri> mImportFile = BetterActivityResult
            .registerForActivityResult(this, new ActivityResultContracts.GetContent());

    public void setOnKeySelectedListener(OnKeySelectedListener listener) {
        mListener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mActivity = requireActivity();
        String targetAlias = requireArguments().getString(EXTRA_ALIAS);
        if (targetAlias == null) {
            return super.onCreateDialog(savedInstanceState);
        }
        View view = getLayoutInflater().inflate(R.layout.dialog_key_pair_importer, null);
        MaterialSpinner keyTypeSpinner = view.findViewById(R.id.key_type_selector_spinner);
        mKsPassOrPk8Layout = view.findViewById(R.id.hint);
        mKsPassOrPk8 = view.findViewById(R.id.text);
        mKeyListener = mKsPassOrPk8.getKeyListener();
        mKsLocationOrPemLayout = view.findViewById(R.id.hint2);
        mKsLocationOrPem = view.findViewById(R.id.text2);
        mKsLocationOrPem.setKeyListener(null);
        mKsLocationOrPem.setOnFocusChangeListener((v, hasFocus) -> {
            if (v.isInTouchMode() && hasFocus) {
                v.performClick();
            }
        });
        mKsLocationOrPem.setOnClickListener(v -> mImportFile.launch("application/*", result -> {
            mKsOrPemFile = result;
            if (result != null) {
                mKsLocationOrPem.setText(result.toString());
            }
        }));
        keyTypeSpinner.setAdapter(SelectedArrayAdapter.createFromResource(mActivity, R.array.crypto_import_types,
                io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item));
        keyTypeSpinner.setOnItemClickListener((parent, view1, position, id) -> {
                mKsPassOrPk8.setText(null);
                mKsLocationOrPem.setText(null);

                if (position == KeyStoreUtils.KeyType.PK8) {
                    // PKCS #8 and PEM
                    mKsPassOrPk8Layout.setHint(R.string.pk8_file);
                    mKsPassOrPk8.setKeyListener(null);
                    mKsPassOrPk8.setOnFocusChangeListener((v, hasFocus) -> {
                        if (v.isInTouchMode() && hasFocus) {
                            v.performClick();
                        }
                    });
                    mKsPassOrPk8.setOnClickListener(v -> mImportFile.launch("application/*", result -> {
                        mPk8File = result;
                        if (result != null) {
                            mKsPassOrPk8.setText(result.toString());
                        }
                    }));
                    mKsLocationOrPemLayout.setHint(R.string.pem_file);
                } else {
                    // KeyStore
                    setDefault();
                }
                mKeyType = position;
        });
        setDefault();
        AlertDialog alertDialog = new MaterialAlertDialogBuilder(mActivity)
                .setTitle(R.string.import_key)
                .setView(view)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        alertDialog.setOnShowListener(dialog -> {
            AlertDialog dialog1 = (AlertDialog) dialog;
            Button okButton = dialog1.getButton(AlertDialog.BUTTON_POSITIVE);
            okButton.setOnClickListener(v -> {
                if (mListener == null) return;
                if (mKeyType == KeyStoreUtils.KeyType.PK8) {
                    // PKCS #8 and PEM
                    try {
                        if (mPk8File == null || mKsOrPemFile == null) {
                            throw new Exception("PK8 or PEM can't be null.");
                        }
                        KeyPair keyPair = KeyStoreUtils.getKeyPair(mActivity, mPk8File, mKsOrPemFile);
                        mListener.onKeySelected(keyPair);
                    } catch (Exception e) {
                        Log.e(TAG, e);
                        mListener.onKeySelected(null);
                    }
                    dialog.dismiss();
                } else {
                    // KeyStore
                    char[] ksPassword = Utils.getChars(mKsPassOrPk8.getText());
                    ThreadUtils.postOnBackgroundThread(() -> {
                        try {
                            if (mKsOrPemFile == null) {
                                throw new Exception("KeyStore file can't be null.");
                            }
                            ArrayList<String> aliases = KeyStoreUtils.listAliases(mActivity, mKsOrPemFile, mKeyType,
                                    ksPassword);
                            if (mListener == null) return;
                            ThreadUtils.postOnMainThread(() -> {
                                if (aliases.isEmpty()) {
                                    UIUtils.displayLongToast(R.string.found_no_alias_in_keystore);
                                    ExUtils.exceptionAsIgnored(dialog::dismiss);
                                    return;
                                }
                                TextInputDropdownDialogBuilder builder;
                                builder = new TextInputDropdownDialogBuilder(mActivity, R.string.choose_an_alias)
                                        .setDropdownItems(aliases, -1, true)
                                        .setAuxiliaryInputLabel(R.string.alias_pass)
                                        .setTitle(R.string.choose_an_alias)
                                        .setNegativeButton(R.string.cancel, null);
                                builder.setPositiveButton(R.string.ok, (dialog2, which, inputText, isChecked) -> {
                                    String aliasName = inputText == null ? null : inputText.toString();
                                    char[] aliasPassword = Utils.getChars(builder.getAuxiliaryInput());
                                    ThreadUtils.postOnBackgroundThread(() -> {
                                        try {
                                            KeyPair keyPair = KeyStoreUtils.getKeyPair(mActivity, mKsOrPemFile, mKeyType,
                                                    aliasName, ksPassword, aliasPassword);
                                            mListener.onKeySelected(keyPair);
                                        } catch (Exception e) {
                                            Log.e(TAG, e);
                                            mListener.onKeySelected(null);
                                        }
                                        ThreadUtils.postOnMainThread(() -> ExUtils.exceptionAsIgnored(dialog::dismiss));
                                    });
                                }).show();
                            });
                        } catch (Exception e) {
                            Log.e(TAG, e);
                            ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(R.string.failed_to_read_keystore));
                        }
                    });
                }
            });
        });
        return alertDialog;
    }

    private void setDefault() {
        mKeyType = KeyStoreUtils.KeyType.JKS;
        // KeyStore
        mKsPassOrPk8Layout.setHint(R.string.keystore_pass);
        mKsPassOrPk8.setKeyListener(mKeyListener);
        mKsPassOrPk8.setOnFocusChangeListener(null);
        mKsPassOrPk8.setOnClickListener(null);
        mKsLocationOrPemLayout.setHint(R.string.keystore_file);
    }
}
