// SPDX-License-Identifier: GPL-3.0-or-later

package androidx.documentfile.provider;

import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipDocumentFile extends DocumentFile {
    @NonNull
    private final ZipFile mZipFile;
    @NonNull
    private final Node rootNode;
    @Nullable
    private final Node currentNode;

    public ZipDocumentFile(@NonNull ZipFile zipFile, @Nullable String basePath) {
        super(null);
        this.mZipFile = Objects.requireNonNull(zipFile);
        this.rootNode = buildTree(zipFile);
        this.currentNode = this.rootNode.getLastChild(basePath);
    }

    public ZipDocumentFile(@NonNull ZipDocumentFile parent, @NonNull String relativePath) {
        super(parent);
        this.mZipFile = Objects.requireNonNull(parent).mZipFile;
        this.rootNode = parent.rootNode;
        this.currentNode = parent.currentNode == null ? null : parent.currentNode.getLastChild(relativePath);
    }

    private ZipDocumentFile(@NonNull ZipDocumentFile parent, @NonNull Node currentNode) {
        super(parent);
        this.mZipFile = Objects.requireNonNull(parent).mZipFile;
        this.rootNode = parent.rootNode;
        this.currentNode = currentNode;
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

    @NonNull
    @Override
    public Uri getUri() {
        // TODO: 14/10/21 Fix URI
        return Uri.parse("zip://" + (currentNode == null ? "" : currentNode.getFullPath()));
    }

    @VisibleForTesting
    @Nullable
    public String getFullPath() {
        if (currentNode == null) return null;
        return currentNode.getFullPath();
    }

    @Nullable
    @Override
    public String getName() {
        if (currentNode == null) return null;
        return currentNode.getName();
    }

    @Nullable
    @Override
    public String getType() {
        if (currentNode == null) return null;
        if (isDirectory()) {
            return "resource/folder";
        } else {
            return getTypeForName(currentNode.getName());
        }
    }

    @Override
    public boolean isDirectory() {
        if (currentNode != null) {
            return currentNode.isDirectory();
        }
        return false;
    }

    @Override
    public boolean isFile() {
        if (currentNode != null) {
            return currentNode.isFile();
        }
        return false;
    }

    @Override
    public boolean isVirtual() {
        return true;
    }

    @Override
    public long lastModified() {
        if (currentNode != null) {
            ZipEntry zipEntry = currentNode.getZipEntry();
            if (zipEntry != null) return zipEntry.getTime();
        }
        return 0;
    }

    @Override
    public long length() {
        if (currentNode != null) {
            ZipEntry zipEntry = currentNode.getZipEntry();
            if (zipEntry != null) return zipEntry.getSize();
        }
        return 0;
    }

    @Override
    public boolean canRead() {
        return exists();
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

    @Override
    public boolean exists() {
        return currentNode != null;
    }

    @Nullable
    @Override
    public ZipDocumentFile findFile(@NonNull String displayName) {
        ZipDocumentFile documentFile = new ZipDocumentFile(this, displayName);
        if (documentFile.currentNode == null) return null;
        return documentFile;
    }

    @NonNull
    @Override
    public ZipDocumentFile[] listFiles() {
        if (currentNode == null) return new ZipDocumentFile[0];
        Node[] nodes = currentNode.listChildren();
        if (nodes == null) return new ZipDocumentFile[0];
        ZipDocumentFile[] documentFiles = new ZipDocumentFile[nodes.length];
        for (int i = 0; i < nodes.length; ++i) {
            documentFiles[i] = new ZipDocumentFile(this, nodes[i]);
        }
        return documentFiles;
    }

    @Override
    public boolean renameTo(@NonNull String displayName) {
        // Not supported
        return false;
    }

    @NonNull
    public InputStream openInputStream() throws IOException {
        if (currentNode == null) throw new FileNotFoundException("Document does not exist.");
        ZipEntry zipEntry = currentNode.getZipEntry();
        if (zipEntry == null) throw new FileNotFoundException("Document is a directory.");
        return mZipFile.getInputStream(zipEntry);
    }

    @NonNull
    private static Node buildTree(@NonNull ZipFile zipFile) {
        Node rootNode = new Node(null, File.separator);
        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
        while (zipEntries.hasMoreElements()) {
            ZipEntry zipEntry = zipEntries.nextElement();
            buildTree(rootNode, zipEntry);
        }
        return rootNode;
    }

    // Build nodes as needed by the entry, entry itself is the last node in the tree if it is not a directory
    private static void buildTree(@NonNull Node rootNode, @NonNull ZipEntry zipEntry) {
        String[] components = getSanitizedPath(zipEntry.getName()).split(File.separator);
        if (components.length < 1) return;
        Node lastNode = rootNode;
        for (int i = 0; i < components.length - 1 /* last one will be set manually */; ++i) {
            Node newNode = lastNode.getChild(components[i]);
            if (newNode == null) {
                // Add children
                newNode = new Node(lastNode.getFullPath(), components[i]);
                lastNode.addChild(newNode);
            }
            lastNode = newNode;
        }
        lastNode.addChild(new Node(lastNode.getFullPath(), components[components.length - 1],
                zipEntry.isDirectory() ? null : zipEntry));
    }

    @Nullable
    private static Node getLastNode(@NonNull Node baseNode, @Nullable String dirtyPath) {
        if (dirtyPath == null) return baseNode;
        String[] components = getSanitizedPath(dirtyPath).split(File.separator);
        Node lastNode = baseNode;
        for (String component : components) {
            lastNode = lastNode.getChild(component);
            if (lastNode == null) {
                // File do not exist
                return null;
            }
        }
        return lastNode;
    }

    @NonNull
    private static String getSanitizedPath(@NonNull String name) {
        //noinspection RegExpRedundantEscape
        name = name.replaceAll("[\\/]+", File.separator);
        if (name.startsWith(File.separator)) name = name.substring(1);
        if (name.endsWith(File.separator)) name = name.substring(0, name.length() - 1);
        return name;
    }

    @NonNull
    private static String getTypeForName(@NonNull String name) {
        final int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0) {
            final String extension = name.substring(lastDot + 1).toLowerCase();
            final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }

        return "application/octet-stream";
    }

    private static class Node {
        private HashMap<String, Node> children = null;
        @NonNull
        private final String name;
        private final String fullPath;
        @Nullable
        private final ZipEntry zipEntry;

        private Node(@Nullable String basePath, @NonNull String name) {
            this(basePath == null ? File.separator : basePath, name, null);
        }

        private Node(@NonNull String basePath, @NonNull String name, @Nullable ZipEntry zipEntry) {
            this.name = name;
            this.fullPath = (basePath.equals(File.separator) ? (name.equals(File.separator) ? "" : File.separator)
                    : basePath + File.separatorChar) + name;
            this.zipEntry = zipEntry;
        }

        @NonNull
        public String getName() {
            return name;
        }

        public String getFullPath() {
            return fullPath;
        }

        @Nullable
        public ZipEntry getZipEntry() {
            return zipEntry;
        }

        public boolean isDirectory() {
            return zipEntry == null;
        }

        public boolean isFile() {
            return zipEntry != null;
        }

        @Nullable
        public Node getChild(String name) {
            if (children == null) return null;
            return children.get(name);
        }

        @Nullable
        public Node getLastChild(@Nullable String name) {
            if (children == null) return null;
            return getLastNode(this, name);
        }

        @Nullable
        public Node[] listChildren() {
            if (children == null) return null;
            return children.values().toArray(new Node[0]);
        }

        public void addChild(@Nullable Node child) {
            if (child == null) return;
            if (children == null) children = new HashMap<>();
            children.put(child.name, child);
        }
    }
}
