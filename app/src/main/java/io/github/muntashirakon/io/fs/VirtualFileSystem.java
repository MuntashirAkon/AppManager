// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io.fs;

import static io.github.muntashirakon.io.FileSystemManager.MODE_READ_ONLY;
import static io.github.muntashirakon.io.FileSystemManager.MODE_READ_WRITE;
import static io.github.muntashirakon.io.FileSystemManager.MODE_WRITE_ONLY;

import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.collection.SparseArrayCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.io.FileSystemManager;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.UidGidPair;

@SuppressWarnings("unused")
public abstract class VirtualFileSystem {
    public static final String TAG = VirtualFileSystem.class.getSimpleName();

    public static class MountOptions {
        public static class Builder {
            private boolean mRemount = false;
            private boolean mReadWrite = false;
            private int mMode = 0;
            @Nullable
            private UidGidPair mUidGidPair = null;
            @Nullable
            private OnFileSystemUnmounted mOnFileSystemUnmounted = null;

            public Builder setRemount(boolean remount) {
                mRemount = remount;
                return this;
            }

            public Builder setReadWrite(boolean readWrite) {
                mReadWrite = readWrite;
                return this;
            }

            public Builder setMode(int mode) {
                mMode = mode;
                return this;
            }

            public Builder setUidGidPair(@Nullable UidGidPair uidGidPair) {
                mUidGidPair = uidGidPair;
                return this;
            }

            public Builder setOnFileSystemUnmounted(@Nullable OnFileSystemUnmounted fileSystemUnmounted) {
                mOnFileSystemUnmounted = fileSystemUnmounted;
                return this;
            }

            public MountOptions build() {
                return new MountOptions(mRemount, mReadWrite, mMode, mUidGidPair, mOnFileSystemUnmounted);
            }
        }

        public final boolean remount;
        public final boolean readWrite;
        public final int mode;
        @Nullable
        public final UidGidPair uidGidPair;
        @Nullable
        public final OnFileSystemUnmounted onFileSystemUnmounted;

        private MountOptions(boolean remount, boolean readWrite, int mode, @Nullable UidGidPair uidGidPair,
                             @Nullable OnFileSystemUnmounted fileSystemUnmounted) {
            this.remount = remount;
            this.readWrite = readWrite;
            this.mode = mode;
            this.uidGidPair = uidGidPair;
            this.onFileSystemUnmounted = fileSystemUnmounted;
        }
    }

    public static final String SCHEME = "vfs";

    public static Uri getUri(int fsId, @Nullable String path) {
        return new Uri.Builder()
                .scheme(SCHEME)
                .authority(String.valueOf(fsId))
                .path(path != null ? path : File.separator)
                .build();
    }

    @NonNull
    private static VirtualFileSystem getNewInstance(@NonNull Path file, @NonNull String type) {
        if (ZipFileSystem.TYPE.equals(type)) {
            return new ZipFileSystem(file);
        }
        if (DexFileSystem.TYPE.equals(type)) {
            return new DexFileSystem(file);
        }
        throw new IllegalArgumentException("Invalid type " + type);
    }

    private static final SparseArrayCompat<VirtualFileSystem> sFileSystems = new SparseArrayCompat<>(3);
    private static final HashMap<Uri, Integer> sUriVfsIdsMap = new HashMap<>(3);
    private static final HashMap<Uri, List<Integer>> sParentUriVfsIdsMap = new HashMap<>(3);

    @WorkerThread
    public static int mount(@NonNull Uri mountPoint, @NonNull Path file, @NonNull String type) throws IOException {
        return mount(mountPoint, file, type, new MountOptions.Builder().build());
    }

    @WorkerThread
    public static int mount(@NonNull Uri mountPoint,
                            @NonNull Path file,
                            @NonNull String type,
                            @NonNull MountOptions options)
            throws IOException {
        return mount(mountPoint, getNewInstance(file, type), options);
    }

    @WorkerThread
    private static int mount(@NonNull Uri mountPoint, @NonNull VirtualFileSystem fs, @NonNull MountOptions options)
            throws IOException {
        int vfsId;
        synchronized (sFileSystems) {
            synchronized (sUriVfsIdsMap) {
                if (!options.remount && sUriVfsIdsMap.get(mountPoint) != null) {
                    throw new IOException(String.format("Mount point (%s) is already in use.", mountPoint));
                }
            }
            do {
                vfsId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
            } while (vfsId == 0 || sFileSystems.get(vfsId) != null);
            fs.mount(mountPoint, vfsId, options);
            sFileSystems.put(vfsId, fs);
        }
        synchronized (sUriVfsIdsMap) {
            sUriVfsIdsMap.put(mountPoint, vfsId);
        }
        synchronized (sParentUriVfsIdsMap) {
            Uri uri = Paths.removeLastPathSegment(mountPoint);
            List<Integer> vfsIds = sParentUriVfsIdsMap.get(uri);
            if (vfsIds == null) {
                vfsIds = new ArrayList<>(1);
                sParentUriVfsIdsMap.put(uri, vfsIds);
            }
            vfsIds.add(vfsId);
        }
        Log.d(TAG, "Mounted %d at %s", vfsId, mountPoint);
        return vfsId;
    }

    @WorkerThread
    public static void unmount(int vfsId) throws IOException {
        VirtualFileSystem fs = getFileSystem(vfsId);
        if (fs == null) return;
        Uri mountPoint = fs.getMountPoint();
        synchronized (sFileSystems) {
            sFileSystems.remove(vfsId);
        }
        synchronized (sUriVfsIdsMap) {
            sUriVfsIdsMap.remove(mountPoint);
        }
        synchronized (sParentUriVfsIdsMap) {
            if (mountPoint != null) {
                Uri uri = Paths.removeLastPathSegment(mountPoint);
                List<Integer> vfsIds = sParentUriVfsIdsMap.get(uri);
                if (vfsIds != null && vfsIds.contains(vfsId)) {
                    if (vfsIds.size() == 1) sParentUriVfsIdsMap.remove(uri);
                    else vfsIds.remove((Integer) vfsId);
                }
            }
        }
        fs.unmount();
        Log.d(TAG, "%d unmounted at %s", vfsId, mountPoint);
    }

    public static void alterMountPoint(Uri oldMountPoint, Uri newMountPoint) {
        VirtualFileSystem fs = getFileSystem(oldMountPoint);
        if (fs == null) return;
        synchronized (sUriVfsIdsMap) {
            sUriVfsIdsMap.remove(oldMountPoint);
            sUriVfsIdsMap.put(newMountPoint, fs.getFsId());
        }
        synchronized (sParentUriVfsIdsMap) {
            // Remove old mount point
            Uri oldParent = Paths.removeLastPathSegment(oldMountPoint);
            List<Integer> oldFsIds = sParentUriVfsIdsMap.get(oldParent);
            if (oldFsIds != null) {
                oldFsIds.remove((Integer) fs.getFsId());
            }
            // Add new mount point
            Uri newParent = Paths.removeLastPathSegment(newMountPoint);
            List<Integer> newFsIds = sParentUriVfsIdsMap.get(newParent);
            if (newFsIds == null) {
                newFsIds = new ArrayList<>(1);
                sParentUriVfsIdsMap.put(newParent, newFsIds);
            }
            newFsIds.add(fs.getFsId());
        }
        fs.mMountPoint = newMountPoint;
        Log.d(TAG, "Mount point of %d altered from %s to %s", fs.getFsId(), oldMountPoint, newMountPoint);
    }

    public static boolean isMountPoint(@NonNull Uri uri) {
        return getFileSystem(uri) != null;
    }

    /**
     * @see #getRootPath()
     */
    @Nullable
    public static Path getFsRoot(int vfsId) {
        VirtualFileSystem fs = getFileSystem(vfsId);
        return fs != null ? fs.getRootPath() : null;
    }

    /**
     * @see #getRootPath()
     */
    @Nullable
    public static Path getFsRoot(Uri mountPoint) {
        Integer vfsId;
        synchronized (sUriVfsIdsMap) {
            vfsId = sUriVfsIdsMap.get(mountPoint);
        }
        if (vfsId == null) return null;
        return getFsRoot(vfsId);
    }

    @Nullable
    public static VirtualFileSystem getFileSystem(Uri mountPoint) {
        Integer vfsId;
        synchronized (sUriVfsIdsMap) {
            vfsId = sUriVfsIdsMap.get(mountPoint);
        }
        if (vfsId == null) return null;
        return getFileSystem(vfsId);
    }

    @Nullable
    public static VirtualFileSystem getFileSystem(int vfsId) {
        synchronized (sFileSystems) {
            return sFileSystems.get(vfsId);
        }
    }

    @NonNull
    public static VirtualFileSystem[] getFileSystemsAtUri(Uri parentUri) {
        List<Integer> vfsIds;
        synchronized (sParentUriVfsIdsMap) {
            vfsIds = sParentUriVfsIdsMap.get(parentUri);
        }
        if (vfsIds == null) return ArrayUtils.emptyArray(VirtualFileSystem.class);
        VirtualFileSystem[] fs = new VirtualFileSystem[vfsIds.size()];
        synchronized (sFileSystems) {
            for (int i = 0; i < fs.length; ++i) {
                fs[i] = sFileSystems.get(vfsIds.get(i));
            }
        }
        return fs;
    }

    /* Static members ends */

    public interface OnFileSystemUnmounted {
        /**
         * Run after the file is unmounted.
         *
         * @param fs         The file system, now unusable
         * @param cachedFile The cached file created by the file system if it supports writing
         * @return {@code true} if the cached file is handled manually, {@code false} otherwise. The file should be
         * handled manually in most cases.
         */
        boolean onUnmounted(@NonNull VirtualFileSystem fs, @Nullable File cachedFile);
    }

    @IntDef({ACTION_CREATE, ACTION_UPDATE, ACTION_DELETE, ACTION_MOVE})
    @Retention(RetentionPolicy.SOURCE)
    protected @interface CrudAction {
    }

    /**
     * Create a new path in the system. {@link Action#targetNode} for this action may not exist.
     */
    protected static final int ACTION_CREATE = 1;
    protected static final int ACTION_UPDATE = 2;
    protected static final int ACTION_DELETE = 3;
    protected static final int ACTION_MOVE = 4;

    protected static class Action {
        @CrudAction
        public final int action;
        /**
         * Target path within this file system. It may or may not be an actual path depending on the previous actions.
         */
        @NonNull
        public final Node<?> targetNode;

        /**
         * Cached path is located outside the file system, in a physical location. Contents of this path is later used
         * to modify the virtual file system.
         */
        @Nullable
        private File mCachedPath;
        // Only applicable for ACTION_MOVE
        private String mSourcePath;

        public Action(@CrudAction int action, @NonNull Node<?> targetNode) {
            this.action = action;
            this.targetNode = targetNode;
        }

        private void setCachedPath(@Nullable File cachedPath) {
            mCachedPath = cachedPath;
        }

        @Nullable
        public File getCachedPath() {
            return mCachedPath;
        }

        private void setSourcePath(String sourcePath) {
            mSourcePath = sourcePath;
        }

        public String getSourcePath() {
            return mSourcePath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Action)) return false;
            Action action1 = (Action) o;
            return action == action1.action;
        }

        @Override
        public int hashCode() {
            return Objects.hash(action);
        }
    }

    private static class FileCacheItem {
        @NonNull
        public final File cachedFile;

        private boolean mModified;

        private FileCacheItem(@NonNull File cachedFile) {
            this.cachedFile = cachedFile;
        }

        public void setModified(boolean modified) {
            mModified = modified;
        }

        public boolean isModified() {
            return mModified;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FileCacheItem)) return false;
            FileCacheItem fileCacheItem = (FileCacheItem) o;
            return cachedFile.equals(fileCacheItem.cachedFile);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cachedFile);
        }
    }

    private final Object sLock = new Object();
    @NonNull
    private final Map<String, List<Action>> mActions = new HashMap<>();
    private final Map<String, FileCacheItem> mFileCacheMap = new HashMap<>();
    private final FileCache mFileCache = new FileCache();
    @NonNull
    private final Path mFile;

    @Nullable
    private Uri mMountPoint;
    @Nullable
    private MountOptions mOptions;
    private int mFsId = 0;
    private Path mRootPath;

    protected VirtualFileSystem(@NonNull Path file) {
        mFile = file;
    }

    @NonNull
    public Path getFile() {
        return mFile;
    }

    public abstract String getType();

    public int getFsId() {
        return mFsId;
    }

    /**
     * Return the abstract location where the file system is mounted. This is similar to the mount point returned by the
     * {@code mount} command.
     */
    @Nullable
    public final Uri getMountPoint() {
        return mMountPoint;
    }

    @Nullable
    public MountOptions getOptions() {
        return mOptions;
    }

    /**
     * Return the abstract location where the file system is located. This is not the same as {@link #getMountPoint()}
     * which returns the abstract location where the file system is mounted. In other words, this is the real location
     * to the file system, and should never be used other than the Path APIs.
     */
    @NonNull
    public Path getRootPath() {
        return Objects.requireNonNull(mRootPath);
    }

    /**
     * Mount the file system.
     *
     * @param fsId Unique file system ID to locate it.
     * @throws IOException If the file system cannot be mounted.
     */
    private void mount(@NonNull Uri mountPoint, int fsId, MountOptions options) throws IOException {
        synchronized (sLock) {
            mMountPoint = mountPoint;
            mFsId = fsId;
            mOptions = options;
            onPreMount();
            mRootPath = onMount();
            onMounted();
        }
    }


    /**
     * Instructions to execute before mounting the file system.
     * <p>
     * Note that the root path haven't been initialised at this point and calling {@link #getRootPath()} would throw
     * an NPE.
     */
    protected void onPreMount() {
    }

    /**
     * Mount the file system. Operations related to mounting the file system should be done here.
     *
     * @return The real path of the file system AKA root path.
     * @see #getRootPath()
     */
    protected abstract Path onMount() throws IOException;

    /**
     * Instructions to execute after mounting the file system. Any initialisations such as building file trees should
     * be done where.
     */
    protected void onMounted() {
    }

    private void unmount() throws IOException {
        checkMounted();
        Objects.requireNonNull(mOptions);
        synchronized (sLock) {
            // Update actions
            for (String path : mFileCacheMap.keySet()) {
                FileCacheItem fileCacheItem = Objects.requireNonNull(mFileCacheMap.get(path));
                if (fileCacheItem.isModified()) {
                    Node<?> node = getNode(path);
                    if (node != null) {
                        Action action = new Action(ACTION_UPDATE, node);
                        action.setCachedPath(fileCacheItem.cachedFile);
                        addAction(node.getFullPath(), action);
                    }
                }
            }
            File cachedFile = onUnmount(mActions);
            mFsId = 0;
            mMountPoint = null;
            // Cleanup
            mFileCacheMap.clear();
            mFileCache.deleteAll();
            mActions.clear();
            // Handle cached file
            if (cachedFile != null) {
                // Run interface if available
                boolean handled = mOptions.onFileSystemUnmounted != null
                        && mOptions.onFileSystemUnmounted.onUnmounted(this, cachedFile);
                if (!handled) {
                    // Cached file isn't handled above, try the default ignoring all issues
                    Path dest = getFile();
                    Path source = Paths.get(cachedFile);
                    dest.delete();
                    source.moveTo(dest);
                }
            }
            mOptions = null;
            onUnmounted();
        }
    }

    @Override
    protected void finalize() {
        mFileCache.close();
    }

    /**
     * Instructions to execute to unmount the file system. Example operations include cleaning up trees, saving
     * changes, closing resources.
     *
     * @return The final cached file if the file system support modification, {@code null} otherwise.
     */
    @Nullable
    protected abstract File onUnmount(@NonNull Map<String, List<Action>> actions) throws IOException;

    /**
     * Instructions to execute after unmounting the file system.
     */
    protected void onUnmounted() {
    }

    protected void checkMounted() {
        synchronized (sLock) {
            if (mFsId == 0) {
                throw new NotMountedException("Not mounted");
            }
        }
    }

    /* File APIs */
    private void addAction(@NonNull String path, @NonNull Action action) {
        synchronized (mActions) {
            List<Action> actionSet = mActions.get(path);
            if (actionSet == null) {
                actionSet = new ArrayList<>();
                mActions.put(path, actionSet);
            }
            actionSet.add(action);
        }
    }

    @Nullable
    protected abstract Node<?> getNode(String path);

    protected abstract void invalidate(String path);

    @Nullable
    public String getCanonicalPath(String path) {
        checkMounted();
        return null;
    }

    public boolean isDirectory(String path) {
        Node<?> targetNode = getNode(path);
        if (targetNode == null) {
            return false;
        }
        return targetNode.isDirectory();
    }

    public boolean isFile(String path) {
        Node<?> targetNode = getNode(path);
        if (targetNode == null) {
            return false;
        }
        return targetNode.isFile();
    }

    public boolean isHidden(String path) {
        Node<?> targetNode = getNode(path);
        if (targetNode == null) {
            return false;
        }
        return targetNode.getName().startsWith(".");
    }

    protected abstract long lastModified(@NonNull Node<?> path);

    public long lastModified(String path) {
        Node<?> targetNode = getNode(path);
        if (targetNode == null) {
            return getFile().lastModified();
        }
        File cachedFile = findCachedFile(targetNode);
        if (cachedFile != null) {
            return cachedFile.lastModified();
        }
        return lastModified(targetNode);
    }

    public long lastAccess(String path) {
        return lastModified(path);
    }

    public long creationTime(String path) {
        return lastModified(path);
    }

    public abstract long length(String path);

    public boolean createNewFile(String path) {
        if (checkAccess(path, OsConstants.F_OK)) {
            return false;
        }
        String filename = Paths.getLastPathSegment(path);
        String parent = Paths.removeLastPathSegment(path);
        if (!checkAccess(parent, OsConstants.W_OK)) {
            return false;
        }
        Node<?> parentNode = getNode(parent);
        if (parentNode == null || !parentNode.isDirectory()) {
            return false;
        }
        Node<?> newFileNode = new Node<>(parentNode, filename, false);
        newFileNode.setFile(true);
        parentNode.addChild(newFileNode);
        addAction(newFileNode.getFullPath(), new Action(ACTION_CREATE, newFileNode));
        invalidate(path);
        return true;
    }

    public boolean delete(String path) {
        if (!checkAccess(path, OsConstants.W_OK)) {
            return false;
        }
        Node<?> targetNode = getNode(path);
        if (targetNode == null) {
            return false;
        }
        addAction(targetNode.getFullPath(), new Action(ACTION_DELETE, targetNode));
        Node<?> parentNode = targetNode.getParent();
        if (parentNode != null) {
            parentNode.removeChild(targetNode);
        }
        invalidate(path);
        return true;
    }

    @Nullable
    public String[] list(String path) {
        Node<?> targetNode = getNode(path);
        if (targetNode == null) {
            return null;
        }
        Node<?>[] children = targetNode.listChildren();
        if (children == null) {
            return null;
        }
        String[] childNames = new String[children.length];
        for (int i = 0; i < children.length; ++i) {
            childNames[i] = children[i].getName();
        }
        return childNames;
    }

    public boolean mkdir(String path) {
        if (checkAccess(path, OsConstants.F_OK)) {
            // File/folder exists
            return false;
        }
        String filename = Paths.getLastPathSegment(path);
        String parent = Paths.removeLastPathSegment(path);
        if (!checkAccess(parent, OsConstants.W_OK)) {
            return false;
        }
        Node<?> parentNode = getNode(parent);
        if (parentNode == null || !parentNode.isDirectory()) {
            return false;
        }
        Node<?> newFileNode = new Node<>(parentNode, filename, false);
        newFileNode.setDirectory(true);
        parentNode.addChild(newFileNode);
        addAction(newFileNode.getFullPath(), new Action(ACTION_CREATE, newFileNode));
        invalidate(path);
        return true;
    }

    public boolean mkdirs(String path) {
        if (checkAccess(path, OsConstants.F_OK)) {
            // File/folder exists
            return false;
        }
        List<String> parts = new ArrayList<>();
        String parent = path;
        Node<?> parentNode;
        do {
            String filename = Paths.getLastPathSegment(parent);
            parent = Paths.removeLastPathSegment(parent);
            parts.add(filename);
            parentNode = getNode(parent);
        } while (parentNode == null && !parent.equals(File.separator));
        // Found a parent node
        if (!checkAccess(parent, OsConstants.W_OK) || parentNode == null || !parentNode.isDirectory()) {
            // Parent node is inaccessible
            return false;
        }
        for (int i = parts.size() - 1; i >= 0; --i) {
            parentNode = new Node<>(parentNode, parts.get(i), false);
            parentNode.setDirectory(true);
            Objects.requireNonNull(parentNode.getParent()).addChild(parentNode);
            String fullPath = parentNode.getFullPath();
            addAction(fullPath, new Action(ACTION_CREATE, parentNode));
            invalidate(fullPath);
        }
        return true;
    }

    /**
     * Similar to Java File API in Unix.
     *
     * <ul>
     *  <li>If destination is a file, it overrides it.
     *  <li>If destination is a directory, it overrides it only if it has no children.
     *  <li>If destination is does not exist, it creates it.
     * </ul>
     */
    public boolean renameTo(String source, String dest) {
        // path and dest must belong to the same file system and are relative as usual.
        if (dest.startsWith(source)) {
            // Destination cannot be the same or a subdirectory of source
            return false;
        }
        if (!checkAccess(source, OsConstants.W_OK)) {
            // Directory not modifiable
            return false;
        }
        boolean destExists = checkAccess(dest, OsConstants.F_OK);
        Node<?> sourceNode = getNode(source);
        if (sourceNode == null) {
            return false;
        }
        if (sourceNode.isDirectory()) {
            // Source node is a directory, so create some directories and move whatever this directory has.
            String filename = Paths.getLastPathSegment(dest);
            String parent = Paths.removeLastPathSegment(dest);
            mkdirs(parent);
            Node<?> targetNode = getNode(parent);
            if (targetNode == null) {
                return false;
            }
            if (destExists) {
                // Override existing node
                Node<?> node = getNode(filename);
                if (node != null) {
                    if (node.isFile()) {
                        // Existing node is a file
                        return false;
                    } else if (node.listChildren() != null) {
                        // Is a directory with children
                        return false;
                    } else {
                        // A directory with no children
                        addAction(dest, new Action(ACTION_DELETE, node));
                        targetNode.removeChild(node);
                        invalidate(dest);
                    }
                }
            }
            // Rename sourceNode to filename
            moveChildren(sourceNode, source, dest);
            Node<?> parentNode = sourceNode.getParent();
            if (parentNode != null) {
                parentNode.removeChild(sourceNode);
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            Node<?> renamedNode = new Node(targetNode, sourceNode, filename);
            Action action = new Action(ACTION_MOVE, renamedNode);
            action.setSourcePath(source);
            if (!dest.equals(renamedNode.getFullPath())) {
                throw new IllegalStateException(String.format(Locale.ROOT, "Invalid destination for the renamed node. Required: %s, was: %s", dest, renamedNode.getFullPath()));
            }
            addAction(dest, action);
            targetNode.addChild(renamedNode);
            invalidate(source);
        } else if (sourceNode.isFile()) {
            // Source node is a file, create some directories up to this point and move this file to there
            // Overriding the existing one if necessary.
            String filename = Paths.getLastPathSegment(dest);
            String parent = Paths.removeLastPathSegment(dest);
            // Output of mkdirs is not relevant
            mkdirs(parent);
            Node<?> targetNode = getNode(parent);
            if (targetNode == null) {
                return false;
            }
            if (destExists) {
                // Override existing node
                Node<?> destNode = getNode(filename);
                if (destNode != null) {
                    if (destNode.isDirectory()) {
                        // Existing node is a directory
                        return false;
                    }
                    addAction(dest, new Action(ACTION_DELETE, destNode));
                    targetNode.removeChild(destNode);
                    invalidate(dest);
                }
            }
            // Rename sourceNode to filename
            Node<?> parentNode = sourceNode.getParent();
            if (parentNode != null) {
                parentNode.removeChild(sourceNode);
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            Node<?> renamedNode = new Node(targetNode, sourceNode, filename);
            Action action = new Action(ACTION_MOVE, renamedNode);
            action.setSourcePath(source);
            if (!dest.equals(renamedNode.getFullPath())) {
                throw new IllegalStateException(String.format(Locale.ROOT, "Invalid destination for the renamed node. Required: %s, was: %s", dest, renamedNode.getFullPath()));
            }
            addAction(dest, action);
            // Check if it's cached
            FileCacheItem cache = mFileCacheMap.remove(source);
            if (cache != null) {
                // Cache exists, alter it
                mFileCacheMap.put(dest, cache);
            }
            targetNode.addChild(renamedNode);
            invalidate(source);
        }
        return true;
    }

    private void moveChildren(@NonNull Node<?> parentNode, @NonNull String sourceBase, @NonNull String destBase) {
        Node<?>[] children = parentNode.listChildren();
        if (children == null) {
            return;
        }
        for (Node<?> node : children) {
            moveChildren(node, sourceBase, destBase);
            // Move this node
            String source = node.getFullPath();
            String dest = new File(destBase, Paths.relativePath(sourceBase, source)).getAbsolutePath();
            Action action = new Action(ACTION_MOVE, node);
            action.setSourcePath(source);
            addAction(dest, action);
            // Check if it's cached
            FileCacheItem cache = mFileCacheMap.remove(source);
            if (cache != null) {
                // Cache exists, alter it
                mFileCacheMap.put(dest, cache);
            }
            invalidate(source);
            invalidate(dest);
        }
    }

    public boolean setLastModified(String path, long time) {
        checkMounted();
        return false;
    }

    public boolean setReadOnly(String path) {
        checkMounted();
        return false;
    }

    public boolean setWritable(String path, boolean writable, boolean ownerOnly) {
        checkMounted();
        return false;
    }

    public boolean setReadable(String path, boolean readable, boolean ownerOnly) {
        checkMounted();
        return false;
    }

    public boolean setExecutable(String path, boolean executable, boolean ownerOnly) {
        checkMounted();
        return false;
    }

    public abstract boolean checkAccess(String path, int access);

//    public abstract long getTotalSpace(String path);
//
//    public abstract long getFreeSpace(String path);
//
//    public abstract long getUsableSpace(String path);

    @SuppressWarnings("OctalInteger")
    public int getMode(String path) {
        Node<?> targetNode = getNode(path);
        if (targetNode == null) {
            return 0;
        }
        int mode = Objects.requireNonNull(getOptions()).mode & 0777;
        if (mode == 0) {
            mode = getOptions().readWrite ? 0666 : 0444;
        }
        if (targetNode.isDirectory()) {
            return mode | OsConstants.S_IFDIR;
        }
        if (targetNode.isFile()) {
            return mode | OsConstants.S_IFREG;
        }
        return 0;
    }

    public void setMode(String path, int mode) {
        // TODO: 7/12/22 This should either throw ErrnoException or a boolean value
        checkMounted();
    }

    @Nullable
    public UidGidPair getUidGid(String path) {
        checkMounted();
        return Objects.requireNonNull(getOptions()).uidGidPair;
    }

    public void setUidGid(String path, int uid, int gid) {
        // TODO: 7/12/22 This should either throw ErrnoException or a boolean value
        checkMounted();
    }

    public boolean createLink(String link, String target, boolean soft) {
        checkMounted();
        return false;
    }

    /* I/O APIs */
    @NonNull
    public FileInputStream newInputStream(String path) throws IOException {
        if (!checkAccess(path, OsConstants.R_OK)) {
            throw new IOException(path + " is inaccessible.");
        }
        Node<?> targetNode = getNode(path);
        if (targetNode == null) {
            throw new FileNotFoundException(path + " does not exist.");
        }
        if (!targetNode.isFile()) {
            throw new IOException(path + " is not a file.");
        }
        return new FileInputStream(getCachedFile(targetNode, false));
    }

    @NonNull
    public FileOutputStream newOutputStream(String path, boolean append) throws IOException {
        if (!checkAccess(path, OsConstants.W_OK)) {
            throw new IOException(path + " is inaccessible.");
        }
        Node<?> targetNode = getNode(path);
        if (targetNode == null) {
            throw new FileNotFoundException(path + " does not exist.");
        }
        if (!targetNode.isFile()) {
            throw new IOException(path + " is not a file.");
        }
        return new FileOutputStream(getCachedFile(targetNode, true), append);
    }

    @NonNull
    public FileChannel openChannel(String path, int mode) throws IOException {
        boolean read = false;
        boolean write = false;
        if ((mode & MODE_READ_WRITE) != 0) {
            read = true;
            write = true;
        } else if ((mode & MODE_READ_ONLY) != 0) {
            read = true;
        } else if ((mode & MODE_WRITE_ONLY) != 0) {
            write = true;
        } else {
            throw new IllegalArgumentException("Bad mode: " + mode);
        }

        if (read && write && !checkAccess(path, OsConstants.R_OK | OsConstants.W_OK)) {
            throw new IOException(path + " cannot be opened for both reading and writing.");
        } else if (read && !checkAccess(path, OsConstants.R_OK)) {
            throw new IOException(path + " cannot be opened for both reading.");
        } else if (write && !checkAccess(path, OsConstants.W_OK)) {
            throw new IOException(path + " cannot be opened for both writing.");
        }
        Node<?> targetNode = getNode(path);
        if (targetNode == null) {
            throw new FileNotFoundException(path + " does not exist.");
        }
        if (!targetNode.isFile()) {
            throw new IOException(path + " is not a file.");
        }
        return FileSystemManager.getLocal().openChannel(getCachedFile(targetNode, write), mode);
    }

    @NonNull
    public ParcelFileDescriptor openFileDescriptor(String path, int mode) throws IOException {
        boolean read = false;
        boolean write = false;
        if ((mode & MODE_READ_WRITE) != 0) {
            read = true;
            write = true;
        } else if ((mode & MODE_READ_ONLY) != 0) {
            read = true;
        } else if ((mode & MODE_WRITE_ONLY) != 0) {
            write = true;
        } else {
            throw new IllegalArgumentException("Bad mode: " + mode);
        }

        if (read && write && !checkAccess(path, OsConstants.R_OK | OsConstants.W_OK)) {
            write = false;
            // The below does not work with ContentProvider, only disable writing
            // throw new IOException(path + " cannot be opened for both reading and writing.");
        }
        if (read && !checkAccess(path, OsConstants.R_OK)) {
            throw new IOException(path + " cannot be opened for both reading.");
        }
        if (write && !checkAccess(path, OsConstants.W_OK)) {
            throw new IOException(path + " cannot be opened for both writing.");
        }
        Node<?> targetNode = getNode(path);
        if (targetNode == null) {
            throw new FileNotFoundException(path + " does not exist.");
        }
        if (!targetNode.isFile()) {
            throw new IOException(path + " is not a file.");
        }
        return ParcelFileDescriptor.open(getCachedFile(targetNode, write), mode);
    }

    @NonNull
    protected abstract InputStream getInputStream(@NonNull Node<?> node) throws IOException;

    @Nullable
    protected File findCachedFile(@NonNull Node<?> node) {
        FileCacheItem fileCacheItem = mFileCacheMap.get(node.getFullPath());
        return fileCacheItem != null ? fileCacheItem.cachedFile : null;
    }

    private File getCachedFile(@NonNull Node<?> node, boolean write) throws IOException {
        FileCacheItem fileCacheItem = mFileCacheMap.get(node.getFullPath());
        if (fileCacheItem != null) {
            if (write) {
                fileCacheItem.setModified(true);
            }
            return fileCacheItem.cachedFile;
        }
        // File hasn't been cached.
        File cachedFile = mFileCache.createCachedFile(Paths.getPathExtension(node.getName()));
        if (node.isPhysical()) {
            // The file exists physically. It has to be cached first.
            try (InputStream is = getInputStream(node);
                 FileOutputStream os = new FileOutputStream(cachedFile)) {
                IoUtils.copy(is, os);
            }
        }
        fileCacheItem = new FileCacheItem(cachedFile);
        if (write) {
            fileCacheItem.setModified(true);
        }
        mFileCacheMap.put(node.getFullPath(), fileCacheItem);
        return fileCacheItem.cachedFile;
    }

    /* File tree */
    protected static class Node<T> {
        // TODO: 23/11/22 Should include modification, creation, access times
        @NonNull
        private final String mName;
        @Nullable
        private final T mObject;
        private final boolean mPhysical;
        @Nullable
        private final String mPhysicalFullPath;

        @Nullable
        private Node<T> mParent;
        @Nullable
        private HashMap<String, Node<T>> mChildren = null;
        private boolean mDirectory;

        protected Node(@Nullable Node<T> parent, @NonNull String name) {
            this(parent, name, null);
        }

        protected Node(@Nullable Node<T> parent, @NonNull String name, boolean physical) {
            this(parent, name, null, physical);
        }

        protected Node(@Nullable Node<T> parent, @NonNull String name, @Nullable T object) {
            this(parent, name, object, true);
        }

        protected Node(@Nullable Node<T> parent, @NonNull Node<T> node, @NonNull String newName) {
            this(parent, newName, node.mObject, node.mPhysical);
            mChildren = node.mChildren;
            mDirectory = node.mDirectory;
            if (mChildren != null) {
                for (Node<T> child : mChildren.values()) {
                    child.mParent = this;
                }
            }
        }

        protected Node(@Nullable Node<T> parent, @NonNull String name, @Nullable T object, boolean physical) {
            mParent = parent;
            mName = name;
            mObject = object;
            mPhysical = physical;
            mDirectory = object == null;
            mPhysicalFullPath = physical ? calculateFullPath(parent, this) : null;
        }

        @NonNull
        public String getName() {
            return mName;
        }

        @Nullable
        public Node<T> getParent() {
            return mParent;
        }

        @NonNull
        public String getFullPath() {
            return calculateFullPath(mParent, this);
        }

        @Nullable
        public String getPhysicalFullPath() {
            return mPhysicalFullPath;
        }

        @Nullable
        public T getObject() {
            return mObject;
        }

        public boolean isPhysical() {
            return mPhysical;
        }

        public void setDirectory(boolean directory) {
            mDirectory = directory;
        }

        public void setFile(boolean file) {
            mDirectory = !file;
        }

        public boolean isDirectory() {
            return mDirectory;
        }

        public boolean isFile() {
            return !mDirectory;
        }

        @Nullable
        public Node<T> getChild(String name) {
            if (mChildren == null) return null;
            return mChildren.get(name);
        }

        @Nullable
        public Node<T> getLastChild(@Nullable String name) {
            if (mChildren == null) return null;
            return getLastNode(this, name);
        }

        @SuppressWarnings("unchecked")
        @Nullable
        public Node<T>[] listChildren() {
            if (mChildren == null || mChildren.size() == 0) return null;
            return mChildren.values().toArray(new Node[0]);
        }

        @SuppressWarnings("unchecked")
        public void addChild(@Nullable Node<?> child) {
            if (child == null) return;
            if (mChildren == null) mChildren = new HashMap<>();
            Node<T> node = (Node<T>) child;
            node.mParent = this;
            mChildren.put(node.mName, node);
        }

        public void removeChild(@Nullable Node<?> child) {
            if (child == null || mChildren == null) return;
            mChildren.remove(child.mName);
            child.mParent = null;
        }

        private static String calculateFullPath(@Nullable Node<?> parent, @NonNull Node<?> child) {
            String basePath = parent == null ? File.separator : parent.getFullPath();
            return (basePath.equals(File.separator) ? (child.mName.equals(File.separator) ? "" : File.separator)
                    : basePath + File.separatorChar) + child.mName;
        }

        @SuppressWarnings("SuspiciousRegexArgument") // Not on Windows
        @Nullable
        private static <T> Node<T> getLastNode(@NonNull Node<T> baseNode, @Nullable String dirtyPath) {
            if (dirtyPath == null) return baseNode;
            String path = Paths.sanitize(dirtyPath, true);
            if (path == null) {
                return baseNode;
            }
            String[] components = path.split(File.separator);
            Node<T> lastNode = baseNode;
            for (String component : components) {
                lastNode = lastNode.getChild(component);
                if (lastNode == null) {
                    // File do not exist
                    return null;
                }
            }
            return lastNode;
        }
    }

    public static class NotMountedException extends RuntimeException {
        public NotMountedException() {
            super();
        }

        public NotMountedException(String message) {
            super(message);
        }

        public NotMountedException(String message, Throwable th) {
            super(message, th);
        }
    }
}
