package io.github.muntashirakon.AppManager.utils;

import android.os.Build;
import android.os.FileUtils;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

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
    public static File getSharableApk(@NonNull File privateApkFile) throws IOException {
        File tmpApkSource = File.createTempFile(privateApkFile.getName(), ".apk", AppManager.getContext().getExternalCacheDir());
        try (FileInputStream apkInputStream = new FileInputStream(privateApkFile);
             FileOutputStream apkOutputStream = new FileOutputStream(tmpApkSource)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                FileUtils.copy(apkInputStream, apkOutputStream);
            } else com.google.classysharkandroid.utils.IOUtils.copy(apkInputStream, apkOutputStream);
        }
        return tmpApkSource;
    }
}
