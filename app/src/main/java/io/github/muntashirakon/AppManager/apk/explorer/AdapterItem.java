// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.explorer;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import io.github.muntashirakon.AppManager.fm.FileType;
import io.github.muntashirakon.io.Path;

public class AdapterItem implements Comparable<AdapterItem> {
    @FileType
    final int type;
    @NonNull
    final Path path;

    @Nullable
    private Path cachedFile;

    public AdapterItem(@NonNull Path path) {
        this.path = path;
        if (path.isDirectory()) {
            type = FileType.DIRECTORY;
        } else type = FileType.FILE;
    }

    public Uri getUri() {
        return path.getUri();
    }

    @Nullable
    public String getType() {
        return path.getType();
    }

    public long length() {
        return path.length();
    }

    @Nullable
    public Path getCachedFile() {
        return cachedFile;
    }

    public void setCachedFile(@Nullable Path cachedFile) {
        this.cachedFile = cachedFile;
    }

    public InputStream openInputStream() throws IOException {
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
            return path.getName().compareToIgnoreCase(o.path.getName());
        } else return typeComp;
    }

    @NonNull
    @Override
    public String toString() {
        return "AdapterItem{" +
                "name='" + path.getName() + '\'' +
                ", extension='" + path.getExtension() + '\'' +
                ", uri='" + path.getUri() + '\'' +
                '}';
    }
}
