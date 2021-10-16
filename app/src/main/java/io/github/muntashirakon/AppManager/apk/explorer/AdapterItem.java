// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.explorer;

import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

import io.github.muntashirakon.AppManager.fm.FileType;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.Path;

public class AdapterItem implements Comparable<AdapterItem> {
    @IntDef(flag = true, value = {
            ACTION_DELETE,
            ACTION_REPLACE,
            ACTION_RENAME,
            ACTION_CREATE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Action {
    }

    public static final int ACTION_DELETE = 1;
    public static final int ACTION_REPLACE = 1 << 1;
    public static final int ACTION_RENAME = 1 << 2;
    public static final int ACTION_CREATE = 1 << 3;

    final String extension;
    final FileType type;
    @NonNull
    final Path path;

    @Nullable
    private Path cachedFile;
    @Action
    private int action;
    @NonNull
    private String name;

    public AdapterItem(@NonNull Path path) {
        this.path = path;
        name = path.getName();
        extension = FileUtils.getExtension(name);
        if (path.isDirectory()) {
            type = FileType.DIRECTORY;
        } else type = FileType.FILE;
    }

    public Uri getUri() {
        // Should always return the real path
        return path.getUri();
    }

    @NonNull
    public String getName() {
        return name;
    }

    @Nullable
    public String getType() {
        if (path.isDirectory()) {
            return "application/octet-stream";
        }
        String type = path.getType();
        if (type != null) return type;
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    }

    public long length() {
        if (hasRenamed() && path.isFile()) {
            return Objects.requireNonNull(cachedFile).length();
        }
        return path.length();
    }

    @Nullable
    public Path getCachedFile() {
        return cachedFile;
    }

    public void setCachedFile(@Nullable Path cachedFile) {
        this.cachedFile = cachedFile;
    }

    public void delete() {
        action |= ACTION_DELETE;
    }

    public void rename(@NonNull String displayName) {
        action |= ACTION_RENAME;
        name = displayName;
    }

    public void replace(@NonNull Path newPath) {
        action |= ACTION_REPLACE;
        cachedFile = newPath;
    }

    @NonNull
    public static AdapterItem create(@NonNull Path newPath) {
        AdapterItem adapterItem = new AdapterItem(newPath);
        adapterItem.action = ACTION_CREATE;
        return adapterItem;
    }

    public void removeAction(@Action int action) {
        this.action &= ~action;
    }

    public boolean hasDeleted() {
        return (action & ACTION_DELETE) != 0;
    }

    public boolean hasReplaced() {
        return (action & ACTION_REPLACE) != 0;
    }

    public boolean hasRenamed() {
        return (action & ACTION_RENAME) != 0;
    }

    public boolean isNew() {
        return (action & ACTION_CREATE) != 0;
    }

    public InputStream openInputStream() throws IOException {
        if (hasReplaced() && cachedFile != null) {
            return cachedFile.openInputStream();
        }
        return path.openInputStream();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdapterItem)) return false;
        AdapterItem item = (AdapterItem) o;
        return path.equals(item.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    @Override
    public int compareTo(AdapterItem o) {
        if (equals(o)) return 0;
        int typeComp;
        if (type == o.type) typeComp = 0;
        else if (type == FileType.DIRECTORY) typeComp = -1;
        else typeComp = 1;
        if (typeComp == 0) {
            return name.compareToIgnoreCase(o.name);
        } else return typeComp;
    }

    @NonNull
    @Override
    public String toString() {
        return "AdapterItem{" +
                "name='" + name + '\'' +
                ", extension='" + extension + '\'' +
                ", uri='" + path.getUri() + '\'' +
                '}';
    }
}
