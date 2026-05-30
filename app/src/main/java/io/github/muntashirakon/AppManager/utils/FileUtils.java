// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static android.system.OsConstants.O_ACCMODE;
import static android.system.OsConstants.O_APPEND;
import static android.system.OsConstants.O_RDONLY;
import static android.system.OsConstants.O_RDWR;
import static android.system.OsConstants.O_TRUNC;
import static android.system.OsConstants.O_WRONLY;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.util.Objects;
import java.util.zip.ZipEntry;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.io.FileSystemManager;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public final class FileUtils {
    public static final String TAG = FileUtils.class.getSimpleName();

    @AnyThread
    public static boolean isZip(@NonNull Path path) throws IOException {
        int header;
        try (InputStream is = path.openInputStream()) {
            byte[] headerBytes = new byte[4];
            is.read(headerBytes);
            header = new BigInteger(headerBytes).intValue();
        }
        return header == 0x504B0304 || header == 0x504B0506 || header == 0x504B0708;
    }

    @AnyThread
    @NonNull
    public static String getFilenameFromZipEntry(@NonNull ZipEntry zipEntry) {
        return Paths.getLastPathSegment(zipEntry.getName());
    }

    @NonNull
    public static ParcelFileDescriptor getFdFromUri(@NonNull Context context, @NonNull Uri uri, String mode)
            throws FileNotFoundException {
        ParcelFileDescriptor fd = context.getContentResolver().openFileDescriptor(uri, mode);
        if (fd == null) {
            throw new FileNotFoundException("Uri inaccessible or empty.");
        }
        return fd;
    }

    @AnyThread
    @NonNull
    public static File getFileFromFd(@NonNull ParcelFileDescriptor fd) {
        return new File("/proc/self/fd/" + fd.getFd());
    }

    @AnyThread
    public static void deleteSilently(@Nullable Path path) {
        if (path == null || !path.exists()) return;
        if (!path.delete()) {
            Log.w(TAG, "Unable to delete %s", path);
        }
    }

    @AnyThread
    public static void deleteSilently(@Nullable File file) {
        if (!Paths.exists(file)) return;
        if (!file.delete()) {
            Log.w(TAG, "Unable to delete %s", file);
        }
    }

    @WorkerThread
    @NonNull
    public static String getContentFromAssets(@NonNull Context context, String fileName) {
        try (InputStream inputStream = context.getResources().getAssets().open(fileName)) {
            return IoUtils.getInputStreamContent(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    @AnyThread
    public static boolean isAssetDirectory(@NonNull Context context, @NonNull String path) {
        String[] files;
        try {
            files = context.getAssets().list(path);
        } catch (IOException e) {
            // Doesn't exist
            return false;
        }
        return files != null && files.length > 0;
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
     *
     * @param totalSize Total size of the stream. Only used for handling progress. Set {@code -1} if unknown.
     */
    @AnyThread
    public static long copy(@NonNull InputStream in, @NonNull OutputStream out, long totalSize,
                            @Nullable ProgressHandler progressHandler) throws IOException {
        float lastProgress = progressHandler != null ? progressHandler.getLastProgress() : 0;
        return IoUtils.copy(in, out, ThreadUtils.getBackgroundThreadExecutor(), progress -> {
            if (progressHandler != null) {
                progressHandler.postUpdate(100, lastProgress + (progress * 100f / totalSize));
            }
        });
    }

    @WorkerThread
    public static void copyFromAsset(@NonNull Context context, @NonNull String fileName, @NonNull Path dest)
            throws IOException {
        try (InputStream is = context.getAssets().open(fileName);
             OutputStream os = dest.openOutputStream()) {
            IoUtils.copy(is, os);
        }
    }

    @AnyThread
    @NonNull
    public static Path getTempPath(@NonNull String relativeDir, @NonNull String filename) {
        File newDir = FileCache.getGlobalFileCache().createCachedDir(relativeDir);
        return Paths.get(new File(newDir, filename));
    }

    @AnyThread
    @NonNull
    public static File getCachePath() {
        Context context = ContextUtils.getContext();
        try {
            return getExternalCachePath(context);
        } catch (FileNotFoundException e) {
            return context.getCacheDir();
        }
    }

    @AnyThread
    @NonNull
    public static File getExternalCachePath(@NonNull Context context) throws FileNotFoundException {
        return getBestExternalDataSubdir(context.getExternalCacheDirs());
    }

    @AnyThread
    @NonNull
    public static File getBestExternalDataSubdir(@Nullable File[] extDirs) throws FileNotFoundException {
        if (extDirs == null) {
            throw new FileNotFoundException("Shared storage unavailable.");
        }
        String lastReason = null;
        for (File extDir : extDirs) {
            // The priority is from top to bottom of the list as per Context#getExternalDir()
            if (extDir == null) {
                // Other external directory might exist
                continue;
            }
            if (!(extDir.exists() || extDir.mkdirs())) {
                // Try to recreate this with root
                if (RunnerUtils.isRootGiven() && forceCreateExternalDataSubDir(extDir)) {
                    Log.i(TAG, "Root created %s", extDir);
                    return extDir;
                }
                lastReason = extDir + ": permission denied.";
                Log.w(TAG, "Could not use %s.", extDir);
                continue;
            }
            String storageState = Environment.getExternalStorageState(extDir);
            if (!Objects.equals(storageState, Environment.MEDIA_MOUNTED)) {
                lastReason = extDir + ": not mounted (" + storageState + ")";
                Log.w(TAG, "Path %s not mounted. State: %s", extDir, storageState);
                continue;
            }
            return extDir;
        }
        throw new FileNotFoundException(lastReason != null ? lastReason : "No available shared storage found.");
    }

    public static boolean forceCreateExternalDataSubDir(@NonNull File dir) {
        File parentFile = Objects.requireNonNull(dir.getParentFile());
        String parent = parentFile.getAbsolutePath();
        if (!parentFile.exists()) {
            // Even the parent file doesn't exist which should not happen
            Log.w(TAG, parent + " doesn't exist.");
            return false;
        }
        String target = dir.getAbsolutePath();
        String chownTarget;
        try {
            StructStat parentStat = Os.stat(parent);
            chownTarget = parentStat.st_uid + ":" + parentStat.st_gid;
        } catch (ErrnoException e) {
            // Fallback to shell
            Runner.Result result = Runner.runCommand("stat -c '%u:%g' " + parent);
            String output = result.getOutput();
            if (result.isSuccessful() && !output.isEmpty()) {
                chownTarget = output.trim();
            } else {
                // Didn't work
                Log.w(TAG, "Could not retrieve UID:GID from " + parent);
                return false;
            }
        }

        return Runner.runCommand("mkdir " + target).isSuccessful() &&
                Runner.runCommand("chmod 770 " + target).isSuccessful() &&
                Runner.runCommand("chown " + chownTarget + " " + target).isSuccessful() &&
                Runner.runCommand("restorecon " + target).isSuccessful();
    }

    @AnyThread
    public static void chmod711(@NonNull File file) throws IOException {
        try {
            Os.chmod(file.getAbsolutePath(), 457);
        } catch (ErrnoException e) {
            Log.e("IOUtils", "Failed to apply mode 711 to " + file);
            throw new IOException(e);
        }
    }

    @AnyThread
    public static void chmod644(@NonNull File file) throws IOException {
        try {
            Os.chmod(file.getAbsolutePath(), 420);
        } catch (ErrnoException e) {
            Log.e(TAG, "Failed to apply mode 644 to %s", file);
            throw new IOException(e);
        }
    }

    public static boolean canReadUnprivileged(@NonNull File file) {
        if (file.canRead()) {
            try (FileChannel ignored = FileSystemManager.getLocal().openChannel(file, FileSystemManager.MODE_READ_ONLY)) {
                return true;
            } catch (IOException | SecurityException e) {
                return false;
            }
        }
        return false;
    }

    public static String translateModePosixToString(int mode) {
        String res;
        if ((mode & O_ACCMODE) == O_RDWR) {
            res = "rw";
        } else if ((mode & O_ACCMODE) == O_WRONLY) {
            res = "w";
        } else if ((mode & O_ACCMODE) == O_RDONLY) {
            res = "r";
        } else {
            throw new IllegalArgumentException("Bad mode: " + mode);
        }
        if ((mode & O_TRUNC) == O_TRUNC) {
            res += "t";
        }
        if ((mode & O_APPEND) == O_APPEND) {
            res += "a";
        }
        return res;
    }
}
