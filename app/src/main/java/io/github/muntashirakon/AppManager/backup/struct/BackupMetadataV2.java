// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.struct;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import aosp.libcore.util.HexEncoding;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.BackupItems;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.misc.VMRuntime;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;

// For an extended documentation, see https://github.com/MuntashirAkon/AppManager/issues/30
// All the attributes must be non-null
public class BackupMetadataV2 implements IJsonSerializer {
    @Nullable
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
    @Nullable
    public byte[] iv;  // iv
    @Nullable
    public byte[] aes;  // aes (encrypted using RSA/ECC, for RSA/ECC only)
    @Nullable
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
    public int version;  // version
    public String apkName;  // apk_name
    public String instructionSet = VMRuntime.getInstructionSet(Build.SUPPORTED_ABIS[0]);  // instruction_set
    public BackupFlags flags;  // flags
    public int userId;  // user_handle
    @TarUtils.TarType
    public String tarType;  // tar_type
    public boolean keyStore;  // key_store
    public String installer;  // installer

    public BackupMetadataV2() {
        version = MetadataManager.getCurrentBackupMetaVersion();
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
        userId = metadata.userId;
        tarType = metadata.tarType;
        keyStore = metadata.keyStore;
        installer = metadata.installer;
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
        rootObject.put("user_handle", userId);
        rootObject.put("tar_type", tarType);
        rootObject.put("key_store", keyStore);
        rootObject.put("installer", installer);
        return rootObject;
    }
}
