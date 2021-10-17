// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.explorer;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlDecoder;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.VirtualFileSystem;

public class AppExplorerViewModel extends AndroidViewModel {
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final MutableLiveData<List<AdapterItem>> fmItems = new MutableLiveData<>();
    private final MutableLiveData<Boolean> modificationObserver = new MutableLiveData<>();
    private final MutableLiveData<AdapterItem> openObserver = new MutableLiveData<>();
    private final MutableLiveData<Uri> uriChangeObserver = new MutableLiveData<>();
    private Uri apkUri;
    private ApkFile apkFile;
    private Path zipFileRoot;
    private boolean modified;
    private final List<Integer> vfsIds = new ArrayList<>();
    private final List<File> cachedFiles = new ArrayList<>();

    public AppExplorerViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
        // Unmount file systems
        for (int vsfId : vfsIds) {
            try {
                VirtualFileSystem.unmount(vsfId);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        FileUtils.closeQuietly(apkFile);
        for (File cachedFile : cachedFiles) {
            FileUtils.deleteSilently(cachedFile);
        }
    }

    public void setApkUri(Uri apkUri) {
        this.apkUri = apkUri;
    }

    public boolean isModified() {
        return modified;
    }

    @NonNull
    public String getName() {
        if (apkUri != null) return apkUri.getLastPathSegment();
        return "";
    }

    @AnyThread
    public void reload(Uri uri) {
        loadFiles(uri);
    }

    @AnyThread
    public void loadFiles(@Nullable Uri uri) {
        executor.submit(() -> {
            if (apkFile == null) {
                try {
                    int key = ApkFile.createInstance(apkUri, null);
                    apkFile = ApkFile.getInstance(key);
                    ApkFile.Entry baseEntry = apkFile.getBaseEntry();
                    File cachedFile = baseEntry.getRealCachedFile();
                    cachedFiles.add(cachedFile);
                    int vfsId = VirtualFileSystem.mount(new VirtualFileSystem.ZipFileSystem(apkUri, cachedFile));
                    vfsIds.add(vfsId);
                    zipFileRoot = VirtualFileSystem.getFsRoot(vfsId);
                } catch (Throwable e) {
                    e.printStackTrace();
                    this.fmItems.postValue(Collections.emptyList());
                    return;
                }
            }
            List<AdapterItem> adapterItems = new ArrayList<>();
            try {
                Path path;
                if (uri == null) {
                    // Null URI always means root of the zip file
                    path = zipFileRoot;
                } else {
                    path = new Path(AppManager.getContext(), uri);
                }
                for (Path child : path.listFiles()) {
                    adapterItems.add(new AdapterItem(child));
                }
                Collections.sort(adapterItems);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                this.fmItems.postValue(adapterItems);
            }
        });
    }

    @AnyThread
    public void cacheAndOpen(@NonNull AdapterItem item, boolean convertXml) {
        if (item.getCachedFile() != null || item.extension.equals("smali")) {
            // Already cached
            openObserver.postValue(item);
            return;
        }
        executor.submit(() -> {
            try (InputStream is = item.openInputStream()) {
                if (convertXml) {
                    byte[] fileBytes = IoUtils.readFully(is, -1, true);
                    ByteBuffer byteBuffer = ByteBuffer.wrap(fileBytes);
                    File cachedFile = FileUtils.getTempFile();
                    try (PrintStream ps = new PrintStream(cachedFile)) {
                        if (AndroidBinXmlDecoder.isBinaryXml(byteBuffer)) {
                            AndroidBinXmlDecoder.decode(byteBuffer, ps);
                        } else {
                            ps.write(fileBytes);
                        }
                        addCachedFile(item, cachedFile);
                    }
                } else {
                    addCachedFile(item, FileUtils.getCachedFile(is));
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
            openObserver.postValue(item);
        });
    }

    public void browseDexOrOpenExternal(@NonNull AdapterItem item) {
        executor.submit(() -> {
            if (VirtualFileSystem.getFileSystem(item.getUri()) != null) {
                uriChangeObserver.postValue(item.getUri());
                return;
            }
            try {
                int vfsId = VirtualFileSystem.mount(new VirtualFileSystem.DexFileSystem(item.getUri(), item.path));
                vfsIds.add(vfsId);
                uriChangeObserver.postValue(item.getUri());
            } catch (Throwable th) {
                th.printStackTrace();
                if (item.getCachedFile() != null) {
                    openObserver.postValue(item);
                    return;
                }
                try (InputStream is = new BufferedInputStream(item.openInputStream())) {
                    boolean isZipFile = FileUtils.isInputFileZip(is);
                    File cachedFile = FileUtils.getCachedFile(is);
                    addCachedFile(item, cachedFile);
                    if (isZipFile) {
                        int vfsId = VirtualFileSystem.mount(new VirtualFileSystem.ZipFileSystem(item.getUri(), cachedFile));
                        vfsIds.add(vfsId);
                        uriChangeObserver.postValue(item.getUri());
                    } else openObserver.postValue(item);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public LiveData<List<AdapterItem>> observeFiles() {
        return fmItems;
    }

    public LiveData<Boolean> observeModification() {
        return modificationObserver;
    }

    public LiveData<AdapterItem> observeOpen() {
        return openObserver;
    }

    public LiveData<Uri> observeUriChange() {
        return uriChangeObserver;
    }

    private void addCachedFile(@NonNull AdapterItem item, File file) {
        item.setCachedFile(new Path(AppManager.getContext(), file));
        cachedFiles.add(file);
    }
}
