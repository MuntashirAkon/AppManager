// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.annotation.TargetApi;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

import aosp.libcore.util.HexEncoding;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.ProxyInputStream;

public class DigestUtils {
    @StringDef({CRC32, MD2, MD5, SHA_1, SHA_224, SHA_256, SHA_384, SHA_512})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Algorithm {
    }

    public static final String CRC32 = "CRC32";
    public static final String MD2 = "MD2";
    public static final String MD5 = "MD5";
    public static final String SHA_1 = "SHA-1";
    @TargetApi(22)
    public static final String SHA_224 = "SHA-224";
    public static final String SHA_256 = "SHA-256";
    public static final String SHA_384 = "SHA-384";
    public static final String SHA_512 = "SHA-512";

    @AnyThread
    @NonNull
    public static String getHexDigest(@Algorithm String algo, @NonNull byte[] bytes) {
        return HexEncoding.encodeToString(getDigest(algo, bytes), false /* lowercase */);
    }

    @WorkerThread
    @NonNull
    public static String getHexDigest(@Algorithm String algo, @NonNull File path) {
        List<File> allFiles = new ArrayList<>();
        gatherFiles(allFiles, path);
        List<String> hashes = new ArrayList<>(allFiles.size());
        for (File file : allFiles) {
            try (InputStream fileInputStream = new ProxyInputStream(file)) {
                hashes.add(DigestUtils.getHexDigest(algo, fileInputStream));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (hashes.size() == 0) return HexEncoding.encodeToString(new byte[0], false /* lowercase */);
        if (hashes.size() == 1) return hashes.get(0);
        String fullString = TextUtils.join("", hashes);
        return getHexDigest(algo, fullString.getBytes());
    }

    @WorkerThread
    @NonNull
    public static String getHexDigest(@Algorithm String algo, @NonNull Path path) {
        List<Path> allFiles = new ArrayList<>();
        gatherFiles(allFiles, path);
        List<String> hashes = new ArrayList<>(allFiles.size());
        for (Path file : allFiles) {
            try (InputStream fileInputStream = file.openInputStream()) {
                hashes.add(DigestUtils.getHexDigest(algo, fileInputStream));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (hashes.size() == 0) return HexEncoding.encodeToString(new byte[0], false /* lowercase */);
        if (hashes.size() == 1) return hashes.get(0);
        String fullString = TextUtils.join("", hashes);
        return getHexDigest(algo, fullString.getBytes());
    }

    @WorkerThread
    @NonNull
    public static String getHexDigest(@Algorithm String algo, @NonNull InputStream stream) {
        return HexEncoding.encodeToString(getDigest(algo, stream), false /* lowercase */);
    }

    @AnyThread
    @NonNull
    public static byte[] getDigest(@Algorithm String algo, @NonNull byte[] bytes) {
        if (CRC32.equals(algo)) {
            java.util.zip.CRC32 crc32 = new CRC32();
            crc32.update(bytes);
            return longToBytes(crc32.getValue());
        }
        try {
            return MessageDigest.getInstance(algo).digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    @WorkerThread
    @NonNull
    public static byte[] getDigest(@Algorithm String algo, @NonNull InputStream stream) {
        if (CRC32.equals(algo)) {
            java.util.zip.CRC32 crc32 = new CRC32();
            byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
            try (CheckedInputStream cis = new CheckedInputStream(stream, crc32)) {
                //noinspection StatementWithEmptyBody
                while (cis.read(buffer) >= 0) {
                }
            } catch (IOException ignore) {
            }
            return longToBytes(crc32.getValue());
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(algo);
            try (DigestInputStream digestInputStream = new DigestInputStream(stream, messageDigest)) {
                byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
                //noinspection StatementWithEmptyBody
                while (digestInputStream.read(buffer) != -1) {
                }
                digestInputStream.close();
                return messageDigest.digest();
            }
        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    @WorkerThread
    @NonNull
    public static Pair<String, String>[] getDigests(File path) {
        @Algorithm String[] algorithms = new String[]{DigestUtils.MD5, DigestUtils.SHA_1, DigestUtils.SHA_256,
                DigestUtils.SHA_384, DigestUtils.SHA_512};
        @SuppressWarnings("unchecked")
        Pair<String, String>[] digests = new Pair[algorithms.length];
        for (int i = 0; i < algorithms.length; ++i) {
            digests[i] = new Pair<>(algorithms[i], getHexDigest(algorithms[i], path));
        }
        return digests;
    }

    @AnyThread
    @NonNull
    public static Pair<String, String>[] getDigests(byte[] bytes) {
        @Algorithm String[] algorithms = new String[]{DigestUtils.MD5, DigestUtils.SHA_1, DigestUtils.SHA_256,
                DigestUtils.SHA_384, DigestUtils.SHA_512};
        @SuppressWarnings("unchecked")
        Pair<String, String>[] digests = new Pair[algorithms.length];
        for (int i = 0; i < algorithms.length; ++i) {
            digests[i] = new Pair<>(algorithms[i], getHexDigest(algorithms[i], bytes));
        }
        return digests;
    }

    @NonNull
    private static byte[] longToBytes(long l) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (l & 0xFF);
            l >>= 8;
        }
        return result;
    }

    static void gatherFiles(@NonNull List<File> files, @NonNull File source) {
        if (source.isDirectory()) {
            File[] children = source.listFiles();
            if (children == null || children.length == 0) {
                return;
            }
            for (File child : children) {
                gatherFiles(files, child);
            }
        } else if (source.isFile()) {
            // Not directory, add it
            files.add(source);
        } // else we don't support other type of files
    }

    static void gatherFiles(@NonNull List<Path> files, @NonNull Path source) {
        if (source.isDirectory()) {
            Path[] children = source.listFiles();
            if (children.length == 0) {
                return;
            }
            for (Path child : children) {
                gatherFiles(files, child);
            }
        } else if (source.isFile()) {
            // Not directory, add it
            files.add(source);
        } // else we don't support other type of files
    }
}
