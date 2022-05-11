// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.annotation.SuppressLint;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.util.LruCache;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static android.system.OsConstants.O_APPEND;
import static android.system.OsConstants.O_CREAT;
import static android.system.OsConstants.O_NONBLOCK;
import static android.system.OsConstants.O_RDONLY;
import static android.system.OsConstants.O_TRUNC;
import static android.system.OsConstants.O_WRONLY;
import static android.system.OsConstants.SEEK_CUR;
import static android.system.OsConstants.SEEK_END;
import static android.system.OsConstants.SEEK_SET;

// Copyright 2022 John "topjohnwu" Wu
class FileSystemService extends IFileSystemService.Stub {

    static final int PIPE_CAPACITY = 16 * 4096;

    private final LruCache<String, File> mCache = new LruCache<String, File>(100) {
        @Override
        protected File create(String key) {
            return new File(key);
        }
    };

    @Override
    public ParcelValues getCanonicalPath(String path) {
        ParcelValues p = new ParcelValues();
        try {
            String v = mCache.get(path).getCanonicalPath();
            p.add(null);
            p.add(v);
        } catch (IOException e) {
            p.add(e);
            p.add(null);
        }
        return p;
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
    public long length(String path) {
        return mCache.get(path).length();
    }

    @Override
    public ParcelValues createNewFile(String path) {
        ParcelValues p = new ParcelValues();
        try {
            boolean v = mCache.get(path).createNewFile();
            p.add(null);
            p.add(v);
        } catch (IOException e) {
            p.add(e);
            p.add(null);
        }
        return p;
    }

    @Override
    public boolean delete(String path) {
        return mCache.get(path).delete();
    }

    @Override
    public String[] list(String path) {
        return mCache.get(path).list();
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
    public ParcelValues getMode(String path) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        try {
            values.add(Os.lstat(path).st_mode);
        } catch (ErrnoException e) {
            values.set(0, e);
        }
        return values;
    }

    @Override
    public ParcelValues setMode(String path, int mode) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        try {
            Os.chmod(path, mode);
        } catch (ErrnoException e) {
            values.set(0, e);
        }
        return values;
    }

    @Override
    public ParcelValues getUidGid(String path) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        try {
            StructStat s = Os.lstat(path);
            values.add(s.st_uid);
            values.add(s.st_gid);
        } catch (ErrnoException e) {
            values.set(0, e);
        }
        return values;
    }

    @Override
    public ParcelValues setUidGid(String path, int uid, int gid) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        try {
            Os.chown(path, uid, gid);
        } catch (ErrnoException e) {
            values.set(0, e);
        }
        return values;
    }

    @Override
    public ParcelValues createLink(String link, String target, boolean soft) {
        ParcelValues p = new ParcelValues();
        try {
            if (soft)
                Os.symlink(target, link);
            else
                Os.link(target, link);
            p.add(null);
            p.add(true);
        } catch (ErrnoException e) {
            if (e.errno == OsConstants.EEXIST) {
                p.add(null);
            } else {
                p.add(e);
            }
            p.add(false);
        }
        return p;
    }

    // I/O APIs

    private final FileContainer openFiles = new FileContainer();
    private final ExecutorService ioPool = Executors.newCachedThreadPool();

    @Override
    public void register(IBinder client) {
        int pid = Binder.getCallingPid();
        try {
            client.linkToDeath(() -> openFiles.pidDied(pid), 0);
        } catch (RemoteException ignored) {}
    }

    @SuppressWarnings("OctalInteger")
    @Override
    public ParcelValues openChannel(String path, int mode, String fifo) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        FileHolder h = new FileHolder();
        try {
            h.fd = Os.open(path, mode | O_NONBLOCK, 0666);
            h.read = Os.open(fifo, O_RDONLY | O_NONBLOCK, 0);
            h.write = Os.open(fifo, O_WRONLY | O_NONBLOCK, 0);
            values.add(openFiles.put(h));
        } catch (ErrnoException e) {
            values.set(0, e);
            h.close();
        }
        return values;
    }

    @Override
    public ParcelValues openReadStream(String path, ParcelFileDescriptor fd) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        FileHolder h = new FileHolder();
        try {
            h.fd = Os.open(path, O_RDONLY, 0);
            ioPool.execute(() -> {
                try {
                    h.write = FileUtils.createFileDescriptor(fd.detachFd());
                    while (h.fdToPipe(PIPE_CAPACITY, -1) > 0);
                } catch (ErrnoException | IOException ignored) {
                } finally {
                    h.close();
                }
            });
        } catch (ErrnoException e) {
            values.set(0, e);
            h.close();
        }
        return values;
    }

    @SuppressWarnings("OctalInteger")
    @Override
    public ParcelValues openWriteStream(String path, ParcelFileDescriptor fd, boolean append) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        FileHolder h = new FileHolder();
        try {
            int mode = O_CREAT | O_WRONLY | (append ? O_APPEND : O_TRUNC);
            h.fd = Os.open(path, mode, 0666);
            ioPool.execute(() -> {
                try {
                    h.read = FileUtils.createFileDescriptor(fd.detachFd());
                    while (h.pipeToFd(PIPE_CAPACITY, -1, false) > 0);
                } catch (ErrnoException | IOException ignored) {
                } finally {
                    h.close();
                }
            });
        } catch (ErrnoException e) {
            values.set(0, e);
            h.close();
        }
        return values;
    }

    @Override
    public void close(int handle) {
        openFiles.remove(handle);
    }

    @Override
    public ParcelValues pread(int handle, int len, long offset) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        try {
            final FileHolder h = openFiles.get(handle);
            synchronized (h) {
                values.add(h.fdToPipe(len, offset));
            }
        } catch (IOException | ErrnoException e) {
            values.set(0, e);
        }
        return values;
    }

    @Override
    public ParcelValues pwrite(int handle, int len, long offset) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        try {
            final FileHolder h = openFiles.get(handle);
            synchronized (h) {
                h.pipeToFd(len, offset, true);
            }
        } catch (IOException | ErrnoException e) {
            values.set(0, e);
        }
        return values;
    }

    @Override
    public ParcelValues lseek(int handle, long offset, int whence) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        try {
            final FileHolder h = openFiles.get(handle);
            synchronized (h) {
                h.ensureOpen();
                values.add(Os.lseek(h.fd, offset, whence));
            }
        } catch (IOException | ErrnoException e) {
            values.set(0, e);
        }
        return values;
    }

    @Override
    public ParcelValues size(int handle) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        try {
            final FileHolder h = openFiles.get(handle);
            synchronized (h) {
                h.ensureOpen();
                long cur = Os.lseek(h.fd, 0, SEEK_CUR);
                Os.lseek(h.fd, 0, SEEK_END);
                values.add(Os.lseek(h.fd, 0, SEEK_CUR));
                Os.lseek(h.fd, cur, SEEK_SET);
            }
        } catch (IOException | ErrnoException e) {
            values.set(0, e);
        }
        return values;
    }

    @Override
    public ParcelValues ftruncate(int handle, long length) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        try {
            final FileHolder h = openFiles.get(handle);
            synchronized (h) {
                h.ensureOpen();
                Os.ftruncate(h.fd, length);
            }
        } catch (IOException | ErrnoException e) {
            values.set(0, e);
        }
        return values;
    }

    @Override
    public ParcelValues sync(int handle, boolean metaData) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        try {
            final FileHolder h = openFiles.get(handle);
            synchronized (h) {
                h.ensureOpen();
                if (metaData)
                    Os.fsync(h.fd);
                else
                    Os.fdatasync(h.fd);
            }
        } catch (IOException | ErrnoException e) {
            values.set(0, e);
        }
        return values;
    }
}