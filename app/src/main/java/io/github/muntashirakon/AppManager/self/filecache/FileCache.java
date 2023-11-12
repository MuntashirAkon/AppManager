// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self.filecache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class FileCache implements Closeable {

    private static FileCache sInstance;

    @NonNull
    public static FileCache getGlobalFileCache() {
        if (sInstance == null) {
            sInstance = new FileCache(false);
        }
        return sInstance;
    }

    private final File mCacheDir;
    private final Map<Path, File> mFileCacheMap = new HashMap<>();
    private final Set<File> mFileCache = new HashSet<>();
    private final Set<File> mDirectoryCache = new HashSet<>();
    private final boolean mSessionOnly;

    private boolean mClosed = false;

    public FileCache() {
        this(true);
    }

    private FileCache(boolean sessionOnly) {
        mSessionOnly = sessionOnly;
        mCacheDir = new File(FileUtils.getCachePath(), "files");
        if (!mCacheDir.exists()) {
            if (!mCacheDir.mkdirs()) {
                throw new IllegalStateException("Could not create cache. Is this OS broken?");
            }
        }
    }

    @Override
    public void close() {
        mClosed = true;
        if (mSessionOnly) {
            deleteAll();
        }
    }

    @Override
    protected void finalize() {
        if (!mClosed) {
            close();
        }
    }

    @NonNull
    public File getCachedFile(@NonNull Path source) throws IOException {
        if (!source.exists()) {
            // No need for cache if the path is non-existent
            throw new FileNotFoundException("Path " + source + " does not exist.");
        }
        File tempFile = mFileCacheMap.get(source);
        if (tempFile == null || !tempFile.exists()) {
            String extension = source.getExtension();
            tempFile = File.createTempFile(source.getName() + "_", "." + (extension != null ? extension : "tmp"), mCacheDir);
            mFileCacheMap.put(source, tempFile);
        } else if (source.lastModified() > 0 && source.lastModified() < tempFile.lastModified()) {
            return tempFile;
        }
        IoUtils.copy(source, Paths.get(tempFile));
        return tempFile;
    }

    @NonNull
    public File getCachedFile(@NonNull InputStream is, @Nullable String extension) throws IOException {
        File tempFile = File.createTempFile("file_", "." + (extension != null ? extension : "tmp"), mCacheDir);
        mFileCache.add(tempFile);
        try (OutputStream os = new FileOutputStream(tempFile)) {
            IoUtils.copy(is, os);
        }
        return tempFile;
    }

    @NonNull
    public File createCachedFile(@Nullable String extension) throws IOException {
        File tempFile = File.createTempFile("file_", "." + (extension != null ? extension : "tmp"), mCacheDir);
        mFileCache.add(tempFile);
        return tempFile;
    }

    @NonNull
    public File createCachedDir(@Nullable String prefix) {
        if (prefix != null) {
            prefix = Paths.sanitize(prefix, true);
        }
        if (prefix == null) {
            prefix = "folder";
        }
        String dirName = prefix;
        int i = 1;
        File newDir = new File(mCacheDir, dirName);
        while (newDir.exists()) {
            dirName = prefix + "_" + i;
            newDir = new File(mCacheDir, dirName);
            ++i;
        }
        newDir.mkdirs();
        // Get first path component from dirName
        int idx = dirName.indexOf(File.separator);
        String firstComponent;
        if (idx == -1) {
            firstComponent = dirName;
        } else {
            firstComponent = dirName.substring(0, idx);
        }
        mDirectoryCache.add(new File(mCacheDir, firstComponent));
        return newDir;
    }

    public boolean delete(@Nullable Path path) {
        File tempFile = mFileCacheMap.remove(path);
        return tempFile != null && tempFile.delete();
    }

    public boolean delete(@Nullable File tempFile) {
        if (mFileCache.remove(tempFile)) {
            return tempFile != null && tempFile.delete();
        }
        return false;
    }

    public void deleteAll() {
        for (File file : mFileCacheMap.values()) {
            FileUtils.deleteSilently(file);
        }
        for (File file : mFileCache) {
            FileUtils.deleteSilently(file);
        }
        for (File file : mDirectoryCache) {
            Paths.get(file).delete();
        }
    }
}
