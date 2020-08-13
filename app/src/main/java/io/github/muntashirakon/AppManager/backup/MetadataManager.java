package io.github.muntashirakon.AppManager.backup;

import android.annotation.SuppressLint;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import androidx.annotation.NonNull;
import dalvik.system.VMRuntime;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public final class MetadataManager implements Closeable {
    public static final String META_FILE = "meta.am.v1";

    // For an extended documentation, see https://github.com/MuntashirAkon/AppManager/issues/30
    // All the attributes must be non-null
    public static class MetadataV1 {
        public String label;  // label
        public String packageName;  // package_name
        public String versionName;  // version_name
        public long versionCode;  // version_code
        public String sourceDir;  // source_dir
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

    public static boolean hasMetadata(String packageName) {
        return new File(BackupStorageManager.getBackupPath(packageName), META_FILE).exists();
    }

    @Override
    public void close() {}

    private @NonNull String packageName;
    private MetadataV1 metadataV1;
    private AppManager appManager;
    MetadataManager(@NonNull String packageName) {
        this.packageName = packageName;
        this.appManager = AppManager.getInstance();
    }

    public MetadataV1 getMetadataV1() {
        return metadataV1;
    }

    public void setMetadataV1(MetadataV1 metadataV1) {
        this.metadataV1 = metadataV1;
    }

    synchronized public void readMetadata() throws JSONException {
        File metadataFile = getMetadataFile(false);
        String metadata = Utils.getFileContent(metadataFile);
        if (TextUtils.isEmpty(metadata)) throw new JSONException("Empty JSON string");
        JSONObject rootObject = new JSONObject(metadata);
        metadataV1 = new MetadataV1();
        metadataV1.label = rootObject.getString("label");
        metadataV1.packageName = rootObject.getString("package_name");
        metadataV1.versionName = rootObject.getString("version_name");
        metadataV1.versionCode = rootObject.getLong("version_code");
        metadataV1.sourceDir = rootObject.getString("source_dir");
        metadataV1.dataDirs = getArrayFromJSONArray(rootObject.getJSONArray("data_dirs"));
        metadataV1.isSystem = rootObject.getBoolean("is_system");
        metadataV1.isSplitApk = rootObject.getBoolean("is_split_apk");
        metadataV1.splitNames = getArrayFromJSONArray(rootObject.getJSONArray("split_names"));
        metadataV1.splitSources = getArrayFromJSONArray(rootObject.getJSONArray("split_sources"));
        metadataV1.hasRules = rootObject.getBoolean("has_rules");
        metadataV1.backupTime = rootObject.getLong("backup_time");
        metadataV1.certSha256Checksum = getArrayFromJSONArray(rootObject.getJSONArray("cert_sha256_checksum"));
        metadataV1.sourceDirSha256Checksum = rootObject.getString("source_dir_sha256_checksum");
        metadataV1.dataDirsSha256Checksum = getArrayFromJSONArray(rootObject.getJSONArray("data_dirs_sha256_checksum"));
        metadataV1.mode = rootObject.getInt("mode");
        metadataV1.version = rootObject.getInt("version");
        try {
            metadataV1.apkName = rootObject.getString("apk_name");
        } catch (JSONException e) {
            metadataV1.apkName = "base.apk";
        }
        try {
            metadataV1.instructionSet = rootObject.getString("instruction_set");
        } catch (JSONException e) {
            // Add "-unknown" suffix to the current platform (to skip restoring)
            metadataV1.instructionSet = VMRuntime.getInstructionSet(Build.SUPPORTED_ABIS[0]) + "-unknown";
        }
    }

    synchronized public void writeMetadata() throws IOException, JSONException {
        if (metadataV1 == null) throw new RuntimeException("Metadata is not set.");
        File metadataFile = getMetadataFile(true);
        try (FileOutputStream fileOutputStream = new FileOutputStream(metadataFile)) {
            JSONObject rootObject = new JSONObject();
            rootObject.put("label", metadataV1.label);
            rootObject.put("package_name", metadataV1.packageName);
            rootObject.put("version_name", metadataV1.versionName);
            rootObject.put("version_code", metadataV1.versionCode);
            rootObject.put("source_dir", metadataV1.sourceDir);
            rootObject.put("data_dirs", getJSONArrayFromArray(metadataV1.dataDirs));
            rootObject.put("is_system", metadataV1.isSystem);
            rootObject.put("is_split_apk", metadataV1.isSplitApk);
            rootObject.put("split_names", getJSONArrayFromArray(metadataV1.splitNames));
            rootObject.put("split_sources", getJSONArrayFromArray(metadataV1.splitSources));
            rootObject.put("has_rules", metadataV1.hasRules);
            rootObject.put("backup_time", metadataV1.backupTime);
            rootObject.put("cert_sha256_checksum", getJSONArrayFromArray(metadataV1.certSha256Checksum));
            rootObject.put("source_dir_sha256_checksum", metadataV1.sourceDirSha256Checksum);
            rootObject.put("data_dirs_sha256_checksum", getJSONArrayFromArray(metadataV1.dataDirsSha256Checksum));
            rootObject.put("mode", metadataV1.mode);
            rootObject.put("version", metadataV1.version);
            rootObject.put("apk_name", metadataV1.apkName);
            rootObject.put("instruction_set", metadataV1.instructionSet);
            fileOutputStream.write(rootObject.toString().getBytes());
        }
    }

    @NonNull
    private static JSONArray getJSONArrayFromArray(@NonNull final String[] stringArray) {
        JSONArray jsonArray = new JSONArray();
        for (String string: stringArray) jsonArray.put(string);
        return jsonArray;
    }

    @NonNull
    private static String[] getArrayFromJSONArray(@NonNull final JSONArray jsonArray) throws JSONException {
        String[] stringArray = new String[jsonArray.length()];
        for (int i = 0; i<jsonArray.length(); ++i) stringArray[i] = (String) jsonArray.get(i);
        return stringArray;
    }

    public MetadataV1 setupMetadata(@BackupStorageManager.BackupFlags int flags) throws PackageManager.NameNotFoundException {
        PackageManager pm = appManager.getPackageManager();
        int flagSigningInfo;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            flagSigningInfo = PackageManager.GET_SIGNING_CERTIFICATES;
        else flagSigningInfo = PackageManager.GET_SIGNATURES;
        @SuppressLint("PackageManagerGetSignatures")
        PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_META_DATA | flagSigningInfo);
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        metadataV1 = new MetadataV1();
        metadataV1.label = applicationInfo.loadLabel(pm).toString();
        metadataV1.packageName = packageName;
        metadataV1.versionName = packageInfo.versionName;
        metadataV1.versionCode = PackageUtils.getVersionCode(packageInfo);
        if ((flags & BackupStorageManager.BACKUP_APK) != 0)
            metadataV1.sourceDir = PackageUtils.getSourceDir(applicationInfo);
        else metadataV1.sourceDir = "";
        metadataV1.apkName = new File(applicationInfo.sourceDir).getName();
        if ((flags & BackupStorageManager.BACKUP_DATA) != 0) {
            metadataV1.dataDirs = PackageUtils.getDataDirs(applicationInfo,
                    (flags & BackupStorageManager.BACKUP_EXT_DATA) != 0);
        } else metadataV1.dataDirs = new  String[0];
        metadataV1.isSystem = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        metadataV1.isSplitApk = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            metadataV1.splitNames = applicationInfo.splitNames;
            if (metadataV1.splitNames != null) {
                metadataV1.isSplitApk = true;
                metadataV1.splitSources = applicationInfo.splitPublicSourceDirs;
            }
        }
        if (metadataV1.splitNames == null) metadataV1.splitNames = new String[0];
        if (metadataV1.splitSources == null) metadataV1.splitSources = new String[0];
        metadataV1.hasRules = false;
        if ((flags & BackupStorageManager.BACKUP_RULES) != 0) {
            try (ComponentsBlocker cb = ComponentsBlocker.getInstance(appManager, packageName)) {
                metadataV1.hasRules = cb.entryCount() > 0;
            }
        }
        metadataV1.backupTime = 0;
        metadataV1.certSha256Checksum = PackageUtils.getSigningCertSha256Checksum(packageInfo);
        // Initialize checksums
        metadataV1.sourceDirSha256Checksum = "";
        metadataV1.dataDirsSha256Checksum = new String[metadataV1.dataDirs.length];
        return metadataV1;
    }

    @NonNull
    private File getMetadataFile(boolean temporary) {
        if (temporary) return new File(BackupStorageManager.getTemporaryBackupPath(packageName), META_FILE);
        else return new File(BackupStorageManager.getBackupPath(packageName), META_FILE);
    }
}
