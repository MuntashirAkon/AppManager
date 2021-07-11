// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.Formatter;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PackageInfoCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import aosp.libcore.util.HexEncoding;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.VMRuntime;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.io.Path;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getTitleText;

public final class MetadataManager {
    public static final String TAG = MetadataManager.class.getSimpleName();

    public static final String META_FILE = "meta_v2.am.json";
    public static final String[] TAR_TYPES = new String[]{TarUtils.TAR_GZIP, TarUtils.TAR_BZIP2};

    // For an extended documentation, see https://github.com/MuntashirAkon/AppManager/issues/30
    // All the attributes must be non-null
    public static class Metadata {
        public String backupName;  // This isn't part of the json file and for internal use only
        public Path backupPath;  // This isn't part of the json file and for internal use only

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
         * <p>
         * {@code 1} indicates that it was part of an alpha version which is no longer supported.
         * <p>
         * {@code 2} was used in the beta versions but it is used to simulate that the permissions are not preserved.
         * <p>
         * {@code 3} is the currently used version and it preserves permissions.
         */
        public int version = 3;  // version
        public String apkName;  // apk_name
        public String instructionSet = VMRuntime.getInstructionSet(Build.SUPPORTED_ABIS[0]);  // instruction_set
        public BackupFlags flags;  // flags
        public int userHandle;  // user_handle
        @TarUtils.TarType
        public String tarType;  // tar_type
        public boolean keyStore;  // key_store
        public String installer;  // installer

        public Metadata() {
        }

        public Metadata(@NonNull Metadata metadata) {
            backupName = metadata.backupName;
            backupPath = metadata.backupPath;

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

        public long getBackupSize() {
            if (backupPath == null) return 0L;
            return IOUtils.fileSize(backupPath);
        }

        @WorkerThread
        public CharSequence toLocalizedString(Context context) {
            String shortName = BackupUtils.getShortBackupName(backupName);
            CharSequence titleText = shortName == null ? context.getText(R.string.base_backup) : shortName;

            StringBuilder subtitleText = new StringBuilder(DateUtils.formatDateTime(backupTime)).append(", ")
                    .append(flags.toLocalisedString(context)).append(", ");
            subtitleText.append(context.getString(R.string.version)).append(": ").append(versionName)
                    .append(", ").append(context.getString(R.string.user_id)).append(": ").append(userHandle);
            if (crypto.equals(CryptoUtils.MODE_NO_ENCRYPTION)) {
                subtitleText.append(", ").append(context.getString(R.string.no_encryption));
            } else {
                subtitleText.append(", ").append(context.getString(R.string.pgp_aes_rsa_encrypted,
                        crypto.toUpperCase(Locale.ROOT)));
            }
            subtitleText.append(", ").append(context.getString(R.string.gz_bz2_compressed,
                    tarType.equals(TarUtils.TAR_GZIP) ? "GZip" : "BZip2"));
            if (keyStore) subtitleText.append(", ").append(context.getString(R.string.keystore));
            subtitleText.append(", ").append(context.getString(R.string.size)).append(": ").append(Formatter
                    .formatFileSize(context, getBackupSize()));

            return new SpannableStringBuilder(getTitleText(context, titleText)).append("\n")
                    .append(getSmallerText(getSecondaryText(context, subtitleText)));
        }
    }

    @NonNull
    public static MetadataManager getNewInstance() {
        return new MetadataManager();
    }

    public static boolean hasAnyMetadata(String packageName) {
        try {
            for (Path file : getBackupFiles(packageName)) {
                if (file.hasFile(META_FILE)) {
                    return true;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, e);
        }
        return false;
    }

    public static boolean hasBaseMetadata(String packageName) {
        try {
            return BackupFiles.getPackagePath(packageName, false).hasFile(String.valueOf(Users.myUserId()));
        } catch (IOException e) {
            Log.e(TAG, e);
            return false;
        }
    }

    @WorkerThread
    @NonNull
    public static Metadata[] getMetadata(String packageName) throws IOException {
        Path[] backupFiles = getBackupFiles(packageName);
        List<Metadata> metadataList = new ArrayList<>(backupFiles.length);
        for (Path backupFile : backupFiles) {
            try {
                MetadataManager metadataManager = MetadataManager.getNewInstance();
                metadataManager.readMetadata(new BackupFiles.BackupFile(backupFile, false));
                metadataList.add(metadataManager.getMetadata());
            } catch (IOException e) {
                Log.e("MetadataManager", e.getClass().getName() + ": " + e.getMessage());
            }
        }
        return metadataList.toArray(new Metadata[0]);
    }

    @NonNull
    private static Path[] getBackupFiles(String packageName) throws IOException {
        Path[] backupFiles = BackupFiles.getPackagePath(packageName, false).listFiles(Path::isDirectory);
        return ArrayUtils.defeatNullable(Path.class, backupFiles);
    }

    private Metadata metadata;
    private final AppManager appManager;

    private MetadataManager() {
        this.appManager = AppManager.getInstance();
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    @WorkerThread
    synchronized public void readMetadata(@NonNull BackupFiles.BackupFile backupFile) throws IOException {
        String metadata = IOUtils.getFileContent(backupFile.getMetadataFile());
        try {
            if (TextUtils.isEmpty(metadata)) throw new JSONException("Empty JSON string");
            JSONObject rootObject = new JSONObject(metadata);
            this.metadata = new Metadata();
            this.metadata.backupPath = backupFile.getBackupPath();
            this.metadata.backupName = this.metadata.backupPath.getName();
            this.metadata.label = rootObject.getString("label");
            this.metadata.packageName = rootObject.getString("package_name");
            this.metadata.versionName = rootObject.getString("version_name");
            this.metadata.versionCode = rootObject.getLong("version_code");
            this.metadata.dataDirs = JSONUtils.getArray(String.class, rootObject.getJSONArray("data_dirs"));
            this.metadata.isSystem = rootObject.getBoolean("is_system");
            this.metadata.isSplitApk = rootObject.getBoolean("is_split_apk");
            this.metadata.splitConfigs = JSONUtils.getArray(String.class, rootObject.getJSONArray("split_configs"));
            this.metadata.hasRules = rootObject.getBoolean("has_rules");
            this.metadata.backupTime = rootObject.getLong("backup_time");
            this.metadata.checksumAlgo = rootObject.getString("checksum_algo");
            this.metadata.crypto = rootObject.getString("crypto");
            readCrypto(rootObject);
            this.metadata.version = rootObject.getInt("version");
            this.metadata.apkName = rootObject.getString("apk_name");
            this.metadata.instructionSet = rootObject.getString("instruction_set");
            this.metadata.flags = new BackupFlags(rootObject.getInt("flags"));
            this.metadata.userHandle = rootObject.getInt("user_handle");
            this.metadata.tarType = rootObject.getString("tar_type");
            this.metadata.keyStore = rootObject.getBoolean("key_store");
            this.metadata.installer = JSONUtils.getString(rootObject, "installer", BuildConfig.APPLICATION_ID);
        } catch (JSONException e) {
            ExUtils.rethrowAsIOException(e);
        }
    }

    private void readCrypto(JSONObject rootObj) throws JSONException {
        switch (metadata.crypto) {
            case CryptoUtils.MODE_OPEN_PGP:
                this.metadata.keyIds = rootObj.getString("key_ids");
                break;
            case CryptoUtils.MODE_RSA:
                this.metadata.aes = HexEncoding.decode(rootObj.getString("aes"));
                // Deliberate fallthrough
            case CryptoUtils.MODE_AES:
                this.metadata.iv = HexEncoding.decode(rootObj.getString("iv"));
                break;
            case CryptoUtils.MODE_NO_ENCRYPTION:
            default:
        }
    }

    @WorkerThread
    synchronized public void writeMetadata(@NonNull BackupFiles.BackupFile backupFile) throws IOException {
        if (metadata == null) throw new RuntimeException("Metadata is not set.");
        Path metadataFile = backupFile.getMetadataFile();
        try (OutputStream outputStream = metadataFile.openOutputStream()) {
            JSONObject rootObject = new JSONObject();
            rootObject.put("label", metadata.label);
            rootObject.put("package_name", metadata.packageName);
            rootObject.put("version_name", metadata.versionName);
            rootObject.put("version_code", metadata.versionCode);
            rootObject.put("data_dirs", JSONUtils.getJSONArray(metadata.dataDirs));
            rootObject.put("is_system", metadata.isSystem);
            rootObject.put("is_split_apk", metadata.isSplitApk);
            rootObject.put("split_configs", JSONUtils.getJSONArray(metadata.splitConfigs));
            rootObject.put("has_rules", metadata.hasRules);
            rootObject.put("backup_time", metadata.backupTime);
            rootObject.put("checksum_algo", metadata.checksumAlgo);
            rootObject.put("crypto", metadata.crypto);
            rootObject.put("key_ids", metadata.keyIds);
            rootObject.put("iv", metadata.iv == null ? null : HexEncoding.encodeToString(metadata.iv));
            rootObject.put("aes", metadata.aes == null ? null : HexEncoding.encodeToString(metadata.aes));
            rootObject.put("version", metadata.version);
            rootObject.put("apk_name", metadata.apkName);
            rootObject.put("instruction_set", metadata.instructionSet);
            rootObject.put("flags", metadata.flags.getFlags());
            rootObject.put("user_handle", metadata.userHandle);
            rootObject.put("tar_type", metadata.tarType);
            rootObject.put("key_store", metadata.keyStore);
            rootObject.put("installer", metadata.installer);
            outputStream.write(rootObject.toString(4).getBytes());
        } catch (JSONException e) {
            ExUtils.rethrowAsIOException(e);
        }
    }

    public Metadata setupMetadata(@NonNull PackageInfo packageInfo,
                                  int userHandle,
                                  @NonNull BackupFlags requestedFlags) {
        PackageManager pm = appManager.getPackageManager();
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        metadata = new Metadata();
        // We don't need to backup custom users or multiple backup flags
        requestedFlags.removeFlag(BackupFlags.BACKUP_CUSTOM_USERS | BackupFlags.BACKUP_MULTIPLE);
        metadata.flags = requestedFlags;
        metadata.userHandle = userHandle;
        metadata.tarType = (String) AppPref.get(AppPref.PrefKey.PREF_BACKUP_COMPRESSION_METHOD_STR);
        metadata.crypto = CryptoUtils.getMode();
        // Verify tar type
        if (ArrayUtils.indexOf(TAR_TYPES, metadata.tarType) == -1) {
            // Unknown tar type, set default
            metadata.tarType = TarUtils.TAR_GZIP;
        }
        metadata.keyStore = KeyStoreUtils.hasKeyStore(applicationInfo.uid);
        metadata.label = applicationInfo.loadLabel(pm).toString();
        metadata.packageName = packageInfo.packageName;
        metadata.versionName = packageInfo.versionName;
        metadata.versionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
        metadata.apkName = new File(applicationInfo.sourceDir).getName();
        metadata.dataDirs = PackageUtils.getDataDirs(applicationInfo, requestedFlags.backupInternalData(),
                requestedFlags.backupExternalData(), requestedFlags.backupMediaObb());
        metadata.isSystem = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        metadata.isSplitApk = false;
        try {
            ApkFile apkFile = ApkFile.getInstance(ApkFile.createInstance(applicationInfo));
            if (apkFile.isSplit()) {
                List<ApkFile.Entry> apkEntries = apkFile.getEntries();
                int splitCount = apkEntries.size() - 1;
                metadata.isSplitApk = splitCount > 0;
                metadata.splitConfigs = new String[splitCount];
                for (int i = 0; i < splitCount; ++i) {
                    metadata.splitConfigs[i] = apkEntries.get(i + 1).getFileName();
                }
            }
        } catch (ApkFile.ApkFileException e) {
            e.printStackTrace();
        }
        metadata.splitConfigs = ArrayUtils.defeatNullable(metadata.splitConfigs);
        metadata.hasRules = false;
        if (requestedFlags.backupRules()) {
            try (ComponentsBlocker cb = ComponentsBlocker.getInstance(packageInfo.packageName, userHandle)) {
                metadata.hasRules = cb.entryCount() > 0;
            }
        }
        metadata.backupTime = 0;
        try {
            metadata.installer = PackageManagerCompat.getInstallerPackage(packageInfo.packageName);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if (metadata.installer == null) {
            metadata.installer = BuildConfig.APPLICATION_ID;
        }
        return metadata;
    }
}
