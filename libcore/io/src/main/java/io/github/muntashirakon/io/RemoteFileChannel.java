// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * Copyright 2022 John "topjohnwu" Wu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.muntashirakon.io;

import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import static android.os.ParcelFileDescriptor.MODE_READ_ONLY;
import static android.os.ParcelFileDescriptor.MODE_READ_WRITE;
import static android.os.ParcelFileDescriptor.MODE_WRITE_ONLY;
import static android.system.OsConstants.O_NONBLOCK;
import static android.system.OsConstants.O_RDONLY;
import static android.system.OsConstants.O_WRONLY;

class RemoteFileChannel extends FileChannel {

    private static final int PIPE_CAPACITY = 16 * 4096;

    private final IFileSystemService fs;
    private final int mode;
    private final Object fdLock = new Object();

    private final FileDescriptor read;
    private final FileDescriptor write;
    private final int handle;

    RemoteFileChannel(IFileSystemService fs, File file, int mode) throws IOException {
        this.fs = fs;
        this.mode = mode;
        File fifo = null;
        try {
            // We use a FIFO created on the client side instead of opening a pipe and
            // passing it through binder as this is the most portable and reliable method.
            fifo = FileUtils.createTempFIFO();

            // Open the file on the remote process
            int posixMode = FileUtils.modeToPosix(mode);
            handle = fs.openChannel(file.getAbsolutePath(), posixMode, fifo.getPath()).tryAndGet();

            // Since we do not have the machinery to interrupt native pthreads, we
            // have to make sure none of our I/O can block in all operations.
            read = Os.open(fifo.getPath(), O_RDONLY | O_NONBLOCK, 0);
            write = Os.open(fifo.getPath(), O_WRONLY | O_NONBLOCK, 0);
        } catch (RemoteException | ErrnoException e) {
            throw new IOException(e);
        } finally {
            // Once both sides opened the pipe, it can be unlinked
            if (fifo != null)
                fifo.delete();
        }
    }

    private void ensureOpen() throws IOException {
        if (!isOpen())
            throw new ClosedChannelException();
    }

    private boolean writable() {
        switch (mode & MODE_READ_WRITE) {
            case MODE_READ_WRITE:
            case MODE_WRITE_ONLY:
                return true;
            default:
                return false;
        }
    }

    private boolean readable() {
        switch (mode & MODE_READ_WRITE) {
            case MODE_READ_WRITE:
            case MODE_READ_ONLY:
                return true;
            default:
                return false;
        }
    }

    private int read0(ByteBuffer dst, long offset) throws IOException {
        begin();
        final int limit = dst.limit();
        final int initial = dst.position();
        boolean success = false;
        try {
            int pos = initial;
            for (; limit > pos; pos = dst.position()) {
                final int len;
                synchronized (fdLock) {
                    if (!isOpen() || Thread.interrupted())
                        return -1;
                    len = fs.pread(handle, limit - pos, offset).tryAndGet();
                    if (len == 0)
                        break;
                    dst.limit(pos + len);
                    // Must read exactly len bytes
                    for (int sz = 0; sz < len;) {
                        sz += Os.read(read, dst);
                    }
                }
                if (offset >= 0) {
                    offset += len;
                }
            }
            success = true;
            return pos - initial;
        } catch (ErrnoException | RemoteException e) {
            throw new IOException(e);
        } finally {
            dst.limit(limit);
            end(success);
        }
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        ensureOpen();
        if (!readable())
            throw new NonReadableChannelException();
        return read0(dst, -1);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        if ((offset < 0) || (length < 0) || (offset > dsts.length - length))
            throw new IndexOutOfBoundsException();
        ensureOpen();
        if (!readable())
            throw new NonReadableChannelException();
        int sz = 0;
        // Real scattered I/O is too complicated, let's cheat
        for (int i = offset; i < offset + length; ++i) {
            sz += read0(dsts[i], -1);
        }
        return sz;
    }

    private int write0(ByteBuffer src, long offset) throws IOException {
        begin();
        final int remaining = src.remaining();
        boolean success = false;
        try {
            while (src.hasRemaining()) {
                final int len;
                synchronized (fdLock) {
                    if (!isOpen() || Thread.interrupted())
                        return -1;
                    len = Os.write(write, src);
                    fs.pwrite(handle, len, offset).checkException();
                }
                if (offset >= 0) {
                    offset += len;
                }
            }
            success = true;
            return remaining;
        } catch (ErrnoException | RemoteException e) {
            throw new IOException(e);
        } finally {
            end(success);
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        ensureOpen();
        if (!writable())
            throw new NonWritableChannelException();
        return write0(src, -1);
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if ((offset < 0) || (length < 0) || (offset > srcs.length - length))
            throw new IndexOutOfBoundsException();
        ensureOpen();
        if (!writable())
            throw new NonWritableChannelException();
        int sz = 0;
        // Real scattered I/O is too complicated, let's cheat
        for (int i = offset; i < offset + length; ++i) {
            sz += write(srcs[i]);
        }
        return sz;
    }

    @Override
    public long position() throws IOException {
        ensureOpen();
        try {
            return fs.lseek(handle, 0, OsConstants.SEEK_CUR).tryAndGet();
        } catch (RemoteException e) {
            throw new IOException(e);
        }
    }

    @Override
    public RemoteFileChannel position(long newPosition) throws IOException {
        ensureOpen();
        if (newPosition < 0)
            throw new IllegalArgumentException();
        try {
            fs.lseek(handle, newPosition, OsConstants.SEEK_SET).checkException();
            return this;
        } catch (RemoteException e) {
            throw new IOException(e);
        }
    }

    @Override
    public long size() throws IOException {
        ensureOpen();
        try {
            return fs.size(handle).tryAndGet();
        } catch (RemoteException e) {
            throw new IOException(e);
        }
    }

    @Override
    public RemoteFileChannel truncate(long size) throws IOException {
        ensureOpen();
        if (size < 0)
            throw new IllegalArgumentException("Negative size");
        if (!writable())
            throw new NonWritableChannelException();
        try {
            fs.ftruncate(handle, size).checkException();
            return this;
        } catch (RemoteException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void force(boolean metaData) throws IOException {
        ensureOpen();
        try {
            fs.sync(handle, metaData).checkException();
        } catch (RemoteException e) {
            throw new IOException(e);
        }
    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target)
            throws IOException {
        ensureOpen();
        if (!target.isOpen())
            throw new ClosedChannelException();
        if (!readable())
            throw new NonReadableChannelException();
        if ((position < 0) || (count < 0))
            throw new IllegalArgumentException();

        ByteBuffer b = ByteBuffer.allocateDirect(PIPE_CAPACITY);
        long bytes = 0;
        while (count > bytes) {
            int limit = (int) Math.min(b.capacity(), count - bytes);
            b.limit(limit);
            if (read0(b, position) <= 0)
                break;
            b.flip();
            int len = target.write(b);
            position += len;
            bytes += len;
            b.clear();
        }
        return bytes;
    }

    @Override
    public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
        ensureOpen();
        if (!src.isOpen())
            throw new ClosedChannelException();
        if (!writable())
            throw new NonWritableChannelException();
        if ((position < 0) || (count < 0))
            throw new IllegalArgumentException();

        ByteBuffer b = ByteBuffer.allocateDirect(PIPE_CAPACITY);
        long bytes = 0;
        while (count > bytes) {
            int limit = (int) Math.min(b.capacity(), count - bytes);
            b.limit(limit);
            if (src.read(b) <= 0)
                break;
            b.flip();
            int len = write0(b, position);
            position += len;
            bytes += len;
            b.clear();
        }
        return bytes;
    }

    @Override
    public int read(ByteBuffer dst, long position) throws IOException {
        if (position < 0)
            throw new IllegalArgumentException("Negative position");
        ensureOpen();
        return read0(dst, position);
    }

    @Override
    public int write(ByteBuffer src, long position) throws IOException {
        if (position < 0)
            throw new IllegalArgumentException("Negative position");
        ensureOpen();
        return write0(src, position);
    }

    @Override
    protected void implCloseChannel() {
        try { fs.close(handle); } catch (RemoteException ignored) {}
        synchronized (fdLock) {
            try { Os.close(read); } catch (ErrnoException ignored) {}
            try { Os.close(write); } catch (ErrnoException ignored) {}
        }
    }

    // Unsupported operations

    @Override
    public MappedByteBuffer map(MapMode mode, long position, long size) {
        throw new UnsupportedOperationException("Memory mapping a remote file is not supported!");
    }

    @Override
    public FileLock lock(long position, long size, boolean shared) {
        throw new UnsupportedOperationException("Locking a remote file is not supported!");
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared) {
        throw new UnsupportedOperationException("Locking a remote file is not supported!");
    }
}