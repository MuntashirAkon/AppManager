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
    private final SparseArray<SparseArray<FileHolder>> files = new SparseArray<>();

    @NonNull
    synchronized FileHolder get(int handle) throws IOException {
        int pid = Binder.getCallingPid();
        SparseArray<FileHolder> pidFiles = files.get(pid);
        if (pidFiles == null)
            throw new IOException(ERROR_MSG);
        FileHolder h = pidFiles.get(handle);
        if (h == null)
            throw new IOException(ERROR_MSG);
        return h;
    }

    synchronized int put(FileHolder h) {
        int pid = Binder.getCallingPid();
        SparseArray<FileHolder> pidFiles = files.get(pid);
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
        SparseArray<FileHolder> pidFiles = files.get(pid);
        if (pidFiles == null)
            return;
        FileHolder h = pidFiles.get(handle);
        if (h == null)
            return;
        pidFiles.remove(handle);
        synchronized (h) {
            h.close();
        }
    }

    synchronized void pidDied(int pid) {
        SparseArray<FileHolder> pidFiles = files.get(pid);
        if (pidFiles == null)
            return;
        files.remove(pid);
        for (int i = 0; i < pidFiles.size(); ++i) {
            FileHolder h = pidFiles.valueAt(i);
            synchronized (h) {
                h.close();
            }
        }
    }
}