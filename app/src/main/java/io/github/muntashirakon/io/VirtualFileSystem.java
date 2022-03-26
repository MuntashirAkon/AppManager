// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;
import androidx.collection.SparseArrayCompat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.scanner.DexClasses;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;

public final class VirtualFileSystem {
    public static final String TAG = VirtualFileSystem.class.getSimpleName();

    private static final SparseArrayCompat<FileSystem> fileSystems = new SparseArrayCompat<>(3);
    private static final HashMap<Uri, Integer> uriVfsIdsMap = new HashMap<>(3);
    private static final HashMap<Uri, List<Integer>> parentUriVfsIdsMap = new HashMap<>(3);

    private VirtualFileSystem() {
    }

    @WorkerThread
    public static int mount(@NonNull FileSystem fs) throws Throwable {
        int vfsId;
        synchronized (fileSystems) {
            synchronized (uriVfsIdsMap) {
                if (uriVfsIdsMap.get(fs.getMountPoint()) != null) {
                    throw new Exception(String.format("Mount point (%s) is already in use.", fs.getMountPoint()));
                }
            }
            do {
                vfsId = ThreadLocalRandom.current().nextInt();
            } while (vfsId == 0 || fileSystems.get(vfsId) != null);
            fs.mount(vfsId);
            fileSystems.put(vfsId, fs);
        }
        synchronized (uriVfsIdsMap) {
            uriVfsIdsMap.put(fs.getMountPoint(), vfsId);
        }
        synchronized (parentUriVfsIdsMap) {
            Uri uri = FileUtils.removeLastPathSegment(fs.getMountPoint());
            List<Integer> vfsIds = parentUriVfsIdsMap.get(uri);
            if (vfsIds == null) {
                vfsIds = new ArrayList<>(1);
                parentUriVfsIdsMap.put(uri, vfsIds);
            }
            vfsIds.add(vfsId);
        }
        Log.d(TAG, String.format(Locale.ROOT, "Mounted %d at %s", vfsId, fs.getMountPoint()));
        return vfsId;
    }

    @WorkerThread
    public static void unmount(int vfsId) throws Throwable {
        FileSystem fs;
        synchronized (fileSystems) {
            fs = fileSystems.get(vfsId);
        }
        if (fs == null) return;
        fs.unmount();
        synchronized (fileSystems) {
            fileSystems.remove(vfsId);
        }
        synchronized (uriVfsIdsMap) {
            uriVfsIdsMap.remove(fs.getMountPoint());
        }
        synchronized (parentUriVfsIdsMap) {
            Uri uri = FileUtils.removeLastPathSegment(fs.getMountPoint());
            List<Integer> vfsIds = parentUriVfsIdsMap.get(uri);
            if (vfsIds != null && vfsIds.contains(vfsId)) {
                if (vfsIds.size() == 1) parentUriVfsIdsMap.remove(uri);
                else vfsIds.remove((Integer) vfsId);
            }
        }
        Log.d(TAG, String.format(Locale.ROOT, "%d unmounted at %s", vfsId, fs.getMountPoint()));
    }

    @Nullable
    public static Uri getMountPoint(int vfsId) {
        FileSystem fs;
        synchronized (fileSystems) {
            fs = fileSystems.get(vfsId);
        }
        if (fs == null) return null;
        return fs.getMountPoint();
    }

    @Nullable
    public static Path getFsRoot(int vfsId) {
        FileSystem fs;
        synchronized (fileSystems) {
            fs = fileSystems.get(vfsId);
        }
        if (fs == null) return null;
        return fs.getRootPath();
    }

    @Nullable
    public static FileSystem getFileSystem(Uri mountPoint) {
        Integer vfsId;
        synchronized (uriVfsIdsMap) {
            vfsId = uriVfsIdsMap.get(mountPoint);
        }
        if (vfsId == null) return null;
        synchronized (fileSystems) {
            return fileSystems.get(vfsId);
        }
    }

    @Nullable
    public static Path getFsRoot(Uri mountPoint) {
        Integer vfsId;
        synchronized (uriVfsIdsMap) {
            vfsId = uriVfsIdsMap.get(mountPoint);
        }
        if (vfsId == null) return null;
        FileSystem fs;
        synchronized (fileSystems) {
            fs = fileSystems.get(vfsId);
        }
        if (fs == null) return null;
        return fs.getRootPath();
    }

    @Nullable
    public static FileSystem getFileSystem(int vfsId) {
        synchronized (fileSystems) {
            return fileSystems.get(vfsId);
        }
    }

    @NonNull
    public static FileSystem[] getFileSystemsAtUri(Uri parentUri) {
        List<Integer> vfsIds;
        synchronized (parentUriVfsIdsMap) {
            vfsIds = parentUriVfsIdsMap.get(parentUri);
        }
        if (vfsIds == null) return ArrayUtils.emptyArray(FileSystem.class);
        FileSystem[] fs = new FileSystem[vfsIds.size()];
        synchronized (fileSystems) {
            for (int i = 0; i < fs.length; ++i) {
                fs[i] = fileSystems.get(vfsIds.get(i));
            }
        }
        return fs;
    }

    public abstract static class FileSystem {
        private final Uri mountPoint;

        public FileSystem(@NonNull Uri mountPoint) {
            this.mountPoint = mountPoint;
        }

        @NonNull
        public final Uri getMountPoint() {
            return mountPoint;
        }

        @NonNull
        public abstract Path getRootPath();

        abstract void mount(int vfsId) throws Throwable;

        abstract void unmount() throws Throwable;
    }

    public static class ZipFileSystem extends FileSystem {
        @NonNull
        private final File zipFilePath;
        private ZipFile zipFile;
        private Path rootPath;

        public ZipFileSystem(@NonNull Uri mountPoint, @NonNull File zipFile) {
            super(mountPoint);
            this.zipFilePath = zipFile;
        }

        @NonNull
        @Override
        public Path getRootPath() {
            return Objects.requireNonNull(rootPath);
        }

        @Override
        public void mount(int vfsId) throws IOException {
            zipFile = new ZipFile(zipFilePath);
            rootPath = new Path(AppManager.getContext(), vfsId, zipFile, null);
        }

        @Override
        public void unmount() throws IOException {
            zipFile.close();
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public static class DexFileSystem extends FileSystem {
        @Nullable
        private final File filePath;
        @Nullable
        private final Path dexPath;
        private DexClasses dexClasses;
        private Path rootPath;

        public DexFileSystem(@NonNull Uri mountPoint, @NonNull File filePath) {
            super(mountPoint);
            this.filePath = filePath;
            this.dexPath = null;
        }

        public DexFileSystem(@NonNull Uri mountPoint, @NonNull DexClasses dexClasses) {
            super(mountPoint);
            this.filePath = null;
            this.dexPath = null;
            this.dexClasses = dexClasses;
        }

        public DexFileSystem(@NonNull Uri mountPoint, @NonNull Path dexPath) {
            super(mountPoint);
            this.filePath = null;
            this.dexPath = dexPath;
        }

        @NonNull
        @Override
        public Path getRootPath() {
            return Objects.requireNonNull(rootPath);
        }

        @NonNull
        public final DexClasses getDexClasses() {
            return dexClasses;
        }

        @Override
        public void mount(int vfsId) throws IOException {
            if (filePath != null) dexClasses = new DexClasses(filePath);
            else if (dexPath != null) {
                try (InputStream is = dexPath.openInputStream()) {
                    dexClasses = new DexClasses(is);
                }
            }
            rootPath = new Path(AppManager.getContext(), vfsId, Objects.requireNonNull(dexClasses), null);
        }

        @Override
        public void unmount() throws IOException {
            dexClasses.close();
        }
    }
}
