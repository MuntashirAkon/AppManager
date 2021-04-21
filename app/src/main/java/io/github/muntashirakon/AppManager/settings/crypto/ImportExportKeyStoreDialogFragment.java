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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

import static io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager.AM_KEYSTORE_FILE;

public class ImportExportKeyStoreDialogFragment extends DialogFragment {
    public static final String TAG = "IEKeyStoreDialogFragment";

    private FragmentActivity activity;
    private final ActivityResultLauncher<String> exportKeyStore = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(), uri -> {
                if (uri == null) {
                    dismiss();
                    return;
                }
                new Thread(() -> {
                    try (InputStream is = new FileInputStream(AM_KEYSTORE_FILE);
                         OutputStream os = activity.getContentResolver().openOutputStream(uri)) {
                        if (os == null) throw new IOException("Unable to open URI");
                        IOUtils.copy(is, os);
                        activity.runOnUiThread(() -> {
                            UIUtils.displayShortToast(R.string.done);
                            dismiss();
                        });
                    } catch (IOException e) {
                        activity.runOnUiThread(() -> {
                            UIUtils.displayShortToast(R.string.failed);
                            dismiss();
                        });
                    }
                }).start();
            });
    private final ActivityResultLauncher<String> importKeyStore = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) {
                    dismiss();
                    return;
                }
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.import_keystore)
                        .setMessage(R.string.confirm_import_keystore)
                        .setPositiveButton(R.string.yes, (dialog, which) -> new Thread(() -> {
                            // Rename old file that will be restored in case of error
                            File tmpFile = new File(AM_KEYSTORE_FILE.getAbsolutePath() + ".tmp");
                            if (AM_KEYSTORE_FILE.exists()) {
                                AM_KEYSTORE_FILE.renameTo(tmpFile);
                            }
                            try (InputStream is = activity.getContentResolver().openInputStream(uri);
                                 OutputStream os = new FileOutputStream(AM_KEYSTORE_FILE)) {
                                if (is == null) throw new IOException("Unable to open URI");
                                IOUtils.copy(is, os);
                                KeyStoreManager.reloadKeyStore();
                                // TODO: 21/4/21 Only import the keys that we use instead of replacing the entire keystore
                                activity.runOnUiThread(() -> {
                                    UIUtils.displayShortToast(R.string.done);
                                    dismiss();
                                });
                            } catch (Exception e) {
                                if (tmpFile.exists()) {
                                    AM_KEYSTORE_FILE.delete();
                                    tmpFile.renameTo(AM_KEYSTORE_FILE);
                                    try {
                                        KeyStoreManager.reloadKeyStore();
                                    } catch (Exception ignore) {
                                    }
                                }
                                activity.runOnUiThread(() -> {
                                    UIUtils.displayShortToast(R.string.failed);
                                    dismiss();
                                });
                            }
                        }).start())
                        .setNegativeButton(R.string.close, (dialog, which) -> dismiss())
                        .setCancelable(false)
                        .show();
            });

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        activity = requireActivity();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.pref_import_export_keystore)
                .setMessage(R.string.choose_what_to_do)
                .setPositiveButton(R.string.pref_export, null)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.pref_import, null);
        AlertDialog alertDialog = builder.create();
        alertDialog.setOnShowListener(dialog -> {
            AlertDialog dialog1 = (AlertDialog) dialog;
            Button exportButton = dialog1.getButton(AlertDialog.BUTTON_POSITIVE);
            Button importButton = dialog1.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (AM_KEYSTORE_FILE.exists()) {
                exportButton.setOnClickListener(v -> exportKeyStore.launch(KeyStoreManager.AM_KEYSTORE_FILE_NAME));
            }
            importButton.setOnClickListener(v -> importKeyStore.launch("application/*"));
        });
        return alertDialog;
    }
}
