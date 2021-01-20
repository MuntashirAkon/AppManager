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

package io.github.muntashirakon.AppManager.ipc;

import android.annotation.SuppressLint;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

import androidx.annotation.NonNull;
import aosp.libcore.util.EmptyArray;
import io.github.muntashirakon.AppManager.IRemoteFile;
import io.github.muntashirakon.AppManager.utils.ParcelFileDescriptorUtil;

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
    @NonNull
    public ParcelFileDescriptor getInputStream() throws RemoteException {
        try {
            return Objects.requireNonNull(ParcelFileDescriptorUtil.pipeFrom(new FileInputStream(file)));
        } catch (IOException | NullPointerException e) {
            Log.e(TAG, null, e);
            throw new RemoteException(e.toString());
        }
    }

    @Override
    @NonNull
    public ParcelFileDescriptor getOutputStream() throws RemoteException {
        try {
            return Objects.requireNonNull(ParcelFileDescriptorUtil.pipeTo(new FileOutputStream(file)));
        } catch (IOException | NullPointerException e) {
            Log.e(TAG, null, e);
            throw new RemoteException(e.toString());
        }
    }
}
