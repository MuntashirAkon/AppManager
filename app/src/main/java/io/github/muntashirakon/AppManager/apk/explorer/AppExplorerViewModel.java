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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlDecoder;
import io.github.muntashirakon.AppManager.dex.DexUtils;
import io.github.muntashirakon.AppManager.fm.ContentType2;
import io.github.muntashirakon.AppManager.fm.FileType;
import io.github.muntashirakon.AppManager.fm.FmListOptions;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.misc.ListOptions;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.fs.VirtualFileSystem;

public class AppExplorerViewModel extends AndroidViewModel implements ListOptions.ListOptionActions {
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final MutableLiveData<List<AdapterItem>> fmItemsLiveData = new MutableLiveData<>();
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
    private final List<AdapterItem> fmItems = new ArrayList<>();
    @FmListOptions.SortOrder
    private int sortBy;
    private boolean reverseSort;
    @FmListOptions.Options
    private int selectedOptions;
    @Nullable
    private String queryString;

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

    @Override
    public void setSortBy(@FmListOptions.SortOrder int sortBy) {
        this.sortBy = sortBy;
        Prefs.FileManager.setSortOrder(sortBy);
        executor.submit(this::filterAndSort);
    }

    @FmListOptions.SortOrder
    @Override
    public int getSortBy() {
        return sortBy;
    }

    @Override
    public void setReverseSort(boolean reverseSort) {
        this.reverseSort = reverseSort;
        Prefs.FileManager.setReverseSort(reverseSort);
        executor.submit(this::filterAndSort);
    }

    @Override
    public boolean isReverseSort() {
        return reverseSort;
    }

    @Override
    public boolean isOptionSelected(@FmListOptions.Options int option) {
        return (selectedOptions & option) != 0;
    }

    @Override
    public void onOptionSelected(@FmListOptions.Options int option, boolean selected) {
        if (selected) selectedOptions |= option;
        else selectedOptions &= ~option;
        Prefs.FileManager.setOptions(selectedOptions);
        executor.submit(this::filterAndSort);
    }

    public void setQueryString(@Nullable String queryString) {
        this.queryString = queryString;
        executor.submit(this::filterAndSort);
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
                    this.fmItemsLiveData.postValue(Collections.emptyList());
                    return;
                }
            }
            Path path;
            if (uri == null) {
                // Null URI always means root of the zip file
                path = baseFsRoot;
            } else {
                path = Paths.get(uri);
            }
            synchronized (fmItems) {
                fmItems.clear();
                for (Path child : path.listFiles()) {
                    fmItems.add(new AdapterItem(child));
                }
            }
            filterAndSort();
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
        return fmItemsLiveData;
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

    private void filterAndSort() {
        boolean displayDotFiles = (selectedOptions & FmListOptions.OPTIONS_DISPLAY_DOT_FILES) != 0;
        boolean foldersOnTop = (selectedOptions & FmListOptions.OPTIONS_FOLDERS_FIRST) != 0;

        List<AdapterItem> filteredList;
        synchronized (fmItems) {
            if (!TextUtilsCompat.isEmpty(queryString)) {
                filteredList = AdvancedSearchView.matches(queryString, fmItems,
                        (AdvancedSearchView.ChoiceGenerator<AdapterItem>) object -> object.path.getName(),
                        AdvancedSearchView.SEARCH_TYPE_CONTAINS);
            } else filteredList = new ArrayList<>(fmItems);
        }
        if (!displayDotFiles) {
            Iterator<AdapterItem> iterator = filteredList.listIterator();
            while (iterator.hasNext()) {
                AdapterItem fmItem = iterator.next();
                if (fmItem.path.getName().startsWith(".")) {
                    iterator.remove();
                }
            }
        }
        // Sort by name first
        Collections.sort(filteredList, (o1, o2) -> o1.path.getName().compareToIgnoreCase(o2.path.getName()));
        // Other sorting options
        int inverse = reverseSort ? -1 : 1;
        Collections.sort(filteredList, (o1, o2) -> {
            int typeComp;
            if (!foldersOnTop) {
                typeComp = 0;
            } else if (o1.type == o2.type) {
                typeComp = 0;
            } else if (o1.type == FileType.DIRECTORY) {
                typeComp = -1 * inverse;
            } else typeComp = 1 * inverse;
            if (typeComp != 0 || sortBy == FmListOptions.SORT_BY_NAME) {
                return typeComp;
            }
            // Apply real sort
            Path p1 = o1.path;
            Path p2 = o2.path;
            if (sortBy == FmListOptions.SORT_BY_LAST_MODIFIED) {
                return -Long.compare(p1.lastModified(), p2.lastModified()) * inverse;
            }
            if (sortBy == FmListOptions.SORT_BY_SIZE) {
                return -Long.compare(p1.length(), p2.length()) * inverse;
            }
            if (sortBy == FmListOptions.SORT_BY_TYPE) {
                return p1.getType().compareToIgnoreCase(p2.getType()) * inverse;
            }
            return typeComp;
        });
        this.fmItemsLiveData.postValue(filteredList);
    }
}
