// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io.fs;

import android.net.Uri;
import android.os.Build;
import android.system.OsConstants;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.FileTime;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.Path;

public class ZipFileSystem extends VirtualFileSystem {
    private final LruCache<String, Node<ZipEntry>> cache = new LruCache<>(100);
    @NonNull
    private final File zipFilePath;
    @Nullable
    private ZipFile zipFile;
    @Nullable
    private Node<ZipEntry> rootNode;

    protected ZipFileSystem(@NonNull Uri mountPoint, @NonNull File zipFile) {
        super(mountPoint);
        this.zipFilePath = zipFile;
    }

    @Nullable
    public ZipFile getZipFile() {
        return zipFile;
    }

    @Override
    protected Path onMount() throws IOException {
        zipFile = new ZipFile(zipFilePath);
        rootNode = buildTree(Objects.requireNonNull(zipFile));
        return new Path(AppManager.getContext(), this);
    }

    @Override
    protected void onUnmount(List<Action> actions) throws IOException {
        if (zipFile != null) {
            zipFile.close();
        }
        rootNode = null;
        cache.evictAll();
    }

    @Nullable
    @Override
    protected Node<?> getNode(String path) {
        if (getFsId() == 0) {
            throw new NotMountedException("Not mounted");
        }
        Node<ZipEntry> targetNode = cache.get(path);
        if (targetNode == null) {
            if (path.equals(File.separator)) {
                targetNode = rootNode;
            } else {
                targetNode = Objects.requireNonNull(rootNode).getLastChild(FileUtils.getSanitizedPath(path));
            }
            if (targetNode != null) {
                cache.put(path, targetNode);
            }
        }
        return targetNode;
    }

    @Override
    public long lastModified(String path) {
        Node<?> targetNode = getNode(path);
        if (targetNode == null || targetNode.getObject() == null) {
            return zipFilePath.lastModified();
        }
        return ((ZipEntry) targetNode.getObject()).getTime();
    }

    @Override
    public long lastAccess(String path) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return lastModified(path);
        }
        Node<?> targetNode = getNode(path);
        if (targetNode == null || targetNode.getObject() == null) {
            return zipFilePath.lastModified();
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
            return zipFilePath.lastModified();
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
            return 0L;
        }
        if (!targetNode.isFile() || targetNode.getObject() == null) {
            return -1;
        }
        return ((ZipEntry) targetNode.getObject()).getSize();
    }

    @SuppressWarnings({"PointlessBooleanExpression", "ConstantConditions"})
    @Override
    public boolean checkAccess(String path, int access) {
        Node<?> targetNode = getNode(path);
        if (access == OsConstants.F_OK) {
            return targetNode != null && targetNode.exists();
        }
        boolean canAccess = true;
        if ((access & OsConstants.R_OK) != 0) {
            canAccess &= true;
        }
        if ((access & OsConstants.W_OK) != 0) {
            canAccess &= false;
        }
        if ((access & OsConstants.X_OK) != 0) {
            canAccess &= false;
        }
        return canAccess;
    }

    @SuppressWarnings("OctalInteger")
    @Override
    public int getMode(String path) {
        Node<?> targetNode = getNode(path);
        if (targetNode == null) {
            return 0;
        }
        if (targetNode.isDirectory()) {
            return 0444 | OsConstants.S_IFDIR;
        }
        if (targetNode.isFile()) {
            return 0444 | OsConstants.S_IFREG;
        }
        return 0;
    }

    @NonNull
    @Override
    protected InputStream getInputStream(@NonNull Node<?> node) throws IOException {
        ZipEntry zipEntry = (ZipEntry) node.getObject();
        if (zipEntry == null) {
            throw new FileNotFoundException("Class definition for " + node.getFullPath() + " is not found.");
        }
        return Objects.requireNonNull(zipFile).getInputStream(zipEntry);
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
        String[] components = FileUtils.getSanitizedPath(zipEntry.getName()).split(File.separator);
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
