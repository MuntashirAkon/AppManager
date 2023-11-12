// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SplitInputStream extends InputStream {
    private final List<InputStream> mInputStreams;
    private int mCurrentIndex = -1;
    private final List<Path> mFiles;

    private final byte[] mBuf;

    // Number of valid bytes in buf
    private int mCount = 0;
    // Current read pos, > 0 in buf, < 0 in markBuf (interpret in bitwise negate)
    private int mPos = 0;

    // -1 when no active mark, 0 when markBuf is active, pos when mark is called
    private int mMarkPos = -1;
    // Number of valid bytes in markBuf
    private int mMarkBufCount = 0;

    // markBuf.length == markBufSize
    private int mMarkBufSize;
    @Nullable
    private byte[] mMarkBuf;

    // Some value ranges:
    // 0 <= count <= buf.length
    // 0 <= pos <= count (if pos > 0)
    // 0 <= markPos <= pos (markPos = -1 means no mark)
    // 0 <= ~pos <= markBufCount (if pos < 0)
    // 0 <= markBufCount <= markLimit

    public SplitInputStream(@NonNull List<Path> files) {
        mFiles = files;
        mInputStreams = new ArrayList<>(files.size());
        mBuf = new byte[1024 * 4];
    }

    public SplitInputStream(@NonNull Path[] files) {
        this(Arrays.asList(files));
    }

    @Override
    public int read() throws IOException {
        byte[] bytes = new byte[1];
        int readBytes = read(bytes);
        if (readBytes != 1) return -1;
        else return bytes[0];
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len > b.length)
            throw new IndexOutOfBoundsException();
        return read0(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 0) return 0;
        return Math.max(read0(null, 0, (int) n), 0);
    }

    @Override
    public void close() throws IOException {
        for (InputStream stream : mInputStreams) {
            stream.close();
        }
    }

    @Override
    public synchronized void mark(int readlimit) {
        // Reset mark
        mMarkPos = mPos;
        mMarkBufCount = 0;
        mMarkBuf = null;

        int remain = mCount - mPos;
        if (readlimit <= remain) {
            // Don't need a separate buffer
            mMarkBufSize = 0;
        } else {
            // Extra buffer required is remain + n * buf.length
            mMarkBufSize = remain + ((readlimit - remain) / mBuf.length) * mBuf.length;
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        if (mMarkPos < 0)
            throw new IOException("Resetting to invalid mark");
        // Switch to markPos or use markBuf
        mPos = mMarkBuf == null ? mMarkPos : ~0;
    }

    @WorkerThread
    @Override
    public synchronized int available() throws IOException {
        if (mCount < 0) return 0;
        if (mPos >= mCount) {
            // Try to read the next chunk into memory
            read0(null, 0, 1);
            if (mCount < 0) return 0;
            // Revert the 1 byte read
            --mPos;
        }
        // Return the size available in memory
        if (mPos < 0) {
            return (mMarkBufCount - ~mPos) + mCount;
        } else {
            return mCount - mPos;
        }
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @WorkerThread
    private synchronized int read0(@Nullable byte[] b, int off, int len) throws IOException {
        int n = 0;
        while (n < len) {
            if (mPos < 0) {
                // Read from markBuf
                int pos = ~mPos;
                int size = Math.min(mMarkBufCount - pos, len - n);
                if (b != null) {
                    System.arraycopy(mMarkBuf, pos, b, off + n, size);
                }
                n += size;
                pos += size;
                if (pos == mMarkBufCount) {
                    // markBuf done, switch to buf
                    mPos = 0;
                } else {
                    // continue reading markBuf
                    mPos = ~pos;
                }
                continue;
            }
            // Read from buf
            if (mPos >= mCount) {
                // We ran out of buffer, need to either refill or abort
                if (mMarkPos >= 0) {
                    // We need to preserve some buffer for mark
                    long size = mCount - mMarkPos;
                    if ((mMarkBufSize - mMarkBufCount) < size) {
                        // Out of mark limit, discard markBuf
                        mMarkBuf = null;
                        mMarkBufCount = 0;
                        mMarkPos = -1;
                    } else if (mMarkBuf == null) {
                        mMarkBuf = new byte[(int) mMarkBufSize];
                        mMarkBufCount = 0;
                    }
                    if (mMarkBuf != null) {
                        // Accumulate data in markBuf
                        System.arraycopy(mBuf, mMarkPos, mMarkBuf, mMarkBufCount, (int) size);
                        mMarkBufCount += (int) size;
                        // Set markPos to 0 as buffer will refill
                        mMarkPos = 0;
                    }
                }
                // refill buffer
                mPos = 0;
                mCount = readStream(mBuf);
                if (mCount < 0) {
                    return n == 0 ? -1 : n;
                }
            }
            int size = Math.min(mCount - mPos, len - n);
            if (b != null) {
                System.arraycopy(mBuf, mPos, b, off + n, size);
            }
            n += size;
            mPos += size;
        }
        return n;
    }

    @WorkerThread
    private synchronized int readStream(@NonNull byte[] b) throws IOException {
        int off = 0;
        int len = b.length;
        if (len <= 0) return len;
        try {
            if (mFiles.isEmpty()) {
                // No files supplied, nothing to read
                return -1;
            } else if (mCurrentIndex == -1) {
                // Initialize a new stream
                mInputStreams.add(mFiles.get(0).openInputStream());
                ++mCurrentIndex;
            }
            do {
                int readCount = mInputStreams.get(mCurrentIndex).read(b, off, len);
                if (readCount <= 0) {
                    // This stream has been read completely, initialize new stream if available
                    if (mCurrentIndex + 1 != mFiles.size()) {
                        mInputStreams.add(mFiles.get(mCurrentIndex + 1).openInputStream());
                        ++mCurrentIndex;
                    } else {
                        // Last stream reached
                        if (len == b.length) {
                            // Read nothing
                            return -1;
                        } else {
                            // Read something
                            return b.length - len;
                        }
                    }
                } else {
                    off += readCount;
                    len -= readCount;
                }
            } while (len > 0);
            return b.length - len;
        } catch (IOException e) {
            throw e;
        } catch (Throwable th) {
            throw new IOException(th);
        }
    }
}
