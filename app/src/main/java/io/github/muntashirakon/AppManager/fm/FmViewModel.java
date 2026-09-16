// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
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

import java.io.FileNotFoundException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.dex.DexUtils;
import io.github.muntashirakon.AppManager.db.entity.FmDirectorySort;
import io.github.muntashirakon.AppManager.db.entity.FmDirectorySize;
import io.github.muntashirakon.AppManager.fm.icons.FmIconFetcher;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.misc.ListOptions;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.AlphanumComparator;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.PathAttributes;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.fs.VirtualFileSystem;
import io.github.muntashirakon.lifecycle.SingleLiveEvent;

public class FmViewModel extends AndroidViewModel implements ListOptions.ListOptionActions {
    public static final String TAG = FmViewModel.class.getSimpleName();

    private final Object mSizeLock = new Object();
    private final MutableLiveData<List<FmItem>> mFmItemsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Throwable> mFmErrorLiveData = new MutableLiveData<>();
    private final MutableLiveData<FolderShortInfo> mFolderShortInfoLiveData = new MutableLiveData<>();
    private final MutableLiveData<Uri> mUriLiveData = new MutableLiveData<>();
    private final MutableLiveData<Uri> mLastUriLiveData = new MutableLiveData<>();
    private final MutableLiveData<Uri> mDisplayPropertiesLiveData = new MutableLiveData<>();
    private final SingleLiveEvent<Pair<Path, Bitmap>> mShortcutCreatorLiveData = new SingleLiveEvent<>();
    private final SingleLiveEvent<SharableItems> mSharableItemsLiveData = new SingleLiveEvent<>();
    private final List<FmItem> mFmItems = new ArrayList<>();
    private final AtomicLong mLoadGeneration = new AtomicLong();
    private final Set<Path> mSelectedItems = Collections.synchronizedSet(new LinkedHashSet<>());
    private final HashMap<Uri, Integer> mPathScrollPositionMap = new HashMap<>();
    private FmActivity.Options mOptions;
    private Uri mCurrentUri;
    @FmListOptions.SortOrder
    private int mSortBy;
    private boolean mReverseSort;
    private boolean mFolderOnly;
    private boolean mOptionsOpen;
    private int mGlobalSortByWhenOptionsOpened;
    private boolean mGlobalReverseSortWhenOptionsOpened;
    @FmListOptions.Options
    private int mSelectedOptions;
    @Nullable
    private String mQueryString;
    @Nullable
    private Future<?> mFmFileLoaderResult;
    private Future<?> mFmFileSystemLoaderResult;
    private final Set<Integer> mVfsIdSet = new HashSet<>();
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
        for (int vfsId : mVfsIdSet) {
            ExUtils.exceptionAsIgnored(() -> VirtualFileSystem.unmount(vfsId));
        }
        IoUtils.closeQuietly(mFileCache);
        super.onCleared();
    }

    @Override
    public void setSortBy(@FmListOptions.SortOrder int sortBy) {
        mSortBy = sortBy;
        if (!mOptionsOpen) {
            if (mFolderOnly && mCurrentUri != null) {
                saveFolderSort();
            } else {
                Prefs.FileManager.setSortOrder(sortBy);
            }
        }
        ThreadUtils.postOnBackgroundThread(() -> filterAndSort());
    }

    @FmListOptions.SortOrder
    @Override
    public int getSortBy() {
        return mSortBy;
    }

    @Override
    public void setReverseSort(boolean reverseSort) {
        mReverseSort = reverseSort;
        if (!mOptionsOpen) {
            if (mFolderOnly && mCurrentUri != null) {
                saveFolderSort();
            } else {
                Prefs.FileManager.setReverseSort(reverseSort);
            }
        }
        ThreadUtils.postOnBackgroundThread(() -> filterAndSort());
    }

    @Override
    public boolean supportsFolderOnly() {
        return true;
    }

    @Override
    public void onOptionsOpened() {
        mOptionsOpen = true;
        mGlobalSortByWhenOptionsOpened = Prefs.FileManager.getSortOrder();
        mGlobalReverseSortWhenOptionsOpened = Prefs.FileManager.isReverseSort();
    }

    @Override
    public void onOptionsClosed() {
        if (!mOptionsOpen) return;
        mOptionsOpen = false;
        Uri currentUri = mCurrentUri;
        if (currentUri == null) return;
        int sortBy = mSortBy;
        boolean reverseSort = mReverseSort;
        boolean folderOnly = mFolderOnly;
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                Path directory = Paths.getStrict(currentUri);
                if (folderOnly) {
                    FmDirectorySettings.saveSort(directory, sortBy, reverseSort);
                } else {
                    FmDirectorySettings.deleteSort(directory);
                    Prefs.FileManager.setSortOrder(sortBy);
                    Prefs.FileManager.setReverseSort(reverseSort);
                }
            } catch (IOException e) {
                Log.w(TAG, "Could not save sorting options: %s", e);
            }
        });
    }

    @Override
    public boolean isFolderOnly() {
        return mFolderOnly;
    }

    @Override
    public void setFolderOnly(boolean folderOnly) {
        if (mCurrentUri == null) {
            return;
        }
        mFolderOnly = folderOnly;
        if (!folderOnly && mOptionsOpen) {
            mSortBy = mGlobalSortByWhenOptionsOpened;
            mReverseSort = mGlobalReverseSortWhenOptionsOpened;
            ThreadUtils.postOnBackgroundThread(() -> filterAndSort());
        }
        if (!mOptionsOpen) {
            onOptionsClosed();
        }
    }

    private void saveFolderSort() {
        Uri currentUri = mCurrentUri;
        if (currentUri == null) return;
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                FmDirectorySettings.saveSort(Paths.getStrict(currentUri), mSortBy, mReverseSort);
            } catch (IOException e) {
                Log.w(TAG, "Could not save folder-only sorting: %s", e);
            }
        });
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
        ThreadUtils.postOnBackgroundThread(() -> filterAndSort());
    }

    public void setQueryString(@Nullable String queryString) {
        mQueryString = queryString;
        ThreadUtils.postOnBackgroundThread(() -> filterAndSort());
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
        if (!options.isVfs()) {
            // No need to mount anything. Options#uri is the base URI
            loadFiles(defaultUri != null ? defaultUri : options.uri, null, false);
            return;
        }
        // Need to mount the file system
        mFmFileSystemLoaderResult = ThreadUtils.postOnBackgroundThread(() -> {
            try {
                VirtualFileSystem fs = mountVfs();
                int vfsId = fs.getFsId();
                mVfsIdSet.add(vfsId);
                // vfs ID/authority has altered
                Uri newUri;
                if (defaultUri != null) {
                    newUri = defaultUri.buildUpon().authority(String.valueOf(vfsId)).build();
                } else newUri = fs.getRootPath().getUri();
                // Now load files
                ThreadUtils.postOnMainThread(() -> loadFiles(newUri, null, false));
            } catch (IOException e) {
                handleError(e, mOptions.uri);
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
        Log.d(TAG, "Store: Scroll position = %d, uri = %s", currentScrollPosition, uri);
        mPathScrollPositionMap.put(uri, currentScrollPosition);
    }

    public int getCurrentScrollPosition() {
        Integer scrollPosition = mPathScrollPositionMap.get(mCurrentUri);
        Log.d(TAG, "Load: Scroll position = %d, uri = %s", scrollPosition, mCurrentUri);
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
            loadFiles(mCurrentUri, scrollToFilename, true);
        }
    }

    @MainThread
    public void loadFiles(@NonNull Uri uri) {
        if (mCurrentUri != null) {
            // May need to reload options
            if (!Objects.equals(mCurrentUri.getScheme(), uri.getScheme())
                    || !Objects.equals(mCurrentUri.getAuthority(), uri.getAuthority())) {
                updateOptions(uri);
                return;
            }
        }
        loadFiles(uri, null, false);
    }

    @MainThread
    private void updateOptions(@NonNull Uri refUri) {
        FmActivity.Options options = new FmActivity.Options(refUri);
        setOptions(options, null);
    }

    @SuppressLint("WrongThread")
    @MainThread
    private void loadFiles(@NonNull Uri uri, @Nullable String scrollToFilename, boolean forceSizeRefresh) {
        long loadGeneration = mLoadGeneration.incrementAndGet();
        if (mFmFileLoaderResult != null) {
            mFmFileLoaderResult.cancel(true);
        }
        Uri lastUri = mCurrentUri;
        // Send last URI
        mLastUriLiveData.setValue(lastUri);
        mCurrentUri = uri;
        Path currentPath;
        try {
            currentPath = Paths.getStrict(uri);
        } catch (IOException e) {
            handleError(e, uri);
            return;
        }
        while (currentPath.isSymbolicLink()) {
            try {
                Path realPath = currentPath.getRealPath();
                if (realPath == null || realPath.equals(currentPath)) {
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
        Uri currentUri = mCurrentUri;
        mFmFileLoaderResult = ThreadUtils.postOnBackgroundThread(() -> {
            if (!isCurrentLoad(loadGeneration)) {
                return;
            }
            loadDirectorySort(path);
            if (!path.isDirectory()) {
                IOException e;
                if (path.exists()) {
                    e = new FileNotFoundException(getApplication().getString(R.string.path_not_a_folder, path.getName()));
                } else {
                    e = new IOException(getApplication().getString(R.string.path_does_not_exist, path.getName()));
                }
                handleError(e, currentUri, loadGeneration);
                return;
            }
            // Send current URI
            ThreadUtils.postOnMainThread(() -> {
                if (isCurrentLoad(loadGeneration)) {
                    mUriLiveData.setValue(currentUri);
                }
            });
            long s, e;
            boolean isSaf = ContentResolver.SCHEME_CONTENT.equals(currentUri.getScheme());
            FolderShortInfo folderShortInfo = new FolderShortInfo();
            FmDirectorySize cachedSize = null;
            try {
                cachedSize = FmDirectorySettings.getSize(path);
                if (cachedSize != null) {
                    folderShortInfo.size = cachedSize.sizeBytes;
                }
            } catch (Throwable ex) {
                Log.w(TAG, "Could not load cached folder size: %s", ex);
            }
            int folderCount = 0;
            List<FmItem> loadedItems = new ArrayList<>();
            if (isSaf) {
                    // SAF needs special handling to retrieve children
                    s = System.currentTimeMillis();
                    ContentResolver resolver = getApplication().getContentResolver();
                    Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(currentUri,
                            DocumentsContract.getDocumentId(currentUri));
                    Cursor c = null;
                    try {
                        c = resolver.query(childrenUri, null, null, null, null);
                        if (c == null) {
                            throw new IOException("Could not query directory: " + currentUri);
                        }
                        String[] columns = c.getColumnNames();
                        boolean hasDocumentId = false;
                        for (String column : columns) {
                            if (DocumentsContract.Document.COLUMN_DOCUMENT_ID.equals(column)) {
                                hasDocumentId = true;
                                break;
                            }
                        }
                        if (!hasDocumentId) {
                            throw new IOException("Directory query has no document ID column: " + currentUri);
                        }
                        while (c.moveToNext()) {
                            String documentId = null;
                            for (int i = 0; i < columns.length; ++i) {
                                if (DocumentsContract.Document.COLUMN_DOCUMENT_ID.equals(columns[i])) {
                                    documentId = c.getString(i);
                                }
                            }
                            if (documentId == null) {
                                // Invalid document, probably loading still?
                                continue;
                            }
                            Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(currentUri, documentId);
                            Path child = Paths.getTreeDocument(path, documentUri);
                            PathAttributes attributes = Paths.getAttributesFromSafTreeCursor(documentUri, c);
                            FmItem fmItem = new FmItem(child, attributes);
                            loadedItems.add(fmItem);
                            if (fmItem.isDirectory) {
                                ++folderCount;
                            }
                            if (ThreadUtils.isInterrupted() || !isCurrentLoad(loadGeneration)) {
                                return;
                            }
                        }
                        e = System.currentTimeMillis();
                        Log.d(TAG, "Time to fetch files via SAF: %d ms", e - s);
                    } catch (Exception ex) {
                        Log.w(TAG, "Failed query: %s", ex);
                        handleError(ex, currentUri, loadGeneration);
                        return;
                    } finally {
                        IoUtils.closeQuietly(c);
                    }
                } else {
                    s = System.currentTimeMillis();
                    Path[] children = path.listFiles();
                    e = System.currentTimeMillis();
                    Log.d(TAG, "Time to list files: %d ms", e - s);
                    s = System.currentTimeMillis();
                    for (Path child : children) {
                        FmItem fmItem = new FmItem(child);
                        loadedItems.add(fmItem);
                        if (fmItem.isDirectory) {
                            ++folderCount;
                        }
                        if (ThreadUtils.isInterrupted() || !isCurrentLoad(loadGeneration)) {
                            return;
                        }
                    }
                    e = System.currentTimeMillis();
                    Log.d(TAG, "Time to process file list: %d ms", e - s);
                }
            if (!isCurrentLoad(loadGeneration)) {
                return;
            }
            synchronized (mFmItems) {
                if (!isCurrentLoad(loadGeneration)) {
                    return;
                }
                mFmItems.clear();
                mFmItems.addAll(loadedItems);
            }
            folderShortInfo.folderCount = folderCount;
            folderShortInfo.fileCount = loadedItems.size() - folderCount;
            folderShortInfo.canRead = path.canRead();
            folderShortInfo.canWrite = path.canWrite();
            if (ThreadUtils.isInterrupted() || !isCurrentLoad(loadGeneration)) {
                return;
            }
            // Send folder info for the first time
            FolderShortInfo initialFolderShortInfo = new FolderShortInfo(folderShortInfo);
            ThreadUtils.postOnMainThread(() -> {
                if (isCurrentLoad(loadGeneration)) {
                    mFolderShortInfoLiveData.setValue(initialFolderShortInfo);
                }
            });
            // Run filter and sorting options for fmItems
            s = System.currentTimeMillis();
            filterAndSort(loadGeneration, currentUri, scrollToFilename);
            e = System.currentTimeMillis();
            Log.d(TAG, "Time to sort files: %d ms", e - s);
            synchronized (mSizeLock) {
                // Recalculate only when there is no recent cache, or when reload()
                // explicitly indicates that the directory contents changed.
                if (forceSizeRefresh || cachedSize == null || !FmDirectorySettings.isSizeFresh(
                        cachedSize, System.currentTimeMillis())) {
                    try {
                        folderShortInfo.size = Paths.size(path);
                        try {
                            FmDirectorySettings.saveSize(path, folderShortInfo.size, System.currentTimeMillis());
                        } catch (Throwable ex) {
                            Log.w(TAG, "Could not save cached folder size: %s", ex);
                        }
                    } catch (Throwable ex) {
                        Log.w(TAG, "Could not calculate folder size: %s", ex);
                    }
                }
                if (ThreadUtils.isInterrupted() || !isCurrentLoad(loadGeneration)) {
                    return;
                }
                ThreadUtils.postOnMainThread(() -> {
                    if (isCurrentLoad(loadGeneration)) {
                        mFolderShortInfoLiveData.setValue(folderShortInfo);
                    }
                });
            }
        });
    }

    private void loadDirectorySort(@NonNull Path directory) {
        try {
            FmDirectorySort settings = FmDirectorySettings.getSort(directory);
            if (settings != null) {
                mSortBy = settings.sortOrder;
                mReverseSort = settings.reverseSort;
                mFolderOnly = true;
                return;
            }
        } catch (Throwable e) {
            Log.w(TAG, "Could not load folder-only sorting: %s", e);
        }
        boolean downloads = isDownloadsDirectory(directory);
        mSortBy = downloads ? FmListOptions.SORT_BY_LAST_MODIFIED : Prefs.FileManager.getSortOrder();
        mReverseSort = downloads ? false : Prefs.FileManager.isReverseSort();
        mFolderOnly = false;
    }

    private static boolean isDownloadsDirectory(@NonNull Path directory) {
        Uri uri = directory.getUri();
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme()) && uri.getPath() != null) {
            try {
                String downloads = new File(Environment.getExternalStorageDirectory(),
                        Environment.DIRECTORY_DOWNLOADS).getCanonicalPath();
                return downloads.equals(new File(uri.getPath()).getCanonicalPath());
            } catch (IOException ignored) {
                return false;
            }
        }
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            try {
                String documentId = DocumentsContract.getDocumentId(uri);
                return documentId != null && (documentId.equalsIgnoreCase("primary:Download")
                        || documentId.equalsIgnoreCase("primary:Downloads"));
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        return false;
    }

    public void addToFavorite(@NonNull Path path, @NonNull FmActivity.Options options) {
        ThreadUtils.postOnBackgroundThread(() -> {
            FmFavoritesManager.addToFavorite(path, options);
        });
    }

    public void createShortcut(@NonNull FmItem fmItem) {
        ThreadUtils.postOnBackgroundThread(() -> {
            Bitmap bitmap = ImageLoader.getInstance().getCachedImage(fmItem.getTag());
            if (bitmap == null) {
                ImageLoader.ImageFetcherResult result = new FmIconFetcher(fmItem).fetchImage(fmItem.getTag());
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

    public LiveData<Throwable> getFmErrorLiveData() {
        return mFmErrorLiveData;
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

    private void handleError(@NonNull Throwable th, @NonNull Uri currentUri) {
        FolderShortInfo folderShortInfo = new FolderShortInfo();
        if (ThreadUtils.isMainThread()) {
            mUriLiveData.setValue(currentUri);
            mFolderShortInfoLiveData.setValue(folderShortInfo);
            mFmErrorLiveData.setValue(th);
        } else {
            mUriLiveData.postValue(currentUri);
            mFolderShortInfoLiveData.postValue(folderShortInfo);
            mFmErrorLiveData.postValue(th);
        }
    }

    private void handleError(@NonNull Throwable th, @NonNull Uri currentUri, long loadGeneration) {
        ThreadUtils.postOnMainThread(() -> {
            if (!isCurrentLoad(loadGeneration)) {
                return;
            }
            FolderShortInfo folderShortInfo = new FolderShortInfo();
            mUriLiveData.setValue(currentUri);
            mFolderShortInfoLiveData.setValue(folderShortInfo);
            mFmErrorLiveData.setValue(th);
        });
    }

    private void filterAndSort() {
        long loadGeneration = mLoadGeneration.get();
        Uri currentUri = mCurrentUri;
        if (currentUri == null) {
            return;
        }
        filterAndSort(loadGeneration, currentUri, null);
    }

    private void filterAndSort(long loadGeneration, @NonNull Uri currentUri,
                               @Nullable String scrollToFilename) {
        if (!isCurrentLoad(loadGeneration)) {
            return;
        }
        boolean displayDotFiles = (mSelectedOptions & FmListOptions.OPTIONS_DISPLAY_DOT_FILES) != 0;
        boolean foldersOnTop = (mSelectedOptions & FmListOptions.OPTIONS_FOLDERS_FIRST) != 0;

        List<FmItem> filteredList;
        synchronized (mFmItems) {
            if (!TextUtils.isEmpty(mQueryString)) {
                filteredList = AdvancedSearchView.matches(mQueryString, mFmItems, FmItem::getName,
                        AdvancedSearchView.SEARCH_TYPE_CONTAINS);
            } else filteredList = new ArrayList<>(mFmItems);
        }
        if (ThreadUtils.isInterrupted() || !isCurrentLoad(loadGeneration)) {
            return;
        }
        if (!displayDotFiles) {
            Iterator<FmItem> iterator = filteredList.listIterator();
            while (iterator.hasNext()) {
                FmItem fmItem = iterator.next();
                if (fmItem.getName().startsWith(".")) {
                    iterator.remove();
                }
            }
        }
        if (ThreadUtils.isInterrupted() || !isCurrentLoad(loadGeneration)) {
            return;
        }
        // Sort by name first
        Collections.sort(filteredList, (o1, o2) -> AlphanumComparator.compareStringIgnoreCase(o1.getName(), o2.getName()));
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
                    return -Long.compare(o1.getLastModified(), o2.getLastModified()) * inverse;
                }
                if (mSortBy == FmListOptions.SORT_BY_SIZE) {
                    return -Long.compare(o1.getSize(), o2.getSize()) * inverse;
                }
                if (mSortBy == FmListOptions.SORT_BY_TYPE) {
                    return p1.getType().compareToIgnoreCase(p2.getType()) * inverse;
                }
                return 0;
            });
        }
        if (foldersOnTop) {
            // Folders should be on top
            Collections.sort(filteredList, (o1, o2) -> -Boolean.compare(o1.isDirectory, o2.isDirectory));
        }
        if (ThreadUtils.isInterrupted() || !isCurrentLoad(loadGeneration)) {
            return;
        }
        if (scrollToFilename != null) {
            for (int i = 0; i < filteredList.size(); ++i) {
                if (scrollToFilename.equals(filteredList.get(i).getName())) {
                    setScrollPosition(currentUri, i);
                    break;
                }
            }
        }
        ThreadUtils.postOnMainThread(() -> {
            if (isCurrentLoad(loadGeneration)) {
                mFmItemsLiveData.setValue(filteredList);
            }
        });
    }

    private boolean isCurrentLoad(long loadGeneration) {
        return mLoadGeneration.get() == loadGeneration;
    }

    @WorkerThread
    @NonNull
    private VirtualFileSystem mountVfs() throws IOException {
        if (!mOptions.isVfs()) {
            throw new IOException("VFS expected, found regular FS.");
        }
        VirtualFileSystem fs = VirtualFileSystem.getFileSystem(mOptions.uri);
        if (fs == null) {
            // TODO: 31/5/23 Handle read-only
            Path filePath = Paths.getStrict(mOptions.uri);
            Path cachedPath = Paths.get(mFileCache.getCachedFile(filePath));
            String type = cachedPath.getType();
            int vfsId;
            if (ContentType.APK.getMimeType().equals(type)) {
                vfsId = VirtualFileSystem.mount(filePath.getUri(), cachedPath, ContentType.APK.getMimeType());
            } else if (FileUtils.isZip(cachedPath)) {
                vfsId = VirtualFileSystem.mount(filePath.getUri(), cachedPath, ContentType.ZIP.getMimeType());
            } else if (DexUtils.isDex(cachedPath)) {
                vfsId = VirtualFileSystem.mount(filePath.getUri(), cachedPath, ContentType2.DEX.getMimeType());
            } else {
                vfsId = VirtualFileSystem.mount(filePath.getUri(), cachedPath, cachedPath.getType());
            }
            fs = VirtualFileSystem.getFileSystem(vfsId);
            if (fs == null) {
                throw new IOException("Could not mount " + mOptions.uri);
            }
        }
        return fs;
    }
}
