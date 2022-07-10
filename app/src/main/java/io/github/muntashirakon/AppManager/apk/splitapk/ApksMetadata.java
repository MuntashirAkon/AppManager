// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.splitapk;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.PackageInfoCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class ApksMetadata {
    public static final String META_FILE = "info.json";
    public static final String ICON_FILE = "icon.png";

    public static class Dependency {
        public String packageName;
        public String displayName;
        public String versionName;
        public long versionCode;
        @Nullable
        public String[] signatures;
        public String match;
        public boolean required;
        @Nullable
        public String path;
    }

    public static class BuildInfo {
        public final long timestamp = System.currentTimeMillis();
        public final String builderId = BuildConfig.APPLICATION_ID;
        public final String builderLabel;
        public final String builderVersion = BuildConfig.VERSION_NAME;
        public final String platform = "android";

        public BuildInfo() {
            builderLabel = AppManager.getContext().getString(R.string.app_name);
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
    public final List<Dependency> dependencies = new ArrayList<>();

    private PackageInfo mPackageInfo;

    public ApksMetadata(PackageInfo packageInfo) {
        mPackageInfo = packageInfo;
    }

    public void readMetadata() {
        // TODO: 10/7/22
    }

    public void writeMetadata(@NonNull ZipOutputStream zipOutputStream) throws IOException {
        // Fetch meta
        ApplicationInfo applicationInfo = mPackageInfo.applicationInfo;
        packageName = mPackageInfo.packageName;
        displayName = applicationInfo.loadLabel(AppManager.getContext().getPackageManager()).toString();
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
                PackageManager pm = AppManager.getContext().getPackageManager();
                PackageInfo packageInfo = pm.getPackageArchiveInfo(file, PackageManager.GET_SHARED_LIBRARY_FILES);
                if (packageInfo == null) {
                    continue;
                }
                // Save as APKS first
                Path tempFile = Paths.get(FileUtils.getTempFile(".apks"));
                SplitApkExporter.saveApks(packageInfo, tempFile);
                String path = packageInfo.packageName + ApkUtils.EXT_APKS;
                SplitApkExporter.addFile(zipOutputStream, tempFile, path, exportTimestamp);
                // Add as dependency
                Dependency dependency = new Dependency();
                dependency.packageName = packageInfo.packageName;
                dependency.displayName = packageInfo.applicationInfo.loadLabel(pm).toString();
                dependency.versionName = packageInfo.versionName;
                dependency.versionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
                dependency.required = true;
                dependency.signatures = null;
                dependency.match = "exact";
                dependency.path = path;
                dependencies.add(dependency);
            }
        }
        // Write meta
        byte[] meta = getMetadata().getBytes(StandardCharsets.UTF_8);
        SplitApkExporter.addBytes(zipOutputStream, meta, ApksMetadata.META_FILE, exportTimestamp);
    }

    @NonNull
    public String getMetadata() {
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
