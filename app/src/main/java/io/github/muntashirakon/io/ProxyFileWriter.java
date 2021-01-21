package io.github.muntashirakon.io;

import android.os.RemoteException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class ProxyFileWriter extends OutputStreamWriter {

    /**
     * Constructs a ProxyFileWriter object given a file name.
     *
     * @param fileName  String The system-dependent filename.
     * @throws IOException  if the named file exists but is a directory rather
     *                  than a regular file, does not exist but cannot be
     *                  created, or cannot be opened for any other reason
     */
    public ProxyFileWriter(String fileName) throws IOException, RemoteException {
        super(new ProxyOutputStream(new ProxyFile(fileName)));
    }

    /**
     * Constructs a ProxyFileWriter object given a File object.
     *
     * @param file  a File object to write to.
     * @throws IOException  if the file exists but is a directory rather than
     *                  a regular file, does not exist but cannot be created,
     *                  or cannot be opened for any other reason
     */
    public ProxyFileWriter(File file) throws IOException, RemoteException {
        super(new ProxyOutputStream(file));
    }
}
