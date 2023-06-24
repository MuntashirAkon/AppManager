// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.io;

import static android.system.OsConstants.SEEK_CUR;
import static android.system.OsConstants.SEEK_END;
import static android.system.OsConstants.SEEK_SET;

import android.annotation.SuppressLint;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Int64Ref;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.util.MutableLong;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;

// Copyright 2022 John "topjohnwu" Wu
class OpenFile implements Closeable {
    // This is only for testing purpose
    private static final boolean FORCE_NO_SPLICE = false;

    FileDescriptor fd;
    FileDescriptor read;
    FileDescriptor write;

    private ByteBuffer buf;
    private StructStat st;

    private ByteBuffer getBuf() {
        if (buf == null)
            buf = ByteBuffer.allocateDirect(FileSystemService.PIPE_CAPACITY);
        buf.clear();
        return buf;
    }

    private StructStat getStat() throws ErrnoException {
        if (st == null)
            st = Os.fstat(fd);
        return st;
    }

    private void ensureOpen() throws ClosedChannelException {
        if (fd == null)
            throw new ClosedChannelException();
    }

    @Override
    synchronized public void close() {
        if (fd != null) {
            try {
                Os.close(fd);
            } catch (ErrnoException ignored) {
            }
            fd = null;
        }
        if (read != null) {
            try {
                Os.close(read);
            } catch (ErrnoException ignored) {
            }
            read = null;
        }
        if (write != null) {
            try {
                Os.close(write);
            } catch (ErrnoException ignored) {
            }
            write = null;
        }
    }

    synchronized long lseek(long offset, int whence) throws ErrnoException, IOException {
        ensureOpen();
        return Os.lseek(fd, offset, whence);
    }

    synchronized long size() throws ErrnoException, IOException {
        ensureOpen();
        long cur = Os.lseek(fd, 0, SEEK_CUR);
        Os.lseek(fd, 0, SEEK_END);
        long sz = Os.lseek(fd, 0, SEEK_CUR);
        Os.lseek(fd, cur, SEEK_SET);
        return sz;
    }

    synchronized void ftruncate(long length) throws ErrnoException, IOException {
        ensureOpen();
        Os.ftruncate(fd, length);
    }

    synchronized void sync(boolean metadata) throws ErrnoException, IOException {
        ensureOpen();
        if (metadata)
            Os.fsync(fd);
        else
            Os.fdatasync(fd);
    }

    @SuppressLint("NewApi")
    synchronized int pread(int len, long offset) throws ErrnoException, IOException {
        if (fd == null || write == null)
            throw new ClosedChannelException();
        final long result;
        if (!FORCE_NO_SPLICE && Build.VERSION.SDK_INT >= 28) {
            Int64Ref inOff = offset < 0 ? null : new Int64Ref(offset);
            result = FileUtils.splice(fd, inOff, write, null, len, 0);
        } else {
            StructStat st = getStat();
            if (OsConstants.S_ISREG(st.st_mode) || OsConstants.S_ISBLK(st.st_mode)) {
                // sendfile only supports reading from mmap-able files
                MutableLong inOff = offset < 0 ? null : new MutableLong(offset);
                result = FileUtils.sendfile(write, fd, inOff, len);
            } else {
                // Fallback to copy into internal buffer
                ByteBuffer buf = getBuf();
                buf.limit(Math.min(len, buf.capacity()));
                if (offset < 0) {
                    Os.read(fd, buf);
                } else {
                    Os.pread(fd, buf, offset);
                }
                buf.flip();
                result = buf.remaining();
                // Need to write all bytes
                for (int sz = (int) result; sz > 0; ) {
                    sz -= Os.write(write, buf);
                }
            }
        }
        return (int) result;
    }

    @SuppressLint("NewApi")
    synchronized int pwrite(int len, long offset, boolean exact) throws ErrnoException, IOException {
        if (fd == null || read == null)
            throw new ClosedChannelException();
        if (!FORCE_NO_SPLICE && Build.VERSION.SDK_INT >= 28) {
            Int64Ref outOff = offset < 0 ? null : new Int64Ref(offset);
            if (exact) {
                int sz = len;
                while (sz > 0) {
                    sz -= FileUtils.splice(read, null, fd, outOff, sz, 0);
                }
                return len;
            } else {
                return (int) FileUtils.splice(read, null, fd, outOff, len, 0);
            }
        } else {
            // Unfortunately, sendfile does not allow reading from pipes.
            // Manually read into an internal buffer then write to output.
            ByteBuffer buf = getBuf();
            int sz = 0;
            buf.limit(len);
            if (exact) {
                while (len > sz) {
                    sz += Os.read(read, buf);
                }
            } else {
                sz = Os.read(read, buf);
            }
            len = sz;
            buf.flip();
            while (sz > 0) {
                if (offset < 0) {
                    sz -= Os.write(fd, buf);
                } else {
                    int w = Os.pwrite(fd, buf, offset);
                    sz -= w;
                    offset += w;
                }
            }
            return len;
        }
    }
}