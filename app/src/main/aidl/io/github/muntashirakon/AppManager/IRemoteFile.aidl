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

package io.github.muntashirakon.AppManager;

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

    ParcelFileDescriptor getInputStream();
    ParcelFileDescriptor getOutputStream();

    ParcelFileDescriptor getPipedInputStream();
    ParcelFileDescriptor getPipedOutputStream();
}
