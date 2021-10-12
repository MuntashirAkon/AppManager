// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.explorer;

import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Objects;
import java.util.zip.ZipEntry;

import io.github.muntashirakon.AppManager.fm.FileType;
import io.github.muntashirakon.AppManager.utils.FileUtils;

public class AdapterItem implements Comparable<AdapterItem> {
    final String extension;
    final FileType type;
    @NonNull
    final String name;
    @NonNull
    final String fullName;
    @NonNull
    final ZipEntry zipEntry;
    File cachedFile;
    final int depth;

    public AdapterItem(@NonNull ZipEntry zipEntry, int depth) {
        this.zipEntry = zipEntry;
        this.depth = depth;
        String[] splits = zipEntry.getName().split(File.separator);
        name = splits[depth];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; ++i) sb.append(splits[i]).append(File.separatorChar);
        fullName = sb.append(name).toString();
        extension = FileUtils.getExtension(name);
        if (zipEntry.isDirectory() || splits.length > depth + 1) type = FileType.DIRECTORY;
        else type = FileType.FILE;
    }

    @Nullable
    public String getMime() {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdapterItem)) return false;
        AdapterItem item = (AdapterItem) o;
        return fullName.equals(item.fullName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullName);
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

    @Override
    public String toString() {
        return "AdapterItem{" +
                "name='" + name + '\'' +
                ", extension='" + extension + '\'' +
                ", fullName='" + fullName + '\'' +
                ", depth=" + depth +
                '}';
    }
}
