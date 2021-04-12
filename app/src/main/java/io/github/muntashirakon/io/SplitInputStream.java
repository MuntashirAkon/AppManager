/*
 * Copyright (c) 2021 Muntashir Al-Islam
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

package io.github.muntashirakon.io;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SplitInputStream extends InputStream {
    private final List<InputStream> inputStreams;
    private int currentIndex = -1;
    private final List<File> files;

    private final byte[] buf;

    // Number of valid bytes in buf
    private int count = 0;
    // Current read pos, > 0 in buf, < 0 in markBuf (interpret in bitwise negate)
    private int pos = 0;

    // -1 when no active mark, 0 when markBuf is active, pos when mark is called
    private int markPos = -1;
    // Number of valid bytes in markBuf
    private int markBufCount = 0;

    // markBuf.length == markBufSize
    private int markBufSize;
    private byte[] markBuf;

    // Some value ranges:
    // 0 <= count <= buf.length
    // 0 <= pos <= count (if pos > 0)
    // 0 <= markPos <= pos (markPos = -1 means no mark)
    // 0 <= ~pos <= markBufCount (if pos < 0)
    // 0 <= markBufCount <= markLimit

    public SplitInputStream(@NonNull List<File> files) {
        this.files = files;
        this.inputStreams = new ArrayList<>(files.size());
        buf = new byte[1024 * 4];
    }

    public SplitInputStream(@NonNull File[] files) {
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
        for (InputStream stream : inputStreams) {
            stream.close();
        }
    }

    @Override
    public synchronized void mark(int readlimit) {
        // Reset mark
        markPos = pos;
        markBufCount = 0;
        markBuf = null;

        int remain = count - pos;
        if (readlimit <= remain) {
            // Don't need a separate buffer
            markBufSize = 0;
        } else {
            // Extra buffer required is remain + n * buf.length
            markBufSize = remain + ((readlimit - remain) / buf.length) * buf.length;
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        if (markPos < 0)
            throw new IOException("Resetting to invalid mark");
        // Switch to markPos or use markBuf
        pos = markBuf == null ? markPos : ~0;
    }

    @WorkerThread
    @Override
    public synchronized int available() throws IOException {
        if (count < 0) return 0;
        if (pos >= count) {
            // Try to read the next chunk into memory
            read0(null, 0, 1);
            if (count < 0) return 0;
            // Revert the 1 byte read
            --pos;
        }
        // Return the size available in memory
        if (pos < 0) {
            return (markBufCount - ~pos) + count;
        } else {
            return count - pos;
        }
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @WorkerThread
    private synchronized int read0(byte[] b, int off, int len) throws IOException {
        int n = 0;
        while (n < len) {
            if (pos < 0) {
                // Read from markBuf
                int pos = ~this.pos;
                int size = Math.min(markBufCount - pos, len - n);
                if (b != null) {
                    System.arraycopy(markBuf, pos, b, off + n, size);
                }
                n += size;
                pos += size;
                if (pos == markBufCount) {
                    // markBuf done, switch to buf
                    this.pos = 0;
                } else {
                    // continue reading markBuf
                    this.pos = ~pos;
                }
                continue;
            }
            // Read from buf
            if (pos >= count) {
                // We ran out of buffer, need to either refill or abort
                if (markPos >= 0) {
                    // We need to preserve some buffer for mark
                    long size = count - markPos;
                    if ((markBufSize - markBufCount) < size) {
                        // Out of mark limit, discard markBuf
                        markBuf = null;
                        markBufCount = 0;
                        markPos = -1;
                    } else if (markBuf == null) {
                        markBuf = new byte[(int) markBufSize];
                        markBufCount = 0;
                    }
                    if (markBuf != null) {
                        // Accumulate data in markBuf
                        System.arraycopy(buf, markPos, markBuf, markBufCount, (int) size);
                        markBufCount += size;
                        // Set markPos to 0 as buffer will refill
                        markPos = 0;
                    }
                }
                // refill buffer
                pos = 0;
                count = readStream(buf);
                if (count < 0) {
                    return n == 0 ? -1 : n;
                }
            }
            int size = Math.min(count - pos, len - n);
            if (b != null) {
                System.arraycopy(buf, pos, b, off + n, size);
            }
            n += size;
            pos += size;
        }
        return n;
    }

    @WorkerThread
    private synchronized int readStream(@NonNull byte[] b) throws IOException {
        int off = 0;
        int len = b.length;
        if (len <= 0) return len;
        try {
            if (files.size() == 0) {
                // No files supplied, nothing to read
                return -1;
            } else if (currentIndex == -1) {
                // Initialize a new stream
                inputStreams.add(new ProxyInputStream(files.get(0)));
                ++currentIndex;
            }
            do {
                int readCount = inputStreams.get(currentIndex).read(b, off, len);
                if (readCount <= 0) {
                    // This stream has been read completely, initialize new stream if available
                    if (currentIndex + 1 != files.size()) {
                        inputStreams.add(new ProxyInputStream(files.get(currentIndex + 1)));
                        ++currentIndex;
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
