// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import static android.system.OsConstants.O_APPEND;
import static android.system.OsConstants.O_CREAT;
import static android.system.OsConstants.O_NONBLOCK;
import static android.system.OsConstants.O_RDONLY;
import static android.system.OsConstants.O_TRUNC;
import static android.system.OsConstants.O_WRONLY;

import android.annotation.SuppressLint;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SELinux;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.util.LruCache;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import aosp.android.content.pm.StringParceledListSlice;
import io.github.muntashirakon.compat.system.OsCompat;
import io.github.muntashirakon.compat.system.StructTimespec;

// Copyright 2022 John "topjohnwu" Wu
// Copyright 2022 Muntashir Al-Islam
class FileSystemService extends IFileSystemService.Stub {

    static final int PIPE_CAPACITY = 16 * 4096;

    private final LruCache<String, File> mCache = new LruCache<String, File>(100) {
        @Override
        protected File create(String key) {
            return new File(key);
        }
    };

    @Override
    public IOResult getCanonicalPath(String path) {
        try {
            return new IOResult(mCache.get(path).getCanonicalPath());
        } catch (IOException e) {
            return new IOResult(e);
        }
    }

    @Override
    public boolean isDirectory(String path) {
        return mCache.get(path).isDirectory();
    }

    @Override
    public boolean isFile(String path) {
        return mCache.get(path).isFile();
    }

    @Override
    public boolean isHidden(String path) {
        return mCache.get(path).isHidden();
    }

    @Override
    public long lastModified(String path) {
        return mCache.get(path).lastModified();
    }

    @Override
    public IOResult lastAccess(String path) {
        try {
            return new IOResult(Os.lstat(path).st_atime * 1000);
        } catch (ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult creationTime(String path) {
        try {
            return new IOResult(Os.lstat(path).st_ctime * 1000);
        } catch (ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public long length(String path) {
        return mCache.get(path).length();
    }

    @Override
    public IOResult createNewFile(String path) {
        try {
            return new IOResult(mCache.get(path).createNewFile());
        } catch (IOException e) {
            return new IOResult(e);
        }
    }

    @Override
    public boolean delete(String path) {
        return mCache.get(path).delete();
    }

    @Override
    public StringParceledListSlice list(String path) {
        String[] list = mCache.get(path).list();
        return list != null ? new StringParceledListSlice(Arrays.asList(list)) : null;
    }

    @Override
    public boolean mkdir(String path) {
        return mCache.get(path).mkdir();
    }

    @Override
    public boolean mkdirs(String path) {
        return mCache.get(path).mkdirs();
    }

    @Override
    public boolean renameTo(String path, String dest) {
        return mCache.get(path).renameTo(mCache.get(dest));
    }

    @Override
    public boolean setLastModified(String path, long time) {
        return mCache.get(path).setLastModified(time);
    }

    @Override
    public IOResult setLastAccess(String path, long time) {
        long seconds_part = time / 1_000;
        long nanoseconds_part = (time % 1_000) * 1_000_000;
        StructTimespec atime = new StructTimespec(seconds_part, nanoseconds_part);
        StructTimespec mtime = new StructTimespec(0, OsCompat.UTIME_OMIT);
        try {
            OsCompat.utimensat(OsCompat.AT_FDCWD, path, atime, mtime, OsCompat.AT_SYMLINK_NOFOLLOW);
            return new IOResult(true);
        } catch (ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public boolean setReadOnly(String path) {
        return mCache.get(path).setReadOnly();
    }

    @Override
    public boolean setWritable(String path, boolean writable, boolean ownerOnly) {
        return mCache.get(path).setWritable(writable, ownerOnly);
    }

    @Override
    public boolean setReadable(String path, boolean readable, boolean ownerOnly) {
        return mCache.get(path).setReadable(readable, ownerOnly);
    }

    @Override
    public boolean setExecutable(String path, boolean executable, boolean ownerOnly) {
        return mCache.get(path).setExecutable(executable, ownerOnly);
    }

    @Override
    public boolean checkAccess(String path, int access) {
        try {
            return Os.access(path, access);
        } catch (ErrnoException e) {
            return false;
        }
    }

    @Override
    public long getTotalSpace(String path) {
        return mCache.get(path).getTotalSpace();
    }

    @Override
    public long getFreeSpace(String path) {
        return mCache.get(path).getFreeSpace();
    }

    @SuppressLint("UsableSpace")
    @Override
    public long getUsableSpace(String path) {
        return mCache.get(path).getUsableSpace();
    }

    @Override
    public IOResult getMode(String path) {
        try {
            return new IOResult(Os.lstat(path).st_mode);
        } catch (ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult setMode(String path, int mode) {
        try {
            Os.chmod(path, mode);
            return new IOResult(true);
        } catch (ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult getUidGid(String path) {
        try {
            StructStat s = Os.lstat(path);
            return new IOResult(new UidGidPair(s.st_uid, s.st_gid));
        } catch (ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult setUidGid(String path, int uid, int gid) {
        try {
            Os.chown(path, uid, gid);
            return new IOResult(true);
        } catch (ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public String getSelinuxContext(String path) {
        return SELinux.getFileContext(path);
    }

    @Override
    public boolean restoreSelinuxContext(String path) {
        return SELinux.restorecon(path);
    }

    @Override
    public boolean setSelinuxContext(String path, String context) {
        return SELinux.setFileContext(path, context);
    }

    @Override
    public IOResult createLink(String link, String target, boolean soft) {
        try {
            if (soft) {
                Os.symlink(target, link);
            } else {
                Os.link(target, link);
            }
            return new IOResult(true);
        } catch (ErrnoException e) {
            if (e.errno == OsConstants.EEXIST) {
                return new IOResult(false);
            } else {
                return new IOResult(e);
            }
        }
    }

    // I/O APIs

    private final FileContainer openFiles = new FileContainer();
    private final ExecutorService streamPool = Executors.newCachedThreadPool();

    @Override
    public void register(IBinder client) {
        int pid = Binder.getCallingPid();
        try {
            client.linkToDeath(() -> openFiles.pidDied(pid), 0);
        } catch (RemoteException ignored) {
        }
    }

    @SuppressWarnings("OctalInteger")
    @Override
    public IOResult openChannel(String path, int mode, String fifo) {
        OpenFile f = new OpenFile();
        try {
            f.fd = Os.open(path, mode | O_NONBLOCK, 0666);
            f.read = Os.open(fifo, O_RDONLY | O_NONBLOCK, 0);
            f.write = Os.open(fifo, O_WRONLY | O_NONBLOCK, 0);
            return new IOResult(openFiles.put(f));
        } catch (ErrnoException e) {
            f.close();
            return new IOResult(e);
        }
    }

    @Override
    public IOResult openReadStream(String path, ParcelFileDescriptor fd) {
        OpenFile f = new OpenFile();
        try {
            f.fd = Os.open(path, O_RDONLY, 0);
            streamPool.execute(() -> {
                try (OpenFile of = f) {
                    of.write = FileUtils.createFileDescriptor(fd.detachFd());
                    while (of.pread(PIPE_CAPACITY, -1) > 0);
                } catch (ErrnoException | IOException ignored) {}
            });
            return new IOResult();
        } catch (ErrnoException e) {
            f.close();
            return new IOResult(e);
        }
    }

    @SuppressWarnings("OctalInteger")
    @Override
    public IOResult openWriteStream(String path, ParcelFileDescriptor fd, boolean append) {
        OpenFile f = new OpenFile();
        try {
            int mode = O_CREAT | O_WRONLY | (append ? O_APPEND : O_TRUNC);
            f.fd = Os.open(path, mode, 0666);
            streamPool.execute(() -> {
                try (OpenFile of = f) {
                    of.read = FileUtils.createFileDescriptor(fd.detachFd());
                    while (of.pwrite(PIPE_CAPACITY, -1, false) > 0);
                } catch (ErrnoException | IOException ignored) {}
            });
            return new IOResult();
        } catch (ErrnoException e) {
            f.close();
            return new IOResult(e);
        }
    }

    @Override
    public void close(int handle) {
        openFiles.remove(handle);
    }

    @Override
    public IOResult pread(int handle, int len, long offset) {
        try {
            return new IOResult(openFiles.get(handle).pread(len, offset));
        } catch (IOException | ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult pwrite(int handle, int len, long offset) {
        try {
            openFiles.get(handle).pwrite(len, offset, true);
            return new IOResult();
        } catch (IOException | ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult lseek(int handle, long offset, int whence) {
        try {
            return new IOResult(openFiles.get(handle).lseek(offset, whence));
        } catch (IOException | ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult size(int handle) {
        try {
            return new IOResult(openFiles.get(handle).size());
        } catch (IOException | ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult ftruncate(int handle, long length) {
        try {
            openFiles.get(handle).ftruncate(length);
            return new IOResult();
        } catch (IOException | ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult sync(int handle, boolean metadata) {
        try {
            openFiles.get(handle).sync(metadata);
            return new IOResult();
        } catch (IOException | ErrnoException e) {
            return new IOResult(e);
        }
    }
}