// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Parcelable;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class ApkSource implements Parcelable {
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
}