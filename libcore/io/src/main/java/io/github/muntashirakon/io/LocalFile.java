// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.os.SELinux;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import io.github.muntashirakon.compat.system.OsCompat;
import io.github.muntashirakon.compat.system.StructTimespec;

// Copyright 2022 John "topjohnwu" Wu
// Copyright 2022 Muntashir Al-Islam
class LocalFile extends FileImpl<LocalFile> {

    LocalFile(@NonNull String pathname) {
        super(pathname);
    }

    LocalFile(@Nullable String parent, @NonNull String child) {
        super(parent, child);
    }

    @Override
    protected LocalFile create(String path) {
        return new LocalFile(path);
    }

    @NonNull
    @Override
    public LocalFile getChildFile(String name) {
        return new LocalFile(getPath(), name);
    }

    @Override
    protected LocalFile[] createArray(int n) {
        return new LocalFile[n];
    }

    @Override
    public int getMode() throws ErrnoException {
        return Os.lstat(getPath()).st_mode;
    }

    @Override
    public boolean setMode(int mode) throws ErrnoException {
        Os.chmod(getPath(), mode);
        return true;
    }

    @Override
    public UidGidPair getUidGid() throws ErrnoException {
        StructStat s = Os.lstat(getPath());
        return new UidGidPair(s.st_uid, s.st_gid);
    }

    @Override
    public boolean setUidGid(int uid, int gid) throws ErrnoException {
        Os.chown(getPath(), uid, gid);
        return true;
    }

    @Override
    public String getSelinuxContext() {
        return SELinux.getFileContext(getPath());
    }

    @Override
    public boolean restoreSelinuxContext() {
        return SELinux.restorecon(getPath());
    }

    @Override
    public boolean setSelinuxContext(String context) {
        return SELinux.setFileContext(getPath(), context);
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
    public long creationTime() {
        try {
            return Os.lstat(getPath()).st_ctime * 1000;
        } catch (ErrnoException e) {
            return 0;
        }
    }

    @Override
    public long lastAccess() {
        try {
            return Os.lstat(getPath()).st_atime * 1000;
        } catch (ErrnoException e) {
            return 0;
        }
    }

    @Override
    public boolean setLastAccess(long millis) {
        long seconds_part = millis / 1_000;
        long nanoseconds_part = (millis % 1_000) * 1_000_000;
        StructTimespec atime = new StructTimespec(seconds_part, nanoseconds_part);
        StructTimespec mtime = new StructTimespec(0, OsCompat.UTIME_OMIT);
        try {
            OsCompat.utimensat(OsCompat.AT_FDCWD, getPath(), atime, mtime, OsCompat.AT_SYMLINK_NOFOLLOW);
            return true;
        } catch (ErrnoException e) {
            return false;
        }
    }

    @NonNull
    @Override
    public FileInputStream newInputStream() throws IOException {
        return new FileInputStream(this);
    }

    @NonNull
    @Override
    public FileOutputStream newOutputStream(boolean append) throws IOException {
        return new FileOutputStream(this, append);
    }

    @Override
    public boolean createNewLink(String existing) throws IOException {
        return createLink(existing, false);
    }

    @Override
    public boolean createNewSymlink(String target) throws IOException {
        return createLink(target, true);
    }

    private boolean createLink(String target, boolean soft) throws IOException {
        try {
            if (soft)
                Os.symlink(target, getPath());
            else
                Os.link(target, getPath());
            return true;
        } catch (ErrnoException e) {
            if (e.errno != OsConstants.EEXIST) {
                throw new IOException(e);
            }
            return false;
        }
    }
}