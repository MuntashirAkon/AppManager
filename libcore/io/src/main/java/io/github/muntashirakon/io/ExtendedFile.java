// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.system.ErrnoException;
import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;

/**
 * {@link File} API with extended features.
 * <p>
 * The goal of this class is to extend missing features in the {@link File} API that are available
 * in the NIO package but not possible to be re-implemented without low-level file system access.
 * For instance, detecting file types other than regular files and directories, handling and
 * creating hard links and symbolic links.
 * <p>
 * Another goal of this class is to provide a generalized API interface for custom file system
 * backends. The library includes backends for accessing files locally, accessing files remotely
 * via IPC, and accessing files through shell commands (by using {@code SuFile}, included in the
 * {@code io} module). The developer can get instances of this class with
 * {@link FileSystemManager#getFile}.
 * <p>
 * Implementations of this class is required to return the same type of {@link ExtendedFile} in
 * all of its APIs returning {@link File}s. This means that, for example, if the developer is
 * getting a list of files in a directory using a remote file system with {@link #listFiles()},
 * all files returned in the array will also be using the same remote file system backend.
 */
// Copyright 2022 John "topjohnwu" Wu
// Copyright 2022 Muntashir Al-Islam
public abstract class ExtendedFile extends File {

    /**
     * @see File#File(String)
     */
    protected ExtendedFile(@NonNull String pathname) {
        super(pathname);
    }

    /**
     * @see File#File(String, String)
     */
    protected ExtendedFile(@Nullable String parent, @NonNull String child) {
        super(parent, child);
    }

    /**
     * @see File#File(File, String)
     */
    protected ExtendedFile(@Nullable File parent, @NonNull String child) {
        super(parent, child);
    }

    /**
     * @see File#File(URI)
     */
    protected ExtendedFile(@NonNull URI uri) {
        super(uri);
    }

    /**
     * @return Get mode (permission) of the abstract pathname.
     */
    public abstract int getMode() throws ErrnoException;

    /**
     * Set mode (permission) of the abstract pathname.
     *
     * @return <code>true</code> on success.
     * @see Os#chmod(String, int)
     */
    public abstract boolean setMode(int mode) throws ErrnoException;

    /**
     * @return Get UID and GID of the abstract pathname.
     */
    public abstract UidGidPair getUidGid() throws ErrnoException;

    /**
     * Set UID and GID of the abstract pathname.
     *
     * @return <code>true</code> on success.
     * @see Os#chown(String, int, int)
     */
    public abstract boolean setUidGid(int uid, int gid) throws ErrnoException;

    @Nullable
    public abstract String getSelinuxContext();

    public abstract boolean restoreSelinuxContext();

    public abstract boolean setSelinuxContext(@NonNull String context);

    /**
     * @return true if the abstract pathname denotes a block device.
     */
    public abstract boolean isBlock();

    /**
     * @return true if the abstract pathname denotes a character device.
     */
    public abstract boolean isCharacter();

    /**
     * @return true if the abstract pathname denotes a symbolic link.
     */
    public abstract boolean isSymlink();

    /**
     * @return true if the abstract pathname denotes a named pipe (FIFO).
     */
    public abstract boolean isNamedPipe();

    /**
     * @return true if the abstract pathname denotes a socket file.
     */
    public abstract boolean isSocket();

    /**
     * Returns the time that the file denoted by this abstract pathname was created.
     *
     * @return A <code>long</code> value representing the time the file was
     * created, measured in milliseconds since the epoch
     * (00:00:00 GMT, January 1, 1970), or <code>0L</code> if the
     * file does not exist or if an I/O error occurs
     */
    public abstract long creationTime();

    /**
     * Returns the time that the file denoted by this abstract pathname was last accessed.
     *
     * @return A <code>long</code> value representing the time the file was
     * last accessed, measured in milliseconds since the epoch
     * (00:00:00 GMT, January 1, 1970), or <code>0L</code> if the
     * file does not exist or if an I/O error occurs
     */
    public abstract long lastAccess();

    /**
     * Set the time that the file denoted by this abstract pathname was last accessed.
     *
     * @param millis A <code>long</code> value representing the time the file was
     *               last accessed, measured in milliseconds since the epoch
     *               (00:00:00 GMT, January 1, 1970)
     * @return {@code true} if and only if the operation succeeded; {@code false} otherwise.
     */
    public abstract boolean setLastAccess(long millis);

    /**
     * Creates a new hard link named by this abstract pathname of an existing file
     * if and only if a file with this name does not yet exist.
     *
     * @param existing a path to an existing file.
     * @return <code>true</code> if the named file does not exist and was successfully
     * created; <code>false</code> if the named file already exists.
     * @throws IOException if an I/O error occurred.
     */
    public abstract boolean createNewLink(String existing) throws IOException;

    /**
     * Creates a new symbolic link named by this abstract pathname to a target file
     * if and only if a file with this name does not yet exist.
     *
     * @param target the target of the symbolic link.
     * @return <code>true</code> if the named file does not exist and was successfully
     * created; <code>false</code> if the named file already exists.
     * @throws IOException if an I/O error occurred.
     */
    public abstract boolean createNewSymlink(String target) throws IOException;

    /**
     * Opens an InputStream with the matching file system backend of the file.
     *
     * @see FileInputStream#FileInputStream(File)
     */
    @NonNull
    public abstract FileInputStream newInputStream() throws IOException;

    /**
     * Opens an OutputStream with the matching file system backend of the file.
     *
     * @see FileOutputStream#FileOutputStream(File)
     */
    @NonNull
    public final FileOutputStream newOutputStream() throws IOException {
        return newOutputStream(false);
    }

    /**
     * Opens an OutputStream with the matching file system backend of the file.
     *
     * @see FileOutputStream#FileOutputStream(File, boolean)
     */
    @NonNull
    public abstract FileOutputStream newOutputStream(boolean append) throws IOException;

    /**
     * Create a child relative to the abstract pathname using the same file system backend.
     *
     * @see File#File(File, String)
     */
    @NonNull
    public abstract ExtendedFile getChildFile(String child);

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public abstract ExtendedFile getAbsoluteFile();

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public abstract ExtendedFile getCanonicalFile() throws IOException;

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public abstract ExtendedFile getParentFile();

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public abstract ExtendedFile[] listFiles();

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public abstract ExtendedFile[] listFiles(@Nullable FilenameFilter filter);

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public abstract ExtendedFile[] listFiles(@Nullable FileFilter filter);
}