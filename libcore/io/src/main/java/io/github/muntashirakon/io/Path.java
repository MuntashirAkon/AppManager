// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.content.Context;
import android.net.Uri;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import org.jetbrains.annotations.Contract;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

/**
 * Provide an interface to {@link File} and {@link DocumentFile} with basic functionalities.
 */
public abstract class Path implements Comparable<Path> {
    @NonNull
    protected final Context context;
    @NonNull
    protected DocumentFile documentFile;

    protected Path(@NonNull Context context, @NonNull DocumentFile documentFile) {
        this.context = context;
        this.documentFile = documentFile;
    }

    /**
     * Return the last segment of this path.
     */
    @NonNull
    public abstract String getName();

    @Nullable
    public String getExtension() {
        String name = getName();
        int lastIndexOfDot = name.lastIndexOf('.');
        if (lastIndexOfDot == -1 || lastIndexOfDot + 1 == name.length()) {
            return null;
        }
        return name.substring(lastIndexOfDot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Return a URI for the underlying document represented by this file. This
     * can be used with other platform APIs to manipulate or share the
     * underlying content. {@link DocumentFile#isDocumentUri(Context, Uri)} can
     * be used to test if the returned Uri is backed by an
     * {@link android.provider.DocumentsProvider}.
     */
    @NonNull
    public Uri getUri() {
        return documentFile.getUri();
    }

    /**
     * Return the underlying {@link ExtendedFile} if the path is backed by a real file,
     * {@code null} otherwise.
     */
    @Nullable
    public abstract ExtendedFile getFile();

    /**
     * Same as {@link #getFile()} except it return a raw string.
     */
    @Nullable
    public abstract String getFilePath();

    /**
     * Same as {@link #getFile()} except it returns the real path if the
     * current path is a symbolic link.
     */
    @Nullable
    public abstract String getRealFilePath() throws IOException;

    /**
     * Same as {@link #getFile()} except it returns the real path if the
     * current path is a symbolic link.
     */
    @Nullable
    public abstract Path getRealPath() throws IOException;

    /**
     * Return the MIME type of the path
     */
    @NonNull
    public abstract String getType();

    /**
     * Return the content info of the path.
     * <p>
     * This is an expensive operation and should be done in a non-UI thread.
     */
    @NonNull
    public abstract PathContentInfo getPathContentInfo();

    /**
     * Returns the length of this path in bytes. Returns 0 if the path does not
     * exist, or if the length is unknown. The result for a directory is not
     * defined.
     */
    @CheckResult
    public abstract long length();

    /**
     * Recreate this path if required.
     * <p>
     * This only recreates files and not directories in order to avoid potential mass destructive operation.
     *
     * @return {@code true} iff the path has been recreated.
     */
    @CheckResult
    public abstract boolean recreate();

    /**
     * Create a new file as a direct child of this directory. If the file
     * already exists, and it is not a directory, it will try to delete it
     * and create a new one.
     *
     * @param displayName Display name for the file with or without extension.
     *                    The name must not contain any file separator.
     * @param mimeType    Mime type for the new file. Underlying provider may
     *                    choose to add extension based on the mime type. If
     *                    displayName contains an extension, set it to null.
     * @return The newly created file.
     * @throws IOException              If the target is a mount point, a directory, or the current file is not a
     *                                  directory, or failed for any other reason.
     * @throws IllegalArgumentException If the display name contains file separator.
     */
    @NonNull
    public abstract Path createNewFile(@NonNull String displayName, @Nullable String mimeType) throws IOException;

    /**
     * Create a new directory as a direct child of this directory.
     *
     * @param displayName Display name for the directory. The name must not
     *                    contain any file separator.
     * @return The newly created directory.
     * @throws IOException              If the target is a mount point or the current file is not a directory,
     *                                  or failed for any other reason.
     * @throws IllegalArgumentException If the display name contains file separator.
     */
    @NonNull
    public abstract Path createNewDirectory(@NonNull String displayName) throws IOException;

    /**
     * Create a new file at some arbitrary level under this directory,
     * non-existing paths are created if necessary. If the file already exists,
     * and it isn't a directory, it will try to delete it and create a new one.
     * If mount points encountered while iterating through the paths, it will
     * try to create a new file under the last mount point.
     *
     * @param displayName Display name for the file with or without extension
     *                    and/or file separator.
     * @param mimeType    Mime type for the new file. Underlying provider may
     *                    choose to add extension based on the mime type. If
     *                    displayName contains an extension, set it to null.
     * @return The newly created file.
     * @throws IOException              If the target is a mount point, a directory or failed for any other reason.
     * @throws IllegalArgumentException If the display name is malformed.
     */
    @NonNull
    public abstract Path createNewArbitraryFile(@NonNull String displayName, @Nullable String mimeType) throws IOException;


    /**
     * Create all the non-existing directories under this directory. If mount
     * points encountered while iterating through the paths, it will try to
     * create a new directory under the last mount point.
     *
     * @param displayName Relative path to the target directory.
     * @return The newly created directory.
     * @throws IOException If the target is a mount point, or failed for any other reason.
     */
    @NonNull
    public abstract Path createDirectoriesIfRequired(@NonNull String displayName) throws IOException;

    /**
     * Create all the non-existing directories under this directory. If mount
     * points encountered while iterating through the paths, it will try to
     * create a new directory under the last mount point.
     *
     * @param displayName Relative path to the target directory.
     * @return The newly created directory.
     * @throws IOException If the target exists, or it is a mount point, or failed for any other reason.
     */
    @NonNull
    public abstract Path createDirectories(@NonNull String displayName) throws IOException;

    /**
     * Delete this file. If this is a directory, it is deleted recursively.
     *
     * @return {@code true} if the file was deleted, {@code false} if the file
     * is a mount point or any other error occurred.
     */
    public boolean delete() {
        if (isMountPoint()) {
            return false;
        }
        return documentFile.delete();
    }

    /**
     * Return the parent file of this document. If this is a mount point,
     * the parent is the parent of the mount point. For tree-documents,
     * the consistency of the parent file isn't guaranteed as the underlying
     * directory tree might be altered by another application.
     */
    @Nullable
    public abstract Path getParent();

    /**
     * Return the parent file of this document. If this is a mount point,
     * the parent is the parent of the mount point. For tree-documents,
     * the consistency of the parent file isn't guaranteed as the underlying
     * directory tree might be altered by another application.
     */
    @NonNull
    public Path requireParent() {
        return Objects.requireNonNull(getParent());
    }

    /**
     * Whether this file has a file denoted by this abstract name. The file
     * isn't necessarily have to be a direct child of this file.
     *
     * @param displayName Display name for the file with extension and/or
     *                    file separator if applicable.
     * @return {@code true} if the file denoted by this abstract name exists.
     */
    public abstract boolean hasFile(@NonNull String displayName);

    /**
     * Return the file denoted by this abstract name in this file. File name
     * can be either case-sensitive or case-insensitive depending on the file
     * provider.
     *
     * @param displayName Display name for the file with extension and/or
     *                    file separator if applicable.
     * @return The first file that matches the name.
     * @throws FileNotFoundException If the file was not found.
     */
    @NonNull
    public abstract Path findFile(@NonNull String displayName) throws FileNotFoundException;

    /**
     * Return a file that is a direct child of this directory, creating if necessary.
     *
     * @param displayName Display name for the file with or without extension.
     *                    The name must not contain any file separator.
     * @param mimeType    Mime type for the new file. Underlying provider may
     *                    choose to add extension based on the mime type. If
     *                    displayName contains an extension, set it to null.
     * @return The existing or newly created file.
     * @throws IOException              If the target is a mount point, a directory, or the current file is not a
     *                                  directory, or failed for any other reason.
     * @throws IllegalArgumentException If the display name contains file separator.
     */
    @NonNull
    public abstract Path findOrCreateFile(@NonNull String displayName, @Nullable String mimeType) throws IOException;

    /**
     * Return a directory that is a direct child of this directory, creating
     * if necessary.
     *
     * @param displayName Display name for the directory. The name must not
     *                    contain any file separator.
     * @return The existing or newly created directory or mount point.
     * @throws IOException              If the target directory could not be created, or the existing or the
     *                                  current file is not a directory.
     * @throws IllegalArgumentException If the display name contains file separator.
     */
    @NonNull
    public abstract Path findOrCreateDirectory(@NonNull String displayName) throws IOException;

    @NonNull
    public abstract PathAttributes getAttributes() throws IOException;

    /**
     * Whether this file can be found. This is useful only for the paths
     * accessed using Java File API. In other cases, the file has to exist
     * before it can be accessed. However, in SAF, the file can be deleted
     * by another application in which case the URI becomes non-existent.
     *
     * @return {@code true} if the file exists.
     */
    @CheckResult
    public abstract boolean exists();

    /**
     * Whether this file is a directory. A mount point is also considered as a
     * directory.
     * <p>
     * Note that the return value {@code false} does not necessarily mean that
     * the path is a file.
     *
     * @return {@code true} if the file is a directory or a mount point.
     */
    @CheckResult
    public abstract boolean isDirectory();

    /**
     * Whether this file is a file.
     * <p>
     * Note that the return value {@code false} does not necessarily mean that
     * the path is a directory.
     *
     * @return {@code true} if the file is a file.
     */
    @CheckResult
    public abstract boolean isFile();

    /**
     * Whether the file is a virtual file i.e. it has no physical existence.
     *
     * @return {@code true} if this is a virtual file.
     */
    @CheckResult
    public abstract boolean isVirtual();

    /**
     * Whether the file is a symbolic link, only applicable for Java File API.
     *
     * @return {@code true} iff the file is accessed using Java File API and
     * is a symbolic link.
     */
    @CheckResult
    public abstract boolean isSymbolicLink();

    /**
     * Creates a new symbolic link named by this abstract pathname to a target file if and only if the pathname is a
     * physical file and is not yet exist.
     *
     * @param target the target of the symbolic link.
     * @return {@code true} if target did not exist and the link was successfully created, and {@code false} otherwise.
     */
    public abstract boolean createNewSymbolicLink(String target);

    /**
     * Whether the file can be read.
     *
     * @return {@code true} if it can be read.
     */
    public abstract boolean canRead();

    /**
     * Whether the file can be written.
     *
     * @return {@code true} if it can be written.
     */
    public abstract boolean canWrite();

    /**
     * Whether the file can be executed.
     *
     * @return {@code true} if it can be executed.
     */
    public abstract boolean canExecute();

    public abstract int getMode();

    public abstract boolean setMode(int mode);

    @Nullable
    public abstract UidGidPair getUidGid();

    public abstract boolean setUidGid(UidGidPair uidGidPair);

    @Nullable
    public abstract String getSelinuxContext();

    public abstract boolean setSelinuxContext(@Nullable String context);

    /**
     * Whether the file is a mount point, thereby, is being overridden by another file system.
     *
     * @return {@code true} if this is a mount point.
     */
    public abstract boolean isMountPoint();

    public abstract boolean mkdir();

    public abstract boolean mkdirs();

    /**
     * Renames this file to {@code displayName}, both containing in the same directory.
     * <p>
     * Note that this method does <i>not</i> throw {@code IOException} on
     * failure. Callers must check the return value.
     * <p>
     * Some providers may need to create a new document to reflect the rename,
     * potentially with a different MIME type, so {@link #getUri()} and
     * {@link #getType()} may change to reflect the rename.
     * <p>
     * When renaming a directory, children previously enumerated through
     * {@link #listFiles()} may no longer be valid.
     *
     * @param displayName the new display name.
     * @return {@code true} on success. It returns {@code false} if the displayName is invalid or if it already exists.
     * @throws UnsupportedOperationException when working with a single document
     */
    public abstract boolean renameTo(@NonNull String displayName);

    /**
     * Same as {@link #moveTo(Path, boolean)} with override enabled
     */
    public boolean moveTo(@NonNull Path dest) {
        return moveTo(dest, true);
    }

    /**
     * Move the given path based on the following criteria:
     * <ol>
     *     <li>If both paths are physical (i.e. uses File API), use normal move behaviour
     *     <li>If one of the paths is virtual or the above fails, use special copy and delete operation
     * </ol>
     * <p>
     * Move behavior is as follows:
     * <ul>
     *     <li>If both are directories, move {@code this} inside {@code path}
     *     <li>If both are files, move {@code this} to {@code path} overriding it
     *     <li>If {@code this} is a file and {@code path} is a directory, move the file inside the directory
     *     <li>If {@code path} does not exist, it is created based on {@code this}.
     * </ul>
     *
     * @param path     Target file/directory which may or may not exist
     * @param override Whether to override the files in the destination
     * @return {@code true} on success and {@code false} on failure
     */
    public abstract boolean moveTo(@NonNull Path path, boolean override);


    @Nullable
    public Path copyTo(@NonNull Path path) {
        return copyTo(path, true);
    }

    @Nullable
    public abstract Path copyTo(@NonNull Path path, boolean override);

    public abstract long lastModified();

    public abstract boolean setLastModified(long time);

    public abstract long lastAccess();

    public abstract boolean setLastAccess(long millis);

    public abstract long creationTime();

    @NonNull
    public abstract Path[] listFiles();

    @NonNull
    public Path[] listFiles(@Nullable FileFilter filter) {
        Path[] ss = listFiles();
        ArrayList<Path> files = new ArrayList<>();
        for (Path s : ss) {
            if ((filter == null) || filter.accept(s)) {
                files.add(s);
            }
        }
        return files.toArray(new Path[0]);
    }

    @NonNull
    public Path[] listFiles(@Nullable FilenameFilter filter) {
        Path[] ss = listFiles();
        ArrayList<Path> files = new ArrayList<>();
        for (Path s : ss) {
            if (filter == null || filter.accept(this, s.getName())) {
                files.add(s);
            }
        }
        return files.toArray(new Path[0]);
    }

    @NonNull
    public String[] listFileNames() {
        Path[] ss = listFiles();
        ArrayList<String> files = new ArrayList<>();
        for (Path s : ss) {
            files.add(s.getName());
        }
        return files.toArray(new String[0]);
    }

    @NonNull
    public String[] listFileNames(@Nullable FileFilter filter) {
        Path[] ss = listFiles();
        ArrayList<String> files = new ArrayList<>();
        for (Path s : ss) {
            if (filter == null || filter.accept(s)) {
                files.add(s.getName());
            }
        }
        return files.toArray(new String[0]);
    }

    @NonNull
    public String[] listFileNames(@Nullable FilenameFilter filter) {
        Path[] ss = listFiles();
        ArrayList<String> files = new ArrayList<>();
        for (Path s : ss) {
            String name = s.getName();
            if (filter == null || filter.accept(this, name)) {
                files.add(name);
            }
        }
        return files.toArray(new String[0]);
    }

    @NonNull
    public abstract ParcelFileDescriptor openFileDescriptor(@NonNull String mode, @NonNull HandlerThread callbackThread)
            throws FileNotFoundException;

    public OutputStream openOutputStream() throws IOException {
        return openOutputStream(false);
    }

    @NonNull
    public abstract OutputStream openOutputStream(boolean append) throws IOException;

    @NonNull
    public abstract InputStream openInputStream() throws IOException;

    public abstract FileChannel openFileChannel(int mode) throws IOException;

    @NonNull
    public byte[] getContentAsBinary() {
        return getContentAsBinary(new byte[0]);
    }

    @Nullable
    @Contract("!null -> !null")
    public byte[] getContentAsBinary(byte[] emptyValue) {
        try (InputStream inputStream = openInputStream()) {
            return IoUtils.readFully(inputStream, -1, true);
        } catch (IOException e) {
            if (!(e.getCause() instanceof ErrnoException)) {
                // This isn't just another EACCESS exception
                e.printStackTrace();
            }
        }
        return emptyValue;
    }

    @NonNull
    public String getContentAsString() {
        return getContentAsString("");
    }

    @Nullable
    @Contract("!null -> !null")
    public String getContentAsString(@Nullable String emptyValue) {
        try (InputStream inputStream = openInputStream()) {
            return new String(IoUtils.readFully(inputStream, -1, true), Charset.defaultCharset());
        } catch (Exception e) {
            e.printStackTrace();
            return emptyValue;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return getUri().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Path)) return false;
        Path path = (Path) o;
        return documentFile.getUri().equals(path.documentFile.getUri());
    }

    @Override
    public int hashCode() {
        return documentFile.getUri().hashCode();
    }

    @Override
    public int compareTo(@NonNull Path o) {
        return documentFile.getUri().compareTo(o.documentFile.getUri());
    }

    @FunctionalInterface
    public interface FilenameFilter {
        /**
         * Tests if a specified file should be included in a file list.
         *
         * @param dir  the directory in which the file was found.
         * @param name the name of the file.
         * @return <code>true</code> if and only if the name should be
         * included in the file list; <code>false</code> otherwise.
         */
        boolean accept(Path dir, String name);
    }

    @FunctionalInterface
    public interface FileFilter {

        /**
         * Tests whether or not the specified abstract pathname should be
         * included in a pathname list.
         *
         * @param pathname The abstract pathname to be tested
         * @return <code>true</code> if and only if <code>pathname</code>
         * should be included
         */
        boolean accept(Path pathname);
    }
}
