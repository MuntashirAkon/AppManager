package io.github.muntashirakon.AppManager.settings;

import android.os.Bundle;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.io.ProxyFile;

public class BackupRestorePreferences extends PreferenceFragmentCompat {
    @StringRes
    private static final int[] encryptionNames = new int[]{
            R.string.none,
            R.string.aes,
            R.string.rsa,
            R.string.ecc,
            R.string.open_pgp_provider
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
        compressionMethod.setSummary(getString(R.string.compression_method, readableTarTypes[currentCompression == -1 ? 0 : currentCompression]));
        compressionMethod.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.pref_compression_method)
                    .setSingleChoiceItems(readableTarTypes, currentCompression,
                            (dialog, which) -> currentCompression = which)
                    .setPositiveButton(R.string.save, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_BACKUP_COMPRESSION_METHOD_STR, tarTypes[currentCompression]);
                        compressionMethod.setSummary(getString(R.string.compression_method, readableTarTypes[currentCompression == -1 ? 0 : currentCompression]));
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
                            case CryptoUtils.MODE_AES:
                            case CryptoUtils.MODE_RSA:
                            case CryptoUtils.MODE_ECC:
                                // TODO(12/11/20): Implement encryption options
                                Toast.makeText(activity, "Not implemented yet.", Toast.LENGTH_SHORT).show();
                                break;
                            case CryptoUtils.MODE_OPEN_PGP:
                                AppPref.set(AppPref.PrefKey.PREF_ENCRYPTION_STR, encryptionMode);
                                new OpenPgpKeySelectionDialogFragment().show(getParentFragmentManager(), OpenPgpKeySelectionDialogFragment.TAG);
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
                    ProxyFile[] backupVolumes = OsEnvironment.buildExternalStoragePublicDirs();
                    if (backupVolumes.length == 0) {
                        new MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.backup_volume)
                                .setMessage(R.string.no_volumes_found)
                                .setNegativeButton(R.string.ok, null)
                                .show();
                    } else {
                        String[] backupVolumesStr = new String[backupVolumes.length];
                        for (int i = 0; i < backupVolumesStr.length; ++i) {
                            backupVolumesStr[i] = backupVolumes[i].getAbsolutePath();
                        }
                        new MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.backup_volume)
                                .setSingleChoiceItems(backupVolumesStr, Arrays.asList(backupVolumesStr).indexOf(this.backupVolume), (dialog, which) ->
                                        this.backupVolume = backupVolumesStr[which])
                                .setNegativeButton(R.string.cancel, null)
                                .setPositiveButton(R.string.save, (dialog, which) ->
                                        AppPref.set(AppPref.PrefKey.PREF_BACKUP_VOLUME_STR, this.backupVolume))
                                .show();
                    }
                    return true;
                });
    }

    @CryptoUtils.Mode
    private String indexToEncMode(int index) {
        switch (index) {
            default:
            case 0:
                return CryptoUtils.MODE_NO_ENCRYPTION;
            case 1:
                return CryptoUtils.MODE_AES;
            case 2:
                return CryptoUtils.MODE_RSA;
            case 3:
                return CryptoUtils.MODE_ECC;
            case 4:
                return CryptoUtils.MODE_OPEN_PGP;
        }
    }

    private int encModeToIndex(@NonNull @CryptoUtils.Mode String mode) {
        switch (mode) {
            default:
            case CryptoUtils.MODE_NO_ENCRYPTION:
                return 0;
            case CryptoUtils.MODE_AES:
                return 1;
            case CryptoUtils.MODE_RSA:
                return 2;
            case CryptoUtils.MODE_ECC:
                return 3;
            case CryptoUtils.MODE_OPEN_PGP:
                return 4;
        }
    }
}
