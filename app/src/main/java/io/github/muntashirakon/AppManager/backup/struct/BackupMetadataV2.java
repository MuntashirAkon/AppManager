// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.struct;

import static io.github.muntashirakon.AppManager.backup.BackupUtils.getReadableTarType;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getTitleText;

import android.content.Context;
import android.os.Build;
import android.os.UserHandleHidden;
import android.text.SpannableStringBuilder;
import android.text.format.Formatter;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

import aosp.libcore.util.HexEncoding;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.BackupItems;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.misc.VMRuntime;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.util.LocalizedString;

// For an extended documentation, see https://github.com/MuntashirAkon/AppManager/issues/30
// All the attributes must be non-null
public class BackupMetadataV2 implements LocalizedString, IJsonSerializer {
    public String backupName;  // This isn't part of the json file and for internal use only
    public BackupItems.BackupItem backupItem; // This isn't part of the json file and for internal use only

    public String label;  // label
    public String packageName;  // package_name
    public String versionName;  // version_name
    public long versionCode;  // version_code
    public String[] dataDirs;  // data_dirs
    public boolean isSystem;  // is_system
    public boolean isSplitApk;  // is_split_apk
    public String[] splitConfigs;  // split_configs
    public boolean hasRules;  // has_rules
    public long backupTime;  // backup_time
    @DigestUtils.Algorithm
    public String checksumAlgo = DigestUtils.SHA_256;  // checksum_algo
    @CryptoUtils.Mode
    public String crypto;  // crypto
    public byte[] iv;  // iv
    public byte[] aes;  // aes (encrypted using RSA, for RSA only)
    public String keyIds;  // key_ids
    /**
     * Metadata version.
     * <ul>
     *     <li>{@code 1} - Alpha version, no longer supported</li>
     *     <li>{@code 2} - Beta version (v2.5.2x), permissions aren't preserved (special action needed)</li>
     *     <li>{@code 3} - From v2.6.x to v3.0.2 and v3.1.0-alpha01, permissions are preserved, AES GCM MAC size is 32 bits</li>
     *     <li>{@code 4} - Since v3.0.3 and v3.1.0-alpha02, AES GCM MAC size is 128 bits</li>
     * </ul>
     */
    public int version = 4;  // version
    public String apkName;  // apk_name
    public String instructionSet = VMRuntime.getInstructionSet(Build.SUPPORTED_ABIS[0]);  // instruction_set
    public BackupFlags flags;  // flags
    public int userHandle;  // user_handle
    @TarUtils.TarType
    public String tarType;  // tar_type
    public boolean keyStore;  // key_store
    public String installer;  // installer

    public BackupMetadataV2() {
    }

    public BackupMetadataV2(@NonNull BackupMetadataV2 metadata) {
        backupName = metadata.backupName;
        backupItem = metadata.backupItem;

        label = metadata.label;
        packageName = metadata.packageName;
        versionName = metadata.versionName;
        versionCode = metadata.versionCode;
        if (metadata.dataDirs != null) {
            dataDirs = metadata.dataDirs.clone();
        }
        isSystem = metadata.isSystem;
        isSplitApk = metadata.isSplitApk;
        if (metadata.splitConfigs != null) {
            splitConfigs = metadata.splitConfigs.clone();
        }
        hasRules = metadata.hasRules;
        backupTime = metadata.backupTime;
        checksumAlgo = metadata.checksumAlgo;
        crypto = metadata.crypto;
        if (metadata.iv != null) {
            iv = metadata.iv.clone();
        }
        if (metadata.aes != null) {
            aes = metadata.aes.clone();
        }
        keyIds = metadata.keyIds;
        version = metadata.version;
        apkName = metadata.apkName;
        instructionSet = metadata.instructionSet;
        flags = new BackupFlags(metadata.flags.getFlags());
        userHandle = metadata.userHandle;
        tarType = metadata.tarType;
        keyStore = metadata.keyStore;
        installer = metadata.installer;
    }

    public BackupMetadataV2(@NonNull JSONObject rootObject) throws JSONException {
        label = rootObject.getString("label");
        packageName = rootObject.getString("package_name");
        versionName = rootObject.getString("version_name");
        versionCode = rootObject.getLong("version_code");
        dataDirs = JSONUtils.getArray(String.class, rootObject.getJSONArray("data_dirs"));
        isSystem = rootObject.getBoolean("is_system");
        isSplitApk = rootObject.getBoolean("is_split_apk");
        splitConfigs = JSONUtils.getArray(String.class, rootObject.getJSONArray("split_configs"));
        hasRules = rootObject.getBoolean("has_rules");
        backupTime = rootObject.getLong("backup_time");
        checksumAlgo = rootObject.getString("checksum_algo");
        crypto = rootObject.getString("crypto");
        readCrypto(rootObject);
        version = rootObject.getInt("version");
        apkName = rootObject.getString("apk_name");
        instructionSet = rootObject.getString("instruction_set");
        flags = new BackupFlags(rootObject.getInt("flags"));
        userHandle = rootObject.getInt("user_handle");
        tarType = rootObject.getString("tar_type");
        keyStore = rootObject.getBoolean("key_store");
        installer = JSONUtils.getString(rootObject, "installer", BuildConfig.APPLICATION_ID);
    }

    private void readCrypto(JSONObject rootObj) throws JSONException {
        switch (crypto) {
            case CryptoUtils.MODE_OPEN_PGP:
                keyIds = rootObj.getString("key_ids");
                break;
            case CryptoUtils.MODE_RSA:
            case CryptoUtils.MODE_ECC:
                aes = HexEncoding.decode(rootObj.getString("aes"));
                // Deliberate fallthrough
            case CryptoUtils.MODE_AES:
                iv = HexEncoding.decode(rootObj.getString("iv"));
                break;
            case CryptoUtils.MODE_NO_ENCRYPTION:
            default:
        }
    }

    public long getBackupSize() {
        if (backupItem == null) return 0L;
        return Paths.size(backupItem.getBackupPath());
    }

    public boolean isBaseBackup() {
        return String.valueOf(UserHandleHidden.myUserId()).equals(backupName);
    }

    public boolean isFrozen() {
        return backupItem != null && backupItem.isFrozen();
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject rootObject = new JSONObject();
        rootObject.put("label", label);
        rootObject.put("package_name", packageName);
        rootObject.put("version_name", versionName);
        rootObject.put("version_code", versionCode);
        rootObject.put("data_dirs", JSONUtils.getJSONArray(dataDirs));
        rootObject.put("is_system", isSystem);
        rootObject.put("is_split_apk", isSplitApk);
        rootObject.put("split_configs", JSONUtils.getJSONArray(splitConfigs));
        rootObject.put("has_rules", hasRules);
        rootObject.put("backup_time", backupTime);
        rootObject.put("checksum_algo", checksumAlgo);
        rootObject.put("crypto", crypto);
        rootObject.put("key_ids", keyIds);
        rootObject.put("iv", iv == null ? null : HexEncoding.encodeToString(iv));
        rootObject.put("aes", aes == null ? null : HexEncoding.encodeToString(aes));
        rootObject.put("version", version);
        rootObject.put("apk_name", apkName);
        rootObject.put("instruction_set", instructionSet);
        rootObject.put("flags", flags.getFlags());
        rootObject.put("user_handle", userHandle);
        rootObject.put("tar_type", tarType);
        rootObject.put("key_store", keyStore);
        rootObject.put("installer", installer);
        return rootObject;
    }

    @Override
    @NonNull
    @WorkerThread
    public CharSequence toLocalizedString(@NonNull Context context) {
        String shortName = BackupUtils.getShortBackupName(backupName);
        CharSequence titleText = shortName == null ? context.getText(R.string.base_backup) : shortName;

        StringBuilder subtitleText = new StringBuilder()
                .append(DateUtils.formatDateTime(context, backupTime))
                .append(", ")
                .append(flags.toLocalisedString(context))
                .append(", ")
                .append(context.getString(R.string.version)).append(LangUtils.getSeparatorString()).append(versionName)
                .append(", ")
                .append(context.getString(R.string.user_id)).append(LangUtils.getSeparatorString()).append(userHandle);
        if (crypto.equals(CryptoUtils.MODE_NO_ENCRYPTION)) {
            subtitleText.append(", ").append(context.getString(R.string.no_encryption));
        } else {
            subtitleText.append(", ").append(context.getString(R.string.pgp_aes_rsa_encrypted,
                    crypto.toUpperCase(Locale.ROOT)));
        }
        subtitleText.append(", ").append(context.getString(R.string.gz_bz2_compressed, getReadableTarType(tarType)));
        if (keyStore) {
            subtitleText.append(", ").append(context.getString(R.string.keystore));
        }
        subtitleText.append(", ")
                .append(context.getString(R.string.size)).append(LangUtils.getSeparatorString()).append(Formatter
                        .formatFileSize(context, getBackupSize()));

        if (isFrozen()) {
            subtitleText.append(", ").append(context.getText(R.string.frozen));
        }

        return new SpannableStringBuilder(getTitleText(context, titleText)).append("\n")
                .append(getSmallerText(getSecondaryText(context, subtitleText)));
    }
}
