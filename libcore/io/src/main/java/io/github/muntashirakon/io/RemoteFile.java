// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.OsConstants;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

// Copyright 2022 John "topjohnwu" Wu
class RemoteFile extends FileImpl<RemoteFile> {

    private final IFileSystemService fs;

    RemoteFile(IFileSystemService f, String path) {
        super(path);
        fs = f;
    }

    RemoteFile(IFileSystemService f, String parent, String child) {
        super(parent, child);
        fs = f;
    }

    @Override
    protected RemoteFile create(String path) {
        return new RemoteFile(fs, path);
    }

    @NonNull
    @Override
    public RemoteFile getChildFile(String name) {
        return new RemoteFile(fs, getPath(), name);
    }

    @Override
    protected RemoteFile[] createArray(int n) {
        return new RemoteFile[n];
    }

    @Override
    @NonNull
    public String getCanonicalPath() throws IOException {
        try {
            return FileUtils.tryAndGet(fs.getCanonicalPath(getPath()));
        } catch (RemoteException e) {
            throw new IOException(e);
        }
    }

    private boolean checkAccess(int access) {
        try {
            return fs.checkAccess(getPath(), access);
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean canRead() {
        return checkAccess(OsConstants.R_OK);
    }

    @Override
    public boolean canWrite() {
        return checkAccess(OsConstants.W_OK);
    }

    @Override
    public boolean canExecute() {
        return checkAccess(OsConstants.X_OK);
    }

    @Override
    public boolean exists() {
        return checkAccess(OsConstants.F_OK);
    }

    @Override
    public boolean isDirectory() {
        try {
            return fs.isDirectory(getPath());
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean isFile() {
        try {
            return fs.isFile(getPath());
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public int getMode() throws ErrnoException {
        try {
            return FileUtils.tryErrnoAndGet(fs.getMode(getPath()));
        } catch (RemoteException e) {
            return 0;
        }
    }

    @Override
    public boolean setMode(int mode) throws ErrnoException {
        try {
            FileUtils.checkErrnoException(fs.setMode(getPath(), mode));
            return true;
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public UidGidPair getUidGid() throws ErrnoException {
        try {
            ParcelValues values = fs.getUidGid(getPath());
            FileUtils.checkErrnoException(values);
            return new UidGidPair(values.getTyped(1), values.getTyped(2));
        } catch (RemoteException e) {
            return new UidGidPair(0, 0);
        }
    }

    @Override
    public boolean setUidGid(int uid, int gid) throws ErrnoException {
        try {
            FileUtils.checkErrnoException(fs.setUidGid(getPath(), uid, gid));
            return true;
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean isBlock() {
        try {
            return OsConstants.S_ISBLK(getMode());
        } catch (ErrnoException e) {
            return false;
        }
    }

    @Override
    public boolean isCharacter() {
        try {
            return OsConstants.S_ISCHR(getMode());
        } catch (ErrnoException e) {
            return false;
        }
    }

    @Override
    public boolean isSymlink() {
        try {
            return OsConstants.S_ISLNK(getMode());
        } catch (ErrnoException e) {
            return false;
        }
    }

    @Override
    public boolean isNamedPipe() {
        try {
            return OsConstants.S_ISFIFO(getMode());
        } catch (ErrnoException e) {
            return false;
        }
    }

    @Override
    public boolean isSocket() {
        try {
            return OsConstants.S_ISSOCK(getMode());
        } catch (ErrnoException e) {
            return false;
        }
    }

    @Override
    public boolean isHidden() {
        try {
            return fs.isHidden(getPath());
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public long lastModified() {
        try {
            return fs.lastModified(getPath());
        } catch (RemoteException e) {
            return Long.MIN_VALUE;
        }
    }

    @Override
    public long length() {
        try {
            return fs.length(getPath());
        } catch (RemoteException e) {
            return 0L;
        }
    }

    @Override
    public boolean createNewFile() throws IOException {
        try {
            return FileUtils.tryAndGet(fs.createNewFile(getPath()));
        } catch (RemoteException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean createNewLink(String existing) throws IOException {
        try {
            return FileUtils.tryAndGet(fs.createLink(getPath(), existing, false));
        } catch (RemoteException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean createNewSymlink(String target) throws IOException {
        try {
            return FileUtils.tryAndGet(fs.createLink(getPath(), target, true));
        } catch (RemoteException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean delete() {
        try {
            return fs.delete(getPath());
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public void deleteOnExit() {
        throw new UnsupportedOperationException("deleteOnExit() is not supported in RemoteFile");
    }

    @Override
    public String[] list() {
        try {
            return fs.list(getPath());
        } catch (RemoteException e) {
            return null;
        }
    }

    @Override
    public boolean mkdir() {
        try {
            return fs.mkdir(getPath());
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean mkdirs() {
        try {
            return fs.mkdirs(getPath());
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean renameTo(@NonNull File dest) {
        try {
            return fs.renameTo(getPath(), dest.getAbsolutePath());
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean setLastModified(long time) {
        try {
            return fs.setLastModified(getPath(), time);
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean setReadOnly() {
        try {
            return fs.setReadOnly(getPath());
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean setWritable(boolean writable, boolean ownerOnly) {
        try {
            return fs.setWritable(getPath(), writable, ownerOnly);
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean setReadable(boolean readable, boolean ownerOnly) {
        try {
            return fs.setReadable(getPath(), readable, ownerOnly);
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean setExecutable(boolean executable, boolean ownerOnly) {
        try {
            return fs.setExecutable(getPath(), executable, ownerOnly);
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public long getTotalSpace() {
        try {
            return fs.getTotalSpace(getPath());
        } catch (RemoteException e) {
            return 0L;
        }
    }

    @Override
    public long getFreeSpace() {
        try {
            return fs.getFreeSpace(getPath());
        } catch (RemoteException e) {
            return 0L;
        }
    }

    @Override
    public long getUsableSpace() {
        try {
            return fs.getUsableSpace(getPath());
        } catch (RemoteException e) {
            return 0L;
        }
    }

    @NonNull
    @Override
    public FileInputStream newInputStream() throws IOException {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        try {
            FileUtils.checkException(fs.openReadStream(getPath(), pipe[1]));
        } catch (RemoteException e) {
            pipe[0].close();
            throw new IOException(e);
        } finally {
            pipe[1].close();
        }
        return new ParcelFileDescriptor.AutoCloseInputStream(pipe[0]);
    }

    @NonNull
    @Override
    public FileOutputStream newOutputStream(boolean append) throws IOException {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        try {
            FileUtils.checkException(fs.openWriteStream(getPath(), pipe[0], append));
        } catch (RemoteException e) {
            pipe[1].close();
            throw new IOException(e);
        } finally {
            pipe[0].close();
        }
        return new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);
    }
}