// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import androidx.annotation.NonNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

public class PathReader extends InputStreamReader {
    /**
     * Creates a new {@link PathReader}, given the <tt>Path</tt>
     * to read from.
     *
     * @param file the <tt>Path</tt> to read from
     * @throws FileNotFoundException if the file does not exist,
     *                               is a directory rather than a regular file,
     *                               or for some other reason cannot be opened for
     *                               reading.
     */
    public PathReader(@NonNull Path file) throws IOException {
        super(file.openInputStream());
    }
}
