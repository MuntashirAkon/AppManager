// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.misc.ListOptions;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class FmViewModel extends AndroidViewModel implements ListOptions.ListOptionActions {
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final MutableLiveData<List<FmItem>> fmItemsLiveData = new MutableLiveData<>();
    private final List<FmItem> fmItems = new ArrayList<>();
    private Path currentPath;
    @FmListOptions.SortOrder
    private int sortBy;
    private boolean reverseSort;
    @FmListOptions.Options
    private int selectedOptions;
    @Nullable
    private String queryString;

    public FmViewModel(@NonNull Application application) {
        super(application);
        sortBy = Prefs.FileManager.getSortOrder();
        reverseSort = Prefs.FileManager.isReverseSort();
        selectedOptions = Prefs.FileManager.getOptions();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
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

    @AnyThread
    public boolean hasParent() {
        if (currentPath != null) {
            return currentPath.getParentFile() != null;
        }
        return false;
    }

    @AnyThread
    public void loadFiles(Uri uri) {
        Path path = Paths.get(uri);
        loadFiles(path);
    }

    public void reload() {
        if (currentPath != null) {
            loadFiles(currentPath);
        }
    }

    @AnyThread
    private void loadFiles(Path path) {
        currentPath = path;
        executor.submit(() -> {
            if (!path.isDirectory()) return;
            Path[] children = path.listFiles();
            synchronized (fmItems) {
                fmItems.clear();
                for (Path child : children) {
                    fmItems.add(new FmItem(child));
                }
            }
            filterAndSort();
        });
    }

    public LiveData<List<FmItem>> observeFiles() {
        return fmItemsLiveData;
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
        if (!displayDotFiles) {
            Iterator<FmItem> iterator = filteredList.listIterator();
            while (iterator.hasNext()) {
                FmItem fmItem = iterator.next();
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
