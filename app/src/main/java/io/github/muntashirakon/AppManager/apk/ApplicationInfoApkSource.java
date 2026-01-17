// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.core.os.ParcelCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Objects;

import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

public class ApplicationInfoApkSource extends ApkSource {
    public static final String TAG = ApplicationInfoApkSource.class.getSimpleName();

    @NonNull
    private final ApplicationInfo mApplicationInfo;

    private int mApkFileKey;

    ApplicationInfoApkSource(@NonNull ApplicationInfo applicationInfo) {
        mApplicationInfo = Objects.requireNonNull(applicationInfo);
    }

    @NonNull
    @Override
    public ApkFile resolve() throws ApkFile.ApkFileException {
        ApkFile apkFile = ApkFile.getInstance(mApkFileKey);
        if (apkFile != null && !apkFile.isClosed()) {
            // Usable past instance
            return apkFile;
        }
        mApkFileKey = ApkFile.createInstance(mApplicationInfo);
        return Objects.requireNonNull(ApkFile.getInstance(mApkFileKey));
    }

    @NonNull
    @Override
    public ApkSource toCachedSource() {
        return new CachedApkSource(Uri.fromFile(new File(mApplicationInfo.publicSourceDir)),
                "application/vnd.android.package-archive");
    }

    protected ApplicationInfoApkSource(@NonNull Parcel in) {
        mApplicationInfo = Objects.requireNonNull(ParcelCompat.readParcelable(in,
                ApplicationInfo.class.getClassLoader(), ApplicationInfo.class));
        mApkFileKey = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mApplicationInfo, flags);
        dest.writeInt(mApkFileKey);
    }

    protected ApplicationInfoApkSource(@NonNull JSONObject jsonObject) throws JSONException {
        PackageManager pm = ContextUtils.getContext().getPackageManager();
        String file = jsonObject.getString("file");
        PackageInfo packageInfo = Objects.requireNonNull(pm.getPackageArchiveInfo(file, 0));
        mApplicationInfo = Objects.requireNonNull(packageInfo.applicationInfo);
        mApplicationInfo.publicSourceDir = mApplicationInfo.sourceDir = file;
        mApkFileKey = jsonObject.getInt("apk_file_key");
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tag", TAG);
        jsonObject.put("file", mApplicationInfo.publicSourceDir);
        jsonObject.put("apk_file_key", mApkFileKey);
        return jsonObject;
    }

    public static final JsonDeserializer.Creator<ApplicationInfoApkSource> DESERIALIZER = ApplicationInfoApkSource::new;

    public static final Creator<ApplicationInfoApkSource> CREATOR = new Creator<ApplicationInfoApkSource>() {
        @Override
        public ApplicationInfoApkSource createFromParcel(Parcel source) {
            return new ApplicationInfoApkSource(source);
        }

        @Override
        public ApplicationInfoApkSource[] newArray(int size) {
            return new ApplicationInfoApkSource[size];
        }
    };
}
