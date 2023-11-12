// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io.fs;

import android.os.Build;
import android.system.OsConstants;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.j256.simplemagic.ContentType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

class ZipFileSystem extends VirtualFileSystem {
    public static final String TYPE = ContentType.ZIP.getMimeType();

    private static class VirtualZipEntry extends ZipEntry {
        private File mCachedFile;

        public VirtualZipEntry(String name) {
            super(name);
        }

        public File getCachedFile() {
            return mCachedFile;
        }

        public void setCachedFile(File cachedFile) {
            mCachedFile = cachedFile;
        }
    }

    private final LruCache<String, Node<ZipEntry>> mCache = new LruCache<>(100);
    @Nullable
    private ZipFile mZipFile;
    @Nullable
    private Node<ZipEntry> mRootNode;

    protected ZipFileSystem(@NonNull Path zipFile) {
        super(zipFile);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected Path onMount() throws IOException {
        if (Objects.requireNonNull(getOptions()).remount && mZipFile != null && mRootNode != null) {
            // Remount requested, no need to generate anything if they're already generated.
            return Paths.get(this);
        }
        mZipFile = new ZipFile(Objects.requireNonNull(getFile().getFile()));
        mRootNode = buildTree(Objects.requireNonNull(mZipFile));
        return Paths.get(this);
    }

    @Nullable
    @Override
    protected File onUnmount(@NonNull Map<String, List<Action>> actionList) throws IOException {
        File cachedFile = getUpdatedZipFile(actionList);
        mRootNode = null;
        mCache.evictAll();
        if (mZipFile != null) {
            mZipFile.close();
            mZipFile = null;
        }
        return cachedFile;
    }

    @Nullable
    private File getUpdatedZipFile(@NonNull Map<String, List<Action>> actionList) throws IOException {
        if (!Objects.requireNonNull(getOptions()).readWrite || actionList.isEmpty()) {
            return null;
        }
        String extension = getFile().getExtension();
        File file = FileCache.getGlobalFileCache().createCachedFile(extension);
        Map<String, ZipEntry> zipEntries = new HashMap<>();
        for (ZipEntry zipEntry : Collections.list(Objects.requireNonNull(mZipFile).entries())) {
            zipEntries.put(Paths.sanitize(File.separator + zipEntry.getName(), false), zipEntry);
        }
        for (String path : actionList.keySet()) {
            // Perform action for each path
            List<Action> actions = actionList.get(path);
            if (actions == null) continue;
            for (Action action : actions) {
                Node<?> targetNode = action.targetNode;
                // Actions are linear
                switch (action.action) {
                    case ACTION_CREATE:
                        // This must be a new file/folder. So, override the existing one.
                        zipEntries.put(targetNode.getFullPath(), getNewZipEntry(targetNode));
                        break;
                    case ACTION_DELETE:
                        // Delete the entry
                        zipEntries.remove(targetNode.getFullPath());
                        break;
                    case ACTION_UPDATE:
                        // It's a file and it's updated. So, cached file must exist.
                        File cachedFile = Objects.requireNonNull(action.getCachedPath());
                        zipEntries.put(targetNode.getFullPath(), getZipEntry(targetNode, cachedFile));
                        break;
                    case ACTION_MOVE:
                        // File/directory move
                        String sourcePath = Objects.requireNonNull(action.getSourcePath());
                        ZipEntry zipEntry = zipEntries.get(sourcePath);
                        if (zipEntry != null) {
                            zipEntries.put(targetNode.getFullPath(), zipEntry);
                        } else {
                            zipEntries.put(targetNode.getFullPath(), getNewZipEntry(targetNode));
                        }
                        zipEntries.remove(sourcePath);
                        break;
                }
            }
        }
        try (FileOutputStream os = new FileOutputStream(file);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            zos.setMethod(ZipOutputStream.DEFLATED);
            zos.setLevel(Deflater.BEST_COMPRESSION);
            List<String> paths = new ArrayList<>(zipEntries.keySet());
            Collections.sort(paths);
            for (String path : paths) {
                ZipEntry zipEntry = zipEntries.get(path);
                if (zipEntry == null) continue;
                if (zipEntry instanceof VirtualZipEntry) {
                    // Our custom zip files
                    zos.putNextEntry(zipEntry);
                    if (zipEntry.isDirectory()) {
                        zos.closeEntry();
                        continue;
                    }
                    // Entry is a file
                    File cachedFile = ((VirtualZipEntry) zipEntry).getCachedFile();
                    if (cachedFile != null) {
                        try (InputStream is = new FileInputStream(cachedFile)) {
                            IoUtils.copy(is, zos);
                        }
                    } // else cached file was not created because the file was only created and never written to
                    zos.closeEntry();
                } else {
                    // Not our custom files, need to copy from zipEntry everything except the name
                    ZipEntry newZipEntry = getZipEntry(path, zipEntry);
                    zos.putNextEntry(newZipEntry);
                    if (zipEntry.isDirectory()) {
                        zos.closeEntry();
                        continue;
                    }
                    // Entry is a file
                    try (InputStream is = mZipFile.getInputStream(zipEntry)) {
                        IoUtils.copy(is, zos);
                    }
                    zos.closeEntry();
                }
            }
        }
        return file;
    }

    @NonNull
    private ZipEntry getNewZipEntry(@NonNull Node<?> node) {
        String name = Paths.sanitize(node.getFullPath(), false);
        if (node.isDirectory()) {
            name += File.separator;
        }
        ZipEntry zipEntry = new VirtualZipEntry(name);
        zipEntry.setMethod(ZipEntry.DEFLATED);
        if (node.isFile()) {
            zipEntry.setSize(0L);
        }
        zipEntry.setTime(System.currentTimeMillis());
        return zipEntry;
    }

    @NonNull
    private ZipEntry getZipEntry(@NonNull Node<?> node, @NonNull File cachedFile) throws IOException {
        String name = Paths.sanitize(node.getFullPath(), false);
        if (node.isDirectory()) {
            name += File.separator;
        }
        VirtualZipEntry zipEntry = new VirtualZipEntry(name);
        zipEntry.setMethod(ZipEntry.DEFLATED);
        zipEntry.setCachedFile(cachedFile);
        zipEntry.setSize(cachedFile.length());
        zipEntry.setCrc(DigestUtils.calculateCrc32(Paths.get(cachedFile)));
        zipEntry.setTime(cachedFile.lastModified());
        return zipEntry;
    }

    @NonNull
    private ZipEntry getZipEntry(@NonNull String path, @NonNull ZipEntry zipEntry) {
        String name = Paths.sanitize(File.separator + path, false);
        if (zipEntry.isDirectory()) {
            name += File.separator;
        }
        ZipEntry zipEntry1 = new VirtualZipEntry(name);
        zipEntry1.setMethod(ZipEntry.DEFLATED);
        zipEntry1.setSize(zipEntry.getSize());
        zipEntry1.setCrc(zipEntry.getCrc());
        zipEntry1.setTime(zipEntry.getTime());
        zipEntry1.setComment(zipEntry.getComment());
        zipEntry1.setExtra(zipEntry.getExtra());
        return zipEntry1;
    }

    @Nullable
    @Override
    protected Node<?> getNode(String path) {
        checkMounted();
        Node<ZipEntry> targetNode = mCache.get(path);
        if (targetNode == null) {
            if (path.equals(File.separator)) {
                targetNode = mRootNode;
            } else {
                targetNode = Objects.requireNonNull(mRootNode).getLastChild(Paths.sanitize(path, true));
            }
            if (targetNode != null) {
                mCache.put(path, targetNode);
            }
        }
        return targetNode;
    }

    @Override
    protected void invalidate(String path) {
        mCache.remove(path);
    }

    @Override
    public long lastModified(@NonNull Node<?> node) {
        if (node.getObject() == null) {
            return getFile().lastModified();
        }
        return ((ZipEntry) node.getObject()).getTime();
    }

    @Override
    public long lastAccess(String path) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return lastModified(path);
        }
        Node<?> targetNode = getNode(path);
        if (targetNode == null || targetNode.getObject() == null) {
            return getFile().lastModified();
        }
        FileTime ft = ((ZipEntry) targetNode.getObject()).getLastAccessTime();
        if (ft != null) {
            return ft.toMillis();
        }
        return ((ZipEntry) targetNode.getObject()).getTime();
    }

    @Override
    public long creationTime(String path) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return lastModified(path);
        }
        Node<?> targetNode = getNode(path);
        if (targetNode == null || targetNode.getObject() == null) {
            return getFile().lastModified();
        }
        FileTime ft = ((ZipEntry) targetNode.getObject()).getCreationTime();
        if (ft != null) {
            return ft.toMillis();
        }
        return ((ZipEntry) targetNode.getObject()).getTime();
    }

    @Override
    public long length(String path) {
        Node<?> targetNode = getNode(path);
        if (targetNode == null) {
            return -1;
        }
        if (targetNode.isDirectory()) {
            return 0;
        }
        if (!targetNode.isFile()) {
            return -1;
        }
        if (targetNode.getObject() == null) {
            // Check for cache
            File cachedFile = findCachedFile(targetNode);
            if (cachedFile != null) {
                return cachedFile.length();
            }
            return targetNode.isPhysical() ? -1 : 0;
        }
        return ((ZipEntry) targetNode.getObject()).getSize();
    }

    @Override
    public boolean checkAccess(String path, int access) {
        Node<?> targetNode = getNode(path);
        if (access == OsConstants.F_OK) {
            return targetNode != null;
        }
        if (access == OsConstants.R_OK) {
            return true;
        }
        if (access == OsConstants.W_OK) {
            return Objects.requireNonNull(getOptions()).readWrite;
        }
        if (access == (OsConstants.R_OK | OsConstants.W_OK)) {
            return Objects.requireNonNull(getOptions()).readWrite;
        }
        // X_OK, R_OK|X_OK, R_OK|W_OK|X_OK are false
        return false;
    }

    @NonNull
    @Override
    protected InputStream getInputStream(@NonNull Node<?> node) throws IOException {
        ZipEntry zipEntry = (ZipEntry) node.getObject();
        if (zipEntry == null) {
            throw new FileNotFoundException("Class definition for " + node.getFullPath() + " is not found.");
        }
        return Objects.requireNonNull(mZipFile).getInputStream(zipEntry);
    }

    @NonNull
    private static Node<ZipEntry> buildTree(@NonNull ZipFile zipFile) {
        Node<ZipEntry> rootNode = new Node<>(null, File.separator);
        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
        while (zipEntries.hasMoreElements()) {
            ZipEntry zipEntry = zipEntries.nextElement();
            buildTree(rootNode, zipEntry);
        }
        return rootNode;
    }

    // Build nodes as needed by the entry, entry itself is the last node in the tree if it is not a directory
    private static void buildTree(@NonNull Node<ZipEntry> rootNode, @NonNull ZipEntry zipEntry) {
        String filename = Paths.sanitize(zipEntry.getName(), true);
        if (filename == null) {
            return;
        }
        String[] components = filename.split(File.separator);
        if (components.length < 1) return;
        Node<ZipEntry> lastNode = rootNode;
        for (int i = 0; i < components.length - 1 /* last one will be set manually */; ++i) {
            Node<ZipEntry> newNode = lastNode.getChild(components[i]);
            if (newNode == null) {
                // Add children
                newNode = new Node<>(lastNode, components[i]);
                lastNode.addChild(newNode);
            }
            lastNode = newNode;
        }
        lastNode.addChild(new Node<>(lastNode, components[components.length - 1],
                zipEntry.isDirectory() ? null : zipEntry));
    }
}
