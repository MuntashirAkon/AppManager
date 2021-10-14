// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.explorer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import io.github.muntashirakon.AppManager.fm.FileType;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.Path;

public class AdapterItem implements Comparable<AdapterItem> {
    final String extension;
    final FileType type;
    @NonNull
    final String name;
    @NonNull
    final String fullName;
    @NonNull
    final Path path;

    private Path cachedFile;

    public AdapterItem(@NonNull Path path) {
        this.path = path;
        name = path.getName();
        fullName = path.getUri().getPath();
        extension = FileUtils.getExtension(name);
        if (path.isDirectory()) {
            type = FileType.DIRECTORY;
        } else type = FileType.FILE;
    }

    @Nullable
    public String getMime() {
        return path.getType();
    }

    public Path getCachedFile() {
        return cachedFile;
    }

    public void setCachedFile(Path cachedFile) {
        this.cachedFile = cachedFile;
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
