// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.PathContentInfo;

public class FmItem implements Comparable<FmItem> {
    public static final int UNRESOLVED = -2;

    final int type;
    @NonNull
    public final Path path;
    @NonNull
    private final String tag;

    @Nullable
    private PathContentInfo mContentInfo;
    @Nullable
    private String name;
    private long lastModified = UNRESOLVED;
    private long size = UNRESOLVED;
    private int childCount = UNRESOLVED;

    FmItem(@NonNull Path path) {
        this.path = path;
        if (path.isFile()) type = FileType.FILE;
        else if (path.isDirectory()) type = FileType.DIRECTORY;
        else type = FileType.UNKNOWN;
        tag = "fm_" + Base64.encodeToString(path.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    FmItem(@NonNull Path path, boolean isDirectory) {
        this.path = path;
        this.type = isDirectory ? FileType.DIRECTORY : FileType.FILE;
        tag = "fm_" + Base64.encodeToString(path.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    @NonNull
    public String getTag() {
        return tag;
    }

    @NonNull
    public String getName() {
        if (name == null) {
            // WARNING: The name of the file can be changed in SAF anytime from anywhere.
            // But we don't care because speed matters more.
            name = path.getName();
        }
        return name;
    }

    public long getLastModified() {
        if (lastModified == UNRESOLVED) {
            lastModified = path.lastModified();
        }
        return lastModified;
    }

    public long getSize() {
        if (size == UNRESOLVED) {
            size = path.length();
        }
        return size;
    }

    public int getChildCount() {
        if (childCount == UNRESOLVED) {
            childCount = path.listFiles().length;
        }
        return childCount;
    }

    @Nullable
    public PathContentInfo getContentInfo() {
        return mContentInfo;
    }

    public void setContentInfo(@Nullable PathContentInfo contentInfo) {
        this.mContentInfo = contentInfo;
    }

    public void cache() {
        getName();
        getLastModified();
        getSize();
        if (type == FileType.DIRECTORY) {
            getChildCount();
        } else childCount = 0;
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
