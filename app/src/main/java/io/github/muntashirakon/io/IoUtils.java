// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import io.github.muntashirakon.AppManager.logs.Log;

import static android.system.OsConstants.O_APPEND;
import static android.system.OsConstants.O_CREAT;
import static android.system.OsConstants.O_RDONLY;
import static android.system.OsConstants.O_RDWR;
import static android.system.OsConstants.O_TRUNC;
import static android.system.OsConstants.O_WRONLY;

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
    @WorkerThread
    public static byte[] readFully(InputStream is, int length, boolean readAll)
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

    @WorkerThread
    public static long copy(File from, File to) throws IOException, RemoteException {
        try (InputStream in = new ProxyInputStream(from);
             OutputStream out = new ProxyOutputStream(to)) {
            return copy(in, out);
        }
    }

    @WorkerThread
    public static long copy(Path from, Path to) throws IOException {
        try (InputStream in = from.openInputStream();
             OutputStream out = to.openOutputStream()) {
            return copy(in, out);
        }
    }

    @WorkerThread
    public static long copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return android.os.FileUtils.copy(inputStream, outputStream);
        } else {
            long count = copyLarge(inputStream, outputStream);
            if (count > Integer.MAX_VALUE) return -1;
            return count;
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

    @WorkerThread
    private static long copyLarge(@NonNull InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long count = 0;
        int n;
        while ((n = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    public static int translateModeStringToPosix(@NonNull String mode) {
        // Sanity check for invalid chars
        for (int i = 0; i < mode.length(); i++) {
            switch (mode.charAt(i)) {
                case 'r':
                case 'w':
                case 't':
                case 'a':
                    break;
                default:
                    throw new IllegalArgumentException("Bad mode: " + mode);
            }
        }

        int res;
        if (mode.startsWith("rw")) {
            res = O_RDWR | O_CREAT;
        } else if (mode.startsWith("w")) {
            res = O_WRONLY | O_CREAT;
        } else if (mode.startsWith("r")) {
            res = O_RDONLY;
        } else {
            throw new IllegalArgumentException("Bad mode: " + mode);
        }
        if (mode.indexOf('t') != -1) {
            res |= O_TRUNC;
        }
        if (mode.indexOf('a') != -1) {
            res |= O_APPEND;
        }
        return res;
    }
}
