/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.system.ErrnoException;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;

public final class IOUtils {

    public static void bytesToFile(byte[] bytes, File result) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(result));
        bos.write(bytes);
        bos.flush();
        bos.close();
    }

    /**
     * Get byte array from an InputStream most efficiently.
     * Taken from sun.misc.IOUtils
     * @param is InputStream
     * @param length Length of the buffer, -1 to read the whole stream
     * @param readAll Whether to read the whole stream
     * @return Desired byte array
     * @throws IOException If maximum capacity exceeded.
     */
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

    public static long copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return FileUtils.copy(inputStream, outputStream);
        } else {
            long count = copyLarge(inputStream, outputStream);
            if (count > Integer.MAX_VALUE) return -1;
            return count;
        }
    }

    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

    private static long copyLarge(@NonNull InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long count = 0;
        int n;
        while (-1 != (n = inputStream.read(buffer))) {
            outputStream.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    @NonNull
    public static File saveZipFile(@NonNull InputStream zipInputStream,
                                   @NonNull File destinationDirectory,
                                   @NonNull String fileName)
            throws IOException {
        return  saveZipFile(zipInputStream, new File(destinationDirectory, fileName));
    }

    @NonNull
    public static File saveZipFile(@NonNull InputStream zipInputStream, @NonNull File filePath)
            throws IOException {
        if (filePath.exists()) //noinspection ResultOfMethodCallIgnored
            filePath.delete();
        try (OutputStream outputStream = new FileOutputStream(filePath)) {
            copy(zipInputStream, outputStream);
        }
        return filePath;
    }

    @Nullable
    public static String getFileName(@NonNull ContentResolver resolver, Uri uri) {
        Cursor returnCursor =
                resolver.query(uri, null, null, null, null);
        if (returnCursor == null) return null;
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        returnCursor.moveToFirst();
        String name = returnCursor.getString(nameIndex);
        returnCursor.close();
        return name;
    }

    @NonNull
    public static String getFileNameFromZipEntry(@NonNull ZipEntry zipEntry) {
        String path = zipEntry.getName();
        int lastIndexOfSeparator = path.lastIndexOf("/");
        if (lastIndexOfSeparator == -1)
            return path;
        return path.substring(lastIndexOfSeparator + 1);
    }

    @NonNull
    public static String getLastPathComponent(@NonNull String path) {
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

    @NonNull
    public static String trimExtension(@NonNull String filename) {
        try {
            return filename.substring(0, filename.lastIndexOf('.'));
        } catch (Exception e) {
            return filename;
        }
    }

    @NonNull
    public static File getFileFromFd(@NonNull ParcelFileDescriptor fd) {
        return new File("/proc/self/fd/" + fd.getFd());
    }

    public static void closeSilently(@Nullable Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception e) {
            Log.w("IOUtils", String.format("Unable to close %s", closeable.getClass().getCanonicalName()), e);
        }
    }

    @NonNull
    public static String getExtension(@NonNull String path) {
        String str = getLastPathComponent(path);
        try {
            return str.substring(str.lastIndexOf('.') + 1);
        } catch (Exception e) {
            return str;
        }
    }

    @NonNull
    public static String getFileContent(@NonNull File file) {
        return getFileContent(file, "");
    }

    /**
     * Read the full content of a file.
     *
     * @param file       The file to be read
     * @param emptyValue Empty value if no content has been found
     * @return File content as string
     */
    @NonNull
    public static String getFileContent(@NonNull File file, @NonNull String emptyValue) {
        if (!file.exists() || file.isDirectory()) return emptyValue;
        try {
            return getInputStreamContent(new FileInputStream(file));
        } catch (IOException e) {
            if (!(e.getCause() instanceof ErrnoException)) {
                // This isn't just another EACCESS exception
                e.printStackTrace();
            }
        }
        if (AppPref.isRootOrAdbEnabled()) {
            return RunnerUtils.cat(file.getAbsolutePath(), emptyValue);
        }
        return emptyValue;
    }

    @NonNull
    public static String getInputStreamContent(@NonNull InputStream inputStream) throws IOException {
        return new String(IOUtils.readFully(inputStream, -1, true), Charset.defaultCharset());
    }

    @NonNull
    public static String getContentFromAssets(@NonNull Context context, String fileName) {
        try {
            InputStream inputStream = context.getResources().getAssets().open(fileName);
            return getInputStreamContent(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    @NonNull
    public static String getFileContent(@NonNull ContentResolver contentResolver, @NonNull Uri file)
            throws IOException {
        InputStream inputStream = contentResolver.openInputStream(file);
        if (inputStream == null) throw new IOException("Failed to open " + file.toString());
        return getInputStreamContent(inputStream);
    }

    /**
     * Delete a directory by recursively deleting its children
     * @param dir The directory to delete
     * @return True on success, false on failure
     */
    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children == null) return false;
            for (String child: children) {
                boolean success = deleteDir(new File(dir, child));
                if (!success) return false;
            }
            return dir.delete();
        } else if(dir != null && dir.isFile()) {
            return dir.delete();
        } else return false;
    }

    @NonNull
    public static Bitmap getBitmapFromDrawable(@NonNull Drawable drawable) {
        final Bitmap bmp = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bmp;
    }

    @NonNull
    public static File getSharableFile(@NonNull File privateFile) throws IOException {
        File tmpPublicSource = new File(AppManager.getContext().getExternalCacheDir(), privateFile.getName());
        try (FileInputStream inputStream = new FileInputStream(privateFile);
             FileOutputStream outputStream = new FileOutputStream(tmpPublicSource)) {
            copy(inputStream, outputStream);
        }
        return tmpPublicSource;
    }

    public static long calculateFileCrc32(File file) throws IOException {
        return calculateCrc32(new FileInputStream(file));
    }

    public static long calculateBytesCrc32(byte[] bytes) throws IOException {
        return calculateCrc32(new ByteArrayInputStream(bytes));
    }

    public static long calculateCrc32(InputStream inputStream) throws IOException {
        try (InputStream in = inputStream) {
            CRC32 crc32 = new CRC32();
            byte[] buffer = new byte[1024 * 1024];
            int read;

            while ((read = in.read(buffer)) > 0)
                crc32.update(buffer, 0, read);

            return crc32.getValue();
        }
    }
}
