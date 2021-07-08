// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import androidx.annotation.NonNull;

import java.util.Objects;

import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.io.Path;

public class FmItem implements Comparable<FmItem> {
    final String extension;
    final FileType type;
    final String name;
    @NonNull
    final Path path;

    FmItem(@NonNull Path path) {
        this.path = path;
        name = path.getName();
        extension = IOUtils.getExtension(name);
        if (path.isFile()) type = FileType.FILE;
        else if (path.isDirectory()) type = FileType.DIRECTORY;
        else if (path.isVirtual()) type = FileType.VIRTUAL;
        else type = FileType.UNKNOWN;
    }

    FmItem(@NonNull String name, @NonNull Path path) {
        this.path = path;
        this.name = name;
        extension = IOUtils.getExtension(name);
        if (path.isFile()) type = FileType.FILE;
        else if (path.isDirectory()) type = FileType.DIRECTORY;
        else if (path.isVirtual()) type = FileType.VIRTUAL;
        else type = FileType.UNKNOWN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FmItem)) return false;
        FmItem item = (FmItem) o;
        return path.equals(item.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    @Override
    public int compareTo(FmItem o) {
        if (equals(o)) return 0;
        int typeComp;
        if (type == o.type) typeComp = 0;
        else if (type == FileType.DIRECTORY) typeComp = -1;
        else typeComp = 1;
        if (typeComp == 0) {
            return name.compareToIgnoreCase(o.name);
        } else return typeComp;
    }
}
