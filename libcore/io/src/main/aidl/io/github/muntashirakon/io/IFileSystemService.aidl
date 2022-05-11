// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.io;

import io.github.muntashirakon.io.ParcelValues;

// Copyright 2022 John "topjohnwu" Wu
interface IFileSystemService {
    // File APIs
    /* (err, String) */ ParcelValues getCanonicalPath(String path);
    boolean isDirectory(String path);
    boolean isFile(String path);
    boolean isHidden(String path);
    long lastModified(String path);
    long length(String path);
    /* (err, bool) */ ParcelValues createNewFile(String path);
    boolean delete(String path);
    String[] list(String path);
    boolean mkdir(String path);
    boolean mkdirs(String path);
    boolean renameTo(String path, String dest);
    boolean setLastModified(String path, long time);
    boolean setReadOnly(String path);
    boolean setWritable(String path, boolean writable, boolean ownerOnly);
    boolean setReadable(String path, boolean readable, boolean ownerOnly);
    boolean setExecutable(String path, boolean executable, boolean ownerOnly);
    boolean checkAccess(String path, int access);
    long getTotalSpace(String path);
    long getFreeSpace(String path);
    long getUsableSpace(String path);
    /* (err, int) */ ParcelValues getMode(String path);
    /* (err) */ ParcelValues setMode(String path, int mode);
    /* (err, int, int) */ ParcelValues getUidGid(String path);
    /* (err) */ ParcelValues setUidGid(String path, int uid, int gid);
    /* (err, bool) */ ParcelValues createLink(String link, String target, boolean soft);

    // I/O APIs
    oneway void register(IBinder client);
    /* (err, int) */ ParcelValues openChannel(String path, int mode, String fifo);
    /* (err) */ ParcelValues openReadStream(String path, in ParcelFileDescriptor fd);
    /* (err) */ ParcelValues openWriteStream(String path, in ParcelFileDescriptor fd, boolean append);
    oneway void close(int handle);
    /* (err, int) */ ParcelValues pread(int handle, int len, long offset);
    /* (err) */ ParcelValues pwrite(int handle, int len, long offset);
    /* (err, long) */ ParcelValues lseek(int handle, long offset, int whence);
    /* (err, long) */ ParcelValues size(int handle);
    /* (err) */ ParcelValues ftruncate(int handle, long length);
    /* (err) */ ParcelValues sync(int handle, boolean metaData);
}
