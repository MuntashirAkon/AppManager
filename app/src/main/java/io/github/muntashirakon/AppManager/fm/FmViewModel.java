// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.annotation.SuppressLint;
import android.app.Application;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.util.Pair;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.j256.simplemagic.ContentType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.dex.DexUtils;
import io.github.muntashirakon.AppManager.fm.icons.FmIconFetcher;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.misc.ListOptions;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.fs.VirtualFileSystem;
import io.github.muntashirakon.lifecycle.SingleLiveEvent;

public class FmViewModel extends AndroidViewModel implements ListOptions.ListOptionActions {
    private final Object sizeLock = new Object();
    private final MutableLiveData<List<FmItem>> fmItemsLiveData = new MutableLiveData<>();
    private final MutableLiveData<FolderShortInfo> folderShortInfoLiveData = new MutableLiveData<>();
    private final MutableLiveData<Uri> uriLiveData = new MutableLiveData<>();
    private final MutableLiveData<Uri> lastUriLiveData = new MutableLiveData<>();
    private final MutableLiveData<Uri> displayPropertiesLiveData = new MutableLiveData<>();
    private final SingleLiveEvent<Pair<Path, Bitmap>> shortcutCreatorLiveData = new SingleLiveEvent<>();
    private final List<FmItem> fmItems = new ArrayList<>();
    private final HashMap<Uri, Integer> pathScrollPositionMap = new HashMap<>();
    private FmActivity.Options options;
    private Uri currentUri;
    @FmListOptions.SortOrder
    private int sortBy;
    private boolean reverseSort;
    @FmListOptions.Options
    private int selectedOptions;
    @Nullable
    private String queryString;
    @Nullable
    private Future<?> fmFileLoaderResult;
    private Future<?> fmFileSystemLoaderResult;
    // These are for VFS
    private Integer vfsId;
    private File cachedFile;
    private Path baseFsRoot;
    private final FileCache fileCache = new FileCache();

    public FmViewModel(@NonNull Application application) {
        super(application);
        sortBy = Prefs.FileManager.getSortOrder();
        reverseSort = Prefs.FileManager.isReverseSort();
        selectedOptions = Prefs.FileManager.getOptions();
    }

    @Override
    protected void onCleared() {
        // Ensure that file loader no longer doing anything
        if (fmFileLoaderResult != null) {
            fmFileLoaderResult.cancel(true);
        }
        if (fmFileSystemLoaderResult != null) {
            fmFileSystemLoaderResult.cancel(true);
        }
        // Clear VFS related data
        if (vfsId != null) {
            try {
                VirtualFileSystem.unmount(vfsId);
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
        ThreadUtils.postOnBackgroundThread(this::filterAndSort);
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
        ThreadUtils.postOnBackgroundThread(this::filterAndSort);
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
        ThreadUtils.postOnBackgroundThread(this::filterAndSort);
    }

    public void setQueryString(@Nullable String queryString) {
        this.queryString = queryString;
        ThreadUtils.postOnBackgroundThread(this::filterAndSort);
    }

    @MainThread
    public void setOptions(@NonNull FmActivity.Options options, @Nullable Uri defaultUri) {
        if (fmFileLoaderResult != null) {
            fmFileLoaderResult.cancel(true);
        }
        if (fmFileSystemLoaderResult != null) {
            fmFileSystemLoaderResult.cancel(true);
        }
        this.options = options;
        if (!options.isVfs) {
            // No need to mount anything. Options#uri is the base URI
            loadFiles(defaultUri != null ? defaultUri : options.uri);
            return;
        }
        // Need to mount the file system
        fmFileSystemLoaderResult = ThreadUtils.postOnBackgroundThread(() -> {
            try {
                handleOptions();
                // Now load files
                ThreadUtils.postOnMainThread(() -> loadFiles(defaultUri != null ? defaultUri : baseFsRoot.getUri()));
            } catch (IOException e) {
                e.printStackTrace();
                this.fmItemsLiveData.postValue(Collections.emptyList());
            }
        });
    }

    public FmActivity.Options getOptions() {
        return options;
    }

    public Uri getCurrentUri() {
        return currentUri;
    }

    public void setScrollPosition(Uri uri, int currentScrollPosition) {
        pathScrollPositionMap.put(uri, currentScrollPosition);
    }

    public int getCurrentScrollPosition() {
        Integer scrollPosition = pathScrollPositionMap.get(currentUri);
        return scrollPosition != null ? scrollPosition : 0;
    }

    @MainThread
    public void reload() {
        if (options != null && currentUri != null) {
            loadFiles(currentUri);
        }
    }

    @SuppressLint("WrongThread")
    @MainThread
    public void loadFiles(@NonNull Uri uri) {
        if (fmFileLoaderResult != null) {
            fmFileLoaderResult.cancel(true);
        }
        Uri lastUri = currentUri;
        // Send last URI
        lastUriLiveData.setValue(lastUri);
        currentUri = uri;
        Path currentPath = Paths.get(uri);
        while (currentPath.isSymbolicLink()) {
            try {
                Path realPath = currentPath.getRealPath();
                if (realPath == null) {
                    // Not a symbolic link
                    break;
                }
                currentPath = realPath;
                currentUri = realPath.getUri();
            } catch (IOException ignore) {
                // Since we couldn't resolve the path, try currentPath instead
            }
        }
        Path path = currentPath;
        fmFileLoaderResult = ThreadUtils.postOnBackgroundThread(() -> {
            // Send current URI
            uriLiveData.postValue(currentUri);
            if (!path.isDirectory()) return;
            Path[] children = path.listFiles();
            FolderShortInfo folderShortInfo = new FolderShortInfo();
            int count = children.length;
            int folderCount = 0;
            synchronized (fmItems) {
                fmItems.clear();
                for (Path child : children) {
                    if (child.isDirectory()) {
                        ++folderCount;
                    }
                    fmItems.add(new FmItem(child));
                }
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
            }
            folderShortInfo.folderCount = folderCount;
            folderShortInfo.fileCount = count - folderCount;
            folderShortInfo.canRead = path.canRead();
            folderShortInfo.canWrite = path.canWrite();
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            // Send folder info for the first time
            folderShortInfoLiveData.postValue(folderShortInfo);
            // Run filter and sorting options for fmItems
            filterAndSort();
            synchronized (sizeLock) {
                // Calculate size and send folder info again
                folderShortInfo.size = Paths.size(path);
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
                folderShortInfoLiveData.postValue(folderShortInfo);
            }
        });
    }

    public void createShortcut(@NonNull FmItem fmItem) {
        ThreadUtils.postOnBackgroundThread(() -> {
            Bitmap bitmap = ImageLoader.getInstance().getCachedImage(fmItem.tag);
            if (bitmap == null) {
                ImageLoader.ImageFetcherResult result = new FmIconFetcher(fmItem).fetchImage(fmItem.tag);
                bitmap = result.bitmap != null ? result.bitmap : result.defaultImage.getImage();
            }
            shortcutCreatorLiveData.postValue(new Pair<>(fmItem.path, bitmap));
        });
    }

    public void createShortcut(@NonNull Uri uri) {
        createShortcut(new FmItem(Paths.get(uri)));
    }

    public LiveData<List<FmItem>> getFmItemsLiveData() {
        return fmItemsLiveData;
    }

    public LiveData<Uri> getUriLiveData() {
        return uriLiveData;
    }

    public LiveData<FolderShortInfo> getFolderShortInfoLiveData() {
        return folderShortInfoLiveData;
    }

    public LiveData<Uri> getLastUriLiveData() {
        return lastUriLiveData;
    }

    public MutableLiveData<Uri> getDisplayPropertiesLiveData() {
        return displayPropertiesLiveData;
    }

    public SingleLiveEvent<Pair<Path, Bitmap>> getShortcutCreatorLiveData() {
        return shortcutCreatorLiveData;
    }

    private void filterAndSort() {
        boolean displayDotFiles = (selectedOptions & FmListOptions.OPTIONS_DISPLAY_DOT_FILES) != 0;
        boolean foldersOnTop = (selectedOptions & FmListOptions.OPTIONS_FOLDERS_FIRST) != 0;

        List<FmItem> filteredList;
        synchronized (fmItems) {
            if (!TextUtilsCompat.isEmpty(queryString)) {
                filteredList = AdvancedSearchView.matches(queryString, fmItems,
                        (AdvancedSearchView.ChoiceGenerator<FmItem>) object -> object.path.getName(),
                        AdvancedSearchView.SEARCH_TYPE_CONTAINS);
            } else filteredList = new ArrayList<>(fmItems);
        }
        if (ThreadUtils.isInterrupted()) {
            return;
        }
        if (!displayDotFiles) {
            Iterator<FmItem> iterator = filteredList.listIterator();
            while (iterator.hasNext()) {
                FmItem fmItem = iterator.next();
                if (fmItem.path.getName().startsWith(".")) {
                    iterator.remove();
                }
            }
        }
        if (ThreadUtils.isInterrupted()) {
            return;
        }
        // Sort by name first
        Collections.sort(filteredList, (o1, o2) -> o1.path.getName().compareToIgnoreCase(o2.path.getName()));
        if (sortBy == FmListOptions.SORT_BY_NAME) {
            if (reverseSort) {
                Collections.reverse(filteredList);
            }
        } else {
            // Other sorting options
            int inverse = reverseSort ? -1 : 1;
            Collections.sort(filteredList, (o1, o2) -> {
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
                return 0;
            });
        }
        if (foldersOnTop) {
            // Folders should be on top
            Collections.sort(filteredList, (o1, o2) -> {
                if (o1.type == o2.type) {
                    return 0;
                }
                if (o1.type == FileType.DIRECTORY) {
                    return -1;
                }
                if (o2.type == FileType.DIRECTORY) {
                    return 1;
                }
                return 0;
            });
        }
        if (ThreadUtils.isInterrupted()) {
            return;
        }
        this.fmItemsLiveData.postValue(filteredList);
    }

    @WorkerThread
    private void handleOptions() throws IOException {
        if (!options.isVfs) {
            return;
        }
        if (cachedFile == null) {
            // TODO: 31/5/23 Handle read-only
            Path filePath = Paths.get(options.uri);
            cachedFile = fileCache.getCachedFile(filePath);
            Path cachedPath = Paths.get(cachedFile);
            if (FileUtils.isZip(cachedPath)) {
                vfsId = VirtualFileSystem.mount(filePath.getUri(), cachedPath, ContentType.ZIP.getMimeType());
            } else if (DexUtils.isDex(cachedPath)) {
                vfsId = VirtualFileSystem.mount(filePath.getUri(), cachedPath, ContentType2.DEX.getMimeType());
            } else {
                vfsId = VirtualFileSystem.mount(filePath.getUri(), cachedPath, cachedPath.getType());
            }
            VirtualFileSystem fs = VirtualFileSystem.getFileSystem(vfsId);
            if (fs == null) {
                throw new IOException("Could not mount " + options.uri);
            }
            baseFsRoot = fs.getRootPath();
        }
    }
}
