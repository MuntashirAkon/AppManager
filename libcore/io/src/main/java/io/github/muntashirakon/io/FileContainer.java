// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.io;

import android.os.Binder;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import java.io.IOException;

// Copyright 2022 John "topjohnwu" Wu
class FileContainer {

    private final static String ERROR_MSG = "Requested file was not opened!";

    private int nextHandle = 0;
    // pid -> handle -> holder
    private final SparseArray<SparseArray<OpenFile>> files = new SparseArray<>();

    @NonNull
    synchronized OpenFile get(int handle) throws IOException {
        int pid = Binder.getCallingPid();
        SparseArray<OpenFile> pidFiles = files.get(pid);
        if (pidFiles == null)
            throw new IOException(ERROR_MSG);
        OpenFile h = pidFiles.get(handle);
        if (h == null)
            throw new IOException(ERROR_MSG);
        return h;
    }

    synchronized int put(OpenFile h) {
        int pid = Binder.getCallingPid();
        SparseArray<OpenFile> pidFiles = files.get(pid);
        if (pidFiles == null) {
            pidFiles = new SparseArray<>();
            files.put(pid, pidFiles);
        }
        int handle = nextHandle++;
        pidFiles.append(handle, h);
        return handle;
    }

    synchronized void remove(int handle) {
        int pid = Binder.getCallingPid();
        SparseArray<OpenFile> pidFiles = files.get(pid);
        if (pidFiles == null)
            return;
        OpenFile h = pidFiles.get(handle);
        if (h == null)
            return;
        pidFiles.remove(handle);
        h.close();
    }

    synchronized void pidDied(int pid) {
        SparseArray<OpenFile> pidFiles = files.get(pid);
        if (pidFiles == null)
            return;
        files.remove(pid);
        for (int i = 0; i < pidFiles.size(); ++i) {
            pidFiles.valueAt(i).close();
        }
    }
}