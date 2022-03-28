// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.os.RemoteException;
import android.system.ErrnoException;

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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.ProxyFile;
import io.github.muntashirakon.io.ProxyFiles;
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
     * @param type           Compression type
     * @param source         Source directory/file
     * @param dest           Destination directory
     * @param destFilePrefix filename as a prefix (.0, .1, etc. are added at the end)
     * @param filters        A list of mutually exclusive regex filters
     * @param splitSize      Size of the split, {@link #DEFAULT_SPLIT_SIZE} will be used if null is supplied
     * @param exclude        A list of mutually exclusive regex patterns to be excluded
     * @param followLinks    Whether to follow the links
     * @return List of added files
     */
    @WorkerThread
    @NonNull
    public static List<Path> create(@NonNull @TarType String type, @NonNull Path source, @NonNull Path dest,
                                    @NonNull String destFilePrefix, @Nullable String[] filters,
                                    @Nullable Long splitSize, @Nullable String[] exclude, boolean followLinks)
            throws IOException, RemoteException, ErrnoException {
        try (SplitOutputStream sos = new SplitOutputStream(dest, destFilePrefix, splitSize == null ? DEFAULT_SPLIT_SIZE : splitSize);
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
                Path basePath = source.isDirectory() ? source : source.getParentFile();
                if (basePath == null) basePath = new Path(AppManager.getContext(), new File("/"));
                List<Path> files = listAllFiles(basePath, source, filters, exclude, followLinks);
                for (Path file : files) {
                    String relativePath = getRelativePath(file, basePath);
                    if (relativePath.equals("") || relativePath.equals("/")) continue;
                    // For links, check if followLinks is enabled
                    if (!followLinks && file.isSymbolicLink()) {
                        // A path can be symbolic link only if it's a file
                        // Add the link as is
                        TarArchiveEntry tarEntry = new TarArchiveEntry(relativePath, TarConstants.LF_SYMLINK);
                        tarEntry.setLinkName(file.getRealFilePath());
                        tos.putArchiveEntry(tarEntry);
                    } else {
                        TarArchiveEntry tarEntry = new TarArchiveEntry(file, relativePath);
                        tos.putArchiveEntry(tarEntry);
                        if (!file.isDirectory()) {
                            try (InputStream is = file.openInputStream()) {
                                FileUtils.copy(is, tos);
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
    public static void extract(@NonNull @TarType String type, @NonNull Path[] sources, @NonNull Path dest,
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
                String realDestPath = dest.getRealFilePath();
                TarArchiveEntry entry;
                while ((entry = tis.getNextEntry()) != null) {
                    Path file;
                    if (entry.isDirectory()) {
                        file = dest.createDirectories(entry.getName());
                    } else file = dest.createNewArbitraryFile(entry.getName(), null);
                    if (!entry.isDirectory() && (!isUnderFilter(file, dest, filters)
                            || willExclude(file, dest, exclude))) {
                        // Unlike create, there's no efficient way to detect if a directory contains any filters.
                        // Therefore, directory can't be filtered during extraction
                        file.delete();
                        continue;
                    }
                    // Check if the given entry is a link.
                    if (entry.isSymbolicLink() && file.getFilePath() != null) {
                        String linkName = entry.getLinkName();
                        // There's no need to check if the linkName exists as it may be extracted
                        // after the link has been created
                        // TODO: 27/3/22 It might be necessary to check the link if it points to an old APK folder content
                        try {
                            // Delete link if already exists
                            file.delete();
                            ProxyFiles.symlink(new ProxyFile(linkName), file.getFilePath());
                        } catch (ErrnoException | RemoteException e) {
                            throw new IOException("Couldn't create symbolic link " + file + " pointing to " + linkName, e);
                        }
                        continue;  // links do not need permission fixes
                    } else {
                        // Zip slip vulnerability check
                        String realFilePath = file.getRealFilePath();
                        if (realDestPath != null && realFilePath != null && !realFilePath.startsWith(realDestPath)) {
                            throw new IOException("Zip slip vulnerability detected!" +
                                    "\nExpected dest: " + new File(realDestPath, entry.getName()) +
                                    "\nActual path: " + realFilePath);
                        }
                        if (!entry.isDirectory()) {
                            try (OutputStream os = file.openOutputStream()) {
                                FileUtils.copy(tis, os);
                            }
                        }
                    }
                    // Fix permissions
                    ProxyFiles.setPermissions(file, entry.getMode(), entry.getUserId(), entry.getGroupId());
                    // Restore timestamp
                    file.setLastModified(entry.getModTime().getTime());
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
    @NonNull
    static List<Path> listAllFiles(@NonNull Path basePath, @NonNull Path source, @Nullable String[] filters,
                                          @Nullable String[] exclude, boolean followLinks) throws IOException {
        LinkedList<Path> allFiles = new LinkedList<>();
        if (source.isFile()) { // OsConstants#S_ISREG
            // Add it and return
            allFiles.add(source);
            return allFiles;
        } else if (source.isDirectory()) { // OsConstants#S_ISDIR
            if (!followLinks && source.isSymbolicLink()) {
                // Add the directory only if it's a symbolic link and followLinks is disabled
                allFiles.add(source);
                return allFiles;
            }
        } else {
            // No support for any other files
            return allFiles;
        }
        // Top-level directory
        Path[] fileList = source.listFiles(pathname -> pathname.isDirectory()
                || (isUnderFilter(pathname, basePath, filters) && !willExclude(pathname, basePath, exclude)));
        if (fileList.length == 0) {
            // Add this directory nonetheless if it matches one of the filters, no symlink checks needed
            if (isUnderFilter(source, basePath, filters) && !willExclude(source, basePath, exclude)) {
                allFiles.add(source);
            }
            return allFiles;
        } else {
            // Has children, don't check for filters, just add the directory
            allFiles.add(source);
        }
        // Declare a collection of stored directories
        LinkedList<Path> dirCheckList = new LinkedList<>();
        for (Path curFile: fileList) {
            if (curFile.isFile()) { // OsConstants#S_ISREG
                allFiles.add(curFile);
            } else if(curFile.isDirectory()) { // OsConstants#S_ISDIR
                if (!followLinks && curFile.isSymbolicLink()) {
                    // Add the directory only if it's a symbolic link and followLinks is disabled
                    allFiles.add(curFile);
                } else {
                    // Not a symlink
                    dirCheckList.add(curFile);
                }
            } // else No support for any other files
        }
        while (!dirCheckList.isEmpty()) {
            Path removedDir = dirCheckList.removeFirst();
            // Remove the first catalog
            Path[] removedDirFileList = removedDir.listFiles(pathname -> pathname.isDirectory()
                    || (isUnderFilter(pathname, basePath, filters) && !willExclude(pathname, basePath, exclude)));
            if (removedDirFileList.length == 0) {
                // Add this directory nonetheless if it matches one of the filters, no symlink checks needed
                if (isUnderFilter(removedDir, basePath, filters) && !willExclude(removedDir, basePath, exclude)) {
                    allFiles.add(removedDir);
                }
                continue;
            } else {
                // Has children
                allFiles.add(removedDir);
            }
            for(Path curFile : removedDirFileList) {
                if (curFile.isFile()) { // OsConstants#S_ISREG
                    allFiles.add(curFile);
                } else if(curFile.isDirectory()) { // OsConstants#S_ISDIR
                    if (!followLinks && curFile.isSymbolicLink()) {
                        // Add the directory only if it's a symbolic link and followLinks is disabled
                        allFiles.add(curFile);
                    } else {
                        // Not a symlink
                        dirCheckList.add(curFile);
                    }
                } // else No support for any other files
            }
        }
        return allFiles;
    }

    private static void validateFiles(@NonNull Path basePath,
                                      @NonNull Path source,
                                      @Nullable String[] filters,
                                      @Nullable String[] exclude) {
        if (!source.isDirectory()) {
            // Not a directory, skip
            return;
        }
        // List matched and unmatched children for top-level directory
        LinkedList<Path> matchedChildren = new LinkedList<>();
        List<Path> unmatchedChildren = new ArrayList<>();
        for (Path childPath : source.listFiles()) {
            if (childPath.isDirectory() || (isUnderFilter(childPath, basePath, filters)
                    && !willExclude(childPath, basePath, exclude))) {
                // Matches the filter or it is a directory
                matchedChildren.add(childPath);
            } else unmatchedChildren.add(childPath);
        }
        if (matchedChildren.isEmpty()) {
            // No children have matched, delete this directory
            if (!basePath.equals(source)) {
                // Only delete if the source is not the base path
                source.delete();
            }
            // Create this directory again if it matches one of the filters
            if (isUnderFilter(source, basePath, filters) && !willExclude(source, basePath, exclude)) {
                source.mkdirs();
            }
            return;
        }
        // Validate matched children
        while (!matchedChildren.isEmpty()) {
            Path removedChild = matchedChildren.removeFirst();
            if (!removedChild.isDirectory()) {
                // Not a directory, skip
                continue;
            }
            // List matched and unmatched children
            int matchedCount = 0;
            for (Path childPath : removedChild.listFiles()) {
                if (childPath.isDirectory() || (isUnderFilter(childPath, basePath, filters)
                        && !willExclude(childPath, basePath, exclude))) {
                    // Matches the filter or it is a directory
                    matchedChildren.add(childPath);
                    ++matchedCount;
                } else unmatchedChildren.add(childPath);
            }
            if (matchedCount == 0) {
                // No children have matched, delete this directory
                if (!basePath.equals(removedChild)) {
                    // Only delete if the source is not the base path
                    removedChild.delete();
                }
                // Create this directory again if it matches one of the filters
                if (isUnderFilter(removedChild, basePath, filters) && !willExclude(removedChild, basePath, exclude)) {
                    removedChild.mkdirs();
                }
            }
        }
        // Delete unmatched children
        for (Path child : unmatchedChildren) {
            // No need to check return value as some paths may not exist
            child.delete();
        }
    }

    private static boolean isUnderFilter(@NonNull Path file, @NonNull Path basePath, @Nullable String[] filters) {
        if (filters == null) return true;
        String fileStr = getRelativePath(file, basePath);
        for (String filter : filters) {
            if (fileStr.matches(filter)) return true;
        }
        return false;
    }

    private static boolean willExclude(@NonNull Path file, @NonNull Path basePath, @Nullable String[] exclude) {
        if (exclude == null) return false;
        String fileStr = getRelativePath(file, basePath);
        for (String excludeRegex : exclude) {
            if (fileStr.matches(excludeRegex)) return true;
        }
        return false;
    }

    @NonNull
    private static String getRelativePath(@NonNull Path file, @NonNull Path basePath) {
        return getRelativePath(file, basePath, File.separator);
    }

    @VisibleForTesting
    @NonNull
    static String getRelativePath(@NonNull Path file, @NonNull Path basePath, @NonNull String separator) {
        String baseDir = basePath.getUri().getPath() + (basePath.isDirectory() ? separator : "");
        String targetPath = file.getUri().getPath() + (file.isDirectory() ? separator : "");
        return FileUtils.getRelativePath(targetPath, baseDir, separator);
    }

    @VisibleForTesting
    @NonNull
    static String getRelativePath(@NonNull File file, @NonNull File basePath, @NonNull String separator) {
        String baseDir = basePath.toURI().getPath();
        String targetPath = file.toURI().getPath();
        return FileUtils.getRelativePath(targetPath, baseDir, separator);
    }
}
