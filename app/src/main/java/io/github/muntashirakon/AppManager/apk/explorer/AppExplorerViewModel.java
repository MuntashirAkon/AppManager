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

import com.j256.simplemagic.ContentType;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlDecoder;
import io.github.muntashirakon.AppManager.dex.DexUtils;
import io.github.muntashirakon.AppManager.fm.ContentType2;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.fs.VirtualFileSystem;

public class AppExplorerViewModel extends AndroidViewModel {
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final MutableLiveData<List<AdapterItem>> fmItems = new MutableLiveData<>();
    private final MutableLiveData<Boolean> modificationObserver = new MutableLiveData<>();
    private final MutableLiveData<AdapterItem> openObserver = new MutableLiveData<>();
    private final MutableLiveData<Uri> uriChangeObserver = new MutableLiveData<>();
    private Path filePath;
    private File cachedFile;
    private Path baseFsRoot;
    private String baseType;
    private boolean modified;
    private final List<Integer> vfsIds = new ArrayList<>();
    private final FileCache fileCache = new FileCache();

    public AppExplorerViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
        // Unmount file systems
        for (int vsfId : vfsIds) {
            try {
                VirtualFileSystem.unmount(vsfId);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        IoUtils.closeQuietly(fileCache);
        super.onCleared();
    }

    public void setUri(Uri apkUri) {
        this.filePath = Paths.get(apkUri);
    }

    public boolean isModified() {
        return modified;
    }

    @NonNull
    public String getName() {
        if (filePath != null) return filePath.getName();
        return "";
    }

    @AnyThread
    public void reload(Uri uri) {
        loadFiles(uri);
    }

    @AnyThread
    public void loadFiles(@Nullable Uri uri) {
        executor.submit(() -> {
            if (cachedFile == null) {
                try {
                    cachedFile = fileCache.getCachedFile(filePath);
                    Path cachedPath = Paths.get(cachedFile);
                    int vfsId;
                    if (FileUtils.isZip(cachedPath)) {
                        vfsId = VirtualFileSystem.mount(filePath.getUri(), cachedPath, ContentType.ZIP.getMimeType());
                    } else if (DexUtils.isDex(cachedPath)) {
                        vfsId = VirtualFileSystem.mount(filePath.getUri(), cachedPath, ContentType2.DEX.getMimeType());
                    } else {
                        vfsId = VirtualFileSystem.mount(filePath.getUri(), cachedPath, cachedPath.getType());
                    }
                    VirtualFileSystem fs = VirtualFileSystem.getFileSystem(vfsId);
                    if (fs == null) {
                        throw new IOException("Could not mount " + uri);
                    }
                    vfsIds.add(vfsId);
                    baseFsRoot = fs.getRootPath();
                    baseType = fs.getType();
                } catch (Throwable e) {
                    e.printStackTrace();
                    this.fmItems.postValue(Collections.emptyList());
                    return;
                }
            }
            List<AdapterItem> adapterItems = new ArrayList<>();
            Path path;
            if (uri == null) {
                // Null URI always means root of the zip file
                path = baseFsRoot;
            } else {
                path = Paths.get(uri);
            }
            for (Path child : path.listFiles()) {
                adapterItems.add(new AdapterItem(child));
            }
            Collections.sort(adapterItems);
            this.fmItems.postValue(adapterItems);
        });
    }

    @AnyThread
    public void cacheAndOpen(@NonNull AdapterItem item, boolean convertXml) {
        if (item.getCachedFile() != null || "smali".equals(item.path.getExtension())) {
            // Already cached
            openObserver.postValue(item);
            return;
        }
        executor.submit(() -> {
            if (convertXml) {
                try (InputStream is = item.openInputStream()) {
                    byte[] fileBytes = IoUtils.readFully(is, -1, true);
                    ByteBuffer byteBuffer = ByteBuffer.wrap(fileBytes);
                    File cachedFile = fileCache.createCachedFile(item.path.getExtension());
                    try (PrintStream ps = new PrintStream(cachedFile)) {
                        if (AndroidBinXmlDecoder.isBinaryXml(byteBuffer)) {
                            AndroidBinXmlDecoder.decode(byteBuffer, ps);
                        } else {
                            ps.write(fileBytes);
                        }
                        item.setCachedFile(Paths.get(cachedFile));
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    item.setCachedFile(Paths.get(fileCache.getCachedFile(item.path)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
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
                int vfsId = VirtualFileSystem.mount(item.getUri(), item.path, ContentType2.DEX.getMimeType());
                vfsIds.add(vfsId);
                uriChangeObserver.postValue(item.getUri());
            } catch (Throwable th) {
                th.printStackTrace();
                if (item.getCachedFile() != null) {
                    openObserver.postValue(item);
                    return;
                }
                try {
                    File cachedFile = fileCache.getCachedFile(item.path);
                    item.setCachedFile(Paths.get(cachedFile));
                    try (InputStream is = new BufferedInputStream(item.openInputStream())) {
                        boolean isZipFile = FileUtils.isZip(is);
                        if (isZipFile) {
                            int vfsId = VirtualFileSystem.mount(item.getUri(), Paths.get(cachedFile), ContentType.ZIP.getMimeType());
                            vfsIds.add(vfsId);
                            uriChangeObserver.postValue(item.getUri());
                        } else openObserver.postValue(item);
                    }
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
}
