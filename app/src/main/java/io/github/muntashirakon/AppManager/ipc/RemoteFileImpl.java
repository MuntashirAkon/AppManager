// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.annotation.SuppressLint;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import aosp.libcore.util.EmptyArray;
import io.github.muntashirakon.AppManager.IRemoteFile;
import io.github.muntashirakon.AppManager.IRemoteFileReader;
import io.github.muntashirakon.AppManager.IRemoteFileWriter;

import static io.github.muntashirakon.AppManager.ipc.RootService.TAG;

class RemoteFileImpl extends IRemoteFile.Stub {
    File file;

    RemoteFileImpl(String file) {
        this.file = new File(file);
    }

    @Override
    public boolean isAbsolute() {
        return file.isAbsolute();
    }

    @Override
    public String getAbsolutePath() {
        return file.getAbsolutePath();
    }

    @Override
    public String getCanonicalPath() throws RemoteException {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            Log.e(TAG, null, e);
            throw new RemoteException(e.toString());
        }
    }

    @Override
    public boolean canRead() {
        return file.canRead();
    }

    @Override
    public boolean canWrite() {
        return file.canWrite();
    }

    @Override
    public boolean canExecute() {
        return file.canExecute();
    }

    @Override
    public boolean exists() {
        return file.exists();
    }

    @Override
    public boolean isDirectory() {
        return file.isDirectory();
    }

    @Override
    public boolean isFile() {
        return file.isFile();
    }

    @Override
    public boolean isHidden() {
        return file.isHidden();
    }

    @Override
    public long lastModified() {
        return file.lastModified();
    }

    @Override
    public long length() {
        return file.length();
    }

    @Override
    public boolean createNewFile() throws RemoteException {
        try {
            return file.createNewFile();
        } catch (IOException e) {
            Log.e(TAG, null, e);
            throw new RemoteException(e.toString());
        }
    }

    @Override
    public boolean delete() {
        return file.delete();
    }

    @Override
    public void deleteOnExit() {
        file.deleteOnExit();
    }

    @Override
    public String[] list() {
        return file.list();
    }

    @Override
    public String[] listFiles() {
        File[] files = file.listFiles();
        if (files != null) {
            String[] strFiles = new String[files.length];
            int i = 0;
            for (File file : files) {
                strFiles[i++] = file.getAbsolutePath();
            }
            return strFiles;
        }
        return EmptyArray.STRING;
    }

    @Override
    public boolean mkdir() {
        return file.mkdir();
    }

    @Override
    public boolean mkdirs() {
        return file.mkdirs();
    }

    @Override
    public boolean renameTo(String dest) {
        return file.renameTo(new File(dest));
    }

    @Override
    public boolean setLastModified(long time) {
        return file.setLastModified(time);
    }

    @Override
    public boolean setReadOnly() {
        return file.setReadOnly();
    }

    @Override
    public long getTotalSpace() {
        return file.getTotalSpace();
    }

    @Override
    public long getFreeSpace() {
        return file.getFreeSpace();
    }

    @SuppressLint("UsableSpace")
    @Override
    public long getUsableSpace() {
        return file.getUsableSpace();
    }

    @Override
    public boolean setWritable(boolean writable, boolean ownerOnly) {
        return file.setWritable(writable, ownerOnly);
    }

    @Override
    public boolean setWritable1(boolean writable) {
        return file.setWritable(writable);
    }

    @Override
    public boolean setReadable(boolean readable, boolean ownerOnly) {
        return file.setReadable(readable, ownerOnly);
    }

    @Override
    public boolean setReadable1(boolean readable) {
        return file.setReadable(readable);
    }

    @Override
    public boolean setExecutable(boolean executable, boolean ownerOnly) {
        return file.setExecutable(executable, ownerOnly);
    }

    @Override
    public boolean setExecutable1(boolean executable) {
        return file.setExecutable(executable);
    }

    @Override
    public int compareTo(String pathname) {
        return file.compareTo(new File(pathname));
    }

    @Override
    public IRemoteFileReader getFileReader() throws RemoteException {
        try {
            return new RemoteFileReaderImpl(file);
        } catch (FileNotFoundException e) {
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public IRemoteFileWriter getFileWriter() throws RemoteException {
        try {
            return new RemoteFileWriterImpl(file);
        } catch (FileNotFoundException e) {
            throw new RemoteException(e.getMessage());
        }
    }
}
