// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.provider.OpenableColumns;
import android.system.ErrnoException;
import android.system.Os;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.internal.util.TextUtils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.ProxyFile;
import io.github.muntashirakon.io.ProxyInputStream;
import io.github.muntashirakon.io.ProxyOutputStream;

import static android.system.OsConstants.O_APPEND;
import static android.system.OsConstants.O_CREAT;
import static android.system.OsConstants.O_RDONLY;
import static android.system.OsConstants.O_RDWR;
import static android.system.OsConstants.O_TRUNC;
import static android.system.OsConstants.O_WRONLY;

public final class IOUtils {
    public static final int DEFAULT_BUFFER_SIZE = 1024 * 50;

    @AnyThread
    public static boolean isInputFileZip(@NonNull ContentResolver cr, Uri uri) throws IOException {
        int header;
        try (InputStream is = cr.openInputStream(uri)) {
            byte[] headerBytes = new byte[4];
            is.read(headerBytes);
            header = new BigInteger(headerBytes).intValue();
        }
        return header == 0x504B0304 || header == 0x504B0506 || header == 0x504B0708;
    }

    @WorkerThread
    public static void bytesToFile(byte[] bytes, File file) throws IOException {
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))) {
            bos.write(bytes);
            bos.flush();
        }
    }

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
            return FileUtils.copy(inputStream, outputStream);
        } else {
            long count = copyLarge(inputStream, outputStream);
            if (count > Integer.MAX_VALUE) return -1;
            return count;
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

    @WorkerThread
    @NonNull
    public static File saveZipFile(@NonNull InputStream zipInputStream,
                                   @NonNull File destinationDirectory,
                                   @NonNull String fileName)
            throws IOException, RemoteException {
        return saveZipFile(zipInputStream, new ProxyFile(destinationDirectory, fileName));
    }

    @WorkerThread
    @NonNull
    public static File saveZipFile(@NonNull InputStream zipInputStream, @NonNull File filePath)
            throws IOException {
        if (filePath.exists()) //noinspection ResultOfMethodCallIgnored
            filePath.delete();
        try (OutputStream outputStream = new ProxyOutputStream(filePath)) {
            copy(zipInputStream, outputStream);
        }
        return filePath;
    }

    @AnyThread
    @Nullable
    public static String getFileName(@NonNull ContentResolver resolver, @NonNull Uri uri) {
        if (uri.getScheme() == null) return null;
        switch (uri.getScheme()) {
            case ContentResolver.SCHEME_CONTENT:
                try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
                    if (cursor == null) return null;
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    cursor.moveToFirst();
                    return cursor.getString(nameIndex);
                } catch (CursorIndexOutOfBoundsException ignore) {
                }
            case ContentResolver.SCHEME_FILE:
                if (uri.getPath() == null) return null;
                return new File(uri.getPath()).getName();
            default:
                return null;
        }
    }

    @AnyThread
    @NonNull
    public static String getFileNameFromZipEntry(@NonNull ZipEntry zipEntry) {
        String path = zipEntry.getName();
        int lastIndexOfSeparator = path.lastIndexOf("/");
        if (lastIndexOfSeparator == -1)
            return path;
        return path.substring(lastIndexOfSeparator + 1);
    }

    @AnyThread
    @NonNull
    public static String getLastPathComponent(@NonNull String path) {
        if (path.length() == 0) return path;
        int lastIndexOfSeparator = path.lastIndexOf("/");
        int lastIndexOfPath = path.length() - 1;
        if (lastIndexOfSeparator == -1) {
            // There are no `/` in the string, so return as is.
            return path;
        } else if (lastIndexOfSeparator == lastIndexOfPath) {
            // `/` is the last character.
            // Therefore, trim it and find the last path again.
            return getLastPathComponent(path.substring(0, lastIndexOfPath));
        }
        // There are path components, so return the last one.
        return path.substring(lastIndexOfSeparator + 1);
    }

    @AnyThread
    @Nullable
    public static String getSanitizedFileName(@NonNull String fileName, boolean replaceSpace) {
        if (fileName.equals(".") || fileName.equals("..")) {
            return null;
        }
        fileName = fileName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (replaceSpace) {
            fileName = fileName.replaceAll("\\s", "_");
        }
        if (TextUtils.isEmpty(fileName)) {
            return null;
        }
        return fileName;
    }

    @AnyThread
    @NonNull
    public static String trimExtension(@NonNull String path) {
        String filename = getLastPathComponent(path);
        int lastIndexOfDot = filename.lastIndexOf('.');
        int lastIndexOfPath = filename.length() - 1;
        if (lastIndexOfDot == 0 || lastIndexOfDot == -1 || lastIndexOfDot == lastIndexOfPath) {
            return path;
        }
        return path.substring(0, path.lastIndexOf('.'));
    }

    @AnyThread
    @NonNull
    public static File getFileFromFd(@NonNull ParcelFileDescriptor fd) {
        return new File("/proc/self/fd/" + fd.getFd());
    }

    @AnyThread
    public static void closeQuietly(@Nullable AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception e) {
            Log.w("IOUtils", String.format("Unable to close %s", closeable.getClass().getCanonicalName()), e);
        }
    }

    @AnyThread
    public static void deleteSilently(@Nullable File file) {
        if (file == null || !file.exists()) return;
        if (!file.delete()) {
            Log.w("IOUtils", String.format("Unable to delete %s", file.getAbsoluteFile()));
        }
    }

    @AnyThread
    @NonNull
    public static String getExtension(@NonNull String path) {
        String str = getLastPathComponent(path);
        int lastIndexOfDot = str.lastIndexOf('.');
        if (lastIndexOfDot == -1) return "";
        return str.substring(str.lastIndexOf('.') + 1);
    }

    @AnyThread
    public static long fileSize(@Nullable File root) {
        if (root == null) {
            return 0;
        }
        if (root.isFile()) {
            return root.length();
        }
        try {
            if (isSymlink(root)) {
                return 0;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }

        long length = 0;
        File[] files = root.listFiles();
        if (files == null) {
            return 0;
        }
        for (File file : files) {
            length += fileSize(file);
        }

        return length;
    }

    @AnyThread
    public static long fileSize(@Nullable Path root) {
        if (root == null) {
            return 0;
        }
        if (root.isFile()) {
            return root.length();
        }
        try {
            if (root.isSymbolicLink()) {
                return 0;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }

        long length = 0;
        Path[] files = root.listFiles();
        for (Path file : files) {
            length += fileSize(file);
        }

        return length;
    }

    @AnyThread
    private static boolean isSymlink(@NonNull File file) throws IOException {
        File canon;
        File parentFile = file.getParentFile();
        if (parentFile == null) {
            canon = file;
        } else {
            File canonDir = parentFile.getCanonicalFile();
            canon = new File(canonDir, file.getName());
        }
        return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
    }

    @WorkerThread
    @NonNull
    public static String getFileContent(@NonNull File file) {
        return getFileContent(file, "");
    }

    @WorkerThread
    @NonNull
    public static String getFileContent(@NonNull Path file) {
        return getFileContent(file, "");
    }

    /**
     * Read the full content of a file.
     *
     * @param file       The file to be read
     * @param emptyValue Empty value if no content has been found
     * @return File content as string
     */
    @WorkerThread
    @NonNull
    public static String getFileContent(@NonNull File file, @NonNull String emptyValue) {
        if (!file.exists() || file.isDirectory()) return emptyValue;
        try (InputStream inputStream = new ProxyInputStream(file)) {
            return getInputStreamContent(inputStream);
        } catch (IOException e) {
            if (!(e.getCause() instanceof ErrnoException)) {
                // This isn't just another EACCESS exception
                e.printStackTrace();
            }
        }
        return emptyValue;
    }

    /**
     * Read the full content of a file.
     *
     * @param file       The file to be read
     * @param emptyValue Empty value if no content has been found
     * @return File content as string
     */
    @WorkerThread
    @NonNull
    public static String getFileContent(@NonNull Path file, @NonNull String emptyValue) {
        if (!file.exists() || file.isDirectory()) return emptyValue;
        try (InputStream inputStream = file.openInputStream()) {
            return getInputStreamContent(inputStream);
        } catch (IOException e) {
            if (!(e.getCause() instanceof ErrnoException)) {
                // This isn't just another EACCESS exception
                e.printStackTrace();
            }
        }
        return emptyValue;
    }

    @WorkerThread
    @NonNull
    private static String getInputStreamContent(@NonNull InputStream inputStream) throws IOException {
        return new String(readFully(inputStream, -1, true), Charset.defaultCharset());
    }

    @WorkerThread
    @NonNull
    public static String getContentFromAssets(@NonNull Context context, String fileName) {
        try (InputStream inputStream = context.getResources().getAssets().open(fileName)) {
            return getInputStreamContent(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    @WorkerThread
    @NonNull
    public static String getFileContent(@NonNull ContentResolver contentResolver, @NonNull Uri file)
            throws IOException {
        try (InputStream inputStream = contentResolver.openInputStream(file)) {
            if (inputStream == null) throw new IOException("Failed to open " + file);
            return getInputStreamContent(inputStream);
        }
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

    public static int getRawDataId(@NonNull Context context, @NonNull String name) {
        return context.getResources().getIdentifier(name, "raw", context.getPackageName());
    }

    /**
     * Delete a directory by recursively deleting its children
     *
     * @param dir The directory to delete
     * @return True on success, false on failure
     */
    @AnyThread
    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children == null) return false;
            for (File child : children) {
                boolean success = deleteDir(child);
                if (!success) return false;
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else return false;
    }

    @AnyThread
    @NonNull
    public static Bitmap getBitmapFromDrawable(@NonNull Drawable drawable) {
        final Bitmap bmp = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bmp;
    }

    @WorkerThread
    public static void copyFromAsset(@NonNull Context context, String fileName, File destFile) {
        try (AssetFileDescriptor openFd = context.getAssets().openFd(fileName)) {
            try (InputStream open = openFd.createInputStream();
                 FileOutputStream fos = new FileOutputStream(destFile)) {
                copy(open, fos);
                fos.flush();
                fos.getFD().sync();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @WorkerThread
    @NonNull
    public static File getCachedFile(InputStream inputStream) throws IOException {
        File tempFile = getTempFile();
        try (OutputStream outputStream = new FileOutputStream(tempFile)) {
            copy(inputStream, outputStream);
        }
        return tempFile;
    }

    @WorkerThread
    @NonNull
    public static File getCachedFile(byte[] bytes) throws IOException {
        File tempFile = getTempFile();
        bytesToFile(bytes, tempFile);
        return tempFile;
    }

    @AnyThread
    @NonNull
    public static File getTempFile() throws IOException {
        File extDir = AppManager.getContext().getExternalFilesDir("cache");
        if (extDir == null) throw new FileNotFoundException("External storage not available.");
        if (!extDir.exists() && !extDir.mkdirs()) {
            throw new IOException("Cannot create cache directory in the external storage.");
        }
        return File.createTempFile("file_" + System.currentTimeMillis(), ".cached", extDir);
    }

    @AnyThread
    @NonNull
    public static File getTempFile(String name) throws IOException {
        File extDir = AppManager.getContext().getExternalFilesDir("cache");
        if (extDir == null) throw new FileNotFoundException("External storage not available.");
        if (!extDir.exists() && !extDir.mkdirs()) {
            throw new IOException("Cannot create cache directory in the external storage.");
        }
        File newFile = new File(extDir, name);
        if (newFile.exists()) newFile.delete();
        return newFile;
    }

    @AnyThread
    @NonNull
    public static File getCachePath() throws IOException {
        File extDir = AppManager.getContext().getExternalFilesDir("cache");
        if (extDir == null) throw new FileNotFoundException("External storage not available.");
        if (!extDir.exists() && !extDir.mkdirs()) {
            throw new IOException("Cannot create cache directory in the external storage.");
        }
        return extDir;
    }

    @WorkerThread
    public static long calculateFileCrc32(File file) throws IOException {
        return calculateCrc32(new ProxyInputStream(file));
    }

    @AnyThread
    public static long calculateBytesCrc32(byte[] bytes) throws IOException {
        return calculateCrc32(new ByteArrayInputStream(bytes));
    }

    @AnyThread
    public static long calculateCrc32(InputStream inputStream) throws IOException {
        try (InputStream in = inputStream) {
            CRC32 crc32 = new CRC32();
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int read;

            while ((read = in.read(buffer)) > 0)
                crc32.update(buffer, 0, read);

            return crc32.getValue();
        }
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
            Log.e("IOUtils", "Failed to apply mode 644 to " + file);
            throw new IOException(e);
        }
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

    @NonNull
    public static String getRelativePath(@NonNull String targetPath, @NonNull String baseDir, @NonNull String separator) {
        String[] base = baseDir.split(Pattern.quote(separator));
        String[] target = targetPath.split(Pattern.quote(separator));

        // Count common elements and their length
        int commonCount = 0, commonLength = 0, maxCount = Math.min(target.length, base.length);
        while (commonCount < maxCount) {
            String targetElement = target[commonCount];
            if (!targetElement.equals(base[commonCount])) break;
            commonCount++;
            commonLength += targetElement.length() + 1; // Directory name length plus slash
        }
        if (commonCount == 0) return targetPath; // No common path element

        int targetLength = targetPath.length();
        int dirsUp = base.length - commonCount;
        StringBuilder relative = new StringBuilder(dirsUp * 3 + targetLength - commonLength + 1);
        for (int i = 0; i < dirsUp; i++) {
            relative.append("..").append(separator);
        }
        if (commonLength < targetLength) relative.append(targetPath.substring(commonLength));
        return relative.toString();
    }
}
