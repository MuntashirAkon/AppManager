// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io.fs;

import android.net.Uri;
import android.system.OsConstants;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jf.dexlib2.iface.ClassDef;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.scanner.DexClasses;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.Path;

public class DexFileSystem extends VirtualFileSystem {
    private final LruCache<String, Node<ClassDef>> cache = new LruCache<>(100);
    @Nullable
    private final File filePath;
    @Nullable
    private final Path dexPath;
    @Nullable
    private DexClasses dexClasses;
    @Nullable
    private Node<ClassDef> rootNode;

    public DexFileSystem(@NonNull Uri mountPoint, @NonNull File filePath) {
        super(mountPoint);
        this.filePath = filePath;
        this.dexPath = null;
    }

    public DexFileSystem(@NonNull Uri mountPoint, @NonNull Path dexPath) {
        super(mountPoint);
        this.filePath = null;
        this.dexPath = dexPath;
    }

    @NonNull
    public final DexClasses getDexClasses() {
        if (getFsId() == 0) {
            throw new NotMountedException("Not mounted");
        }
        return Objects.requireNonNull(dexClasses);
    }

    @Override
    protected Path onMount() throws IOException {
        if (filePath != null) {
            dexClasses = new DexClasses(filePath);
        } else if (dexPath != null) {
            try (InputStream is = dexPath.openInputStream()) {
                dexClasses = new DexClasses(is);
            }
        }
        rootNode = buildTree(Objects.requireNonNull(dexClasses));
        return new Path(AppManager.getContext(), this);
    }

    @Override
    protected void onUnmount(List<Action> actions) throws IOException {
        if (dexClasses != null) {
            dexClasses.close();
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
        Node<ClassDef> targetNode = cache.get(path);
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
        return filePath != null ? filePath.lastModified() : 0L;
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
        try {
            return Objects.requireNonNull(dexClasses)
                    .getClassContents((ClassDef) targetNode.getObject())
                    .getBytes(StandardCharsets.UTF_8)
                    .length;
        } catch (ClassNotFoundException e) {
            return -1;
        }
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
        ClassDef classDef = (ClassDef) node.getObject();
        if (classDef == null) {
            throw new FileNotFoundException("Class definition for " + node.getFullPath() + " is not found.");
        }
        try {
            return new ByteArrayInputStream(getDexClasses().getClassContents(classDef).getBytes(StandardCharsets.UTF_8));
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    @NonNull
    private static Node<ClassDef> buildTree(@NonNull DexClasses dexClasses) {
        Node<ClassDef> rootNode = new Node<>(null, File.separator);
        List<String> classNames = dexClasses.getClassNames();
        for (String className : classNames) {
            ClassDef classDef;
            try {
                classDef = dexClasses.getClassDef(className);
            } catch (ClassNotFoundException e) {
                classDef = null;
            }
            buildTree(rootNode, className, Objects.requireNonNull(classDef));
        }
        return rootNode;
    }

    // Build nodes as needed by the entry, entry itself is the last node in the tree if it is not a directory
    private static void buildTree(@NonNull Node<ClassDef> rootNode, @NonNull String className, @NonNull ClassDef classDef) {
        String[] components = FileUtils.getSanitizedPath(className).split("\\.");
        if (components.length < 1) return;
        Node<ClassDef> lastNode = rootNode;
        for (int i = 0; i < components.length - 1 /* last one will be set manually */; ++i) {
            Node<ClassDef> newNode = lastNode.getChild(components[i]);
            if (newNode == null) {
                // Add children
                newNode = new Node<>(lastNode, components[i]);
                lastNode.addChild(newNode);
            }
            lastNode = newNode;
        }
        lastNode.addChild(new Node<>(lastNode, components[components.length - 1] + ".smali", classDef));
    }
}
