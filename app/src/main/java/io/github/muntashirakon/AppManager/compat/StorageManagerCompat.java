// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static io.github.muntashirakon.io.IoUtils.DEFAULT_BUFFER_SIZE;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.storage.StorageManager;
import android.os.storage.StorageManagerHidden;
import android.os.storage.StorageVolume;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

import dev.rikka.tools.refine.Refine;
import io.github.muntashirakon.AppManager.self.SelfPermissions;

// Copyright 2018 Fung Gwo <fythonx@gmail.com>
// Copyright 2021 Muntashir Al-Islam
// Modified from https://gist.github.com/fython/924f8d9019bca75d22de116bb69a54a1
public final class StorageManagerCompat {
    private static final String TAG = StorageManagerCompat.class.getSimpleName();

    /**
     * Flag indicating that a disk space allocation request should be allowed to
     * clear up to all reserved disk space.
     */
    public static final int FLAG_ALLOCATE_DEFY_ALL_RESERVED = 1 << 1;

    /**
     * Flag indicating that a disk space allocation request should be allowed to
     * clear up to half of all reserved disk space.
     */
    public static final int FLAG_ALLOCATE_DEFY_HALF_RESERVED = 1 << 2;

    @IntDef(flag = true, value = {
            FLAG_ALLOCATE_DEFY_ALL_RESERVED,
            FLAG_ALLOCATE_DEFY_HALF_RESERVED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AllocateFlags {
    }

    @NonNull
    public static StorageManager from(@NonNull Context context) {
        return (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
    }

    private StorageManagerCompat() {
    }

    @NonNull
    public static StorageVolume[] getVolumeList(@NonNull Context context, int userId, int flags)
            throws SecurityException {
        if (!SelfPermissions.checkCrossUserPermission(userId, false, Process.myUid())) {
            return new StorageVolume[0];
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return StorageManagerHidden.getVolumeList(userId, flags);
        } else {
            StorageVolume[] volumes = Refine.<StorageManagerHidden>unsafeCast(from(context)).getVolumeList();
            if (volumes != null) {
                return volumes;
            }
        }
        return new StorageVolume[0];
    }

    @NonNull
    public static ParcelFileDescriptor openProxyFileDescriptor(int mode, @NonNull ProxyFileDescriptorCallbackCompat callback)
            throws IOException, UnsupportedOperationException {
        // We cannot use StorageManager#openProxyFileDescriptor directly due to its limitation on how callbacks are handled
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
        if ((mode & ParcelFileDescriptor.MODE_READ_ONLY) != 0) {
            // Reading requested i.e. we have to read from our side and write it to the target
            callback.mHandler.post(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream os = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    long totalSize = callback.onGetSize();
                    long currOffset = 0;
                    byte[] buf = new byte[DEFAULT_BUFFER_SIZE];
                    int size;
                    while ((size = callback.onRead(currOffset, DEFAULT_BUFFER_SIZE, buf)) > 0) {
                        os.write(buf, 0, size);
                        currOffset += size;
                    }
                    if (totalSize > 0 && currOffset != totalSize) {
                        throw new IOException(String.format(Locale.ROOT, "Could not read the whole resource (total = %d, read = %d)", totalSize, currOffset));
                    }
                } catch (IOException | ErrnoException e) {
                    Log.e(TAG, "Failed to read file.", e);
                    try {
                        pipe[1].closeWithError(e.getMessage());
                    } catch (IOException exc) {
                        Log.e(TAG, "Can't even close PFD with error.", exc);
                    }
                } finally {
                    callback.onRelease();
                }
            });
            return pipe[0];
        } else if ((mode & ParcelFileDescriptor.MODE_WRITE_ONLY) != 0) {
            // Writing requested i.e. we have to read from the target and write it to our side
            callback.mHandler.post(() -> {
                try (ParcelFileDescriptor.AutoCloseInputStream is = new ParcelFileDescriptor.AutoCloseInputStream(pipe[0])) {
                    long currOffset = 0;
                    byte[] buf = new byte[DEFAULT_BUFFER_SIZE];
                    int size;
                    while ((size = is.read(buf)) != -1) {
                        callback.onWrite(currOffset, size, buf);
                        currOffset += size;
                    }
                    long totalSize = callback.onGetSize();
                    if (totalSize > 0 && currOffset != totalSize) {
                        throw new IOException(String.format(Locale.ROOT, "Could not write the whole resource (total = %d, read = %d)", totalSize, currOffset));
                    }
                } catch (IOException | ErrnoException e) {
                    Log.e(TAG, "Failed to write file.", e);
                    try {
                        pipe[0].closeWithError(e.getMessage());
                    } catch (IOException exc) {
                        Log.e(TAG, "Can't even close PFD with error.", exc);
                    }
                } finally {
                    callback.onRelease();
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
        private final HandlerThread mCallbackThread;
        private final Handler mHandler;

        public ProxyFileDescriptorCallbackCompat(HandlerThread callbackThread) {
            mCallbackThread = callbackThread;
            mHandler = new Handler(mCallbackThread.getLooper());
        }

        /**
         * Returns size of bytes provided by the file descriptor.
         *
         * @return Size of bytes.
         * @throws ErrnoException Containing E constants in OsConstants.
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
         * @throws ErrnoException Containing E constants in OsConstants.
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
         * @throws ErrnoException Containing E constants in OsConstants.
         */
        public int onWrite(long offset, int size, byte[] data) throws ErrnoException {
            throw new ErrnoException("onWrite", OsConstants.EBADF);
        }

        /**
         * Ensures all the written data are stored in permanent storage device.
         * For example, if it has data stored in on memory cache, it needs to flush data to storage
         * device.
         *
         * @throws ErrnoException Containing E constants in OsConstants.
         */
        public void onFsync() throws ErrnoException {
            throw new ErrnoException("onFsync", OsConstants.EINVAL);
        }

        /**
         * Invoked after the file is closed.
         */
        protected void onRelease() {
            mCallbackThread.quitSafely();
        }
    }
}