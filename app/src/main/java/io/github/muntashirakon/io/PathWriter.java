// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.OutputStreamWriter;

public class PathWriter extends OutputStreamWriter {
    /**
     * Constructs a ProxyFileWriter object given a file name.
     *
     * @param context  File context (usually application context)
     * @param fileName String The system-dependent filename.
     * @throws IOException if the named file exists but is a directory rather
     *                     than a regular file, does not exist but cannot be
     *                     created, or cannot be opened for any other reason
     */
    @WorkerThread
    public PathWriter(Context context, String fileName) throws IOException {
        this(new Path(context, fileName));
    }

    /**
     * Constructs a ProxyFileWriter object given a File object.
     *
     * @param file a Path object to write to.
     * @throws IOException if the file exists but is a directory rather than
     *                     a regular file, does not exist but cannot be created,
     *                     or cannot be opened for any other reason
     */
    @WorkerThread
    public PathWriter(@NonNull Path file) throws IOException {
        super(file.openOutputStream());
    }
}
