// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.UserHandleHidden;
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
import java.util.regex.Pattern;

import aosp.libcore.util.HexEncoding;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.VMRuntime;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.util.LocalizedString;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getTitleText;

public final class MetadataManager {
    public static final String TAG = MetadataManager.class.getSimpleName();

    public static final String META_FILE = "meta_v2.am.json";
    public static final String[] TAR_TYPES = new String[]{TarUtils.TAR_GZIP, TarUtils.TAR_BZIP2};

    public static final Pattern UUID_PATTERN = Pattern.compile("[a-f\\d]{8}(-[a-f\\d]{4}){3}-[a-f\\d]{12}");

    // For an extended documentation, see https://github.com/MuntashirAkon/AppManager/issues/30
    // All the attributes must be non-null
    public static class Metadata implements LocalizedString {
        public String backupName;  // This isn't part of the json file and for internal use only
        public BackupFiles.BackupFile backupFile; // This isn't part of the json file and for internal use only

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
            backupFile = metadata.backupFile;

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
            if (backupFile == null) return 0L;
            return Paths.size(backupFile.getBackupPath());
        }

        public boolean isBaseBackup() {
            return String.valueOf(UserHandleHidden.myUserId()).equals(backupName);
        }

        public boolean isFrozen() {
            return backupFile != null && backupFile.isFrozen();
        }

        @Override
        @NonNull
        @WorkerThread
        public CharSequence toLocalizedString(@NonNull Context context) {
            String shortName = BackupUtils.getShortBackupName(backupName);
            CharSequence titleText = shortName == null ? context.getText(R.string.base_backup) : shortName;

            StringBuilder subtitleText = new StringBuilder()
                    .append(DateUtils.formatDateTime(backupTime))
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
            subtitleText.append(", ").append(context.getString(R.string.gz_bz2_compressed,
                    tarType.equals(TarUtils.TAR_GZIP) ? "GZip" : "BZip2"));
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

    @NonNull
    public static MetadataManager getNewInstance() {
        return new MetadataManager();
    }

    @WorkerThread
    @NonNull
    public static Metadata[] getMetadata(@NonNull Path backupPath) {
        Path[] realBackupPaths;
        if (UUID_PATTERN.matcher(backupPath.getName()).matches()) {
            // UUID-based backups only store one backup per folder
            realBackupPaths = new Path[]{backupPath};
        } else {
            // Other backups can store multiple backups per folder
            realBackupPaths = backupPath.listFiles(Path::isDirectory);
        }
        List<Metadata> metadataList = new ArrayList<>(realBackupPaths.length);
        for (Path path : realBackupPaths) {
            try {
                MetadataManager metadataManager = MetadataManager.getNewInstance();
                metadataManager.readMetadata(new BackupFiles.BackupFile(path, false));
                metadataList.add(metadataManager.getMetadata());
            } catch (IOException e) {
                Log.e(TAG, e);
            }
        }
        return metadataList.toArray(new Metadata[0]);
    }

    @WorkerThread
    @NonNull
    @Deprecated
    public static Metadata[] getMetadata(String packageName) throws IOException {
        Path[] backupFiles = getBackupFiles(packageName);
        List<Metadata> metadataList = new ArrayList<>(backupFiles.length);
        for (Path backupFile : backupFiles) {
            try {
                MetadataManager metadataManager = MetadataManager.getNewInstance();
                metadataManager.readMetadata(new BackupFiles.BackupFile(backupFile, false));
                metadataList.add(metadataManager.getMetadata());
            } catch (IOException e) {
                Log.e("MetadataManager", e);
            }
        }
        return metadataList.toArray(new Metadata[0]);
    }

    @Deprecated
    @NonNull
    private static Path[] getBackupFiles(String packageName) throws IOException {
        return BackupFiles.getPackagePath(packageName, false).listFiles(Path::isDirectory);
    }

    private Metadata mMetadata;
    private final Context mContext;

    private MetadataManager() {
        mContext = AppManager.getInstance();
    }

    public Metadata getMetadata() {
        return mMetadata;
    }

    public void setMetadata(Metadata metadata) {
        this.mMetadata = metadata;
    }

    @WorkerThread
    synchronized public void readMetadata(@NonNull BackupFiles.BackupFile backupFile) throws IOException {
        String metadata = FileUtils.getFileContent(backupFile.getMetadataFile());
        if (TextUtils.isEmpty(metadata)) {
            throw new IOException("Empty JSON string for path " + backupFile.getBackupPath());
        }
        try {
            JSONObject rootObject = new JSONObject(metadata);
            mMetadata = new Metadata();
            mMetadata.backupFile = backupFile;
            mMetadata.backupName = backupFile.getBackupPath().getName();
            mMetadata.label = rootObject.getString("label");
            mMetadata.packageName = rootObject.getString("package_name");
            mMetadata.versionName = rootObject.getString("version_name");
            mMetadata.versionCode = rootObject.getLong("version_code");
            mMetadata.dataDirs = JSONUtils.getArray(String.class, rootObject.getJSONArray("data_dirs"));
            mMetadata.isSystem = rootObject.getBoolean("is_system");
            mMetadata.isSplitApk = rootObject.getBoolean("is_split_apk");
            mMetadata.splitConfigs = JSONUtils.getArray(String.class, rootObject.getJSONArray("split_configs"));
            mMetadata.hasRules = rootObject.getBoolean("has_rules");
            mMetadata.backupTime = rootObject.getLong("backup_time");
            mMetadata.checksumAlgo = rootObject.getString("checksum_algo");
            mMetadata.crypto = rootObject.getString("crypto");
            readCrypto(rootObject);
            mMetadata.version = rootObject.getInt("version");
            mMetadata.apkName = rootObject.getString("apk_name");
            mMetadata.instructionSet = rootObject.getString("instruction_set");
            mMetadata.flags = new BackupFlags(rootObject.getInt("flags"));
            mMetadata.userHandle = rootObject.getInt("user_handle");
            mMetadata.tarType = rootObject.getString("tar_type");
            mMetadata.keyStore = rootObject.getBoolean("key_store");
            mMetadata.installer = JSONUtils.getString(rootObject, "installer", BuildConfig.APPLICATION_ID);
        } catch (JSONException e) {
            throw new IOException(e.getMessage() + " for path " + backupFile.getBackupPath());
        }
    }

    private void readCrypto(JSONObject rootObj) throws JSONException {
        switch (mMetadata.crypto) {
            case CryptoUtils.MODE_OPEN_PGP:
                mMetadata.keyIds = rootObj.getString("key_ids");
                break;
            case CryptoUtils.MODE_RSA:
            case CryptoUtils.MODE_ECC:
                mMetadata.aes = HexEncoding.decode(rootObj.getString("aes"));
                // Deliberate fallthrough
            case CryptoUtils.MODE_AES:
                mMetadata.iv = HexEncoding.decode(rootObj.getString("iv"));
                break;
            case CryptoUtils.MODE_NO_ENCRYPTION:
            default:
        }
    }

    @WorkerThread
    synchronized public void writeMetadata(@NonNull BackupFiles.BackupFile backupFile) throws IOException {
        if (mMetadata == null) {
            throw new RuntimeException("Metadata not set for path " + backupFile.getBackupPath());
        }
        Path metadataFile = backupFile.getMetadataFile();
        try (OutputStream outputStream = metadataFile.openOutputStream()) {
            JSONObject rootObject = new JSONObject();
            rootObject.put("label", mMetadata.label);
            rootObject.put("package_name", mMetadata.packageName);
            rootObject.put("version_name", mMetadata.versionName);
            rootObject.put("version_code", mMetadata.versionCode);
            rootObject.put("data_dirs", JSONUtils.getJSONArray(mMetadata.dataDirs));
            rootObject.put("is_system", mMetadata.isSystem);
            rootObject.put("is_split_apk", mMetadata.isSplitApk);
            rootObject.put("split_configs", JSONUtils.getJSONArray(mMetadata.splitConfigs));
            rootObject.put("has_rules", mMetadata.hasRules);
            rootObject.put("backup_time", mMetadata.backupTime);
            rootObject.put("checksum_algo", mMetadata.checksumAlgo);
            rootObject.put("crypto", mMetadata.crypto);
            rootObject.put("key_ids", mMetadata.keyIds);
            rootObject.put("iv", mMetadata.iv == null ? null : HexEncoding.encodeToString(mMetadata.iv));
            rootObject.put("aes", mMetadata.aes == null ? null : HexEncoding.encodeToString(mMetadata.aes));
            rootObject.put("version", mMetadata.version);
            rootObject.put("apk_name", mMetadata.apkName);
            rootObject.put("instruction_set", mMetadata.instructionSet);
            rootObject.put("flags", mMetadata.flags.getFlags());
            rootObject.put("user_handle", mMetadata.userHandle);
            rootObject.put("tar_type", mMetadata.tarType);
            rootObject.put("key_store", mMetadata.keyStore);
            rootObject.put("installer", mMetadata.installer);
            outputStream.write(rootObject.toString(4).getBytes());
        } catch (JSONException e) {
            throw new IOException(e.getMessage() + " for path " + backupFile.getBackupPath());
        }
    }

    public Metadata setupMetadata(@NonNull PackageInfo packageInfo,
                                  int userHandle,
                                  @NonNull BackupFlags requestedFlags) {
        PackageManager pm = mContext.getPackageManager();
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        mMetadata = new Metadata();
        // We don't need to backup custom users or multiple backup flags
        requestedFlags.removeFlag(BackupFlags.BACKUP_CUSTOM_USERS | BackupFlags.BACKUP_MULTIPLE);
        mMetadata.flags = requestedFlags;
        mMetadata.userHandle = userHandle;
        mMetadata.tarType = (String) AppPref.get(AppPref.PrefKey.PREF_BACKUP_COMPRESSION_METHOD_STR);
        mMetadata.crypto = CryptoUtils.getMode();
        // Verify tar type
        if (ArrayUtils.indexOf(TAR_TYPES, mMetadata.tarType) == -1) {
            // Unknown tar type, set default
            mMetadata.tarType = TarUtils.TAR_GZIP;
        }
        mMetadata.keyStore = KeyStoreUtils.hasKeyStore(mContext, applicationInfo.uid);
        mMetadata.label = applicationInfo.loadLabel(pm).toString();
        mMetadata.packageName = packageInfo.packageName;
        mMetadata.versionName = packageInfo.versionName;
        mMetadata.versionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
        mMetadata.apkName = new File(applicationInfo.sourceDir).getName();
        mMetadata.dataDirs = PackageUtils.getDataDirs(applicationInfo, requestedFlags.backupInternalData(),
                requestedFlags.backupExternalData(), requestedFlags.backupMediaObb());
        mMetadata.isSystem = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        mMetadata.isSplitApk = false;
        try {
            ApkFile apkFile = ApkFile.getInstance(ApkFile.createInstance(applicationInfo));
            if (apkFile.isSplit()) {
                List<ApkFile.Entry> apkEntries = apkFile.getEntries();
                int splitCount = apkEntries.size() - 1;
                mMetadata.isSplitApk = splitCount > 0;
                mMetadata.splitConfigs = new String[splitCount];
                for (int i = 0; i < splitCount; ++i) {
                    mMetadata.splitConfigs[i] = apkEntries.get(i + 1).getFileName();
                }
            }
        } catch (ApkFile.ApkFileException e) {
            e.printStackTrace();
        }
        mMetadata.splitConfigs = ArrayUtils.defeatNullable(mMetadata.splitConfigs);
        mMetadata.hasRules = false;
        if (requestedFlags.backupRules()) {
            try (ComponentsBlocker cb = ComponentsBlocker.getInstance(packageInfo.packageName, userHandle, false)) {
                mMetadata.hasRules = cb.entryCount() > 0;
            }
        }
        mMetadata.backupTime = 0;
        try {
            mMetadata.installer = PackageManagerCompat.getInstallerPackage(packageInfo.packageName);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if (mMetadata.installer == null) {
            mMetadata.installer = BuildConfig.APPLICATION_ID;
        }
        return mMetadata;
    }
}
