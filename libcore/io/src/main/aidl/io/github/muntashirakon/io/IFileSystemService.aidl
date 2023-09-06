// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.io;

import aosp.android.content.pm.StringParceledListSlice;
import io.github.muntashirakon.io.IOResult;

// Copyright 2022 John "topjohnwu" Wu
// Copyright 2022 Muntashir Al-Islam
interface IFileSystemService {
    // File APIs
    /* (err, String) */ IOResult getCanonicalPath(String path);
    boolean isDirectory(String path);
    boolean isFile(String path);
    boolean isHidden(String path);
    long lastModified(String path);
    /* (err, long) */ IOResult lastAccess(String path);
    /* (err, long) */ IOResult creationTime(String path);
    long length(String path);
    /* (err, bool) */ IOResult createNewFile(String path);
    boolean delete(String path);
    StringParceledListSlice list(String path);
    boolean mkdir(String path);
    boolean mkdirs(String path);
    boolean renameTo(String path, String dest);
    boolean setLastModified(String path, long time);
    /* (err) */ IOResult setLastAccess(String path, long time);
    boolean setReadOnly(String path);
    boolean setWritable(String path, boolean writable, boolean ownerOnly);
    boolean setReadable(String path, boolean readable, boolean ownerOnly);
    boolean setExecutable(String path, boolean executable, boolean ownerOnly);
    boolean checkAccess(String path, int access);
    long getTotalSpace(String path);
    long getFreeSpace(String path);
    long getUsableSpace(String path);
    /* (err, int) */ IOResult getMode(String path);
    /* (err) */ IOResult setMode(String path, int mode);
    /* (err, int, int) */ IOResult getUidGid(String path);
    /* (err) */ IOResult setUidGid(String path, int uid, int gid);
    String getSelinuxContext(String path);
    boolean restoreSelinuxContext(String path);
    boolean setSelinuxContext(String path, String context);
    /* (err, bool) */ IOResult createLink(String link, String target, boolean soft);

    // I/O APIs
    oneway void register(IBinder client);
    /* (err, int) */ IOResult openChannel(String path, int mode, String fifo);
    /* (err) */ IOResult openReadStream(String path, in ParcelFileDescriptor fd);
    /* (err) */ IOResult openWriteStream(String path, in ParcelFileDescriptor fd, boolean append);
    oneway void close(int handle);
    /* (err, int) */ IOResult pread(int handle, int len, long offset);
    /* (err) */ IOResult pwrite(int handle, int len, long offset);
    /* (err, long) */ IOResult lseek(int handle, long offset, int whence);
    /* (err, long) */ IOResult size(int handle);
    /* (err) */ IOResult ftruncate(int handle, long length);
    /* (err) */ IOResult sync(int handle, boolean metadata);
}
