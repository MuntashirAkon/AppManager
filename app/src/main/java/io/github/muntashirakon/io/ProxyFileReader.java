// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProxyFileReader extends InputStreamReader {
    /**
     * Creates a new {@code ProxyFileReader}, given the name of the
     * file to read from.
     *
     * @param fileName the name of the file to read from
     * @throws FileNotFoundException if the named file does not exist,
     *                               is a directory rather than a regular file,
     *                               or for some other reason cannot be opened for
     *                               reading.
     */
    @WorkerThread
    public ProxyFileReader(String fileName) throws IOException {
        super(new ProxyInputStream(new ProxyFile(fileName)));
    }

    /**
     * Creates a new {@code ProxyFileReader}, given the <tt>File</tt>
     * to read from.
     *
     * @param file the <tt>File</tt> to read from
     * @throws FileNotFoundException if the file does not exist,
     *                               is a directory rather than a regular file,
     *                               or for some other reason cannot be opened for
     *                               reading.
     */
    @WorkerThread
    public ProxyFileReader(File file) throws IOException {
        super(new ProxyInputStream(file));
    }

    /**
     * Creates a new {@code ProxyFileReader}, given the <tt>File</tt>
     * to read from.
     *
     * @param file the <tt>Path</tt> to read from
     * @throws FileNotFoundException if the file does not exist,
     *                               is a directory rather than a regular file,
     *                               or for some other reason cannot be opened for
     *                               reading.
     */
    @WorkerThread
    public ProxyFileReader(@NonNull Path file) throws IOException {
        super(file.openInputStream());
    }
}
