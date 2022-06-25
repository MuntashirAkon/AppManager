// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings.crypto;

import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.KeyListener;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

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
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder;

public class KeyPairImporterDialogFragment extends DialogFragment {
    public static final String TAG = "KeyPairImporterDialogFragment";

    public static final String EXTRA_ALIAS = "alias";

    public interface OnKeySelectedListener {
        void onKeySelected(@Nullable KeyPair keyPair);
    }

    @Nullable
    private OnKeySelectedListener listener;
    private FragmentActivity activity;
    @KeyStoreUtils.KeyType
    private int keyType;
    @Nullable
    private Uri ksOrPemFile;
    @Nullable
    private Uri pk8File;
    private final BetterActivityResult<String, Uri> importFile = BetterActivityResult
            .registerForActivityResult(this, new ActivityResultContracts.GetContent());

    public void setOnKeySelectedListener(OnKeySelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        activity = requireActivity();
        String targetAlias = requireArguments().getString(EXTRA_ALIAS);
        if (targetAlias == null) {
            return super.onCreateDialog(savedInstanceState);
        }
        View view = getLayoutInflater().inflate(R.layout.dialog_key_pair_importer, null);
        Spinner keyTypeSpinner = view.findViewById(R.id.key_type_selector_spinner);
        TextInputLayout ksPassOrPk8Layout = view.findViewById(R.id.hint);
        EditText ksPassOrPk8 = view.findViewById(R.id.text);
        KeyListener keyListener = ksPassOrPk8.getKeyListener();
        TextInputLayout ksLocationOrPemLayout = view.findViewById(R.id.hint2);
        EditText ksLocationOrPem = view.findViewById(R.id.text2);
        ksLocationOrPem.setKeyListener(null);
        ksLocationOrPem.setOnFocusChangeListener((v, hasFocus) -> {
            if (v.isInTouchMode() && hasFocus) {
                v.performClick();
            }
        });
        ksLocationOrPem.setOnClickListener(v -> importFile.launch("application/*", result -> {
            ksOrPemFile = result;
            if (result != null) {
                ksLocationOrPem.setText(result.toString());
            }
        }));
        keyTypeSpinner.setAdapter(ArrayAdapter.createFromResource(activity, R.array.crypto_import_types,
                R.layout.support_simple_spinner_dropdown_item));
        keyTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, @KeyStoreUtils.KeyType int position, long id) {
                ksPassOrPk8.setText(null);
                ksLocationOrPem.setText(null);

                if (position == KeyStoreUtils.KeyType.PK8) {
                    // PKCS #8 and PEM
                    ksPassOrPk8Layout.setHint(R.string.pk8_file);
                    ksPassOrPk8.setKeyListener(null);
                    ksPassOrPk8.setOnFocusChangeListener((v, hasFocus) -> {
                        if (v.isInTouchMode() && hasFocus) {
                            v.performClick();
                        }
                    });
                    ksPassOrPk8.setOnClickListener(v -> importFile.launch("application/*", result -> {
                        pk8File = result;
                        if (result != null) {
                            ksPassOrPk8.setText(result.toString());
                        }
                    }));
                    ksLocationOrPemLayout.setHint(R.string.pem_file);
                } else {
                    // KeyStore
                    onNothingSelected(parent);
                }
                keyType = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                keyType = KeyStoreUtils.KeyType.JKS;
                // KeyStore
                ksPassOrPk8Layout.setHint(R.string.keystore_pass);
                ksPassOrPk8.setKeyListener(keyListener);
                ksPassOrPk8.setOnFocusChangeListener(null);
                ksPassOrPk8.setOnClickListener(null);
                ksLocationOrPemLayout.setHint(R.string.keystore_file);
            }
        });
        AlertDialog alertDialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.import_key)
                .setView(view)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        alertDialog.setOnShowListener(dialog -> {
            AlertDialog dialog1 = (AlertDialog) dialog;
            Button okButton = dialog1.getButton(AlertDialog.BUTTON_POSITIVE);
            okButton.setOnClickListener(v -> {
                if (listener == null) return;
                if (keyType == KeyStoreUtils.KeyType.PK8) {
                    // PKCS #8 and PEM
                    try {
                        if (pk8File == null || ksOrPemFile == null) {
                            throw new Exception("PK8 or PEM can't be null.");
                        }
                        KeyPair keyPair = KeyStoreUtils.getKeyPair(activity, pk8File, ksOrPemFile);
                        listener.onKeySelected(keyPair);
                    } catch (Exception e) {
                        Log.e(TAG, e);
                        listener.onKeySelected(null);
                    }
                    dialog.dismiss();
                } else {
                    // KeyStore
                    char[] ksPassword = Utils.getChars(ksPassOrPk8.getText());
                    new Thread(() -> {
                        try {
                            if (ksOrPemFile == null) {
                                throw new Exception("KeyStore file can't be null.");
                            }
                            ArrayList<String> aliases = KeyStoreUtils.listAliases(activity, ksOrPemFile, keyType,
                                    ksPassword);
                            if (isDetached()) return;
                            activity.runOnUiThread(() -> {
                                if (aliases.size() == 0) {
                                    UIUtils.displayLongToast(R.string.found_no_alias_in_keystore);
                                    dialog.dismiss();
                                    return;
                                }
                                TextInputDropdownDialogBuilder builder;
                                builder = new TextInputDropdownDialogBuilder(activity, R.string.choose_an_alias)
                                        .setDropdownItems(aliases, -1, true)
                                        .setAuxiliaryInputLabel(R.string.alias_pass)
                                        .setTitle(R.string.choose_an_alias)
                                        .setNegativeButton(R.string.cancel, null);
                                builder.setPositiveButton(R.string.ok, (dialog2, which, inputText, isChecked) -> {
                                    if (isDetached()) return;
                                    String aliasName = inputText == null ? null : inputText.toString();
                                    char[] aliasPassword = Utils.getChars(builder.getAuxiliaryInput());
                                    new Thread(() -> {
                                        try {
                                            KeyPair keyPair = KeyStoreUtils.getKeyPair(activity, ksOrPemFile, keyType,
                                                    aliasName, ksPassword, aliasPassword);
                                            listener.onKeySelected(keyPair);
                                        } catch (Exception e) {
                                            Log.e(TAG, e);
                                            listener.onKeySelected(null);
                                        }
                                        activity.runOnUiThread(dialog::dismiss);
                                    }).start();
                                }).show();
                            });
                        } catch (Exception e) {
                            Log.e(TAG, e);
                            if (isDetached()) return;
                            activity.runOnUiThread(() -> UIUtils.displayLongToast(R.string.failed_to_read_keystore));
                        }
                    }).start();
                }
            });
        });
        return alertDialog;
    }
}
