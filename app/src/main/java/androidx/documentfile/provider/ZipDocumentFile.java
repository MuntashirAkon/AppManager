// SPDX-License-Identifier: GPL-3.0-or-later

package androidx.documentfile.provider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipDocumentFile extends VirtualDocumentFile<ZipEntry> {
    @NonNull
    private final ZipFile mZipFile;

    public ZipDocumentFile(int vfsId, @NonNull ZipFile zipFile, @Nullable String basePath) {
        this(null, vfsId, zipFile, basePath);
    }

    public ZipDocumentFile(@Nullable DocumentFile parent,
                           int vfsId,
                           @NonNull ZipFile zipFile,
                           @Nullable String basePath) {
        super(parent, vfsId, buildTree(Objects.requireNonNull(zipFile)), basePath);
        this.mZipFile = zipFile;
    }

    private ZipDocumentFile(@NonNull ZipDocumentFile parent, @NonNull String relativePath) {
        super(Objects.requireNonNull(parent), relativePath);
        this.mZipFile = Objects.requireNonNull(parent).mZipFile;
    }

    private ZipDocumentFile(@NonNull ZipDocumentFile parent, @NonNull Node<ZipEntry> currentNode) {
        super(parent, currentNode);
        this.mZipFile = Objects.requireNonNull(parent).mZipFile;
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

    @Override
    public long lastModified() {
        if (currentNode != null) {
            ZipEntry zipEntry = currentNode.getObject();
            if (zipEntry != null) return zipEntry.getTime();
        }
        return 0;
    }

    @Override
    public long length() {
        if (currentNode != null) {
            ZipEntry zipEntry = currentNode.getObject();
            if (zipEntry != null) return zipEntry.getSize();
        }
        return 0;
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
    public ZipDocumentFile findFile(@NonNull String displayName) {
        ZipDocumentFile documentFile = new ZipDocumentFile(this, getSanitizedPath(displayName));
        if (documentFile.currentNode == null) return null;
        return documentFile;
    }

    @NonNull
    @Override
    public ZipDocumentFile[] listFiles() {
        if (currentNode == null) return new ZipDocumentFile[0];
        Node<ZipEntry>[] nodes = currentNode.listChildren();
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

    @Override
    @NonNull
    public InputStream openInputStream() throws IOException {
        if (currentNode == null) throw new FileNotFoundException("Document does not exist.");
        ZipEntry zipEntry = currentNode.getObject();
        if (zipEntry == null) throw new FileNotFoundException("Document is a directory.");
        return mZipFile.getInputStream(zipEntry);
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
        String[] components = getSanitizedPath(zipEntry.getName()).split(File.separator);
        if (components.length < 1) return;
        Node<ZipEntry> lastNode = rootNode;
        for (int i = 0; i < components.length - 1 /* last one will be set manually */; ++i) {
            Node<ZipEntry> newNode = lastNode.getChild(components[i]);
            if (newNode == null) {
                // Add children
                newNode = new Node<>(lastNode.getFullPath(), components[i]);
                lastNode.addChild(newNode);
            }
            lastNode = newNode;
        }
        lastNode.addChild(new Node<>(lastNode.getFullPath(), components[components.length - 1],
                zipEntry.isDirectory() ? null : zipEntry));
    }
}
