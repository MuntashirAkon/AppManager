package io.github.muntashirakon.AppManager.apk.splitapk;

import android.content.pm.PackageInfo;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class ApksMetadata {
    public static final String META_V1_FILE = "meta.sai_v1.json";
    public static final String META_V2_FILE = "meta.sai_v2.json";
    public static final String ICON_FILE = "icon.png";

    public long exportTimestamp;
    public boolean splitApk = true;
    public String label;
    public long metaVersion = 2L;
    public long minSdk = 0L;
    public String packageName;  // package
    public long targetSdk;
    public long versionCode;
    public String versionName;
    List<BackupComponent> backupComponents;

    public static class BackupComponent {
        public long size;
        public String type;
        public BackupComponent(String type, long size) {
            this.type = type;
            this.size = size;
        }
    }

    public ApksMetadata(String metadataString) {
        // TODO:
        readMetadata();
    }

    PackageInfo packageInfo;
    public ApksMetadata(PackageInfo packageInfo) {
        this.packageInfo = packageInfo;
        setupMetadata();
    }

    public void readMetadata() {
        // TODO
    }

    public void setupMetadata() {
        packageName = packageInfo.packageName;
        label = packageInfo.applicationInfo.loadLabel(AppManager.getContext().getPackageManager()).toString();
        versionName = packageInfo.versionName;
        versionCode = PackageUtils.getVersionCode(packageInfo);
        exportTimestamp = 946684800000L;  // Fake time
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            minSdk = packageInfo.applicationInfo.minSdkVersion;
        targetSdk = packageInfo.applicationInfo.targetSdkVersion;
        splitApk = packageInfo.applicationInfo.splitPublicSourceDirs != null && packageInfo.applicationInfo.splitPublicSourceDirs.length > 0;
    }

    public String getMetadataV1() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("package", packageName);
            jsonObject.put("label", label);
            jsonObject.put("version_name", versionName);
            jsonObject.put("version_code", versionCode);
            jsonObject.put("export_timestamp", exportTimestamp);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject.toString();
    }

    public String getMetadataV2() {
        JSONObject jsonObject = new JSONObject();
        try {
            JSONArray jsonArrayBackupComponents = new JSONArray();
            for (BackupComponent component: backupComponents) {
                JSONObject componentObject = new JSONObject();
                componentObject.put("type", component.type);
                componentObject.put("size", component.size);
                jsonArrayBackupComponents.put(componentObject);
            }
            jsonObject.put("meta_version", metaVersion);
            jsonObject.put("package", packageName);
            jsonObject.put("label", label);
            jsonObject.put("version_name", versionName);
            jsonObject.put("version_code", versionCode);
            jsonObject.put("export_timestamp", exportTimestamp);
            jsonObject.put("min_sdk", minSdk);
            jsonObject.put("target_sdk", targetSdk);
            jsonObject.put("backup_components", jsonArrayBackupComponents);
            jsonObject.put("split_apk", splitApk);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject.toString();
    }
}
