// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.net.URI;
import java.nio.channels.FileChannel;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Access file system APIs.
 */
// Copyright 2022 John "topjohnwu" Wu
public abstract class FileSystemManager {

    /**
     * For use with {@link #openChannel}: open the file with read-only access.
     */
    public static final int MODE_READ_ONLY = ParcelFileDescriptor.MODE_READ_ONLY;
    /**
     * For use with {@link #openChannel}: open the file with write-only access.
     */
    public static final int MODE_WRITE_ONLY = ParcelFileDescriptor.MODE_WRITE_ONLY;
    /**
     * For use with {@link #openChannel}: open the file with read and write access.
     */
    public static final int MODE_READ_WRITE = ParcelFileDescriptor.MODE_READ_WRITE;
    /**
     * For use with {@link #openChannel}: create the file if it doesn't already exist.
     */
    public static final int MODE_CREATE = ParcelFileDescriptor.MODE_CREATE;
    /**
     * For use with {@link #openChannel}: erase contents of file when opening.
     */
    public static final int MODE_TRUNCATE = ParcelFileDescriptor.MODE_TRUNCATE;
    /**
     * For use with {@link #openChannel}: append to end of file while writing.
     */
    public static final int MODE_APPEND = ParcelFileDescriptor.MODE_APPEND;

    @Retention(SOURCE)
    @IntDef(value = {
            MODE_READ_ONLY, MODE_WRITE_ONLY, MODE_READ_WRITE,
            MODE_CREATE, MODE_TRUNCATE, MODE_APPEND}, flag = true)
    @interface OpenMode {}

    private static final FileSystemManager LOCAL = NIOFactory.createLocal();

    private static Binder fsService;

    /**
     * Get the service that exports the file system of the current process over Binder IPC.
     * <p>
     * Sending the {@link Binder} obtained from this method to a client process enables
     * the current process to perform file system operations on behalf of the client.
     * This allows a client process to access files normally denied by its permissions.
     * This method is usually called in a root process, and the Binder service returned will
     * be send over to a non-root client process.
     * <p>
     * You can pass this {@link Binder} object in multiple ways, such as returning it in the
     * {@code onBind()} method of root services, passing it around in a {@link Bundle},
     * or returning it in an AIDL interface method. The receiving end will get an {@link IBinder},
     * which the developer should then pass to {@link #getRemote(IBinder)} for usage.
     */
    @NonNull
    public synchronized static Binder getService() {
        if (fsService == null) {
            fsService = NIOFactory.createFsService();
        }
        return fsService;
    }

    /**
     * Get the {@link FileSystemManager} to access the file system of the current local process.
     */
    @NonNull
    public static FileSystemManager getLocal() {
        return LOCAL;
    }

    /**
     * Create a {@link FileSystemManager} to access the file system of a remote process.
     * <p>
     * Several APIs are not supported through a remote process:
     * <ul>
     *     <li>{@link File#deleteOnExit()}</li>
     *     <li>{@link FileChannel#map(FileChannel.MapMode, long, long)}</li>
     *     <li>{@link FileChannel#lock()}</li>
     *     <li>{@link FileChannel#lock(long, long, boolean)}</li>
     *     <li>{@link FileChannel#tryLock()}</li>
     *     <li>{@link FileChannel#tryLock(long, long, boolean)}</li>
     * </ul>
     * Calling these APIs will throw {@link UnsupportedOperationException}.
     *
     * @param binder a remote proxy of the {@link Binder} obtained from {@link #getService()}
     * @throws RemoteException if the remote process has died.
     */
    @NonNull
    public static FileSystemManager getRemote(@NonNull IBinder binder) throws RemoteException {
        return NIOFactory.createRemote(binder);
    }

    /**
     * @see File#File(String)
     */
    @NonNull
    public abstract ExtendedFile getFile(@NonNull String pathname);

    /**
     * @see File#File(String, String)
     */
    @NonNull
    public abstract ExtendedFile getFile(@Nullable String parent, @NonNull String child);

    /**
     * @see File#File(File, String)
     */
    @NonNull
    public final ExtendedFile getFile(@Nullable File parent, @NonNull String child) {
        return getFile(parent == null ? null : parent.getPath(), child);
    }

    /**
     * @see File#File(URI)
     */
    @NonNull
    public final ExtendedFile getFile(@NonNull URI uri) {
        return getFile(new File(uri).getPath());
    }

    /**
     * Opens a file channel to access the file.
     *
     * @param pathname the file to be opened.
     * @param mode     the desired access mode.
     * @return a new FileChannel pointing to the given file.
     * @throws IOException if the given file can not be opened with the requested mode.
     */
    @NonNull
    public final FileChannel openChannel(@NonNull String pathname, @OpenMode int mode) throws IOException {
        return openChannel(new File(pathname), mode);
    }

    /**
     * Opens a file channel to access the file.
     *
     * @param file the file to be opened.
     * @param mode the desired access mode.
     * @return a new FileChannel pointing to the given file.
     * @throws IOException if the given file can not be opened with the requested mode.
     */
    @NonNull
    public abstract FileChannel openChannel(@NonNull File file, @OpenMode int mode) throws IOException;
}