// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import io.github.muntashirakon.AppManager.IRemoteFileWriter;

interface IRemoteFile {
    // Only list the methods that are absolutely necessary
    boolean isAbsolute();
    String getAbsolutePath();
    String getCanonicalPath();

    boolean canRead();
    boolean canWrite();
    boolean canExecute();

    boolean exists();
    boolean isDirectory();
    boolean isFile();
    boolean isHidden();

    long lastModified();
    long length();
    boolean createNewFile();
    boolean delete();
    void deleteOnExit();

    String[] list();
    String[] listFiles();

    boolean mkdir();
    boolean mkdirs();
    boolean renameTo(String dest);
    boolean setLastModified(long time);
    boolean setReadOnly();

    long getTotalSpace();
    long getFreeSpace();
    long getUsableSpace();

    boolean setWritable(boolean writable, boolean ownerOnly);
    boolean setWritable1(boolean writable);

    boolean setReadable(boolean readable, boolean ownerOnly);
    boolean setReadable1(boolean readable);

    boolean setExecutable(boolean executable, boolean ownerOnly);
    boolean setExecutable1(boolean executable);

    int compareTo(String pathname);

    IRemoteFileWriter getFileWriter();
}
