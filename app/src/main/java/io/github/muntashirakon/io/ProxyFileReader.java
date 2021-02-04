/*
 * Copyright (C) 2021 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.io;

import android.os.RemoteException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProxyFileReader  extends InputStreamReader {
    /**
     * Creates a new {@code ProxyFileReader}, given the name of the
     * file to read from.
     *
     * @param fileName the name of the file to read from
     * @exception  FileNotFoundException  if the named file does not exist,
     *                   is a directory rather than a regular file,
     *                   or for some other reason cannot be opened for
     *                   reading.
     */
    public ProxyFileReader(String fileName) throws IOException, RemoteException {
        super(new ProxyInputStream(new ProxyFile(fileName)));
    }

    /**
     * Creates a new {@code ProxyFileReader}, given the <tt>File</tt>
     * to read from.
     *
     * @param file the <tt>File</tt> to read from
     * @exception  FileNotFoundException  if the file does not exist,
     *                   is a directory rather than a regular file,
     *                   or for some other reason cannot be opened for
     *                   reading.
     */
    public ProxyFileReader(File file) throws IOException, RemoteException {
        super(new ProxyInputStream(file));
    }
}
