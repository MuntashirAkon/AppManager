// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.collection.ArrayMap;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.backup.convert.ImportType;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.crypto.RSACrypto;
import io.github.muntashirakon.AppManager.settings.crypto.AESCryptoSelectionDialogFragment;
import io.github.muntashirakon.AppManager.settings.crypto.ECCCryptoSelectionDialogFragment;
import io.github.muntashirakon.AppManager.settings.crypto.OpenPgpKeySelectionDialogFragment;
import io.github.muntashirakon.AppManager.settings.crypto.RSACryptoSelectionDialogFragment;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.io.Path;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

public class BackupRestorePreferences extends PreferenceFragment {
    @StringRes
    private static final int[] encryptionNames = new int[]{
            R.string.none,
            R.string.open_pgp_provider,
            R.string.aes,
            R.string.rsa,
            R.string.ecc,
    };

    private SettingsActivity activity;
    private int currentCompression;
    private Uri backupVolume;
    @ImportType
    private int importType;
    private boolean deleteBackupsAfterImport;
    private MainPreferencesViewModel model;

    private final ActivityResultLauncher<Intent> safSelectBackupVolume = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                try {
                    if (result.getResultCode() != Activity.RESULT_OK) return;
                    Intent data = result.getData();
                    if (data == null) return;
                    Uri treeUri = data.getData();
                    if (treeUri == null) return;
                    int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    requireContext().getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
                } finally {
                    // Display backup volumes again
                    model.loadStorageVolumes();
                }
            });
    private final ActivityResultLauncher<Intent> safSelectImportDirectory = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != Activity.RESULT_OK) return;
                Intent data = result.getData();
                if (data == null) return;
                Uri treeUri = data.getData();
                if (treeUri == null) return;
                int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                requireContext().getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
                startImportOperation(importType, treeUri, deleteBackupsAfterImport);
            });

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_backup_restore, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        model = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
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
            List<Integer> supportedBackupFlags = BackupFlags.getSupportedBackupFlagsAsArray();
            new SearchableMultiChoiceDialogBuilder<>(requireActivity(), supportedBackupFlags, BackupFlags.getFormattedFlagNames(requireContext(), supportedBackupFlags))
                    .setTitle(R.string.backup_options)
                    .addSelections(flags.flagsToCheckedIndexes(supportedBackupFlags))
                    .hideSearchBar(true)
                    .showSelectAll(false)
                    .setPositiveButton(R.string.save, (dialog, which, selectedItems) -> {
                        int flagsInt = 0;
                        for (int flag : selectedItems) {
                            flagsInt |= flag;
                        }
                        flags.setFlags(flagsInt);
                        AppPref.set(AppPref.PrefKey.PREF_BACKUP_FLAGS_INT, flags.getFlags());
                    })
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
                                RSACryptoSelectionDialogFragment fragment = RSACryptoSelectionDialogFragment.getInstance(RSACrypto.RSA_KEY_ALIAS);
                                fragment.setOnKeyPairUpdatedListener((keyPair, certificateBytes) -> {
                                    if (keyPair != null) {
                                        AppPref.set(AppPref.PrefKey.PREF_ENCRYPTION_STR, CryptoUtils.MODE_RSA);
                                    }
                                });
                                fragment.show(getParentFragmentManager(), RSACryptoSelectionDialogFragment.TAG);
                                break;
                            }
                            case CryptoUtils.MODE_ECC: {
                                ECCCryptoSelectionDialogFragment fragment = new ECCCryptoSelectionDialogFragment();
                                fragment.setOnKeyPairUpdatedListener((keyPair, certificateBytes) -> {
                                    if (keyPair != null) {
                                        AppPref.set(AppPref.PrefKey.PREF_ENCRYPTION_STR, CryptoUtils.MODE_ECC);
                                    }
                                });
                                fragment.show(getParentFragmentManager(), RSACryptoSelectionDialogFragment.TAG);
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
        this.backupVolume = AppPref.getSelectedDirectory();
        ((Preference) Objects.requireNonNull(findPreference("backup_volume")))
                .setOnPreferenceClickListener(preference -> {
                    model.loadStorageVolumes();
                    return true;
                });
        // Import backups
        ((Preference) Objects.requireNonNull(findPreference("import_backups")))
                .setOnPreferenceClickListener(preference -> {
                    new MaterialAlertDialogBuilder(activity)
                            .setCustomTitle(new DialogTitleBuilder(activity)
                                    .setTitle(R.string.pref_import_backups)
                                    .setSubtitle(R.string.pref_import_backups_hint)
                                    .build())
                            .setItems(R.array.import_backup_options, (dialog, which) -> {
                                importType = which;
                                String path;
                                switch (importType) {
                                    case ImportType.OAndBackup:
                                        path = "oandbackups";
                                        break;
                                    case ImportType.TitaniumBackup:
                                        path = "TitaniumBackup";
                                        break;
                                    case ImportType.SwiftBackup:
                                        path = "SwiftBackup";
                                        break;
                                    default:
                                        path = "";
                                }
                                new MaterialAlertDialogBuilder(activity)
                                        .setTitle(R.string.pref_import_backups)
                                        .setMessage(R.string.import_backups_warning_delete_backups_after_import)
                                        .setPositiveButton(R.string.no, (dialog1, which1) -> {
                                            deleteBackupsAfterImport = false;
                                            safSelectImportDirectory.launch(getSafIntent(path));
                                        })
                                        .setNegativeButton(R.string.yes, (dialog1, which1) -> {
                                            deleteBackupsAfterImport = true;
                                            safSelectImportDirectory.launch(getSafIntent(path));
                                        })
                                        .setNeutralButton(R.string.cancel, null)
                                        .show();
                            })
                            .setNegativeButton(R.string.close, null)
                            .show();
                    return true;
                });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        model.getStorageVolumesLiveData().observe(getViewLifecycleOwner(), this::displayVolumeSelectionDialog);
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.backup_restore);
    }

    @UiThread
    private void startImportOperation(@ImportType int backupType, Uri uri, boolean removeImported) {
        // Start batch ops service
        Intent intent = new Intent(activity, BatchOpsService.class);
        BatchOpsManager.Result input = new BatchOpsManager.Result(Collections.emptyList());
        intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, input.getFailedPackages());
        intent.putIntegerArrayListExtra(BatchOpsService.EXTRA_OP_USERS, input.getAssociatedUserHandles());
        intent.putExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_IMPORT_BACKUPS);
        Bundle args = new Bundle();
        args.putInt(BatchOpsManager.ARG_BACKUP_TYPE, backupType);
        args.putParcelable(BatchOpsManager.ARG_URI, uri);
        args.putBoolean(BatchOpsManager.ARG_REMOVE_IMPORTED, removeImported);
        intent.putExtra(BatchOpsService.EXTRA_OP_EXTRA_ARGS, args);
        ContextCompat.startForegroundService(activity, intent);
    }

    private void displayVolumeSelectionDialog(@NonNull ArrayMap<String, Uri> storageLocations) {
        // TODO: 13/8/22 Move to a separate BottomSheet dialog fragment
        AtomicReference<AlertDialog> alertDialog = new AtomicReference<>(null);
        DialogTitleBuilder titleBuilder = new DialogTitleBuilder(activity)
                .setTitle(R.string.backup_volume)
                .setSubtitle(R.string.backup_volume_dialog_description)
                .setStartIcon(R.drawable.ic_zip_disk)
                .setEndIcon(R.drawable.ic_add, v -> new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.notice)
                        .setMessage(R.string.notice_saf)
                        .setPositiveButton(R.string.go, (dialog1, which1) -> {
                            if (alertDialog.get() != null) {
                                alertDialog.get().dismiss();
                            }
                            safSelectBackupVolume.launch(getSafIntent("AppManager"));
                        })
                        .setNeutralButton(R.string.cancel, null)
                        .show());

        if (storageLocations.size() == 0) {
            alertDialog.set(new MaterialAlertDialogBuilder(activity)
                    .setCustomTitle(titleBuilder.build())
                    .setMessage(R.string.no_volumes_found)
                    .setNegativeButton(R.string.ok, null)
                    .show());
        } else {
            Uri[] backupVolumes = new Uri[storageLocations.size()];
            CharSequence[] backupVolumesStr = new CharSequence[storageLocations.size()];
            AtomicInteger selectedIndex = new AtomicInteger(-1);
            for (int i = 0; i < storageLocations.size(); ++i) {
                backupVolumes[i] = storageLocations.valueAt(i);
                backupVolumesStr[i] = new SpannableStringBuilder(storageLocations.keyAt(i)).append("\n")
                        .append(getSecondaryText(activity, getSmallerText(backupVolumes[i].getPath())));
                if (backupVolumes[i].equals(backupVolume)) {
                    selectedIndex.set(i);
                }
            }
            alertDialog.set(new MaterialAlertDialogBuilder(activity)
                    .setCustomTitle(titleBuilder.build())
                    .setSingleChoiceItems(backupVolumesStr, selectedIndex.get(), (dialog, which) -> {
                        backupVolume = backupVolumes[which];
                        selectedIndex.set(which);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.save, (dialog, which) -> {
                        Uri lastBackupVolume = AppPref.getSelectedDirectory();
                        if (!lastBackupVolume.equals(backupVolume)) {
                            AppPref.set(AppPref.PrefKey.PREF_BACKUP_VOLUME_STR, backupVolume.toString());
                            model.updateBackups();
                        }
                    })
                    .show());
        }
    }

    private Intent getSafIntent(String path) {
        return new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .putExtra("android.provider.extra.SHOW_ADVANCED", true)
                .putExtra("android.provider.extra.INITIAL_URI", Path.getPrimaryPath(activity, path).getUri());
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
