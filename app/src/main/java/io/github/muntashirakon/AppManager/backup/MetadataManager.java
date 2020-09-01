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

import android.annotation.SuppressLint;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import dalvik.system.VMRuntime;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.types.PrivilegedFile;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public final class MetadataManager implements Closeable {
    public static final String META_FILE = "meta.am.v1";

    @StringDef(value = {
            DATA_USER,
            DATA_USER_DE,
            DATA_EXT_DATA,
            DATA_MEDIA,
            DATA_OBB
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DataDirType {
    }

    public static final String DATA_USER = "user";
    public static final String DATA_USER_DE = "user_de";
    public static final String DATA_EXT_DATA = "data";
    public static final String DATA_OBB = "obb";
    public static final String DATA_MEDIA = "media";

    // For an extended documentation, see https://github.com/MuntashirAkon/AppManager/issues/30
    // All the attributes must be non-null
    public static class Metadata {
        public String label;  // label
        public String packageName;  // package_name
        public String versionName;  // version_name
        public long versionCode;  // version_code
        public String sourceDir;  // source_dir
        @DataDirType
        public String[] dataDirs;  // data_dirs
        public boolean isSystem;  // is_system
        public boolean isSplitApk;  // is_split_apk
        public String[] splitNames;  // split_names
        public String[] splitSources;  // split_sources
        public boolean hasRules;  // has_rules
        public long backupTime;  // backup_time
        public String[] certSha256Checksum;  // cert_sha256_checksum
        public String sourceDirSha256Checksum;  // source_dir_sha256_checksum
        public String[] dataDirsSha256Checksum;  // data_dirs_sha256_checksum
        public int mode = 0;  // mode
        public int version = 1;  // version
        public String apkName;  // apk_name
        public String instructionSet = VMRuntime.getInstructionSet(Build.SUPPORTED_ABIS[0]);  // instruction_set
        public int flags = BackupFlags.BACKUP_FLAGS_COMPAT;  // flags, total is set for compatibility
        public int userHandle = Process.myUid() / 100000;  // user_handle
    }

    private static MetadataManager metadataManager;

    public static MetadataManager getInstance(String packageName) {
        if (metadataManager == null) metadataManager = new MetadataManager(packageName);
        if (!metadataManager.packageName.equals(packageName)) {
            metadataManager.close();
            metadataManager = new MetadataManager(packageName);
        }
        return metadataManager;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean hasMetadata(String packageName) {
        PrivilegedFile backupPath = new PrivilegedFile(BackupFiles.getPackagePath(packageName), String.valueOf(0));  // FIXME: Get current user handle
        return new PrivilegedFile(backupPath, META_FILE).exists();
    }

    @Override
    public void close() {
    }

    private @NonNull
    String packageName;
    private Metadata metadata;
    private AppManager appManager;

    MetadataManager(@NonNull String packageName) {
        this.packageName = packageName;
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
        File metadataFile = backupFile.getMetadataFile();
        String metadata = Utils.getFileContent(metadataFile);
        if (TextUtils.isEmpty(metadata)) throw new JSONException("Empty JSON string");
        JSONObject rootObject = new JSONObject(metadata);
        this.metadata = new Metadata();
        this.metadata.label = rootObject.getString("label");
        this.metadata.packageName = rootObject.getString("package_name");
        this.metadata.versionName = rootObject.getString("version_name");
        this.metadata.versionCode = rootObject.getLong("version_code");
        this.metadata.sourceDir = rootObject.getString("source_dir");
        this.metadata.dataDirs = getArrayFromJSONArray(rootObject.getJSONArray("data_dirs"));
        this.metadata.isSystem = rootObject.getBoolean("is_system");
        this.metadata.isSplitApk = rootObject.getBoolean("is_split_apk");
        this.metadata.splitNames = getArrayFromJSONArray(rootObject.getJSONArray("split_names"));
        this.metadata.splitSources = getArrayFromJSONArray(rootObject.getJSONArray("split_sources"));
        this.metadata.hasRules = rootObject.getBoolean("has_rules");
        this.metadata.backupTime = rootObject.getLong("backup_time");
        this.metadata.certSha256Checksum = getArrayFromJSONArray(rootObject.getJSONArray("cert_sha256_checksum"));
        this.metadata.sourceDirSha256Checksum = rootObject.getString("source_dir_sha256_checksum");
        this.metadata.dataDirsSha256Checksum = getArrayFromJSONArray(rootObject.getJSONArray("data_dirs_sha256_checksum"));
        this.metadata.mode = rootObject.getInt("mode");
        this.metadata.version = rootObject.getInt("version");
        try {
            this.metadata.apkName = rootObject.getString("apk_name");
        } catch (JSONException e) {
            this.metadata.apkName = "base.apk";
        }
        try {
            this.metadata.instructionSet = rootObject.getString("instruction_set");
        } catch (JSONException e) {
            // Add "-unknown" suffix to the current platform (to skip restoring)
            this.metadata.instructionSet = VMRuntime.getInstructionSet(Build.SUPPORTED_ABIS[0]) + "-unknown";
        }
        try {
            this.metadata.flags = rootObject.getInt("flags");
        } catch (JSONException e) {
            // Fallback to total
            this.metadata.flags = BackupFlags.BACKUP_FLAGS_COMPAT;
        }
        try {
            this.metadata.userHandle = rootObject.getInt("user_handle");
        } catch (JSONException e) {
            // Fallback to total
            this.metadata.userHandle = Process.myUid() / 100000;
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
            rootObject.put("source_dir", metadata.sourceDir);
            rootObject.put("data_dirs", getJSONArrayFromArray(metadata.dataDirs));
            rootObject.put("is_system", metadata.isSystem);
            rootObject.put("is_split_apk", metadata.isSplitApk);
            rootObject.put("split_names", getJSONArrayFromArray(metadata.splitNames));
            rootObject.put("split_sources", getJSONArrayFromArray(metadata.splitSources));
            rootObject.put("has_rules", metadata.hasRules);
            rootObject.put("backup_time", metadata.backupTime);
            rootObject.put("cert_sha256_checksum", getJSONArrayFromArray(metadata.certSha256Checksum));
            rootObject.put("source_dir_sha256_checksum", metadata.sourceDirSha256Checksum);
            rootObject.put("data_dirs_sha256_checksum", getJSONArrayFromArray(metadata.dataDirsSha256Checksum));
            rootObject.put("mode", metadata.mode);
            rootObject.put("version", metadata.version);
            rootObject.put("apk_name", metadata.apkName);
            rootObject.put("instruction_set", metadata.instructionSet);
            rootObject.put("flags", metadata.flags);
            rootObject.put("user_handle", metadata.userHandle);
            fileOutputStream.write(rootObject.toString().getBytes());
        }
    }

    @NonNull
    private static JSONArray getJSONArrayFromArray(@NonNull final String[] stringArray) {
        JSONArray jsonArray = new JSONArray();
        for (String string : stringArray) jsonArray.put(string);
        return jsonArray;
    }

    @NonNull
    private static String[] getArrayFromJSONArray(@NonNull final JSONArray jsonArray) throws JSONException {
        String[] stringArray = new String[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); ++i) stringArray[i] = (String) jsonArray.get(i);
        return stringArray;
    }

    public Metadata setupMetadata(int userHandle, @NonNull BackupFlags requestedFlags)
            throws PackageManager.NameNotFoundException {
        PackageManager pm = appManager.getPackageManager();
        @SuppressLint("WrongConstant")
        PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_META_DATA | PackageUtils.flagSigningInfo);
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        metadata = new Metadata();
        metadata.userHandle = userHandle;
        metadata.label = applicationInfo.loadLabel(pm).toString();
        metadata.packageName = packageName;
        metadata.versionName = packageInfo.versionName;
        metadata.versionCode = PackageUtils.getVersionCode(packageInfo);
        if (requestedFlags.backupSource()) {
            metadata.sourceDir = PackageUtils.getSourceDir(applicationInfo);
        } else metadata.sourceDir = "";
        metadata.apkName = new File(applicationInfo.sourceDir).getName();
        if (requestedFlags.backupData()) {
            metadata.dataDirs = PackageUtils.getDataDirs(applicationInfo, requestedFlags.backupExtData());
        }
        metadata.dataDirs = ArrayUtils.defeatNullable(metadata.dataDirs);
        metadata.isSystem = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        metadata.isSplitApk = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            metadata.splitNames = applicationInfo.splitNames;
            if (metadata.splitNames != null) {
                metadata.isSplitApk = true;
                metadata.splitSources = applicationInfo.splitPublicSourceDirs;
            }
        }
        metadata.splitNames = ArrayUtils.defeatNullable(metadata.splitNames);
        metadata.splitSources = ArrayUtils.defeatNullable(metadata.splitSources);
        metadata.hasRules = false;
        if (requestedFlags.backupRules()) {
            try (ComponentsBlocker cb = ComponentsBlocker.getInstance(packageName)) {
                metadata.hasRules = cb.entryCount() > 0;
            }
        }
        metadata.backupTime = 0;
        metadata.certSha256Checksum = PackageUtils.getSigningCertSha256Checksum(packageInfo);
        // Initialize checksums
        metadata.sourceDirSha256Checksum = "";
        metadata.dataDirsSha256Checksum = new String[metadata.dataDirs.length];
        return metadata;
    }

    @NonNull
    public String[] buildDataDirTypes(@Nullable String[] dataDir) {
        dataDir = ArrayUtils.defeatNullable(dataDir);
        for (int i = 0; i < dataDir.length; ++i) {
            dataDir[i] = dataDirToDataDirType(dataDir[i]);
        }
        return dataDir;
    }

    @SuppressLint("SdCardPath")
    @DataDirType
    public String dataDirToDataDirType(@NonNull String dir) {
        if (dir.startsWith("/data/user/") || dir.startsWith("/data/data/")) {
            return DATA_USER;
        } else if (dir.startsWith("/data/user_de/")) {
            return DATA_USER_DE;
        } else if (dir.contains("/Android/data/")) {
            return DATA_EXT_DATA;
        } else if (dir.contains("/Android/media")) {
            return DATA_MEDIA;
        } else if (dir.contains("/Android/obb")) {
            return DATA_OBB;
        } else {
            throw new IllegalArgumentException("Cannot infer directory type.");
        }
    }
}
