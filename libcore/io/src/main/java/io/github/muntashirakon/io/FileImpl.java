// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;

// Copyright 2022 John "topjohnwu" Wu
abstract class FileImpl<T extends ExtendedFile> extends ExtendedFile {

    protected FileImpl(@NonNull String pathname) {
        super(pathname);
    }

    protected FileImpl(@Nullable String parent, @NonNull String child) {
        super(parent, child);
    }

    protected abstract T create(String path);
    protected abstract T[] createArray(int n);
    @NonNull
    @Override
    public abstract T getChildFile(String name);

    @NonNull
    @Override
    public T getAbsoluteFile() {
        return create(getAbsolutePath());
    }

    @NonNull
    @Override
    public T getCanonicalFile() throws IOException {
        return create(getCanonicalPath());
    }

    @Nullable
    @Override
    public T getParentFile() {
        String parent = getParent();
        return parent != null ? create(parent) : null;
    }

    @Nullable
    @Override
    public T[] listFiles() {
        String[] ss = list();
        if (ss == null)
            return null;
        int n = ss.length;
        T[] fs = createArray(n);
        for (int i = 0; i < n; i++) {
            fs[i] = getChildFile(ss[i]);
        }
        return fs;
    }

    @Nullable
    @Override
    public T[] listFiles(@Nullable FilenameFilter filter) {
        String[] ss = list();
        if (ss == null)
            return null;
        ArrayList<T> files = new ArrayList<>();
        for (String s : ss) {
            if ((filter == null) || filter.accept(this, s))
                files.add(getChildFile(s));
        }
        return files.toArray(createArray(0));
    }

    @Nullable
    @Override
    public T[] listFiles(@Nullable FileFilter filter) {
        String[] ss = list();
        if (ss == null)
            return null;
        ArrayList<T> files = new ArrayList<>();
        for (String s : ss) {
            T f = getChildFile(s);
            if ((filter == null) || filter.accept(f))
                files.add(f);
        }
        return files.toArray(createArray(0));
    }
}