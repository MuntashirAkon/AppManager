// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.splitapk;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.core.content.pm.PackageInfoCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class ApksMetadata {
    public static final String TAG = ApksMetadata.class.getSimpleName();

    public static final String META_FILE = "info.json";
    public static final String ICON_FILE = "icon.png";

    public static class Dependency {
        public static final String DEPENDENCY_MATCH_EXACT = "exact";
        public static final String DEPENDENCY_MATCH_GREATER = "greater";
        public static final String DEPENDENCY_MATCH_LESS = "less";

        @StringDef({DEPENDENCY_MATCH_EXACT, DEPENDENCY_MATCH_GREATER, DEPENDENCY_MATCH_LESS})
        @Retention(RetentionPolicy.SOURCE)
        public @interface DependencyMatch {
        }

        public String packageName;
        public String displayName;
        public String versionName;
        public long versionCode;
        @Nullable
        public String[] signatures;
        @DependencyMatch
        public String match;
        public boolean required;
        @Nullable
        public String path;
    }

    public static class BuildInfo {
        public final long timestamp;
        public final String builderId;
        public final String builderLabel;
        public final String builderVersion;
        public final String platform;

        public BuildInfo() {
            timestamp = System.currentTimeMillis();
            builderId = BuildConfig.APPLICATION_ID;
            builderLabel = ContextUtils.getContext().getString(R.string.app_name);
            builderVersion = BuildConfig.VERSION_NAME;
            platform = "android";
        }

        public BuildInfo(long timestamp, String builderId, String builderLabel, String builderVersion, String platform) {
            this.timestamp = timestamp;
            this.builderId = builderId;
            this.builderLabel = builderLabel;
            this.builderVersion = builderVersion;
            this.platform = platform;
        }
    }

    public long exportTimestamp;
    public long metaVersion = 1L;
    public String packageName;
    public String displayName;
    public String versionName;
    public long versionCode;
    public long minSdk = 0L;
    public long targetSdk;
    public BuildInfo buildInfo;
    public final List<Dependency> dependencies = new ArrayList<>();

    private final PackageInfo mPackageInfo;

    public ApksMetadata() {
        mPackageInfo = null;
    }

    public ApksMetadata(PackageInfo packageInfo) {
        mPackageInfo = packageInfo;
    }

    public void readMetadata(String jsonString) throws JSONException {
        JSONObject jsonObject = new JSONObject(jsonString);
        metaVersion = jsonObject.getLong("info_version");
        packageName = jsonObject.getString("package_name");
        displayName = jsonObject.getString("display_name");
        versionName = jsonObject.getString("version_name");
        versionCode = jsonObject.getLong("version_code");
        minSdk = JSONUtils.getLong(jsonObject, "min_sdk", 0);
        targetSdk = jsonObject.getLong("target_sdk");
        // Build info
        JSONObject buildInfoObject = JSONUtils.getJSONObject(jsonObject, "build_info");
        if (buildInfoObject != null) {
            buildInfo = new BuildInfo(buildInfoObject.getLong("timestamp"),
                    buildInfoObject.getString("builder_id"),
                    buildInfoObject.getString("builder_label"),
                    buildInfoObject.getString("builder_version"),
                    buildInfoObject.getString("platform"));
        }
        // Dependencies
        JSONArray dependencyInfoArray = JSONUtils.getJSONArray(jsonObject, "dependencies");
        if (dependencyInfoArray != null) {
            for (int i = 0; i < dependencyInfoArray.length(); ++i) {
                JSONObject dependencyInfoObject = dependencyInfoArray.getJSONObject(i);
                Dependency dependency = new Dependency();
                dependency.packageName = dependencyInfoObject.getString("package_name");
                dependency.displayName = dependencyInfoObject.getString("display_name");
                dependency.versionName = dependencyInfoObject.getString("version_name");
                dependency.versionCode = dependencyInfoObject.getLong("version_code");
                String signatures = JSONUtils.getString(dependencyInfoObject, "signature", null);
                if (signatures != null) {
                    dependency.signatures = signatures.split(",");
                }
                dependency.match = dependencyInfoObject.getString("match");
                dependency.required = dependencyInfoObject.getBoolean("required");
                dependency.path = JSONUtils.getString(dependencyInfoObject, "path", null);
                dependencies.add(dependency);
            }
        }
    }

    public void writeMetadata(@NonNull ZipOutputStream zipOutputStream) throws IOException {
        // Fetch meta
        PackageManager pm = ContextUtils.getContext().getPackageManager();
        ApplicationInfo applicationInfo = mPackageInfo.applicationInfo;
        packageName = mPackageInfo.packageName;
        displayName = applicationInfo.loadLabel(pm).toString();
        versionName = mPackageInfo.versionName;
        versionCode = PackageInfoCompat.getLongVersionCode(mPackageInfo);
        exportTimestamp = 946684800000L;  // Fake time
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            minSdk = applicationInfo.minSdkVersion;
        }
        targetSdk = applicationInfo.targetSdkVersion;
        String[] sharedLibraries = applicationInfo.sharedLibraryFiles;
        if (sharedLibraries != null) {
            for (String file : sharedLibraries) {
                if (!file.endsWith(".apk")) {
                    continue;
                }
                PackageInfo packageInfo = pm.getPackageArchiveInfo(file, PackageManager.GET_SHARED_LIBRARY_FILES);
                if (packageInfo == null) {
                    Log.w(TAG, "Could not fetch package info for file %s", file);
                    continue;
                }
                if (packageInfo.applicationInfo.sourceDir == null) {
                    packageInfo.applicationInfo.sourceDir = file;
                }
                if (packageInfo.applicationInfo.publicSourceDir == null) {
                    packageInfo.applicationInfo.publicSourceDir = file;
                }
                // Save as APKS first
                File tempFile = FileCache.getGlobalFileCache().createCachedFile("apks");
                try {
                    Path tempPath = Paths.get(tempFile);
                    SplitApkExporter.saveApks(packageInfo, tempPath);
                    String path = packageInfo.packageName + ApkUtils.EXT_APKS;
                    SplitApkExporter.addFile(zipOutputStream, tempPath, path, exportTimestamp);
                    // Add as dependency
                    Dependency dependency = new Dependency();
                    dependency.packageName = packageInfo.packageName;
                    dependency.displayName = packageInfo.applicationInfo.loadLabel(pm).toString();
                    dependency.versionName = packageInfo.versionName;
                    dependency.versionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
                    dependency.required = true;
                    dependency.signatures = null;
                    dependency.match = Dependency.DEPENDENCY_MATCH_EXACT;
                    dependency.path = path;
                    dependencies.add(dependency);
                } finally {
                    FileCache.getGlobalFileCache().delete(tempFile);
                }
            }
        }
        // Write meta
        byte[] meta = getMetadataAsJson().getBytes(StandardCharsets.UTF_8);
        SplitApkExporter.addBytes(zipOutputStream, meta, ApksMetadata.META_FILE, exportTimestamp);
    }

    @NonNull
    public String getMetadataAsJson() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("info_version", metaVersion);
            jsonObject.put("package_name", packageName);
            jsonObject.put("display_name", displayName);
            jsonObject.put("version_name", versionName);
            jsonObject.put("version_code", versionCode);
            jsonObject.put("min_sdk", minSdk);
            jsonObject.put("target_sdk", targetSdk);
            // Skip build info for privacy
            // Put dependencies
            JSONArray dependenciesArray = new JSONArray();
            for (Dependency dependency : dependencies) {
                JSONObject dependencyObject = new JSONObject();
                dependencyObject.put("package_name", dependency.packageName);
                dependencyObject.put("display_name", dependency.displayName);
                dependencyObject.put("version_name", dependency.versionName);
                dependencyObject.put("version_code", dependency.versionCode);
                if (dependency.signatures != null) {
                    dependencyObject.put("signature", TextUtils.join(",", dependency.signatures));
                }
                dependencyObject.put("match", dependency.match);
                dependencyObject.put("required", dependency.required);
                if (dependency.path != null) {
                    dependencyObject.put("path", dependency.path);
                }
                dependenciesArray.put(dependencyObject);
            }
            if (dependenciesArray.length() > 0) {
                jsonObject.put("dependencies", dependenciesArray);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject.toString();
    }
}
