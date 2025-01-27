// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Parcelable;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

public abstract class ApkSource implements Parcelable, IJsonSerializer {
    @NonNull
    public static ApkSource getApkSource(@NonNull Uri uri, @Nullable String mimeType) {
        return new UriApkSource(uri, mimeType);
    }

    @NonNull
    public static ApkSource getCachedApkSource(@NonNull Uri uri, @Nullable String mimeType) {
        return new CachedApkSource(uri, mimeType);
    }

    @NonNull
    public static ApkSource getApkSource(@NonNull ApplicationInfo applicationInfo) {
        return new ApplicationInfoApkSource(applicationInfo);
    }

    @AnyThread
    @NonNull
    public abstract ApkFile resolve() throws ApkFile.ApkFileException;

    @AnyThread
    @NonNull
    public abstract ApkSource toCachedSource();

    public static final JsonDeserializer.Creator<ApkSource> DESERIALIZER = jsonObject -> {
        String tag = JSONUtils.getString(jsonObject, "tag");
        if (ApplicationInfoApkSource.TAG.equals(tag)) {
            return ApplicationInfoApkSource.DESERIALIZER.deserialize(jsonObject);
        } else if (CachedApkSource.TAG.equals(tag)) {
            return CachedApkSource.DESERIALIZER.deserialize(jsonObject);
        } else if (UriApkSource.TAG.equals(tag)) {
            return UriApkSource.DESERIALIZER.deserialize(jsonObject);
        } else throw new JSONException("Invalid tag: " + tag);
    };
}