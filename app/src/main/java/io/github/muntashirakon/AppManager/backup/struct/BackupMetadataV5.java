// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.struct;

import static io.github.muntashirakon.AppManager.backup.BackupUtils.getReadableTarType;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getTitleText;

import android.annotation.UserIdInt;
import android.content.Context;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.Formatter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Objects;

import aosp.libcore.util.HexEncoding;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.BackupItems;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.crypto.AESCrypto;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.crypto.DummyCrypto;
import io.github.muntashirakon.AppManager.crypto.ECCCrypto;
import io.github.muntashirakon.AppManager.crypto.OpenPGPCrypto;
import io.github.muntashirakon.AppManager.crypto.RSACrypto;
import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.misc.VMRuntime;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.util.LocalizedString;

public class BackupMetadataV5 implements LocalizedString {
    public static class Info implements IJsonSerializer {
        /**
         * Relative location of the backup from the AppManager directory, internal use only.
         */
        private String mRelativeDir;
        public BackupItems.BackupItem mBackupItem; // This isn't part of the json file and for internal use only

        /**
         * Metadata version.
         *
         * <ul>
         *     <li>{@code 1} - Alpha version, no longer supported</li>
         *     <li>{@code 2} - Beta version (v2.5.2x), permissions aren't preserved (special action needed)</li>
         *     <li>{@code 3} - From v2.6.x to v3.0.2 and v3.1.0-alpha01, permissions are preserved, AES GCM MAC size is 32 bits</li>
         *     <li>{@code 4} - Since v3.0.3 and v3.1.0-alpha02, AES GCM MAC size is 128 bits</li>
         *     <li>{@code 5} - Since v4.0.6, meta.json, info.json, privacy-friendly backup</li>
         * </ul>
         */
        public final int version;  // version
        public final long backupTime;  // backup_time
        @NonNull
        public final BackupFlags flags;  // flags
        @UserIdInt
        public final int userId;  // user_handle
        @NonNull
        @TarUtils.TarType
        public final String tarType;  // tar_type
        @NonNull
        @DigestUtils.Algorithm
        public final String checksumAlgo;  // checksum_algo
        @NonNull
        @CryptoUtils.Mode
        public final String crypto;  // crypto
        @Nullable
        public final byte[] iv;  // iv
        @Nullable
        public final byte[] aes;  // aes (encrypted using RSA, for RSA only)
        @Nullable
        public final String keyIds;  // key_ids

        @Nullable
        private Crypto mCrypto;

        public Info(long backupTime,
                    @NonNull BackupFlags flags,
                    @UserIdInt int userId,
                    @TarUtils.TarType @NonNull String tarType,
                    @DigestUtils.Algorithm @NonNull String checksumAlgo,
                    @CryptoUtils.Mode @NonNull String crypto,
                    @Nullable byte[] iv,
                    @Nullable byte[] aes,
                    @Nullable String keyIds) {
            this.version = MetadataManager.getCurrentBackupMetaVersion();
            this.backupTime = backupTime;
            this.flags = flags;
            this.userId = userId;
            this.tarType = tarType;
            this.checksumAlgo = checksumAlgo;
            this.crypto = crypto;
            this.iv = iv;
            this.aes = aes;
            this.keyIds = keyIds;
            verifyCrypto();
        }

        public Info(@NonNull JSONObject rootObject) throws JSONException {
            this.version = rootObject.getInt("version");
            this.backupTime = rootObject.getLong("backup_time");
            this.flags = new BackupFlags(rootObject.getInt("flags"));
            this.userId = rootObject.getInt("user_handle");
            this.tarType = rootObject.getString("tar_type");
            this.checksumAlgo = rootObject.getString("checksum_algo");
            this.crypto = rootObject.getString("crypto");
            this.keyIds = JSONUtils.optString(rootObject, "key_ids");
            String aesKey = JSONUtils.optString(rootObject, "aes");
            this.aes = aesKey != null ? HexEncoding.decode(aesKey) : null;
            String iv = JSONUtils.optString(rootObject, "iv");
            this.iv = iv != null ? HexEncoding.decode(iv) : null;
            verifyCrypto();
        }

        public void setBackupItem(@NonNull BackupItems.BackupItem backupItem) {
            mBackupItem = backupItem;
            mRelativeDir = backupItem.getRelativeDir();
        }

        public BackupItems.BackupItem getBackupItem() {
            return mBackupItem;
        }

        public String getRelativeDir() {
            return mRelativeDir;
        }

        // Get crypto only works when crypto is already setup.
        public Crypto getCrypto() throws CryptoException {
            if (mCrypto == null) {
                mCrypto = getCryptoInternal();
            }
            return mCrypto;
        }

        public long getBackupSize() {
            if (mBackupItem == null) return 0L;
            return Paths.size(mBackupItem.getBackupPath());
        }

        public boolean isFrozen() {
            return mBackupItem != null && mBackupItem.isFrozen();
        }

        private void verifyCrypto() {
            switch (crypto) {
                case CryptoUtils.MODE_OPEN_PGP:
                    Objects.requireNonNull(keyIds);
                    assert !keyIds.isEmpty();
                    break;
                case CryptoUtils.MODE_RSA:
                case CryptoUtils.MODE_ECC:
                    Objects.requireNonNull(aes);
                    assert aes.length > 0;
                    // Deliberate fallthrough
                case CryptoUtils.MODE_AES:
                    Objects.requireNonNull(iv);
                    assert iv.length > 0;
                    break;
                case CryptoUtils.MODE_NO_ENCRYPTION:
                default:
            }
        }

        @NonNull
        private Crypto getCryptoInternal() throws CryptoException {
            switch (crypto) {
                case CryptoUtils.MODE_OPEN_PGP:
                    Objects.requireNonNull(keyIds);
                    return new OpenPGPCrypto(ContextUtils.getContext(), keyIds);
                case CryptoUtils.MODE_AES: {
                    Objects.requireNonNull(iv);
                    AESCrypto aesCrypto = new AESCrypto(iv);
                    if (version < 4) {
                        // Old backups use 32 bit MAC
                        aesCrypto.setMacSizeBits(AESCrypto.MAC_SIZE_BITS_OLD);
                    }
                    return aesCrypto;
                }
                case CryptoUtils.MODE_RSA: {
                    Objects.requireNonNull(iv);
                    Objects.requireNonNull(aes);
                    RSACrypto rsaCrypto = new RSACrypto(iv, aes);
                    if (version < 4) {
                        // Old backups use 32 bit MAC
                        rsaCrypto.setMacSizeBits(AESCrypto.MAC_SIZE_BITS_OLD);
                    }
                    return rsaCrypto;
                }
                case CryptoUtils.MODE_ECC: {
                    Objects.requireNonNull(iv);
                    Objects.requireNonNull(aes);
                    ECCCrypto eccCrypto = new ECCCrypto(iv, aes);
                    if (version < 4) {
                        // Old backups use 32 bit MAC
                        eccCrypto.setMacSizeBits(AESCrypto.MAC_SIZE_BITS_OLD);
                    }
                    return eccCrypto;
                }
                case CryptoUtils.MODE_NO_ENCRYPTION:
                default:
                    // Dummy crypto to generalise and return nonNull
                    return new DummyCrypto();
            }
        }

        @NonNull
        @Override
        public JSONObject serializeToJson() throws JSONException {
            JSONObject rootObject = new JSONObject();
            rootObject.put("backup_time", backupTime);
            rootObject.put("checksum_algo", checksumAlgo);
            rootObject.put("crypto", crypto);
            rootObject.put("key_ids", keyIds);
            rootObject.put("iv", iv == null ? null : HexEncoding.encodeToString(iv));
            rootObject.put("aes", aes == null ? null : HexEncoding.encodeToString(aes));
            rootObject.put("version", version);
            rootObject.put("flags", flags.getFlags());
            rootObject.put("user_handle", userId);
            rootObject.put("tar_type", tarType);
            return rootObject;
        }


    }

    public static class Metadata implements IJsonSerializer {
        // For backward compatibility only
        public final int version;  // version
        @Nullable
        public final String backupName;  // backup_name
        public boolean hasRules;  // has_rules
        public String label;  // label
        public String packageName;  // package_name
        public String versionName;  // version_name
        public long versionCode;  // version_code
        public String[] dataDirs;  // data_dirs
        public boolean isSystem;  // is_system
        public boolean isSplitApk;  // is_split_apk
        public String[] splitConfigs;  // split_configs
        public String apkName;  // apk_name
        public String instructionSet = VMRuntime.getInstructionSet(Build.SUPPORTED_ABIS[0]);  // instruction_set
        public boolean keyStore;  // key_store
        @Nullable
        public String installer;  // installer

        public Metadata(@Nullable String backupName) {
            this.version = MetadataManager.getCurrentBackupMetaVersion();
            this.backupName = backupName;
        }

        public Metadata(@NonNull Metadata metadata) {
            version = metadata.version;
            backupName = metadata.backupName;
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
            apkName = metadata.apkName;
            instructionSet = metadata.instructionSet;
            keyStore = metadata.keyStore;
            installer = metadata.installer;
        }

        public Metadata(@NonNull JSONObject rootObject) throws JSONException {
            version = rootObject.getInt("version");
            backupName = JSONUtils.optString(rootObject, "backup_name");
            label = rootObject.getString("label");
            packageName = rootObject.getString("package_name");
            versionName = rootObject.getString("version_name");
            versionCode = rootObject.getLong("version_code");
            dataDirs = JSONUtils.getArray(String.class, rootObject.getJSONArray("data_dirs"));
            isSystem = rootObject.getBoolean("is_system");
            isSplitApk = rootObject.getBoolean("is_split_apk");
            splitConfigs = JSONUtils.getArray(String.class, rootObject.getJSONArray("split_configs"));
            hasRules = rootObject.getBoolean("has_rules");
            apkName = rootObject.getString("apk_name");
            instructionSet = rootObject.getString("instruction_set");
            keyStore = rootObject.getBoolean("key_store");
            installer = JSONUtils.optString(rootObject, "installer");
        }

        @NonNull
        @Override
        public JSONObject serializeToJson() throws JSONException {
            JSONObject rootObject = new JSONObject();
            rootObject.put("version", version);
            rootObject.put("backup_name", backupName);
            rootObject.put("label", label);
            rootObject.put("package_name", packageName);
            rootObject.put("version_name", versionName);
            rootObject.put("version_code", versionCode);
            rootObject.put("data_dirs", JSONUtils.getJSONArray(dataDirs));
            rootObject.put("is_system", isSystem);
            rootObject.put("is_split_apk", isSplitApk);
            rootObject.put("split_configs", JSONUtils.getJSONArray(splitConfigs));
            rootObject.put("has_rules", hasRules);
            rootObject.put("apk_name", apkName);
            rootObject.put("instruction_set", instructionSet);
            rootObject.put("key_store", keyStore);
            rootObject.put("installer", installer);
            return rootObject;
        }
    }

    @NonNull
    public final Info info;
    @NonNull
    public final Metadata metadata;

    public BackupMetadataV5(@NonNull Info info, @NonNull Metadata metadata) {
        this.info = info;
        this.metadata = metadata;
    }

    public boolean isBaseBackup() {
        return TextUtils.isEmpty(metadata.backupName);
    }

    @Override
    @NonNull
    @WorkerThread
    public CharSequence toLocalizedString(@NonNull Context context) {
        CharSequence titleText = isBaseBackup() ? context.getText(R.string.base_backup) : Objects.requireNonNull(metadata.backupName);

        StringBuilder subtitleText = new StringBuilder()
                .append(DateUtils.formatDateTime(context, info.backupTime))
                .append(", ")
                .append(info.flags.toLocalisedString(context))
                .append(", ")
                .append(context.getString(R.string.version)).append(LangUtils.getSeparatorString()).append(metadata.versionName)
                .append(", ")
                .append(context.getString(R.string.user_id)).append(LangUtils.getSeparatorString()).append(info.userId);
        if (info.crypto.equals(CryptoUtils.MODE_NO_ENCRYPTION)) {
            subtitleText.append(", ").append(context.getString(R.string.no_encryption));
        } else {
            subtitleText.append(", ").append(context.getString(R.string.pgp_aes_rsa_encrypted,
                    info.crypto.toUpperCase(Locale.ROOT)));
        }
        subtitleText.append(", ").append(context.getString(R.string.gz_bz2_compressed, getReadableTarType(info.tarType)));
        subtitleText.append(", ")
                .append(context.getString(R.string.size)).append(LangUtils.getSeparatorString()).append(Formatter
                        .formatFileSize(context, info.getBackupSize()));

        if (info.isFrozen()) {
            subtitleText.append(", ").append(context.getText(R.string.frozen));
        }

        return new SpannableStringBuilder(getTitleText(context, titleText)).append("\n")
                .append(getSmallerText(getSecondaryText(context, subtitleText)));
    }
}
