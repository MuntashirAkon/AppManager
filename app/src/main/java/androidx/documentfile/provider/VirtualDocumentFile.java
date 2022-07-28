// SPDX-License-Identifier: GPL-3.0-or-later

package androidx.documentfile.provider;

import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;

import io.github.muntashirakon.AppManager.utils.FileUtils;

// Mother of all virtual documents
public abstract class VirtualDocumentFile<T> extends DocumentFile {
    public static final String SCHEME = "vfs";

    @Nullable
    public static Pair<Integer, String> parseUri(@NonNull Uri uri) {
        try {
            return new Pair<>(Integer.decode(uri.getAuthority()), uri.getPath());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected final int VFS_ID;
    @NonNull
    protected final Node<T> rootNode;
    @Nullable
    protected final Node<T> currentNode;
    protected final Uri mUri;

    public VirtualDocumentFile(@Nullable DocumentFile parent,
                               int vfsId,
                               @NonNull Node<T> rootNode,
                               @Nullable String basePath) {
        super(parent);
        this.rootNode = rootNode;
        if (basePath != null) {
            basePath = getSanitizedPath(basePath);
            if (basePath.equals("")) basePath = null;
        }
        this.currentNode = this.rootNode.getLastChild(basePath);
        this.VFS_ID = vfsId;
        this.mUri = generateUri();
    }

    public VirtualDocumentFile(@NonNull VirtualDocumentFile<T> parent, @NonNull String relativePath) {
        super(parent);
        this.VFS_ID = parent.VFS_ID;
        this.rootNode = parent.rootNode;
        this.currentNode = parent.currentNode == null ? null : parent.currentNode.getLastChild(getSanitizedPath(relativePath));
        this.mUri = generateUri();
    }

    public VirtualDocumentFile(@NonNull VirtualDocumentFile<T> parent, @NonNull Node<T> currentNode) {
        super(parent);
        this.VFS_ID = parent.VFS_ID;
        this.rootNode = parent.rootNode;
        this.currentNode = currentNode;
        this.mUri = generateUri();
    }

    @NonNull
    protected String getScheme() {
        return SCHEME;
    }

    @Override
    public final boolean isVirtual() {
        return true;
    }

    @NonNull
    @Override
    public Uri getUri() {
        return mUri;
    }

    @Nullable
    public final String getFullPath() {
        if (currentNode == null) return null;
        return currentNode.getFullPath();
    }

    @Nullable
    @Override
    public final String getName() {
        if (currentNode == null) return null;
        return currentNode.getName();
    }

    @Nullable
    @Override
    public String getType() {
        if (currentNode == null) return null;
        if (isDirectory()) {
            return "resource/folder";
        } else if (isFile()) {
            return getTypeForName(currentNode.getName());
        }
        return null;
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
    public boolean canRead() {
        return exists();
    }

    @Override
    public boolean exists() {
        return currentNode != null;
    }

    @Nullable
    @Override
    public abstract VirtualDocumentFile<T> findFile(@NonNull String displayName);

    @NonNull
    public abstract InputStream openInputStream() throws IOException;

    private Uri generateUri() {
        // Since VFS_ID is unique per virtual FS, the paths are guaranteed to be unique.
        return Uri.parse(getScheme() + "://" + VFS_ID + getFullPath()); // Force authority by adding `//`
    }

    @Nullable
    private static <T> Node<T> getLastNode(@NonNull Node<T> baseNode, @Nullable String dirtyPath) {
        if (dirtyPath == null) return baseNode;
        String[] components = getSanitizedPath(dirtyPath).split(File.separator);
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

    @NonNull
    protected static String getSanitizedPath(@NonNull String name) {
        return FileUtils.getSanitizedPath(name);
    }

    @NonNull
    protected static String getTypeForName(@NonNull String name) {
        final int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0) {
            final String extension = name.substring(lastDot + 1).toLowerCase(Locale.ROOT);
            final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }
        return "application/octet-stream";
    }

    protected static class Node<T> {
        private HashMap<String, Node<T>> children = null;
        @NonNull
        private final String name;
        private final String fullPath;
        @Nullable
        private final T object;

        protected Node(@Nullable String basePath, @NonNull String name) {
            this(basePath == null ? File.separator : basePath, name, null);
        }

        protected Node(@NonNull String basePath, @NonNull String name, @Nullable T object) {
            this.name = name;
            this.fullPath = (basePath.equals(File.separator) ? (name.equals(File.separator) ? "" : File.separator)
                    : basePath + File.separatorChar) + name;
            this.object = object;
        }

        @NonNull
        public String getName() {
            return name;
        }

        public String getFullPath() {
            return fullPath;
        }

        @Nullable
        public T getObject() {
            return object;
        }

        public boolean isDirectory() {
            return object == null;
        }

        public boolean isFile() {
            return object != null;
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

        public void addChild(@Nullable Node<T> child) {
            if (child == null) return;
            if (children == null) children = new HashMap<>();
            children.put(child.name, child);
        }
    }
}
