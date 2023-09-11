// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.PathAttributes;
import io.github.muntashirakon.io.PathContentInfo;

public class FmItem implements Comparable<FmItem> {
    public static final int UNRESOLVED = -2;

    final boolean isDirectory;
    @NonNull
    public final Path path;

    @Nullable
    private String mTag;
    @Nullable
    private PathContentInfo mContentInfo;
    @Nullable
    private PathAttributes mAttributes;
    @Nullable
    private String mName;
    private int mChildCount = UNRESOLVED;
    private boolean mCached = false;

    public FmItem(@NonNull Path path) {
        this.path = path;
        isDirectory = path.isDirectory();
    }

    FmItem(@NonNull Path path, @NonNull PathAttributes attributes) {
        this.path = path;
        mAttributes = attributes;
        mName = mAttributes.name;
        isDirectory = mAttributes.isDirectory;
    }

    @NonNull
    public String getTag() {
        if (mTag == null) {
            mTag = "fm_" + Base64.encodeToString(path.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        }
        return mTag;
    }

    @NonNull
    public String getName() {
        if (mName != null) {
            return mName;
        }
        if (mAttributes != null) {
            return mAttributes.name;
        }
        return path.getName();
    }

    public long getLastModified() {
        if (mAttributes != null) {
            return mAttributes.lastModified;
        }
        return path.lastModified();
    }

    public long getSize() {
        if (mAttributes != null) {
            return mAttributes.size;
        }
        return path.length();
    }

    public int getChildCount() {
        if (!isDirectory) {
            return 0;
        }
        if (mChildCount == UNRESOLVED) {
            mChildCount = path.listFiles().length;
        }
        return mChildCount;
    }

    @Nullable
    public PathContentInfo getContentInfo() {
        return mContentInfo;
    }

    public void setContentInfo(@Nullable PathContentInfo contentInfo) {
        this.mContentInfo = contentInfo;
    }

    public void cache() {
        try {
            getTag();
            fetchAttributes();
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            if (isDirectory) {
                getChildCount();
            } else mChildCount = 0;
        } finally {
            mCached = true;
        }
    }

    public boolean isCached() {
        return mCached;
    }

    private void fetchAttributes() {
        try {
            // WARNING: The attributes can be changed in SAF anytime from anywhere.
            // But we don't care because speed matters more.
            mAttributes = path.getAttributes();
            mName = mAttributes.name;
        } catch (IOException e) {
            e.printStackTrace();
            mName = path.getName();
        }
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
        int typeComp = -Boolean.compare(isDirectory, o.isDirectory);
        if (typeComp == 0) {
            return path.getName().compareToIgnoreCase(o.path.getName());
        } else return typeComp;
    }
}
