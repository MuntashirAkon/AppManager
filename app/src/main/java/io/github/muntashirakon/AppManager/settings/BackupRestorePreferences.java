// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.collection.ArrayMap;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.backup.convert.ImportType;
import io.github.muntashirakon.AppManager.backup.convert.OABConvert;
import io.github.muntashirakon.AppManager.backup.convert.TBConvert;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.crypto.RSACrypto;
import io.github.muntashirakon.AppManager.settings.crypto.AESCryptoSelectionDialogFragment;
import io.github.muntashirakon.AppManager.settings.crypto.OpenPgpKeySelectionDialogFragment;
import io.github.muntashirakon.AppManager.settings.crypto.RSACryptoSelectionDialogFragment;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.StorageUtils;
import io.github.muntashirakon.io.ProxyFile;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

public class BackupRestorePreferences extends PreferenceFragmentCompat {
    @StringRes
    private static final int[] encryptionNames = new int[]{
            R.string.none,
            R.string.open_pgp_provider,
            R.string.aes,
            R.string.rsa,
            /* R.string.ecc, // TODO(01/04/21): Implement ECC */
    };

    SettingsActivity activity;
    private int currentCompression;
    private String backupVolume;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_backup_restore, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        activity = (SettingsActivity) requireActivity();
        // Backup compression method
        String[] tarTypes = MetadataManager.TAR_TYPES;
        String[] readableTarTypes = new String[]{"GZip", "BZip2"};
        currentCompression = ArrayUtils.indexOf(tarTypes, AppPref.get(AppPref.PrefKey.PREF_BACKUP_COMPRESSION_METHOD_STR));
        Preference compressionMethod = Objects.requireNonNull(findPreference("backup_compression_method"));
        compressionMethod.setSummary(readableTarTypes[currentCompression == -1 ? 0 : currentCompression]);
        compressionMethod.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.pref_compression_method)
                    .setSingleChoiceItems(readableTarTypes, currentCompression,
                            (dialog, which) -> currentCompression = which)
                    .setPositiveButton(R.string.save, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_BACKUP_COMPRESSION_METHOD_STR, tarTypes[currentCompression]);
                        compressionMethod.setSummary(readableTarTypes[currentCompression == -1 ? 0 : currentCompression]);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Backup flags
        BackupFlags flags = BackupFlags.fromPref();
        ((Preference) Objects.requireNonNull(findPreference("backup_flags"))).setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.backup_options)
                    .setMultiChoiceItems(BackupFlags.getFormattedFlagNames(activity),
                            flags.flagsToCheckedItems(),
                            (dialog, index, isChecked) -> {
                                if (isChecked) {
                                    flags.addFlag(BackupFlags.backupFlags.get(index));
                                } else flags.removeFlag(BackupFlags.backupFlags.get(index));
                            })
                    .setPositiveButton(R.string.save, (dialog, which) ->
                            AppPref.set(AppPref.PrefKey.PREF_BACKUP_FLAGS_INT, flags.getFlags()))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Keystore toggle
        SwitchPreferenceCompat backupKeyStore = Objects.requireNonNull(findPreference("backup_android_keystore"));
        backupKeyStore.setChecked((boolean) AppPref.get(AppPref.PrefKey.PREF_BACKUP_ANDROID_KEYSTORE_BOOL));
        // Encryption
        ((Preference) Objects.requireNonNull(findPreference("encryption"))).setOnPreferenceClickListener(preference -> {
            CharSequence[] encryptionNamesText = new CharSequence[encryptionNames.length];
            for (int i = 0; i < encryptionNames.length; ++i) {
                encryptionNamesText[i] = getString(encryptionNames[i]);
            }
            int choice = encModeToIndex((String) AppPref.get(AppPref.PrefKey.PREF_ENCRYPTION_STR));
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.encryption)
                    .setSingleChoiceItems(encryptionNamesText, choice, (dialog, which) -> {
                        String encryptionMode = indexToEncMode(which);
                        switch (encryptionMode) {
                            case CryptoUtils.MODE_NO_ENCRYPTION:
                                AppPref.set(AppPref.PrefKey.PREF_ENCRYPTION_STR, encryptionMode);
                                break;
                            case CryptoUtils.MODE_AES: {
                                DialogFragment fragment = new AESCryptoSelectionDialogFragment();
                                fragment.show(getParentFragmentManager(), AESCryptoSelectionDialogFragment.TAG);
                                break;
                            }
                            case CryptoUtils.MODE_RSA: {
                                RSACryptoSelectionDialogFragment fragment = new RSACryptoSelectionDialogFragment();
                                Bundle args = new Bundle();
                                args.putString(RSACryptoSelectionDialogFragment.EXTRA_ALIAS, RSACrypto.RSA_KEY_ALIAS);
                                args.putBoolean(RSACryptoSelectionDialogFragment.EXTRA_ALLOW_DEFAULT, false);
                                fragment.setArguments(args);
                                fragment.setOnKeyPairUpdatedListener((keyPair, certificateBytes) -> {
                                    if (keyPair != null) {
                                        AppPref.set(AppPref.PrefKey.PREF_ENCRYPTION_STR, CryptoUtils.MODE_RSA);
                                    }
                                });
                                fragment.show(getParentFragmentManager(), RSACryptoSelectionDialogFragment.TAG);
                                break;
                            }
                            case CryptoUtils.MODE_ECC: {
                                // TODO(01/04/21): Implement ECC
                                Toast.makeText(activity, "Not implemented yet.", Toast.LENGTH_SHORT).show();
                                break;
                            }
                            case CryptoUtils.MODE_OPEN_PGP: {
                                AppPref.set(AppPref.PrefKey.PREF_ENCRYPTION_STR, encryptionMode);
                                DialogFragment fragment = new OpenPgpKeySelectionDialogFragment();
                                fragment.show(getParentFragmentManager(), OpenPgpKeySelectionDialogFragment.TAG);
                            }
                        }
                    })
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return true;
        });
        // Backup volume
        this.backupVolume = (String) AppPref.get(AppPref.PrefKey.PREF_BACKUP_VOLUME_STR);
        ((Preference) Objects.requireNonNull(findPreference("backup_volume")))
                .setOnPreferenceClickListener(preference -> {
                    new Thread(() -> {
                        ArrayMap<String, ProxyFile> storageLocations = StorageUtils.getAllStorageLocations(activity, false);
                        if (storageLocations.size() == 0) {
                            activity.runOnUiThread(() -> {
                                if (isDetached()) return;
                                new MaterialAlertDialogBuilder(activity)
                                        .setTitle(R.string.backup_volume)
                                        .setMessage(R.string.no_volumes_found)
                                        .setNegativeButton(R.string.ok, null)
                                        .show();
                            });
                        } else {
                            ProxyFile[] backupVolumes = new ProxyFile[storageLocations.size()];
                            CharSequence[] backupVolumesStr = new CharSequence[storageLocations.size()];
                            AtomicInteger selectedIndex = new AtomicInteger(-1);
                            for (int i = 0; i < storageLocations.size(); ++i) {
                                backupVolumes[i] = storageLocations.valueAt(i);
                                backupVolumesStr[i] = new SpannableStringBuilder(storageLocations.keyAt(i)).append("\n")
                                        .append(getSecondaryText(activity, getSmallerText(backupVolumes[i].getAbsolutePath())));
                                if (backupVolumes[i].getAbsolutePath().equals(this.backupVolume)) {
                                    selectedIndex.set(i);
                                }
                            }
                            activity.runOnUiThread(() -> {
                                if (isDetached()) return;
                                new MaterialAlertDialogBuilder(activity)
                                        .setTitle(R.string.backup_volume)
                                        .setSingleChoiceItems(backupVolumesStr, selectedIndex.get(), (dialog, which) -> {
                                            this.backupVolume = backupVolumes[which].getAbsolutePath();
                                            selectedIndex.set(which);
                                        })
                                        .setNegativeButton(R.string.cancel, null)
                                        .setPositiveButton(R.string.save, (dialog, which) ->
                                                AppPref.set(AppPref.PrefKey.PREF_BACKUP_VOLUME_STR, this.backupVolume))
                                        .show();
                            });
                        }
                    }).start();
                    return true;
                });
        // Import backups
        ((Preference) Objects.requireNonNull(findPreference("import_backups")))
                .setOnPreferenceClickListener(preference -> {
                    View view = getLayoutInflater().inflate(R.layout.dialog_import_external_backups, null);
                    String backupVolume = AppPref.getString(AppPref.PrefKey.PREF_BACKUP_VOLUME_STR) + File.separator;
                    ((TextView) view.findViewById(R.id.import_from_oab_msg)).setText(
                            getString(R.string.import_from_oab_tb_msg, backupVolume + OABConvert.PATH_SUFFIX));
                    ((TextView) view.findViewById(R.id.import_from_tb_msg)).setText(
                            getString(R.string.import_from_oab_tb_msg, backupVolume + TBConvert.PATH_SUFFIX));

                    AlertDialog alertDialog = new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.pref_import_backups)
                            .setView(view)
                            .setNegativeButton(R.string.close, null)
                            .show();
                    // Set listeners
                    view.findViewById(R.id.import_from_oab).setOnClickListener(v -> {
                        startImportOperation(ImportType.OAndBackup);
                        alertDialog.dismiss();
                    });
                    view.findViewById(R.id.import_from_tb).setOnClickListener(v -> {
                        startImportOperation(ImportType.TitaniumBackup);
                        alertDialog.dismiss();
                    });
                    return true;
                });
    }

    @UiThread
    private void startImportOperation(@ImportType int backupType) {
        // Start batch ops service
        Intent intent = new Intent(activity, BatchOpsService.class);
        BatchOpsManager.Result input = new BatchOpsManager.Result(Collections.emptyList());
        intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, input.getFailedPackages());
        intent.putIntegerArrayListExtra(BatchOpsService.EXTRA_OP_USERS, input.getAssociatedUserHandles());
        intent.putExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_IMPORT_BACKUPS);
        Bundle args = new Bundle();
        args.putInt(BatchOpsManager.ARG_BACKUP_TYPE, backupType);
        intent.putExtra(BatchOpsService.EXTRA_OP_EXTRA_ARGS, args);
        ContextCompat.startForegroundService(activity, intent);
    }

    @CryptoUtils.Mode
    private String indexToEncMode(int index) {
        switch (index) {
            default:
            case 0:
                return CryptoUtils.MODE_NO_ENCRYPTION;
            case 1:
                return CryptoUtils.MODE_OPEN_PGP;
            case 2:
                return CryptoUtils.MODE_AES;
            case 3:
                return CryptoUtils.MODE_RSA;
            case 4:
                return CryptoUtils.MODE_ECC;
        }
    }

    private int encModeToIndex(@NonNull @CryptoUtils.Mode String mode) {
        switch (mode) {
            default:
            case CryptoUtils.MODE_NO_ENCRYPTION:
                return 0;
            case CryptoUtils.MODE_OPEN_PGP:
                return 1;
            case CryptoUtils.MODE_AES:
                return 2;
            case CryptoUtils.MODE_RSA:
                return 3;
            case CryptoUtils.MODE_ECC:
                return 4;
        }
    }
}
