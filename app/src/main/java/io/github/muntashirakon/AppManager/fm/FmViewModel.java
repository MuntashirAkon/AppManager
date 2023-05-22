// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.annotation.SuppressLint;
import android.app.Application;
import android.net.Uri;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.misc.ListOptions;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class FmViewModel extends AndroidViewModel implements ListOptions.ListOptionActions {
    private final Object sizeLock = new Object();
    private final MutableLiveData<List<FmItem>> fmItemsLiveData = new MutableLiveData<>();
    private final MutableLiveData<FolderShortInfo> folderShortInfoLiveData = new MutableLiveData<>();
    private final MutableLiveData<Uri> uriLiveData = new MutableLiveData<>();
    private final MutableLiveData<Uri> lastUriLiveData = new MutableLiveData<>();
    private final List<FmItem> fmItems = new ArrayList<>();
    private final HashMap<Uri, Integer> pathScrollPositionMap = new HashMap<>();
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

    public FmViewModel(@NonNull Application application) {
        super(application);
        sortBy = Prefs.FileManager.getSortOrder();
        reverseSort = Prefs.FileManager.isReverseSort();
        selectedOptions = Prefs.FileManager.getOptions();
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

    public void reload() {
        if (currentUri != null) {
            loadFiles(currentUri);
        }
    }

    @SuppressLint("WrongThread")
    @AnyThread
    public void loadFiles(@NonNull Uri uri) {
        if (fmFileLoaderResult != null) {
            fmFileLoaderResult.cancel(true);
        }
        Uri lastUri = currentUri;
        // Send last URI
        if (ThreadUtils.isMainThread()) {
            lastUriLiveData.setValue(currentUri);
        } else {
            lastUriLiveData.postValue(lastUri);
        }
        currentUri = uri;
        fmFileLoaderResult = ThreadUtils.postOnBackgroundThread(() -> {
            Path path = Paths.get(uri);
            if (!path.isDirectory()) return;
            // Send current URI as it is approved
            uriLiveData.postValue(uri);
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
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
            }
            folderShortInfo.folderCount = folderCount;
            folderShortInfo.fileCount = count - folderCount;
            folderShortInfo.canRead = path.canRead();
            folderShortInfo.canWrite = path.canWrite();
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            // Send folder info for the first time
            folderShortInfoLiveData.postValue(folderShortInfo);
            // Run filter and sorting options for fmItems
            filterAndSort();
            synchronized (sizeLock) {
                // Calculate size and send folder info again
                folderShortInfo.size = Paths.size(path);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                folderShortInfoLiveData.postValue(folderShortInfo);
            }
        });
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
        if (Thread.currentThread().isInterrupted()) {
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
        if (Thread.currentThread().isInterrupted()) {
            return;
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
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        this.fmItemsLiveData.postValue(filteredList);
    }
}
