// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.explorer;

import android.app.Application;
import android.net.Uri;
import android.os.RemoteException;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.utils.FileUtils;

public class AppExplorerViewModel extends AndroidViewModel {
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final MutableLiveData<List<AdapterItem>> fmItems = new MutableLiveData<>();
    private Uri apkUri;
    private ApkFile apkFile;
    private File cachedFile;
    private ZipFile zipFile;
    private List<? extends ZipEntry> zipEntries;

    public AppExplorerViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
        FileUtils.closeQuietly(zipFile);
        FileUtils.closeQuietly(apkFile);
        FileUtils.deleteSilently(cachedFile);
    }

    public void setApkUri(Uri apkUri) {
        this.apkUri = apkUri;
    }

    @NonNull
    public String getName() {
        if (apkUri != null) return apkUri.getLastPathSegment();
        return "";
    }

    @AnyThread
    public void reload(String name, int depth) {
        loadFiles(name, depth);
    }

    @AnyThread
    public void loadFiles(@Nullable String name, int depth) {
        executor.submit(() -> {
            if (apkFile == null) {
                try {
                    int key = ApkFile.createInstance(apkUri, null);
                    apkFile = ApkFile.getInstance(key);
                    ApkFile.Entry baseEntry = apkFile.getBaseEntry();
                    cachedFile = baseEntry.getRealCachedFile();
                    zipFile = new ZipFile(cachedFile);
                    Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
                    this.zipEntries = Collections.list(zipEntries);
                } catch (ApkFile.ApkFileException | IOException | RemoteException e) {
                    this.fmItems.postValue(Collections.emptyList());
                    return;
                }
            }
            List<AdapterItem> adapterItems = new ArrayList<>();
            try {
                Set<AdapterItem> uniqueItems = new HashSet<>();
                for (ZipEntry zipEntry : zipEntries) {
                    if (name != null && !zipEntry.getName().startsWith(name + File.separatorChar)) {
                        continue;
                    }
                    uniqueItems.add(new AdapterItem(zipEntry, depth));
                }
                adapterItems.addAll(uniqueItems);
                Collections.sort(adapterItems);
            } finally {
                this.fmItems.postValue(adapterItems);
            }
        });
    }

    public LiveData<List<AdapterItem>> observeFiles() {
        return fmItems;
    }
}
