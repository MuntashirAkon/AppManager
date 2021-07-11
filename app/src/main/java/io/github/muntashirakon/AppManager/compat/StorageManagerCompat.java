// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;

// Copyright 2018 Fung Gwo (fythonx@gmail.com)
// Modified from https://gist.github.com/fython/924f8d9019bca75d22de116bb69a54a1
public final class StorageManagerCompat {
    private static final String TAG = StorageManagerCompat.class.getSimpleName();

    public static final int DEFAULT_BUFFER_SIZE = 1024 * 50;

    private StorageManagerCompat() {
    }

    @NonNull
    public static ParcelFileDescriptor openProxyFileDescriptor(int mode, @NonNull ProxyFileDescriptorCallbackCompat callback)
            throws IOException, UnsupportedOperationException {
        // We cannot use StorageManager#openProxyFileDescriptor directly due to its limitation on how callbacks are handled
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
        if ((mode & ParcelFileDescriptor.MODE_READ_ONLY) != 0) {
            callback.handler.post(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream os = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    byte[] buf = new byte[DEFAULT_BUFFER_SIZE];
                    int size;
                    while ((size = callback.onRead(0, DEFAULT_BUFFER_SIZE, buf)) > 0) {
                        os.write(buf, 0, size);
                    }
                    callback.onRelease();
                } catch (IOException | ErrnoException e) {
                    Log.e(TAG, "Failed to read file.", e);
                    try {
                        pipe[1].closeWithError(e.getMessage());
                    } catch (IOException exc) {
                        Log.e(TAG, "Can't even close PFD with error.", exc);
                    }
                }
            });
            return pipe[0];
        } else if ((mode & ParcelFileDescriptor.MODE_WRITE_ONLY) != 0) {
            callback.handler.post(() -> {
                try (ParcelFileDescriptor.AutoCloseInputStream is = new ParcelFileDescriptor.AutoCloseInputStream(pipe[0])) {
                    byte[] buf = new byte[DEFAULT_BUFFER_SIZE];
                    int size;
                    while ((size = is.read(buf)) != -1) {
                        callback.onWrite(0, size, buf);
                    }
                    callback.onRelease();
                } catch (IOException | ErrnoException e) {
                    Log.e(TAG, "Failed to write file.", e);

                    try {
                        pipe[0].closeWithError(e.getMessage());
                    } catch (IOException exc) {
                        Log.e(TAG, "Can't even close PFD with error.", exc);
                    }
                }
            });
            return pipe[1];
        } else {
            // Should never happen.
            pipe[0].close();
            pipe[1].close();
            Log.e(TAG, "Mode " + mode + " is not supported.");
            throw new UnsupportedOperationException("Mode " + mode + " is not supported.");
        }
    }

    public static abstract class ProxyFileDescriptorCallbackCompat {
        private final HandlerThread callbackThread;
        private final Handler handler;

        public ProxyFileDescriptorCallbackCompat(HandlerThread callbackThread) {
            this.callbackThread = callbackThread;
            this.handler = new Handler(this.callbackThread.getLooper());
        }

        /**
         * Returns size of bytes provided by the file descriptor.
         *
         * @return Size of bytes.
         * @throws ErrnoException ErrnoException containing E constants in OsConstants.
         */
        public long onGetSize() throws ErrnoException {
            throw new ErrnoException("onGetSize", OsConstants.EBADF);
        }

        /**
         * Provides bytes read from file descriptor.
         * It needs to return exact requested size of bytes unless it reaches file end.
         *
         * @param offset Offset in bytes from the file head specifying where to read bytes. If a seek
         *               operation is conducted on the file descriptor, then a read operation is requested, the
         *               offset refrects the proper position of requested bytes.
         * @param size   Size for read bytes.
         * @param data   Byte array to store read bytes.
         * @return Size of bytes returned by the function.
         * @throws ErrnoException ErrnoException containing E constants in OsConstants.
         */
        public int onRead(long offset, int size, byte[] data) throws ErrnoException {
            throw new ErrnoException("onRead", OsConstants.EBADF);
        }

        /**
         * Handles bytes written to file descriptor.
         *
         * @param offset Offset in bytes from the file head specifying where to write bytes. If a seek
         *               operation is conducted on the file descriptor, then a write operation is requested, the
         *               offset refrects the proper position of requested bytes.
         * @param size   Size for write bytes.
         * @param data   Byte array to be written to somewhere.
         * @return Size of bytes processed by the function.
         * @throws ErrnoException ErrnoException containing E constants in OsConstants.
         */
        public int onWrite(long offset, int size, byte[] data) throws ErrnoException {
            throw new ErrnoException("onWrite", OsConstants.EBADF);
        }

        /**
         * Ensures all the written data are stored in permanent storage device.
         * For example, if it has data stored in on memory cache, it needs to flush data to storage
         * device.
         *
         * @throws ErrnoException ErrnoException containing E constants in OsConstants.
         */
        public void onFsync() throws ErrnoException {
            throw new ErrnoException("onFsync", OsConstants.EINVAL);
        }

        /**
         * Invoked after the file is closed.
         */
        protected void onRelease() {
            callbackThread.quitSafely();
        }
    }
}