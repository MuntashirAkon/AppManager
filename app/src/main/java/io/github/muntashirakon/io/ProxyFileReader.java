package io.github.muntashirakon.io;

import android.os.RemoteException;

import java.io.File;
import java.io.FileNotFoundException;
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
    public ProxyFileReader(String fileName) throws FileNotFoundException, RemoteException {
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
    public ProxyFileReader(File file) throws FileNotFoundException, RemoteException {
        super(new ProxyInputStream(file));
    }
}
