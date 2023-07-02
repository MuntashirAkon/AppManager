// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.annotation.SuppressLint;
import android.app.Application;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.TextUtils;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.dex.DexUtils;
import io.github.muntashirakon.AppManager.fm.icons.FmIconFetcher;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.misc.ListOptions;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.fs.VirtualFileSystem;
import io.github.muntashirakon.lifecycle.SingleLiveEvent;

public class FmViewModel extends AndroidViewModel implements ListOptions.ListOptionActions {
    private final Object mSizeLock = new Object();
    private final MutableLiveData<List<FmItem>> mFmItemsLiveData = new MutableLiveData<>();
    private final MutableLiveData<FolderShortInfo> mFolderShortInfoLiveData = new MutableLiveData<>();
    private final MutableLiveData<Uri> mUriLiveData = new MutableLiveData<>();
    private final MutableLiveData<Uri> mLastUriLiveData = new MutableLiveData<>();
    private final MutableLiveData<Uri> mDisplayPropertiesLiveData = new MutableLiveData<>();
    private final SingleLiveEvent<Pair<Path, Bitmap>> mShortcutCreatorLiveData = new SingleLiveEvent<>();
    private final SingleLiveEvent<SharableItems> mSharableItemsLiveData = new SingleLiveEvent<>();
    private final List<FmItem> mFmItems = new ArrayList<>();
    private final Set<Path> mSelectedItems = Collections.synchronizedSet(new LinkedHashSet<>());
    private final HashMap<Uri, Integer> mPathScrollPositionMap = new HashMap<>();
    private FmActivity.Options mOptions;
    private Uri mCurrentUri;
    @FmListOptions.SortOrder
    private int mSortBy;
    private boolean mReverseSort;
    @FmListOptions.Options
    private int mSelectedOptions;
    @Nullable
    private String mQueryString;
    @Nullable
    private String mScrollToFilename;
    @Nullable
    private Future<?> mFmFileLoaderResult;
    private Future<?> mFmFileSystemLoaderResult;
    // These are for VFS
    private Integer mVfsId;
    private File mCachedFile;
    private Path mBaseFsRoot;
    private final FileCache mFileCache = new FileCache();

    public FmViewModel(@NonNull Application application) {
        super(application);
        mSortBy = Prefs.FileManager.getSortOrder();
        mReverseSort = Prefs.FileManager.isReverseSort();
        mSelectedOptions = Prefs.FileManager.getOptions();
    }

    @Override
    protected void onCleared() {
        // Ensure that file loader no longer doing anything
        if (mFmFileLoaderResult != null) {
            mFmFileLoaderResult.cancel(true);
        }
        if (mFmFileSystemLoaderResult != null) {
            mFmFileSystemLoaderResult.cancel(true);
        }
        // Clear VFS related data
        if (mVfsId != null) {
            try {
                VirtualFileSystem.unmount(mVfsId);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        IoUtils.closeQuietly(mFileCache);
        super.onCleared();
    }

    @Override
    public void setSortBy(@FmListOptions.SortOrder int sortBy) {
        mSortBy = sortBy;
        Prefs.FileManager.setSortOrder(sortBy);
        ThreadUtils.postOnBackgroundThread(this::filterAndSort);
    }

    @FmListOptions.SortOrder
    @Override
    public int getSortBy() {
        return mSortBy;
    }

    @Override
    public void setReverseSort(boolean reverseSort) {
        mReverseSort = reverseSort;
        Prefs.FileManager.setReverseSort(reverseSort);
        ThreadUtils.postOnBackgroundThread(this::filterAndSort);
    }

    @Override
    public boolean isReverseSort() {
        return mReverseSort;
    }

    @Override
    public boolean isOptionSelected(@FmListOptions.Options int option) {
        return (mSelectedOptions & option) != 0;
    }

    @Override
    public void onOptionSelected(@FmListOptions.Options int option, boolean selected) {
        if (selected) mSelectedOptions |= option;
        else mSelectedOptions &= ~option;
        Prefs.FileManager.setOptions(mSelectedOptions);
        ThreadUtils.postOnBackgroundThread(this::filterAndSort);
    }

    public void setQueryString(@Nullable String queryString) {
        mQueryString = queryString;
        ThreadUtils.postOnBackgroundThread(this::filterAndSort);
    }

    @MainThread
    public void setOptions(@NonNull FmActivity.Options options, @Nullable Uri defaultUri) {
        if (mFmFileLoaderResult != null) {
            mFmFileLoaderResult.cancel(true);
        }
        if (mFmFileSystemLoaderResult != null) {
            mFmFileSystemLoaderResult.cancel(true);
        }
        mOptions = options;
        if (!options.isVfs) {
            // No need to mount anything. Options#uri is the base URI
            loadFiles(defaultUri != null ? defaultUri : options.uri);
            return;
        }
        // Need to mount the file system
        mFmFileSystemLoaderResult = ThreadUtils.postOnBackgroundThread(() -> {
            try {
                handleOptions();
                // Now load files
                ThreadUtils.postOnMainThread(() -> loadFiles(defaultUri != null ? defaultUri : mBaseFsRoot.getUri()));
            } catch (IOException e) {
                e.printStackTrace();
                mFmItemsLiveData.postValue(Collections.emptyList());
            }
        });
    }

    public FmActivity.Options getOptions() {
        return mOptions;
    }

    public Uri getCurrentUri() {
        return mCurrentUri;
    }

    public void setScrollPosition(Uri uri, int currentScrollPosition) {
        mPathScrollPositionMap.put(uri, currentScrollPosition);
    }

    public int getCurrentScrollPosition() {
        Integer scrollPosition = mPathScrollPositionMap.get(mCurrentUri);
        return scrollPosition != null ? scrollPosition : 0;
    }

    public List<Path> getSelectedItems() {
        return new ArrayList<>(mSelectedItems);
    }

    @Nullable
    public Path getLastSelectedItem() {
        // Last selected item is the same as the last added item.
        Iterator<Path> it = mSelectedItems.iterator();
        Path lastItem = null;
        while (it.hasNext()) {
            lastItem = it.next();
        }
        return lastItem;
    }

    public int getSelectedItemCount() {
        return mSelectedItems.size();
    }

    public void setSelectedItem(@NonNull Path path, boolean select) {
        if (select) {
            mSelectedItems.add(path);
        } else {
            mSelectedItems.remove(path);
        }
    }

    public boolean isSelected(@NonNull Path path) {
        return mSelectedItems.contains(path);
    }

    public void clearSelections() {
        mSelectedItems.clear();
    }

    @MainThread
    public void reload() {
        reload(null);
    }

    @MainThread
    public void reload(@Nullable String scrollToFilename) {
        if (mOptions != null && mCurrentUri != null) {
            loadFiles(mCurrentUri, scrollToFilename);
        }
    }

    @MainThread
    public void loadFiles(@NonNull Uri uri) {
        loadFiles(uri, null);
    }

    @SuppressLint("WrongThread")
    @MainThread
    public void loadFiles(@NonNull Uri uri, @Nullable String scrollToFilename) {
        if (mFmFileLoaderResult != null) {
            mFmFileLoaderResult.cancel(true);
        }
        mScrollToFilename = scrollToFilename;
        Uri lastUri = mCurrentUri;
        // Send last URI
        mLastUriLiveData.setValue(lastUri);
        mCurrentUri = uri;
        Path currentPath = Paths.get(uri);
        while (currentPath.isSymbolicLink()) {
            try {
                Path realPath = currentPath.getRealPath();
                if (realPath == null) {
                    // Not a symbolic link
                    break;
                }
                currentPath = realPath;
                mCurrentUri = realPath.getUri();
            } catch (IOException ignore) {
                // Since we couldn't resolve the path, try currentPath instead
            }
        }
        Path path = currentPath;
        mFmFileLoaderResult = ThreadUtils.postOnBackgroundThread(() -> {
            // Send current URI
            mUriLiveData.postValue(mCurrentUri);
            if (!path.isDirectory()) return;
            Path[] children = path.listFiles();
            FolderShortInfo folderShortInfo = new FolderShortInfo();
            int count = children.length;
            int folderCount = 0;
            synchronized (mFmItems) {
                mFmItems.clear();
                for (Path child : children) {
                    if (child.isDirectory()) {
                        ++folderCount;
                    }
                    mFmItems.add(new FmItem(child));
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
            mFolderShortInfoLiveData.postValue(folderShortInfo);
            // Run filter and sorting options for fmItems
            filterAndSort();
            synchronized (mSizeLock) {
                // Calculate size and send folder info again
                folderShortInfo.size = Paths.size(path);
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
                mFolderShortInfoLiveData.postValue(folderShortInfo);
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
            mShortcutCreatorLiveData.postValue(new Pair<>(fmItem.path, bitmap));
        });
    }

    public void shareFiles(@NonNull List<Path> pathList) {
        ThreadUtils.postOnBackgroundThread(() -> {
            SharableItems sharableItems = new SharableItems(pathList);
            mSharableItemsLiveData.postValue(sharableItems);
        });
    }

    public void createShortcut(@NonNull Uri uri) {
        createShortcut(new FmItem(Paths.get(uri)));
    }

    public LiveData<List<FmItem>> getFmItemsLiveData() {
        return mFmItemsLiveData;
    }

    public LiveData<Uri> getUriLiveData() {
        return mUriLiveData;
    }

    public LiveData<FolderShortInfo> getFolderShortInfoLiveData() {
        return mFolderShortInfoLiveData;
    }

    public LiveData<Uri> getLastUriLiveData() {
        return mLastUriLiveData;
    }

    public MutableLiveData<Uri> getDisplayPropertiesLiveData() {
        return mDisplayPropertiesLiveData;
    }

    public LiveData<Pair<Path, Bitmap>> getShortcutCreatorLiveData() {
        return mShortcutCreatorLiveData;
    }

    public LiveData<SharableItems> getSharableItemsLiveData() {
        return mSharableItemsLiveData;
    }

    private void filterAndSort() {
        boolean displayDotFiles = (mSelectedOptions & FmListOptions.OPTIONS_DISPLAY_DOT_FILES) != 0;
        boolean foldersOnTop = (mSelectedOptions & FmListOptions.OPTIONS_FOLDERS_FIRST) != 0;

        List<FmItem> filteredList;
        synchronized (mFmItems) {
            if (!TextUtils.isEmpty(mQueryString)) {
                filteredList = AdvancedSearchView.matches(mQueryString, mFmItems,
                        (AdvancedSearchView.ChoiceGenerator<FmItem>) object -> object.path.getName(),
                        AdvancedSearchView.SEARCH_TYPE_CONTAINS);
            } else filteredList = new ArrayList<>(mFmItems);
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
        if (mSortBy == FmListOptions.SORT_BY_NAME) {
            if (mReverseSort) {
                Collections.reverse(filteredList);
            }
        } else {
            // Other sorting options
            int inverse = mReverseSort ? -1 : 1;
            Collections.sort(filteredList, (o1, o2) -> {
                Path p1 = o1.path;
                Path p2 = o2.path;
                if (mSortBy == FmListOptions.SORT_BY_LAST_MODIFIED) {
                    return -Long.compare(p1.lastModified(), p2.lastModified()) * inverse;
                }
                if (mSortBy == FmListOptions.SORT_BY_SIZE) {
                    return -Long.compare(p1.length(), p2.length()) * inverse;
                }
                if (mSortBy == FmListOptions.SORT_BY_TYPE) {
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
        if (mScrollToFilename != null) {
            for (int i = 0; i < filteredList.size(); ++i) {
                if (mScrollToFilename.equals(filteredList.get(i).path.getName())) {
                    setScrollPosition(mCurrentUri, i);
                    break;
                }
            }
            mScrollToFilename = null;
        }
        mFmItemsLiveData.postValue(filteredList);
    }

    @WorkerThread
    private void handleOptions() throws IOException {
        if (!mOptions.isVfs) {
            return;
        }
        if (mCachedFile == null) {
            // TODO: 31/5/23 Handle read-only
            Path filePath = Paths.get(mOptions.uri);
            mCachedFile = mFileCache.getCachedFile(filePath);
            Path cachedPath = Paths.get(mCachedFile);
            if (FileUtils.isZip(cachedPath)) {
                mVfsId = VirtualFileSystem.mount(filePath.getUri(), cachedPath, ContentType.ZIP.getMimeType());
            } else if (DexUtils.isDex(cachedPath)) {
                mVfsId = VirtualFileSystem.mount(filePath.getUri(), cachedPath, ContentType2.DEX.getMimeType());
            } else {
                mVfsId = VirtualFileSystem.mount(filePath.getUri(), cachedPath, cachedPath.getType());
            }
            VirtualFileSystem fs = VirtualFileSystem.getFileSystem(mVfsId);
            if (fs == null) {
                throw new IOException("Could not mount " + mOptions.uri);
            }
            mBaseFsRoot = fs.getRootPath();
        }
    }
}
