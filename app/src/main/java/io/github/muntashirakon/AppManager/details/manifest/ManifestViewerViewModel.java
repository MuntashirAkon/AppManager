// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.manifest;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlDecoder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.IoUtils;

public class ManifestViewerViewModel extends AndroidViewModel {
    public static final String TAG = ManifestViewerViewModel.class.getSimpleName();

    private ApkFile apkFile;
    @Nullable
    private Future<?> manifestLoaderResult;

    private final FileCache fileCache = new FileCache();
    private final MutableLiveData<Uri> manifestLiveData = new MutableLiveData<>();

    public ManifestViewerViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        if (manifestLoaderResult != null) {
            manifestLoaderResult.cancel(true);
        }
        IoUtils.closeQuietly(apkFile);
        IoUtils.closeQuietly(fileCache);
        super.onCleared();
    }

    public LiveData<Uri> getManifestLiveData() {
        return manifestLiveData;
    }

    public void loadApkFile(@Nullable Uri packageUri, @Nullable String type, @Nullable String packageName) {
        manifestLoaderResult = ThreadUtils.postOnBackgroundThread(() -> {
            final PackageManager pm = getApplication().getPackageManager();
            if (packageUri != null) {
                try {
                    int key = ApkFile.createInstance(packageUri, type);
                    apkFile = ApkFile.getInstance(key);
                    if (ThreadUtils.isInterrupted()) {
                        return;
                    }
                } catch (ApkFile.ApkFileException e) {
                    Log.e(TAG, "Error: ", e);
                    return;
                }
            } else {
                try {
                    ApplicationInfo applicationInfo = pm.getApplicationInfo(packageName, 0);
                    int key = ApkFile.createInstance(applicationInfo);
                    apkFile = ApkFile.getInstance(key);
                    if (ThreadUtils.isInterrupted()) {
                        return;
                    }
                } catch (PackageManager.NameNotFoundException | ApkFile.ApkFileException e) {
                    Log.e(TAG, "Error: ", e);
                }
            }
            if (apkFile != null) {
                ByteBuffer byteBuffer = apkFile.getBaseEntry().manifest;
                // Reset properties
                byteBuffer.position(0);
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                try {
                    File cachedFile = fileCache.createCachedFile("xml");
                    try (PrintStream ps = new PrintStream(cachedFile)) {
                        AndroidBinXmlDecoder.decode(byteBuffer, ps);
                    }
                    manifestLiveData.postValue(Uri.fromFile(cachedFile));
                } catch (Throwable e) {
                    Log.e(TAG, "Could not parse APK", e);
                }
            }
        });
    }
}
