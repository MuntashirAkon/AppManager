// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.PathContentInfo;

public class FmItem implements Comparable<FmItem> {
    final int type;
    @NonNull
    public final Path path;
    @NonNull
    final String tag;

    @Nullable
    private PathContentInfo mContentInfo;

    FmItem(@NonNull Path path) {
        this.path = path;
        if (path.isFile()) type = FileType.FILE;
        else if (path.isDirectory()) type = FileType.DIRECTORY;
        else type = FileType.UNKNOWN;
        tag = "fm_" + DigestUtils.getHexDigest(DigestUtils.SHA_1, path.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Nullable
    public PathContentInfo getContentInfo() {
        return mContentInfo;
    }

    public void setContentInfo(@Nullable PathContentInfo contentInfo) {
        this.mContentInfo = contentInfo;
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
            return path.getName().compareToIgnoreCase(o.path.getName());
        } else return typeComp;
    }
}
