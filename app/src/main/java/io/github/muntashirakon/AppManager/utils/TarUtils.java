// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.system.ErrnoException;
import android.util.Log;

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
import java.util.regex.Pattern;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
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
            throws IOException {
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
                if (basePath == null) {
                    basePath = Paths.get("/");
                }
                List<Path> files = Paths.getAll(basePath, source, filters, exclude, followLinks);
                for (Path file : files) {
                    String relativePath = Paths.getRelativePath(file, basePath);
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
     * @param type       Compression type
     * @param sources    Source files, sorted properly if there are multiple files.
     * @param dest       Destination directory
     * @param filters    A list of mutually exclusive regex filters
     * @param exclusions A list of mutually exclusive regex patterns to be excluded
     */
    @WorkerThread
    public static void extract(@NonNull @TarType String type, @NonNull Path[] sources, @NonNull Path dest,
                               @Nullable String[] filters, @Nullable String[] exclusions,
                               @Nullable String realDataAppPath)
            throws IOException {
        // Convert filters into patterns to reduce overheads
        Pattern[] filterPatterns;
        if (filters != null) {
            filterPatterns = new Pattern[filters.length];
            for (int i = 0; i < filters.length; ++i) {
                filterPatterns[i] = Pattern.compile(filters[i]);
            }
        } else filterPatterns = null;
        Pattern[] exclusionPatterns;
        if (exclusions != null) {
            exclusionPatterns = new Pattern[exclusions.length];
            for (int i = 0; i < exclusions.length; ++i) {
                exclusionPatterns[i] = Pattern.compile(exclusions[i]);
            }
        } else exclusionPatterns = null;
        // Run extraction
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
                    if (!entry.isDirectory() && (!Paths.isUnderFilter(file, dest, filterPatterns)
                            || Paths.willExclude(file, dest, exclusionPatterns))) {
                        // Unlike create, there's no efficient way to detect if a directory contains any filters.
                        // Therefore, directory can't be filtered during extraction
                        file.delete();
                        continue;
                    }
                    // Check if the given entry is a link.
                    if (entry.isSymbolicLink() && file.getFilePath() != null) {
                        if ((!Paths.isUnderFilter(file, dest, filterPatterns) || Paths.willExclude(file, dest, exclusionPatterns))) {
                            // Do not create this link even if it is a directory
                            continue;
                        }
                        String linkName = entry.getLinkName();
                        // There's no need to check if the linkName exists as it may be extracted
                        // after the link has been created
                        // Special check for /data/app
                        if (linkName.startsWith("/data/app/")) {
                            linkName = getAbsolutePathToDataApp(linkName, realDataAppPath);
                        }
                        Log.e("TarUtils", linkName);
                        file.delete();
                        if (!file.createNewSymbolicLink(linkName)) {
                            throw new IOException("Couldn't create symbolic link " + file + " pointing to " + linkName);
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
                    Paths.setPermissions(file, entry.getMode(), entry.getUserId(), entry.getGroupId());
                    // Restore timestamp
                    long modificationTime = entry.getModTime().getTime();
                    if (modificationTime > 0) { // Backward-compatibility
                        file.setLastModified(entry.getModTime().getTime());
                    }
                }
                // Delete unwanted files
                validateFiles(dest, dest, filterPatterns, exclusionPatterns);
            } catch (ErrnoException e) {
                throw new IOException(e);
            } finally {
                is.close();
            }
        }
    }

    private static void validateFiles(@NonNull Path basePath,
                                      @NonNull Path source,
                                      @Nullable Pattern[] filters,
                                      @Nullable Pattern[] exclusions) {
        if (!source.isDirectory()) {
            // Not a directory, skip
            return;
        }
        // List matched and unmatched children for top-level directory
        LinkedList<Path> matchedChildren = new LinkedList<>();
        List<Path> unmatchedChildren = new ArrayList<>();
        for (Path childPath : source.listFiles()) {
            if (childPath.isDirectory() || (Paths.isUnderFilter(childPath, basePath, filters)
                    && !Paths.willExclude(childPath, basePath, exclusions))) {
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
            if (Paths.isUnderFilter(source, basePath, filters) && !Paths.willExclude(source, basePath, exclusions)) {
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
                if (childPath.isDirectory() || (Paths.isUnderFilter(childPath, basePath, filters)
                        && !Paths.willExclude(childPath, basePath, exclusions))) {
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
                if (Paths.isUnderFilter(removedChild, basePath, filters) && !Paths.willExclude(removedChild, basePath, exclusions)) {
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

    @VisibleForTesting
    @Nullable
    static String getAbsolutePathToDataApp(@NonNull String brokenPath, @Nullable String realPath) {
        brokenPath = brokenPath.endsWith(File.separator) ? brokenPath.substring(0, brokenPath.length() - 1) : brokenPath;
        if (realPath == null) return brokenPath;
        if ("/data/app".equals(brokenPath)) {
            return brokenPath;
        }
        String[] brokenPathParts = brokenPath.split(File.separator);
        // The initial number of File.separator is 4, and the rests could be either part of the app path or
        // point to lib, oat or apk files
        // Index 4-1 = 3 is always a link to app
        if (brokenPathParts.length <= 4) {
            return realPath;
        }
        if ("lib".equals(brokenPathParts[4]) || "oat".equals(brokenPathParts[4]) || brokenPathParts[4].endsWith(".apk")) {
            StringBuilder sb = new StringBuilder(realPath);
            for (int i = 4; i < brokenPathParts.length; ++i) {
                sb.append(File.separator).append(brokenPathParts[i]);
            }
            return sb.toString();
        }
        // Index 5-1 = 4 is also a part of the app
        if (brokenPathParts.length == 5) {
            return realPath;
        }
        // Index 6-1 = 5 and later are currently not a part of the app
        StringBuilder sb = new StringBuilder(realPath);
        for (int i = 5; i < brokenPathParts.length; ++i) {
            sb.append(File.separator).append(brokenPathParts[i]);
        }
        return sb.toString();
    }
}
