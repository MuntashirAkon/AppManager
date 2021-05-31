// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.io.FileStatus;
import io.github.muntashirakon.io.ProxyFile;
import io.github.muntashirakon.io.ProxyFiles;
import io.github.muntashirakon.io.ProxyInputStream;
import io.github.muntashirakon.io.ProxyOutputStream;
import io.github.muntashirakon.io.SplitInputStream;
import io.github.muntashirakon.io.SplitOutputStream;

public final class TarUtils {
    public static final long DEFAULT_SPLIT_SIZE = 1024 * 1024 * 1024;

    @StringDef(value = {
            TAR_GZIP,
            TAR_BZIP2
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface TarType {
    }

    public static final String TAR_GZIP = "z";
    public static final String TAR_BZIP2 = "j";

    /**
     * Create a tar file using the given compression method and split it into multiple files based
     * on the supplied split size.
     *
     * @param type        Compression type
     * @param source      Source directory
     * @param dest        Destination directory with file name as prefix (.0, .1, etc. are added at the end)
     * @param filters     A list of mutually exclusive regex filters
     * @param splitSize   Size of the split, {@link #DEFAULT_SPLIT_SIZE} will be used if null is supplied
     * @param exclude     A list of mutually exclusive regex patterns to be excluded
     * @param followLinks Whether to follow the links
     * @return List of added files
     */
    @WorkerThread
    @NonNull
    public static List<File> create(@NonNull @TarType String type, @NonNull File source, @NonNull File dest,
                                    @Nullable String[] filters, @Nullable Long splitSize, @Nullable String[] exclude,
                                    boolean followLinks)
            throws IOException, RemoteException, ErrnoException {
        try (SplitOutputStream sos = new SplitOutputStream(dest, splitSize == null ? DEFAULT_SPLIT_SIZE : splitSize);
             BufferedOutputStream bos = new BufferedOutputStream(sos)) {
            OutputStream os;
            if (TAR_GZIP.equals(type)) {
                os = new GzipCompressorOutputStream(bos);
            } else if (TAR_BZIP2.equals(type)) {
                os = new BZip2CompressorOutputStream(bos);
            } else {
                throw new IllegalArgumentException("Invalid compression type: " + type);
            }
            try (TarArchiveOutputStream tos = new TarArchiveOutputStream(os)) {
                tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
                List<File> files = new ArrayList<>();
                File basePath = source.isDirectory() ? source : source.getParentFile();
                if (basePath == null) basePath = new File("/");
                gatherFiles(files, basePath, source, filters, exclude, followLinks);
                for (File file : files) {
                    String relativePath = getRelativePath(file, basePath);
                    if (relativePath.equals("") || relativePath.equals("/")) continue;
                    // For links, check if followLinks is enabled
                    if (!followLinks && isSymbolicLink(file)) {
                        // Add the link as is
                        TarArchiveEntry tarEntry = new TarArchiveEntry(relativePath, TarConstants.LF_SYMLINK);
                        tarEntry.setLinkName(file.getCanonicalFile().getAbsolutePath());
                        tos.putArchiveEntry(tarEntry);
                    } else {
                        TarArchiveEntry tarEntry = new TarArchiveEntry(file, relativePath);
                        tos.putArchiveEntry(tarEntry);
                        if (!file.isDirectory()) {
                            try (InputStream is = new ProxyInputStream(file)) {
                                IOUtils.copy(is, tos);
                            }
                        }
                    }
                    tos.closeArchiveEntry();
                }
                tos.finish();
            } finally {
                os.close();
            }
            return sos.getFiles();
        }
    }

    /**
     * Create a tar file using the given compression method and split it into multiple files based
     * on the supplied split size.
     *
     * @param type    Compression type
     * @param sources Source files, sorted properly if there are multiple files.
     * @param dest    Destination directory
     * @param filters A list of mutually exclusive regex filters
     * @param exclude A list of mutually exclusive regex patterns to be excluded
     */
    @WorkerThread
    public static void extract(@NonNull @TarType String type, @NonNull File[] sources, @NonNull File dest,
                               @Nullable String[] filters, @Nullable String[] exclude)
            throws IOException, RemoteException {
        try (SplitInputStream sis = new SplitInputStream(sources);
             BufferedInputStream bis = new BufferedInputStream(sis)) {
            InputStream is;
            if (TAR_GZIP.equals(type)) {
                is = new GzipCompressorInputStream(bis, true);
            } else if (TAR_BZIP2.equals(type)) {
                is = new BZip2CompressorInputStream(bis, true);
            } else {
                throw new IllegalArgumentException("Invalid compression type: " + type);
            }
            try (TarArchiveInputStream tis = new TarArchiveInputStream(is)) {
                String realDestPath = dest.getCanonicalFile().toURI().getPath();
                TarArchiveEntry entry;
                while ((entry = tis.getNextEntry()) != null) {
                    File file = new ProxyFile(dest, entry.getName());
                    if (!entry.isDirectory() && (!isUnderFilter(file, dest, filters)
                            || willExclude(file, dest, exclude))) {
                        // Unlike create, there's no efficient way to detect if a directory contains any filters.
                        // Therefore, directory can't be filtered during extraction
                        continue;
                    }
                    // Check if the given entry is a link. If it's a link, check if the linked file actually exist
                    // before creating the link
                    if (entry.isSymbolicLink()) {
                        String linkName = entry.getLinkName();
                        // There's no need to check if the linkName exists as it may be extracted
                        // after the link has been created
                        if (!Runner.runCommand(new String[]{"ln", "-s", linkName, file.getAbsolutePath()})
                                .isSuccessful()) {
                            throw new IOException("Couldn't create symbolic link " + file + " pointing to "
                                    + linkName);
                        }
                        continue;  // links do not need permission fixes
                    } else {
                        // Zip slip vulnerability check
                        if (!file.getCanonicalFile().toURI().getPath().startsWith(realDestPath)) {
                            throw new IOException("Zip slip vulnerability detected!" +
                                    "\nExpected dest: " + new File(realDestPath, entry.getName()) +
                                    "\nActual path: " + file.getCanonicalFile().toURI().getPath());
                        }
                        if (entry.isDirectory()) {
                            file.mkdir();
                        } else {
                            try (OutputStream os = new ProxyOutputStream(file)) {
                                IOUtils.copy(tis, os);
                            }
                        }
                    }
                    // Fix permissions
                    try {
                        ProxyFiles.setPermissions(file, entry.getMode(), entry.getUserId(), entry.getGroupId());
                    } catch (RuntimeException e) {
                        if (e.getMessage() == null || !e.getMessage().contains("mocked")) {
                            throw e;
                        }
                    }
                }
                // Delete unwanted files
                validateFiles(dest, dest, filters, exclude);
            } catch (ErrnoException | RemoteException e) {
                throw new IOException(e);
            } finally {
                is.close();
            }
        }
    }

    @VisibleForTesting
    static void gatherFiles(@NonNull List<File> files, @NonNull File basePath, @NonNull File source,
                            @Nullable String[] filters, @Nullable String[] exclude, boolean followLinks)
            throws ErrnoException, RemoteException {
        if (source.isDirectory()) {  // OsConstants#S_ISDIR
            // Is a directory, add only the directory if it's a symbolic link and followLinks is disabled
            if (!followLinks && isSymbolicLink(source)) {
                files.add(source);
                return;
            }
            // Check if the contents of the directory matches the filters
            File[] children = source.listFiles(pathname -> pathname.isDirectory()
                    || (isUnderFilter(pathname, basePath, filters) && !willExclude(pathname, basePath, exclude)));
            if (children == null || children.length == 0) {
                // Add this directory nonetheless if it matches one of the filters
                if (isUnderFilter(source, basePath, filters) && !willExclude(source, basePath, exclude)) {
                    files.add(source);
                }
                return;
            } else {
                // Has children, don't check for filters, just add the directory
                files.add(source);
            }
            for (File child : children) {
                gatherFiles(files, basePath, child, filters, exclude, followLinks);
            }
        } else if (source.isFile()) {  // OsConstants#S_ISREG
            // Not directory, add it
            files.add(source);
        } // else we don't support other type of files
    }

    private static boolean isSymbolicLink(@NonNull File file) throws ErrnoException, RemoteException {
        try {
            FileStatus lstat = ProxyFiles.lstat(file);
            // https://github.com/win32ports/unistd_h/blob/master/unistd.h
            return OsConstants.S_ISLNK(lstat.st_mode);
        } catch (RuntimeException e) {
            if (e.getMessage() == null || !e.getMessage().contains("mocked")) {
                throw e;
            }
            // Mock only
            return false;
        }
    }

    private static void validateFiles(@NonNull File basePath,
                                      @NonNull File source,
                                      @Nullable String[] filters,
                                      @Nullable String[] exclude) {
        if (source.isDirectory()) {
            // Check if the contents of the directory matches the filters
            File[] children = source.listFiles(pathname -> pathname.isDirectory()
                    || (isUnderFilter(pathname, basePath, filters) && !willExclude(pathname, basePath, exclude)));
            if (children == null || children.length == 0) {
                // No child has matched, delete this directory
                IOUtils.deleteDir(source);
                // Create this directory again if it matches one of the filters
                if (isUnderFilter(source, basePath, filters) && !willExclude(source, basePath, exclude)) {
                    source.mkdirs();
                }
                return;
            }
            // Check for unmatched children
            File[] unmatchedChildren = source.listFiles(pathname -> !ArrayUtils.contains(children, pathname));
            // Delete unmatched children
            if (unmatchedChildren != null) {
                for (File child : unmatchedChildren) {
                    IOUtils.deleteDir(child);
                }
            }
            // Validate matched children
            for (File child : children) {
                validateFiles(basePath, child, filters, exclude);
            }
        }
    }

    private static boolean isUnderFilter(@NonNull File file, @NonNull File basePath, @Nullable String[] filters) {
        if (filters == null) return true;
        String fileStr = getRelativePath(file, basePath);
        for (String filter : filters) {
            if (fileStr.matches(filter)) return true;
        }
        return false;
    }

    private static boolean willExclude(@NonNull File file, @NonNull File basePath, @Nullable String[] exclude) {
        if (exclude == null) return false;
        String fileStr = getRelativePath(file, basePath);
        for (String excludeRegex : exclude) {
            if (fileStr.matches(excludeRegex)) return true;
        }
        return false;
    }

    @NonNull
    private static String getRelativePath(@NonNull File file, @NonNull File baseFile) {
        URI childPath = file.toURI();
        URI basePath = baseFile.toURI();
        URI relPath = basePath.relativize(childPath);
        return relPath.getPath();
    }
}
