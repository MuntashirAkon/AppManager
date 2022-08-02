// SPDX-License-Identifier: GPL-3.0-or-later

package androidx.documentfile.provider;

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

import io.github.muntashirakon.AppManager.scanner.DexClasses;

public class DexDocumentFile extends VirtualDocumentFile<ClassDef> {
    @NonNull
    private final DexClasses dexClasses;

    public DexDocumentFile(int vfsId, @NonNull DexClasses dexClasses, @Nullable String basePath) {
        this(null, vfsId, dexClasses, basePath);
    }

    public DexDocumentFile(@Nullable DocumentFile parent,
                           int vfsId,
                           @NonNull DexClasses dexClasses,
                           @Nullable String basePath) {
        super(parent, vfsId, buildTree(Objects.requireNonNull(dexClasses)), basePath);
        this.dexClasses = dexClasses;
    }

    private DexDocumentFile(@NonNull DexDocumentFile parent, @NonNull String relativePath) {
        super(Objects.requireNonNull(parent), relativePath);
        this.dexClasses = parent.dexClasses;
    }

    private DexDocumentFile(@NonNull DexDocumentFile parent, @NonNull Node<ClassDef> currentNode) {
        super(Objects.requireNonNull(parent), currentNode);
        this.dexClasses = parent.dexClasses;
    }

    @Nullable
    @Override
    public DocumentFile createFile(@NonNull String mimeType, @NonNull String displayName) {
        // Not supported
        return null;
    }

    @Nullable
    @Override
    public DocumentFile createDirectory(@NonNull String displayName) {
        // Not supported
        return null;
    }

    @Nullable
    @Override
    public String getType() {
        if (currentNode != null && currentNode.getObject() != null) {
            return "text/x-smali";
        }
        return super.getType();
    }

    @NonNull
    @Override
    public InputStream openInputStream() throws IOException {
        if (currentNode == null) throw new FileNotFoundException("Document does not exist.");
        ClassDef classDef = currentNode.getObject();
        if (classDef == null) throw new FileNotFoundException("No class definition is associated with this class.");
        try {
            return new ByteArrayInputStream(this.dexClasses.getClassContents(currentNode.getObject()).getBytes(StandardCharsets.UTF_8));
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    @Override
    public long lastModified() {
        return System.currentTimeMillis();
    }

    @Override
    public long length() {
        // TODO: 15/10/21 Calculate real size
        return isFile() ? 10 : 0;
    }

    @Override
    public boolean canWrite() {
        // Not supported
        return false;
    }

    @Override
    public boolean delete() {
        // Not supported
        return false;
    }

    @Nullable
    @Override
    public DexDocumentFile findFile(@NonNull String displayName) {
        DexDocumentFile documentFile = new DexDocumentFile(this, getSanitizedPath(displayName));
        if (documentFile.currentNode == null) return null;
        return documentFile;
    }

    @NonNull
    @Override
    public DexDocumentFile[] listFiles() {
        if (currentNode == null) return new DexDocumentFile[0];
        Node<ClassDef>[] nodes = currentNode.listChildren();
        if (nodes == null) return new DexDocumentFile[0];
        DexDocumentFile[] documentFiles = new DexDocumentFile[nodes.length];
        for (int i = 0; i < nodes.length; ++i) {
            documentFiles[i] = new DexDocumentFile(this, nodes[i]);
        }
        return documentFiles;
    }

    @Override
    public boolean renameTo(@NonNull String displayName) {
        // Not supported
        return false;
    }

    @NonNull
    private static DexNode<ClassDef> buildTree(@NonNull DexClasses dexClasses) {
        DexNode<ClassDef> rootNode = new DexNode<>(null, File.separator);
        List<String> classNames = dexClasses.getClassNames();
        for (String className : classNames) {
            ClassDef classDef;
            try {
                classDef = dexClasses.getClassDef(className);
            } catch (ClassNotFoundException e) {
                classDef = null;
            }
            buildTree(rootNode, className, classDef);
        }
        return rootNode;
    }

    // Build nodes as needed by the entry, entry itself is the last node in the tree if it is not a directory
    private static void buildTree(@NonNull DexNode<ClassDef> rootNode, @NonNull String className, @Nullable ClassDef classDef) {
        String[] components = getSanitizedPath(className).split("\\.");
        if (components.length < 1) return;
        Node<ClassDef> lastNode = rootNode;
        for (int i = 0; i < components.length - 1 /* last one will be set manually */; ++i) {
            Node<ClassDef> newNode = lastNode.getChild(components[i]);
            if (newNode == null) {
                // Add children
                newNode = new DexNode<>(lastNode.getFullPath(), components[i]);
                lastNode.addChild(newNode);
            }
            lastNode = newNode;
        }
        lastNode.addChild(new DexNode<>(lastNode.getFullPath(), components[components.length - 1] + ".smali", classDef));
    }

    private static class DexNode<T> extends Node<T> {
        private final boolean isClass;

        protected DexNode(@Nullable String basePath, @NonNull String name) {
            super(basePath, name);
            isClass = false;
        }

        protected DexNode(@NonNull String basePath, @NonNull String name, @Nullable T object) {
            super(basePath, name, object);
            isClass = true;
        }

        @Override
        public boolean isDirectory() {
            return !isClass;
        }

        @Override
        public boolean isFile() {
            return isClass;
        }
    }
}
