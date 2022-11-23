// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io.fs;

import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;

import androidx.annotation.CallSuper;
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
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.FileSystemManager;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.UidGidPair;

import static io.github.muntashirakon.io.FileSystemManager.MODE_READ_ONLY;
import static io.github.muntashirakon.io.FileSystemManager.MODE_READ_WRITE;
import static io.github.muntashirakon.io.FileSystemManager.MODE_WRITE_ONLY;

@SuppressWarnings("unused")
public abstract class VirtualFileSystem {
    public static final String TAG = VirtualFileSystem.class.getSimpleName();

    public static Uri getUri(int fsId, @Nullable String path) {
        // Force authority by adding `//`
        if (path == null) {
            return Uri.parse("vfs://" + fsId + File.separator);
        }
        return Uri.parse("vfs://" + fsId + (path.startsWith(File.separator) ? path :
                (File.separator + path)));
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

    private static final SparseArrayCompat<VirtualFileSystem> fileSystems = new SparseArrayCompat<>(3);
    private static final HashMap<Uri, Integer> uriVfsIdsMap = new HashMap<>(3);
    private static final HashMap<Uri, List<Integer>> parentUriVfsIdsMap = new HashMap<>(3);

    public static int mount(@NonNull Uri mountPoint, @NonNull Path file, @NonNull String type) throws IOException {
        return mount(mountPoint, getNewInstance(file, type));
    }

    @WorkerThread
    public static int mount(@NonNull Uri mountPoint, @NonNull VirtualFileSystem fs) throws IOException {
        int vfsId;
        synchronized (fileSystems) {
            synchronized (uriVfsIdsMap) {
                if (uriVfsIdsMap.get(mountPoint) != null) {
                    throw new IOException(String.format("Mount point (%s) is already in use.", mountPoint));
                }
            }
            do {
                vfsId = ThreadLocalRandom.current().nextInt();
            } while (vfsId == 0 || fileSystems.get(vfsId) != null);
            fs.mount(mountPoint, vfsId);
            fileSystems.put(vfsId, fs);
        }
        synchronized (uriVfsIdsMap) {
            uriVfsIdsMap.put(mountPoint, vfsId);
        }
        synchronized (parentUriVfsIdsMap) {
            Uri uri = FileUtils.removeLastPathSegment(mountPoint);
            List<Integer> vfsIds = parentUriVfsIdsMap.get(uri);
            if (vfsIds == null) {
                vfsIds = new ArrayList<>(1);
                parentUriVfsIdsMap.put(uri, vfsIds);
            }
            vfsIds.add(vfsId);
        }
        Log.d(TAG, String.format(Locale.ROOT, "Mounted %d at %s", vfsId, mountPoint));
        return vfsId;
    }

    @WorkerThread
    public static void unmount(int vfsId) throws Throwable {
        VirtualFileSystem fs = getFileSystem(vfsId);
        if (fs == null) return;
        Uri mountPoint = fs.getMountPoint();
        fs.unmount();
        synchronized (fileSystems) {
            fileSystems.remove(vfsId);
        }
        synchronized (uriVfsIdsMap) {
            uriVfsIdsMap.remove(mountPoint);
        }
        synchronized (parentUriVfsIdsMap) {
            if (mountPoint != null) {
                Uri uri = FileUtils.removeLastPathSegment(mountPoint);
                List<Integer> vfsIds = parentUriVfsIdsMap.get(uri);
                if (vfsIds != null && vfsIds.contains(vfsId)) {
                    if (vfsIds.size() == 1) parentUriVfsIdsMap.remove(uri);
                    else vfsIds.remove((Integer) vfsId);
                }
            }
        }
        Log.d(TAG, String.format(Locale.ROOT, "%d unmounted at %s", vfsId, mountPoint));
    }

    public static void alterMountPoint(Uri oldMountPoint, Uri newMountPoint) {
        VirtualFileSystem fs = getFileSystem(oldMountPoint);
        if (fs == null) return;
        synchronized (uriVfsIdsMap) {
            uriVfsIdsMap.remove(oldMountPoint);
            uriVfsIdsMap.put(newMountPoint, fs.getFsId());
        }
        synchronized (parentUriVfsIdsMap) {
            // Remove old mount point
            Uri oldParent = FileUtils.removeLastPathSegment(oldMountPoint);
            List<Integer> oldFsIds = parentUriVfsIdsMap.get(oldParent);
            if (oldFsIds != null) {
                oldFsIds.remove((Integer) fs.getFsId());
            }
            // Add new mount point
            Uri newParent = FileUtils.removeLastPathSegment(newMountPoint);
            List<Integer> newFsIds = parentUriVfsIdsMap.get(newParent);
            if (newFsIds == null) {
                newFsIds = new ArrayList<>(1);
                parentUriVfsIdsMap.put(newParent, newFsIds);
            }
            newFsIds.add(fs.getFsId());
        }
        fs.mountPoint = newMountPoint;
        Log.d(TAG, String.format(Locale.ROOT, "Mount point of %d altered from %s to %s", fs.getFsId(),
                oldMountPoint, newMountPoint));
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
        synchronized (uriVfsIdsMap) {
            vfsId = uriVfsIdsMap.get(mountPoint);
        }
        if (vfsId == null) return null;
        return getFsRoot(vfsId);
    }

    @Nullable
    public static VirtualFileSystem getFileSystem(Uri mountPoint) {
        Integer vfsId;
        synchronized (uriVfsIdsMap) {
            vfsId = uriVfsIdsMap.get(mountPoint);
        }
        if (vfsId == null) return null;
        return getFileSystem(vfsId);
    }

    @Nullable
    public static VirtualFileSystem getFileSystem(int vfsId) {
        synchronized (fileSystems) {
            return fileSystems.get(vfsId);
        }
    }

    @NonNull
    public static VirtualFileSystem[] getFileSystemsAtUri(Uri parentUri) {
        List<Integer> vfsIds;
        synchronized (parentUriVfsIdsMap) {
            vfsIds = parentUriVfsIdsMap.get(parentUri);
        }
        if (vfsIds == null) return ArrayUtils.emptyArray(VirtualFileSystem.class);
        VirtualFileSystem[] fs = new VirtualFileSystem[vfsIds.size()];
        synchronized (fileSystems) {
            for (int i = 0; i < fs.length; ++i) {
                fs[i] = fileSystems.get(vfsIds.get(i));
            }
        }
        return fs;
    }

    /* Static members ends */

    @IntDef({ACTION_CREATE, ACTION_UPDATE, ACTION_DELETE})
    @Retention(RetentionPolicy.SOURCE)
    protected @interface CrudAction {
    }

    /**
     * Create a new path in the system. {@link Action#targetPath} for this action may not exist.
     */
    protected static final int ACTION_CREATE = 1;
    protected static final int ACTION_UPDATE = 2;
    protected static final int ACTION_DELETE = 3;

    protected static class Action {
        @CrudAction
        public final int action;
        /**
         * Target path within this file system. It may or may not be an actual path depending on the previous actions.
         */
        @NonNull
        public final Node<?> targetPath;

        /**
         * Cached path is located outside the file system, in a physical location. Contents of this path is later used
         * to modify the virtual file system.
         */
        @Nullable
        private File cachedPath;

        public Action(@CrudAction int action, @NonNull Node<?> targetNode) {
            this.action = action;
            this.targetPath = targetNode;
        }

        public void setCachedPath(@Nullable File cachedPath) {
            this.cachedPath = cachedPath;
        }

        @Nullable
        public File getCachedPath() {
            return cachedPath;
        }
    }

    private static class FileCache {
        public final File cachedFile;

        private boolean modified;

        private FileCache(File cachedFile) {
            this.cachedFile = cachedFile;
        }

        public void setModified(boolean modified) {
            this.modified = modified;
        }

        public boolean isModified() {
            return modified;
        }
    }

    @NonNull
    private final List<Action> actions = new ArrayList<>();
    private final Map<String, FileCache> fileCacheMap = new HashMap<>();

    @Nullable
    private Uri mountPoint;
    private int fsId = 0;
    private Path rootPath;

    protected VirtualFileSystem() {
    }

    public abstract String getType();

    public int getFsId() {
        return fsId;
    }

    /**
     * Return the abstract location where the file system is mounted. This is similar to the mount point returned by the
     * {@code mount} command.
     */
    @Nullable
    public final Uri getMountPoint() {
        return mountPoint;
    }

    /**
     * Return the abstract location where the file system is located. This is not the same as {@link #getMountPoint()}
     * which returns the abstract location where the file system is mounted. In other words, this is the real location
     * to the file system, and should never be used other than the Path APIs.
     */
    @NonNull
    public Path getRootPath() {
        return Objects.requireNonNull(rootPath);
    }

    /**
     * Mount the file system.
     *
     * @param fsId Unique file system ID to locate it.
     * @throws IOException If the file system cannot be mounted.
     */
    private void mount(@NonNull Uri mountPoint, int fsId) throws IOException {
        this.mountPoint = mountPoint;
        this.fsId = fsId;
        onPreMount();
        this.rootPath = onMount();
        onMounted();
    }


    /**
     * Instructions to execute before mounting the file system.
     * <p>
     * Note that the root path haven't been initialised at this point and calling {@link #getRootPath()} would throw
     * an NPE.
     */
    protected void onPreMount() throws IOException {
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
    protected void onMounted() throws IOException {
    }

    private void unmount() throws Throwable {
        // Update actions
        for (String path : fileCacheMap.keySet()) {
            FileCache fileCache = Objects.requireNonNull(fileCacheMap.get(path));
            if (fileCache.isModified()) {
                Node<?> node = getNode(path);
                if (node != null) {
                    Action action = new Action(ACTION_UPDATE, node);
                    action.setCachedPath(fileCache.cachedFile);
                    actions.add(action);
                }
            }
        }
        onUnmount(actions);
        this.fsId = 0;
        this.mountPoint = null;
        // Cleanup
        for (FileCache fileCache : fileCacheMap.values()) {
            fileCache.cachedFile.delete();
        }
        fileCacheMap.clear();
        actions.clear();
        onUnmounted();
    }

    /**
     * Instructions to execute to unmount the file system. Example operations include cleaning up trees, saving
     * changes, closing resources.
     */
    protected abstract void onUnmount(List<Action> actions) throws Throwable;

    /**
     * Instructions to execute after unmounting the file system.
     */
    protected void onUnmounted() throws Throwable {
    }

    /* File APIs */
    @Nullable
    protected abstract Node<?> getNode(String path);

    @Nullable
    public String getCanonicalPath(String path) {
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

    public abstract long lastModified(String path);

    public long lastAccess(String path) {
        return lastModified(path);
    }

    public long creationTime(String path) {
        return lastModified(path);
    }

    public abstract long length(String path);

    @CallSuper
    public boolean createNewFile(String path) {
        String filename = FileUtils.getLastPathComponent(path);
        String parent = FileUtils.removeLastPathSegment(path);
        if (!checkAccess(parent, OsConstants.W_OK)) {
            return false;
        }
        Node<?> parentNode = getNode(parent);
        if (parentNode == null || !parentNode.isDirectory()) {
            return false;
        }
        Node<?> newFileNode = new Node<>(parentNode, filename, false);
        newFileNode.setFile(true);
        actions.add(new Action(ACTION_CREATE, newFileNode));
        return true;
    }

    @CallSuper
    public boolean delete(String path) {
        if (!checkAccess(path, OsConstants.W_OK)) {
            return false;
        }
        Node<?> targetNode = getNode(path);
        if (targetNode == null) {
            return false;
        }
        actions.add(new Action(ACTION_DELETE, targetNode));
        if (targetNode.isPhysical()) {
            // This file exists physically, we mustn't remove from the tree
            targetNode.markDeleted(true);
        } else {
            // This file was created at some point in the past using #createNewFile(String)
            Node<?> parentNode = targetNode.getParent();
            if (parentNode != null) {
                parentNode.removeChild(targetNode);
            }
        }
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
        String filename = FileUtils.getLastPathComponent(path);
        String parent = FileUtils.removeLastPathSegment(path);
        if (!checkAccess(parent, OsConstants.W_OK)) {
            return false;
        }
        Node<?> parentNode = getNode(parent);
        if (parentNode == null || !parentNode.isDirectory()) {
            return false;
        }
        Node<?> newFileNode = new Node<>(parentNode, filename, false);
        newFileNode.setDirectory(true);
        actions.add(new Action(ACTION_CREATE, newFileNode));
        return true;
    }

    public boolean mkdirs(String path) {
        if (checkAccess(path, OsConstants.F_OK)) {
            // File/folder exists
            return false;
        }
        List<String> parts = new ArrayList<>();
        String parent;
        Node<?> parentNode;
        do {
            String filename = FileUtils.getLastPathComponent(path);
            parent = FileUtils.removeLastPathSegment(path);
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
            actions.add(new Action(ACTION_CREATE, parentNode));
        }
        return true;
    }

    public boolean renameTo(String path, String dest) {
        // path and dest must belong to the same file system and are relative as usual.
        if (!checkAccess(path, OsConstants.W_OK)) {
            // Directory not modifiable
            return false;
        }
        if (checkAccess(dest, OsConstants.F_OK)) {
            // Destination file/folder exists
            return false;
        }
        Node<?> sourceNode = getNode(path);
        if (sourceNode == null) {
            return false;
        }
        if (sourceNode.isDirectory()) {
            // Source node is a directory, so create some directories and move whatever this directory has.
            String filename = FileUtils.getLastPathComponent(dest);
            String parent = FileUtils.removeLastPathSegment(dest);
            if (!mkdirs(parent)) {
                return false;
            }
            Node<?> targetNode = getNode(parent);
            if (targetNode == null) {
                return false;
            }
            // Rename sourceNode to filename
            Node<?> parentNode = sourceNode.getParent();
            if (parentNode != null) {
                actions.add(new Action(ACTION_DELETE, sourceNode));
                parentNode.removeChild(sourceNode);
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            Node<?> renamedNode = new Node(sourceNode.getParent(), sourceNode, filename);
            actions.add(new Action(ACTION_CREATE, renamedNode));
            targetNode.addChild(renamedNode);
        } else if (sourceNode.isFile()) {
            // Source node is a file, create some directories up to this point and move this file to there.
            String filename = FileUtils.getLastPathComponent(dest);
            String parent = FileUtils.removeLastPathSegment(dest);
            // Output of mkdirs is not relevant
            mkdirs(parent);
            Node<?> targetNode = getNode(parent);
            if (targetNode == null) {
                return false;
            }
            // Rename sourceNode to filename
            Node<?> parentNode = sourceNode.getParent();
            if (parentNode != null) {
                actions.add(new Action(ACTION_DELETE, sourceNode));
                parentNode.removeChild(sourceNode);
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            Node<?> renamedNode = new Node(sourceNode.getParent(), sourceNode, filename);
            actions.add(new Action(ACTION_CREATE, renamedNode));
            // Check if it's cached
            FileCache cache = fileCacheMap.get(path);
            if (cache != null) {
                // Cache exists, duplicate it
                fileCacheMap.put(dest, cache);
            }
            targetNode.addChild(renamedNode);
        }
        return false;
    }

    public boolean setLastModified(String path, long time) {
        return false;
    }

    public boolean setReadOnly(String path) {
        return false;
    }

    public boolean setWritable(String path, boolean writable, boolean ownerOnly) {
        return false;
    }

    public boolean setReadable(String path, boolean readable, boolean ownerOnly) {
        return false;
    }

    public boolean setExecutable(String path, boolean executable, boolean ownerOnly) {
        return false;
    }

    public abstract boolean checkAccess(String path, int access);

//    public abstract long getTotalSpace(String path);
//
//    public abstract long getFreeSpace(String path);
//
//    public abstract long getUsableSpace(String path);

    public abstract int getMode(String path);

    public void setMode(String path, int mode) {
    }

    @Nullable
    public UidGidPair getUidGid(String path) {
        return null;
    }

    public void setUidGid(String path, int uid, int gid) {
    }

    public boolean createLink(String link, String target, boolean soft) {
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
        return ParcelFileDescriptor.open(getCachedFile(targetNode, write), mode);
    }

    @NonNull
    protected abstract InputStream getInputStream(@NonNull Node<?> node) throws IOException;

    private File getCachedFile(@NonNull Node<?> node, boolean write) throws IOException {
        FileCache fileCache = fileCacheMap.get(node.getFullPath());
        if (fileCache != null) {
            if (write) {
                fileCache.setModified(true);
            }
            return fileCache.cachedFile;
        }
        // File hasn't been cached.
        File cachedFile = FileUtils.getTempFile(FileUtils.getExtension(node.getName()));
        if (node.isPhysical()) {
            // The file exists physically. It has to be cached first.
            try (InputStream is = getInputStream(node);
                 FileOutputStream os = new FileOutputStream(cachedFile)) {
                FileUtils.copy(is, os);
            }
        }
        fileCache = new FileCache(cachedFile);
        if (write) {
            fileCache.setModified(true);
        }
        fileCacheMap.put(node.getFullPath(), fileCache);
        return fileCache.cachedFile;
    }

    /* File tree */
    protected static class Node<T> {
        @NonNull
        private final String name;
        @Nullable
        private final T object;
        private final boolean physical;
        @Nullable
        private final String physicalFullPath;

        @Nullable
        private Node<T> parent;
        @Nullable
        private HashMap<String, Node<T>> children = null;
        private boolean exists = true;
        private boolean directory;

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
            this(parent, newName, node.object, node.physical);
            children = node.children;
            directory = node.directory;
            exists = node.exists;
            if (children != null) {
                for (Node<T> child : children.values()) {
                    child.parent = this;
                }
            }
        }

        protected Node(@Nullable Node<T> parent, @NonNull String name, @Nullable T object, boolean physical) {
            this.parent = parent;
            this.name = name;
            this.object = object;
            this.physical = physical;
            this.directory = object == null;
            this.physicalFullPath = physical ? calculateFullPath(parent, this) : null;
        }

        @NonNull
        public String getName() {
            return name;
        }

        @Nullable
        public Node<T> getParent() {
            return parent;
        }

        @NonNull
        public String getFullPath() {
            return calculateFullPath(parent, this);
        }

        @Nullable
        public String getPhysicalFullPath() {
            return physicalFullPath;
        }

        @Nullable
        public T getObject() {
            return object;
        }

        public boolean isPhysical() {
            return physical;
        }

        public boolean exists() {
            return exists;
        }

        public void markDeleted(boolean delete) {
            exists = !delete;
            if (children == null) {
                return;
            }
            for (Node<T> node : children.values()) {
                node.markDeleted(delete);
            }
        }

        public void setDirectory(boolean directory) {
            this.directory = directory;
        }

        public void setFile(boolean file) {
            this.directory = !file;
        }

        public boolean isDirectory() {
            return exists && directory;
        }

        public boolean isFile() {
            return exists && !directory;
        }

        @Nullable
        public Node<T> getChild(String name) {
            if (children == null) return null;
            return children.get(name);
        }

        @Nullable
        public Node<T> getLastChild(@Nullable String name) {
            if (children == null) return null;
            return getLastNode(this, name);
        }

        @SuppressWarnings("unchecked")
        @Nullable
        public Node<T>[] listChildren() {
            if (children == null) return null;
            return children.values().toArray(new Node[0]);
        }

        @SuppressWarnings("unchecked")
        public void addChild(@Nullable Node<?> child) {
            if (child == null) return;
            if (children == null) children = new HashMap<>();
            Node<T> node = (Node<T>) child;
            node.parent = this;
            children.put(node.name, node);
        }

        public void removeChild(@Nullable Node<?> child) {
            if (child == null || children == null) return;
            children.remove(child.name);
            child.parent = null;
        }

        private static String calculateFullPath(@Nullable Node<?> parent, @NonNull Node<?> child) {
            String basePath = parent == null ? File.separator : parent.getFullPath();
            return (basePath.equals(File.separator) ? (child.name.equals(File.separator) ? "" : File.separator)
                    : basePath + File.separatorChar) + child.name;
        }

        @Nullable
        private static <T> Node<T> getLastNode(@NonNull Node<T> baseNode, @Nullable String dirtyPath) {
            if (dirtyPath == null) return baseNode;
            String[] components = FileUtils.getSanitizedPath(dirtyPath).split(File.separator);
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
