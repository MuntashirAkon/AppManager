// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.os.Build;
import android.os.FileUtils;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public final class IoUtils {
    public static final String TAG = IoUtils.class.getSimpleName();

    public static final int DEFAULT_BUFFER_SIZE = 1024 * 50;

    /**
     * Get byte array from an InputStream most efficiently.
     * Taken from sun.misc.IOUtils
     *
     * @param is      InputStream
     * @param length  Length of the buffer, -1 to read the whole stream
     * @param readAll Whether to read the whole stream
     * @return Desired byte array
     * @throws IOException If maximum capacity exceeded.
     */
    @AnyThread
    public static byte[] readFully(@NonNull InputStream is, int length, boolean readAll)
            throws IOException {
        byte[] output = {};
        if (length == -1) length = Integer.MAX_VALUE;
        int pos = 0;
        while (pos < length) {
            int bytesToRead;
            if (pos >= output.length) {
                bytesToRead = Math.min(length - pos, output.length + 1024);
                if (output.length < pos + bytesToRead) {
                    output = Arrays.copyOf(output, pos + bytesToRead);
                }
            } else {
                bytesToRead = output.length - pos;
            }
            int cc = is.read(output, pos, bytesToRead);
            if (cc < 0) {
                if (readAll && length != Integer.MAX_VALUE) {
                    throw new EOFException("Detect premature EOF");
                } else {
                    if (output.length != pos) {
                        output = Arrays.copyOf(output, pos);
                    }
                    break;
                }
            }
            pos += cc;
        }
        return output;
    }

    @AnyThread
    @NonNull
    public static String getInputStreamContent(@NonNull InputStream inputStream) throws IOException {
        return new String(IoUtils.readFully(inputStream, -1, true), Charset.defaultCharset());
    }

    @AnyThread
    public static long copy(@NonNull Path from, @NonNull Path to, @Nullable ProgressHandler progressHandler)
            throws IOException {
        try (InputStream in = from.openInputStream();
             OutputStream out = to.openOutputStream()) {
            return copy(in, out, from.length(), progressHandler);
        }
    }

    /**
     * Copy the contents of one stream to another.

     * @param totalSize Total size of the stream. Only used for handling progress. Set {@code -1} if unknown.
     */
    @AnyThread
    public static long copy(@NonNull InputStream in, @NonNull OutputStream out, long totalSize,
                            @Nullable ProgressHandler progressHandler) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int lastProgress = progressHandler != null ? progressHandler.getLastProgress() : 0;
            return FileUtils.copy(in, out, null, ThreadUtils.getBackgroundThreadExecutor(), progress -> {
                if (progressHandler != null) {
                    progressHandler.postUpdate(100, (int) (lastProgress + (progress * 100 / totalSize)));
                }
            });
        } else {
            return copyLarge(in, out, totalSize, progressHandler);
        }
    }


    @AnyThread
    public static void closeQuietly(@Nullable AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception e) {
            Log.w(TAG, String.format("Unable to close %s", closeable.getClass().getCanonicalName()), e);
        }
    }

    @AnyThread
    private static long copyLarge(@NonNull InputStream in, @NonNull OutputStream out, long totalSize,
                                  @Nullable ProgressHandler progressHandler) throws IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long count = 0;
        long checkpoint = 0;
        int n;
        int lastProgress = progressHandler != null ? progressHandler.getLastProgress() : 0;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
            count += n;
            checkpoint += n;
            if (checkpoint >= (1 << 19)) { // 512 kB
                if (progressHandler != null) {
                    progressHandler.postUpdate(100, (int) (lastProgress + (count * 100 / totalSize)));
                }
                checkpoint = 0;
            }
        }
        return count;
    }
}
