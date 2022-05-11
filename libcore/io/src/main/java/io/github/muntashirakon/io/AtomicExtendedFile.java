// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Static library support version of the framework's {@link android.util.AtomicFile}, a helper class
 * for performing atomic operations on a file by writing to a new file and renaming it into the
 * place of the original file after the write has successfully completed.
 * <p>
 * Atomic file guarantees file integrity by ensuring that a file has been completely written and
 * sync'd to disk before renaming it to the original file. Previously this is done by renaming the
 * original file to a backup file beforehand, but this approach couldn't handle the case where the
 * file is created for the first time. This class will also handle the backup file created by the
 * old implementation properly.
 * <p>
 * Atomic file does not confer any file locking semantics. Do not use this class when the file may
 * be accessed or modified concurrently by multiple threads or processes. The caller is responsible
 * for ensuring appropriate mutual exclusion invariants whenever it accesses the file.
 */
public class AtomicExtendedFile {
    private static final String TAG = "AtomicExtendedFile";

    private final ExtendedFile mBaseName;
    private final ExtendedFile mNewName;
    private final ExtendedFile mLegacyBackupName;

    /**
     * Create a new AtomicFile for a file located at the given File path.
     * The new file created when writing will be the same file path with ".new" appended.
     */
    public AtomicExtendedFile(@NonNull ExtendedFile baseName) {
        mBaseName = baseName;
        mNewName = Objects.requireNonNull(baseName.getParentFile()).getChildFile(baseName.getName() + ".new");
        mLegacyBackupName = baseName.getParentFile().getChildFile(baseName.getName() + ".bak");
    }

    /**
     * Return the path to the base file.  You should not generally use this,
     * as the data at that path may not be valid.
     */
    @NonNull
    public ExtendedFile getBaseFile() {
        return mBaseName;
    }

    /**
     * Delete the atomic file.  This deletes both the base and new files.
     */
    public void delete() {
        mBaseName.delete();
        mNewName.delete();
        mLegacyBackupName.delete();
    }

    /**
     * Start a new write operation on the file.  This returns a FileOutputStream
     * to which you can write the new file data.  The existing file is replaced
     * with the new data.  You <em>must not</em> directly close the given
     * FileOutputStream; instead call either {@link #finishWrite(FileOutputStream)}
     * or {@link #failWrite(FileOutputStream)}.
     *
     * <p>Note that if another thread is currently performing
     * a write, this will simply replace whatever that thread is writing
     * with the new file being written by this thread, and when the other
     * thread finishes the write the new write operation will no longer be
     * safe (or will be lost).  You must do your own threading protection for
     * access to AtomicFile.
     */
    @WorkerThread
    @NonNull
    public FileOutputStream startWrite() throws IOException {
        if (mLegacyBackupName.exists()) {
            rename(mLegacyBackupName, mBaseName);
        }

        try {
            return mNewName.newOutputStream();
        } catch (FileNotFoundException e) {
            File parent = mNewName.getParentFile();
            if (!parent.mkdirs()) {
                throw new IOException("Failed to create directory for " + mNewName, e);
            }
            try {
                return mNewName.newOutputStream();
            } catch (FileNotFoundException e2) {
                throw new IOException("Failed to create new file " + mNewName, e2);
            }
        }
    }

    /**
     * Call when you have successfully finished writing to the stream
     * returned by {@link #startWrite()}.  This will close, sync, and
     * commit the new data.  The next attempt to read the atomic file
     * will return the new file stream.
     */
    public void finishWrite(@Nullable FileOutputStream str) {
        if (str == null) {
            return;
        }
        if (!sync(str)) {
            Log.e(TAG, "Failed to sync file output stream");
        }
        try {
            str.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to close file output stream", e);
        }
        rename(mNewName, mBaseName);
    }

    /**
     * Call when you have failed for some reason at writing to the stream
     * returned by {@link #startWrite()}.  This will close the current
     * write stream, and delete the new file.
     */
    public void failWrite(@Nullable FileOutputStream str) {
        if (str == null) {
            return;
        }
        if (!sync(str)) {
            Log.e(TAG, "Failed to sync file output stream");
        }
        try {
            str.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to close file output stream", e);
        }
        if (!mNewName.delete()) {
            Log.e(TAG, "Failed to delete new file " + mNewName);
        }
    }

    /**
     * Open the atomic file for reading. You should call close() on the FileInputStream when you are
     * done reading from it.
     * <p>
     * You must do your own threading protection for access to AtomicFile.
     */
    @WorkerThread
    @NonNull
    public FileInputStream openRead() throws IOException, RemoteException {
        if (mLegacyBackupName.exists()) {
            rename(mLegacyBackupName, mBaseName);
        }

        // It was okay to call openRead() between startWrite() and finishWrite() for the first time
        // (because there is no backup file), where openRead() would open the file being written,
        // which makes no sense, but finishWrite() would still persist the write properly. For all
        // subsequent writes, if openRead() was called in between, it would see a backup file and
        // delete the file being written, the same behavior as our new implementation. So we only
        // need a special case for the first write, and don't delete the new file in this case so
        // that finishWrite() can still work.
        if (mNewName.exists() && mBaseName.exists()) {
            if (!mNewName.delete()) {
                Log.e(TAG, "Failed to delete outdated new file " + mNewName);
            }
        }
        return mBaseName.newInputStream();
    }

    /**
     * Checks if the original or legacy backup file exists.
     * @return whether the original or legacy backup file exists.
     */
    public boolean exists() {
        return mBaseName.exists() || mLegacyBackupName.exists();
    }

    /**
     * A convenience for {@link #openRead()} that also reads all of the
     * file contents into a byte array which is returned.
     */
    @NonNull
    public byte[] readFully() throws IOException, RemoteException {
        try (FileInputStream stream = openRead()) {
            int pos = 0;
            int avail = stream.available();
            byte[] data = new byte[avail];
            while (true) {
                int amt = stream.read(data, pos, data.length - pos);
                //Log.i("foo", "Read " + amt + " bytes at " + pos
                //        + " of avail " + data.length);
                if (amt <= 0) {
                    //Log.i("foo", "**** FINISHED READING: pos=" + pos
                    //        + " len=" + data.length);
                    return data;
                }
                pos += amt;
                avail = stream.available();
                if (avail > data.length - pos) {
                    byte[] newData = new byte[pos + avail];
                    System.arraycopy(data, 0, newData, 0, pos);
                    data = newData;
                }
            }
        }
    }

    private static boolean sync(@NonNull FileOutputStream stream) {
        try {
            stream.getFD().sync();
            return true;
        } catch (IOException ignored) {
        }
        return true;
    }

    private static void rename(@NonNull File source, @NonNull File target) {
        // We used to delete the target file before rename, but that isn't atomic, and the rename()
        // syscall should atomically replace the target file. However in the case where the target
        // file is a directory, a simple rename() won't work. We need to delete the file in this
        // case because there are callers who erroneously called mBaseName.mkdirs() (instead of
        // mBaseName.getParentFile().mkdirs()) before creating the AtomicFile, and it worked
        // regardless, so this deletion became some kind of API.
        if (target.isDirectory()) {
            if (!target.delete()) {
                Log.e(TAG, "Failed to delete file which is a directory " + target);
            }
        }
        if (!source.renameTo(target)) {
            Log.e(TAG, "Failed to rename " + source + " to " + target);
        }
    }
}
