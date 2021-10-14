// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.explorer;

import android.app.Application;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.RemoteException;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

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
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlDecoder;
import io.github.muntashirakon.AppManager.scanner.DexClasses;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;

public class AppExplorerViewModel extends AndroidViewModel {
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final MutableLiveData<List<AdapterItem>> fmItems = new MutableLiveData<>();
    private final MutableLiveData<Boolean> modificationObserver = new MutableLiveData<>();
    private final MutableLiveData<AdapterItem> openObserver = new MutableLiveData<>();
    private final MutableLiveData<Uri> uriChangeObserver = new MutableLiveData<>();
    private Uri apkUri;
    private ApkFile apkFile;
    private File cachedFile;
    private ZipFile zipFile;
    private Path zipFileRoot;
    private Path dexFileRoot;
    private boolean modified;

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
                    cachedFile = baseEntry.getRealCachedFile();
                    zipFile = new ZipFile(cachedFile);
                    zipFileRoot = new Path(AppManager.getContext(), zipFile, null);
                } catch (ApkFile.ApkFileException | IOException | RemoteException e) {
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
                    String relativePath = uri.getPath();
                    switch (uri.getScheme()) {
                        default:
                        case "zip":
                            // Browsing the zip file
                            path = relativePath.equals(File.separator) ? zipFileRoot : zipFileRoot.findFile(uri.getPath());
                            break;
                        case "dex":
                            // Browsing a dex file
                            path = relativePath.equals(File.separator) ? dexFileRoot : dexFileRoot.findFile(uri.getPath());
                            break;
                        case ContentResolver.SCHEME_FILE:
                            // Browsing regular files
                            path = new Path(AppManager.getContext(), new File(uri.getPath()));
                            break;
                        case ContentResolver.SCHEME_CONTENT:
                            // Browsing SAF
                            path = new Path(AppManager.getContext(), uri);
                    }
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
        if (item.getCachedFile() != null) {
            // Already cached
            openObserver.postValue(item);
            return;
        }
        executor.submit(() -> {
            try (InputStream is = item.path.openInputStream()) {
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
                        item.setCachedFile(new Path(AppManager.getContext(), cachedFile));
                    }
                } else {
                    item.setCachedFile(new Path(AppManager.getContext(), FileUtils.getCachedFile(is)));
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
            openObserver.postValue(item);
        });
    }

    public void browseDexOrOpenExternal(@NonNull AdapterItem item) {
        executor.submit(() -> {
            try (InputStream is = new BufferedInputStream(item.path.openInputStream())) {
                dexFileRoot = new Path(AppManager.getContext(), new DexClasses(is), null);
                uriChangeObserver.postValue(dexFileRoot.getUri());
            } catch (Throwable th) {
                th.printStackTrace();
                if (item.getCachedFile() != null) {
                    openObserver.postValue(item);
                    return;
                }
                try (InputStream is = item.path.openInputStream()) {
                    // TODO: 15/10/21 Could be a zip file
                    item.setCachedFile(new Path(AppManager.getContext(), FileUtils.getCachedFile(is)));
                    openObserver.postValue(item);
                } catch (IOException e) {
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
