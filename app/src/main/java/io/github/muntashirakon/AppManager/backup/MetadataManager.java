/*
 * Copyright (C) 2020 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.backup;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import dalvik.system.VMRuntime;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.types.PrivilegedFile;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public final class MetadataManager {
    public static final String META_FILE = "meta_v2.am.json";
    public static final String[] TAR_TYPES = new String[]{TarUtils.TAR_GZIP, TarUtils.TAR_BZIP2};

    // For an extended documentation, see https://github.com/MuntashirAkon/AppManager/issues/30
    // All the attributes must be non-null
    public static class Metadata {
        public String backupName;  // This isn't part of the json file and for internal use only
        public String label;  // label
        public String packageName;  // package_name
        public String versionName;  // version_name
        public long versionCode;  // version_code
        public String[] dataDirs;  // data_dirs
        public boolean isSystem;  // is_system
        public boolean isSplitApk;  // is_split_apk
        public String[] splitConfigs;  // split_configs
        public String[] splitNames;  // split_names
        public boolean hasRules;  // has_rules
        public long backupTime;  // backup_time
        @DigestUtils.Algorithm
        public String checksumAlgo = DigestUtils.SHA_256;  // checksum_algo
        @CryptoUtils.Mode
        public String crypto;  // crypto
        public byte[] iv;  // iv
        public String keyIds;  // key_ids
        public int version = 2;  // version
        public String apkName;  // apk_name
        public String instructionSet = VMRuntime.getInstructionSet(Build.SUPPORTED_ABIS[0]);  // instruction_set
        public BackupFlags flags;  // flags
        public int userHandle;  // user_handle
        @TarUtils.TarType
        public String tarType;  // tar_type
        public boolean keyStore;  // key_store
    }

    @NonNull
    public static MetadataManager getNewInstance() {
        return new MetadataManager();
    }

    public static boolean hasAnyMetadata(String packageName) {
        for (File file : getBackupFiles(packageName)) {
            if (new PrivilegedFile(file, META_FILE).exists()) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasBaseMetadata(String packageName) {
        PrivilegedFile backupPath = new PrivilegedFile(BackupFiles.getPackagePath(packageName),
                String.valueOf(Users.getCurrentUserHandle()));
        return new PrivilegedFile(backupPath, META_FILE).exists();
    }

    @NonNull
    public static Metadata[] getMetadata(String packageName) {
        File[] backupFiles = getBackupFiles(packageName);
        List<Metadata> metadataList = new ArrayList<>(backupFiles.length);
        for (File backupFile : backupFiles) {
            try {
                MetadataManager metadataManager = MetadataManager.getNewInstance();
                metadataManager.readMetadata(new BackupFiles.BackupFile((PrivilegedFile) backupFile, false));
                Metadata metadata = metadataManager.getMetadata();
                metadata.backupName = backupFile.getName();
                metadataList.add(metadata);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return metadataList.toArray(new Metadata[0]);
    }

    @NonNull
    private static File[] getBackupFiles(String packageName) {
        PrivilegedFile[] backupFiles = BackupFiles.getPackagePath(packageName).listFiles(pathname -> new PrivilegedFile(pathname).isDirectory());
        return ArrayUtils.defeatNullable(backupFiles);
    }

    private Metadata metadata;
    private AppManager appManager;

    private MetadataManager() {
        this.appManager = AppManager.getInstance();
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    synchronized public void readMetadata(@NonNull BackupFiles.BackupFile backupFile)
            throws JSONException {
        String metadata = IOUtils.getFileContent(backupFile.getMetadataFile());
        if (TextUtils.isEmpty(metadata)) throw new JSONException("Empty JSON string");
        JSONObject rootObject = new JSONObject(metadata);
        this.metadata = new Metadata();
        this.metadata.label = rootObject.getString("label");
        this.metadata.packageName = rootObject.getString("package_name");
        this.metadata.versionName = rootObject.getString("version_name");
        this.metadata.versionCode = rootObject.getLong("version_code");
        this.metadata.dataDirs = JSONUtils.getArray(String.class, rootObject.getJSONArray("data_dirs"));
        this.metadata.isSystem = rootObject.getBoolean("is_system");
        this.metadata.isSplitApk = rootObject.getBoolean("is_split_apk");
        this.metadata.splitConfigs = JSONUtils.getArray(String.class, rootObject.getJSONArray("split_configs"));
        this.metadata.splitNames = JSONUtils.getArray(String.class, rootObject.getJSONArray("split_names"));
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
    }

    private void readCrypto(JSONObject rootObj) throws JSONException {
        switch (metadata.crypto) {
            case CryptoUtils.MODE_OPEN_PGP:
                this.metadata.keyIds = rootObj.getString("key_ids");
                break;
            case CryptoUtils.MODE_AES:
            case CryptoUtils.MODE_RSA:
                this.metadata.iv = Utils.hexToBytes(rootObj.getString("iv"));
                break;
            case CryptoUtils.MODE_NO_ENCRYPTION:
            default:
        }
    }

    synchronized public void writeMetadata(@NonNull BackupFiles.BackupFile backupFile)
            throws IOException, JSONException {
        if (metadata == null) throw new RuntimeException("Metadata is not set.");
        File metadataFile = backupFile.getMetadataFile();
        try (FileOutputStream fileOutputStream = new FileOutputStream(metadataFile)) {
            JSONObject rootObject = new JSONObject();
            rootObject.put("label", metadata.label);
            rootObject.put("package_name", metadata.packageName);
            rootObject.put("version_name", metadata.versionName);
            rootObject.put("version_code", metadata.versionCode);
            rootObject.put("data_dirs", JSONUtils.getJSONArray(metadata.dataDirs));
            rootObject.put("is_system", metadata.isSystem);
            rootObject.put("is_split_apk", metadata.isSplitApk);
            rootObject.put("split_configs", JSONUtils.getJSONArray(metadata.splitConfigs));
            rootObject.put("split_names", JSONUtils.getJSONArray(metadata.splitNames));
            rootObject.put("has_rules", metadata.hasRules);
            rootObject.put("backup_time", metadata.backupTime);
            rootObject.put("checksum_algo", metadata.checksumAlgo);
            rootObject.put("crypto", metadata.crypto);
            rootObject.put("key_ids", metadata.keyIds);
            rootObject.put("iv", Utils.bytesToHex(metadata.iv));
            rootObject.put("version", metadata.version);
            rootObject.put("apk_name", metadata.apkName);
            rootObject.put("instruction_set", metadata.instructionSet);
            rootObject.put("flags", metadata.flags.getFlags());
            rootObject.put("user_handle", metadata.userHandle);
            rootObject.put("tar_type", metadata.tarType);
            rootObject.put("key_store", metadata.keyStore);
            fileOutputStream.write(rootObject.toString().getBytes());
        }
    }

    public Metadata setupMetadata(@NonNull PackageInfo packageInfo,
                                  int userHandle,
                                  @NonNull BackupFlags requestedFlags) {
        PackageManager pm = appManager.getPackageManager();
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        metadata = new Metadata();
        metadata.flags = requestedFlags;
        metadata.userHandle = userHandle;
        metadata.tarType = (String) AppPref.get(AppPref.PrefKey.PREF_BACKUP_COMPRESSION_METHOD_STR);
        metadata.crypto = CryptoUtils.getMode();
        // Verify tar type
        if (ArrayUtils.indexOf(TAR_TYPES, metadata.tarType) == -1) {
            // Unknown tar type, set default
            metadata.tarType = TarUtils.TAR_GZIP;
        }
        metadata.keyStore = BackupUtils.hasKeyStore(applicationInfo.uid);
        metadata.label = applicationInfo.loadLabel(pm).toString();
        metadata.packageName = packageInfo.packageName;
        metadata.versionName = packageInfo.versionName;
        metadata.versionCode = PackageUtils.getVersionCode(packageInfo);
        metadata.apkName = new File(applicationInfo.sourceDir).getName();
        if (requestedFlags.backupData()) {
            // FIXME(10/7/20): External data directory is not respecting userHandle
            metadata.dataDirs = PackageUtils.getDataDirs(applicationInfo,
                    requestedFlags.backupExtData(), requestedFlags.backupMediaObb());
        }
        metadata.dataDirs = ArrayUtils.defeatNullable(metadata.dataDirs);
        metadata.isSystem = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        metadata.isSplitApk = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            metadata.splitConfigs = applicationInfo.splitNames;
            if (metadata.splitConfigs != null) {
                metadata.isSplitApk = true;
                metadata.splitNames = new String[metadata.splitConfigs.length];
                for (int i = 0; i < applicationInfo.splitPublicSourceDirs.length; ++i) {
                    metadata.splitNames[i] = new File(applicationInfo.splitPublicSourceDirs[i]).getName();
                }
            }
        }
        metadata.splitConfigs = ArrayUtils.defeatNullable(metadata.splitConfigs);
        metadata.splitNames = ArrayUtils.defeatNullable(metadata.splitNames);
        metadata.hasRules = false;
        if (requestedFlags.backupRules()) {
            try (ComponentsBlocker cb = ComponentsBlocker.getInstance(packageInfo.packageName, userHandle)) {
                metadata.hasRules = cb.entryCount() > 0;
            }
        }
        metadata.backupTime = 0;
        return metadata;
    }
}
