// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import android.net.Uri;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import io.github.muntashirakon.AppManager.history.JsonDeserializer;

public class UriApkSource extends ApkSource {
    public static final String TAG = UriApkSource.class.getSimpleName();

    @NonNull
    private final Uri mUri;
    @Nullable
    private final String mMimeType;

    private int mApkFileKey;

    public UriApkSource(@NonNull Uri uri, @Nullable String mimeType) {
        mUri = Objects.requireNonNull(uri);
        mMimeType = mimeType;
    }

    @NonNull
    @Override
    public ApkFile resolve() throws ApkFile.ApkFileException {
        ApkFile apkFile = ApkFile.getInstance(mApkFileKey);
        if (apkFile != null && !apkFile.isClosed()) {
            // Usable past instance
            return apkFile;
        }
        mApkFileKey = ApkFile.createInstance(mUri, mMimeType);
        return Objects.requireNonNull(ApkFile.getInstance(mApkFileKey));
    }

    @NonNull
    @Override
    public ApkSource toCachedSource() {
        return new CachedApkSource(mUri, mMimeType);
    }

    protected UriApkSource(@NonNull Parcel in) {
        mUri = Objects.requireNonNull(ParcelCompat.readParcelable(in, Uri.class.getClassLoader(), Uri.class));
        mMimeType = in.readString();
        mApkFileKey = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mUri, flags);
        dest.writeString(mMimeType);
        dest.writeInt(mApkFileKey);
    }

    protected UriApkSource(@NonNull JSONObject jsonObject) throws JSONException {
        mUri = Uri.parse(jsonObject.getString("uri"));
        mMimeType = jsonObject.getString("mime_type");
        mApkFileKey = jsonObject.getInt("apk_file_key");
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tag", TAG);
        jsonObject.put("uri", mUri.toString());
        jsonObject.put("mime_type", mMimeType);
        jsonObject.put("apk_file_key", mApkFileKey);
        return jsonObject;
    }

    public static final JsonDeserializer.Creator<UriApkSource> DESERIALIZER = UriApkSource::new;

    public static final Creator<UriApkSource> CREATOR = new Creator<UriApkSource>() {
        @Override
        public UriApkSource createFromParcel(Parcel source) {
            return new UriApkSource(source);
        }

        @Override
        public UriApkSource[] newArray(int size) {
            return new UriApkSource[size];
        }
    };
}