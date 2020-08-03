package io.github.muntashirakon.AppManager.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.FileUtils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.zip.CRC32;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;

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

    static long copyLarge(@NonNull InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long count = 0;
        int n;
        while (-1 != (n = inputStream.read(buffer))) {
            outputStream.write(buffer, 0, n);
            count += n;
        }
        inputStream.close();
        if (outputStream != null) outputStream.close();
        return count;
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
    public static File getSharableFile(@NonNull File privateFile, String suffix) throws IOException {
        File tmpPublicSource = File.createTempFile(privateFile.getName(), suffix, AppManager.getContext().getExternalCacheDir());
        try (FileInputStream apkInputStream = new FileInputStream(privateFile);
             FileOutputStream apkOutputStream = new FileOutputStream(tmpPublicSource)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                FileUtils.copy(apkInputStream, apkOutputStream);
            } else copy(apkInputStream, apkOutputStream);
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
