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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SplitInputStream extends InputStream {
    private final List<ProxyInputStream> proxyInputStreams;
    private int currentIndex = -1;
    private final List<File> files;

    public SplitInputStream(@NonNull List<File> files) {
        this.files = files;
        this.proxyInputStreams = new ArrayList<>(files.size());
    }

    public SplitInputStream(@NonNull File[] files) {
        this(Arrays.asList(files));
    }

    @Override
    public int read() throws IOException {
        byte[] bytes = new byte[1];
        int readBytes = readStream(bytes, 0, 1);
        if (readBytes == -1) return -1;
        else return bytes[0];
    }

    @Override
    public int read(byte[] b) throws IOException {
        return readStream(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return readStream(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return skipStream(n);
    }

    @Override
    public int available() throws IOException {
        return proxyInputStreams.get(currentIndex).available();
    }

    @Override
    public void close() throws IOException {
        for (ProxyInputStream stream : proxyInputStreams) {
            stream.close();
        }
    }

    @Override
    public synchronized void mark(int readlimit) {
        // Not supported
        // TODO: 27/1/21 Add mark support
    }

    @Override
    public boolean markSupported() {
        // TODO: 27/1/21 Add mark support
        return false;
    }

    private int readStream(byte[] b, int off, int len) throws IOException {
        if (len == 0) return 0;
        try {
            if (currentIndex == -1) {
                // Initialize and read from a new stream
                proxyInputStreams.add(new ProxyInputStream(files.get(0)));
                ++currentIndex;
            }
            while (len != 0) {
                // Fetch bytes from the streams
                int readBytes = proxyInputStreams.get(currentIndex).read(b, off, len);
                if (readBytes == -1 || readBytes < len) {
                    // Need to fetch next bytes from the next stream
                    if (currentIndex + 1 == files.size()) {
                        // Already read from the last file, return as is
                        return readBytes;
                    } else {
                        // Initialize the next stream and set both offset and length
                        proxyInputStreams.add(new ProxyInputStream(files.get(currentIndex + 1)));
                        ++currentIndex;
                        if (readBytes != -1) {
                            off += readBytes;
                            len -= readBytes;
                        }
                        // continue the loop
                    }
                } else if (readBytes == len) {
                    // Read exactly the number of bytes requested
                    return readBytes;
                } else {
                    throw new IOException(String.format("Byte length = %d, bytes read = %d", len, readBytes));
                }
            }
            throw new IOException("Invalid request.");
        } catch (IOException e) {
            throw e;
        } catch (Throwable th) {
            throw new IOException(th);
        }
    }

    private long skipStream(long n) throws IOException {
        if (n <= 0) return 0;
        try {
            if (currentIndex == -1) {
                // Initialize and read from a new stream
                proxyInputStreams.add(new ProxyInputStream(files.get(0)));
                ++currentIndex;
            }
            while (n != 0) {
                // Skip bytes from the streams
                long skippedBytes = proxyInputStreams.get(currentIndex).skip(n);
                if (skippedBytes == 0 || skippedBytes < n) {
                    // Need to fetch next bytes from the next stream
                    if (currentIndex + 1 == files.size()) {
                        // Already skipped from the last file, return as is
                        return skippedBytes;
                    } else {
                        // Initialize the next stream and set length
                        proxyInputStreams.add(new ProxyInputStream(files.get(currentIndex + 1)));
                        ++currentIndex;
                        n -= skippedBytes;
                        // continue the loop
                    }
                } else if (skippedBytes == n) {
                    // Read exactly the number of bytes requested
                    return skippedBytes;
                } else {
                    throw new IOException(String.format("Byte length = %d, bytes skipped = %d", n, skippedBytes));
                }
            }
            throw new IOException("Invalid request.");
        } catch (IOException e) {
            throw e;
        } catch (Throwable th) {
            throw new IOException(th);
        }
    }
}
