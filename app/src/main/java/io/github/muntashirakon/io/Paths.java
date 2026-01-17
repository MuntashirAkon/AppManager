// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.jetbrains.annotations.Contract;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Stack;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.utils.AlphanumComparator;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.fs.VirtualFileSystem;

@SuppressWarnings("SuspiciousRegexArgument") // Not Windows, Android is Linux
public final class Paths {
    public static final String TAG = Paths.class.getSimpleName();

    /**
     * Replace spaces with replacement
     */
    public static final int SANITIZE_FLAG_SPACE = 1;
    /**
     * Replace {@code /} with replacement
     */
    public static final int SANITIZE_FLAG_UNIX_ILLEGAL_CHARS = 1 << 1;
    /**
     * Returns null if the filename becomes {@code .} or {@code ..} after applying all the sanitization rules
     */
    public static final int SANITIZE_FLAG_UNIX_RESERVED = 1 << 2;
    /**
     * Replace {@code :} with replacement
     */
    public static final int SANITIZE_FLAG_MAC_OS_ILLEGAL_CHARS = 1 << 3;
    /**
     * Replace {@code / ? < > \ : * | "} and control characters with replacement
     */
    public static final int SANITIZE_FLAG_NTFS_ILLEGAL_CHARS = 1 << 4;
    /**
     * Replace {@code / ? < > \ : * | " ^} and control characters with replacement
     */
    public static final int SANITIZE_FLAG_FAT_ILLEGAL_CHARS = 1 << 5;
    /**
     * Returns null if the filename becomes com1, com2, com3, com4, com5, com6, com7, com8, com9, lpt1, lpt2, lpt3,
     * lpt4, lpt5, lpt6, lpt7, lpt8, lpt9, con, nul, or prn after applying all the sanitization rules
     */
    public static final int SANITIZE_FLAG_WINDOWS_RESERVED = 1 << 6;

    @IntDef(flag = true, value = {
            SANITIZE_FLAG_SPACE,
            SANITIZE_FLAG_UNIX_ILLEGAL_CHARS,
            SANITIZE_FLAG_UNIX_RESERVED,
            SANITIZE_FLAG_MAC_OS_ILLEGAL_CHARS,
            SANITIZE_FLAG_NTFS_ILLEGAL_CHARS,
            SANITIZE_FLAG_FAT_ILLEGAL_CHARS,
            SANITIZE_FLAG_WINDOWS_RESERVED
    })
    public @interface SanitizeFlags {
    }

    @NonNull
    public static Path getPrimaryPath(@Nullable String path) {
        return get(new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority("com.android.externalstorage.documents")
                .path("/tree/primary:" + (path == null ? "" : path))
                .build());
    }

    @NonNull
    public static Path getUnprivileged(@NonNull File pathName) {
        Path path = null;
        try {
            path = new PathImpl(ContextUtils.getContext(), pathName.getAbsolutePath(), false);
        } catch (RemoteException ignore) {
            // This exception is never called in unprivileged mode.
        }
        // Path is never null because RE is never called.
        return Objects.requireNonNull(path);
    }

    @NonNull
    public static Path getUnprivileged(@NonNull String pathName) {
        Path path = null;
        try {
            path = new PathImpl(ContextUtils.getContext(), pathName, false);
        } catch (RemoteException ignore) {
            // This exception is never called in unprivileged mode.
        }
        // Path is never null because RE is never called.
        return Objects.requireNonNull(path);
    }

    @NonNull
    public static Path get(@NonNull String pathName) {
        return new PathImpl(ContextUtils.getContext(), Objects.requireNonNull(pathName));
    }

    @NonNull
    public static Path get(@NonNull File pathName) {
        return new PathImpl(ContextUtils.getContext(), pathName.getAbsolutePath());
    }

    @NonNull
    public static Path get(@NonNull Uri pathUri) {
        return new PathImpl(ContextUtils.getContext(), pathUri);
    }

    @NonNull
    public static Path getStrict(@NonNull Uri pathUri) throws FileNotFoundException {
        try {
            return new PathImpl(ContextUtils.getContext(), pathUri);
        } catch (IllegalArgumentException e) {
            throw (FileNotFoundException) (new FileNotFoundException(e.getMessage())).initCause(e);
        }
    }

    @NonNull
    public static Path get(@NonNull VirtualFileSystem fs) {
        return new PathImpl(ContextUtils.getContext(), fs);
    }

    @NonNull
    public static Path getTreeDocument(@Nullable Path parent, @NonNull Uri documentUri) {
        return new PathImpl(parent, ContextUtils.getContext(), documentUri);
    }

    @NonNull
    public static Path[] build(@NonNull Path[] base, String... segments) {
        Path[] result = new Path[base.length];
        for (int i = 0; i < base.length; i++) {
            result[i] = build(base[i], segments);
        }
        return result;
    }

    @Nullable
    public static Path build(@NonNull File base, @NonNull String... segments) {
        return build(get(base), segments);
    }

    @Nullable
    public static Path build(@NonNull Path base, @NonNull String... segments) {
        Path cur = base;
        boolean isLfs = cur.getFile() != null;
        try {
            for (String segment : segments) {
                if (isLfs) {
                    cur = get(new File(cur.getFilePath(), segment));
                } else {
                    cur = cur.findFile(segment);
                }
            }
            return cur;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public static boolean exists(@Nullable String path) {
        return path != null && get(path).exists();
    }

    public static boolean exists(@Nullable File path) {
        return path != null && path.exists();
    }

    @Nullable
    @Contract("!null -> !null")
    public static Path[] getSortedPaths(@Nullable Path[] paths) {
        if (paths == null) {
            return null;
        }
        // Default sort is usually an alphabetical sort which should've been an alphanumerical sort
        ArrayList<Path> sortedPaths = new ArrayList<>(Arrays.asList(paths));
        Collections.sort(sortedPaths, (o1, o2) -> AlphanumComparator.compareStringIgnoreCase(o1.getName(), o2.getName()));
        return sortedPaths.toArray(new Path[0]);
    }

    @NonNull
    public static PathAttributes getAttributesFromSafTreeCursor(@NonNull Uri treeUri, @NonNull Cursor c) {
        return PathAttributesImpl.fromSafTreeCursor(treeUri, c);
    }

    /**
     * Replace /storage/emulated with /data/media if the directory is inaccessible
     */
    @NonNull
    public static Path getAccessiblePath(@NonNull Path path) {
        if (!path.getUri().getScheme().equals(ContentResolver.SCHEME_FILE)) {
            // Scheme other than file are already readable at their best notion
            return path;
        }
        if (path.canRead()) {
            return path;
        }
        String pathString = Objects.requireNonNull(path.getFilePath());
        if (pathString.startsWith("/storage/emulated/")) {
            // The only inaccessible path is /storage/emulated/{!myUserId} and it has to be replaced with /data/media/{!myUserId}
            if (!String.format(Locale.ROOT, "/storage/emulated/%d", UserHandleHidden.myUserId()).equals(pathString)) {
                return get(pathString.replaceFirst("/storage/emulated/", "/data/media/"));
            }
        }
        return path;
    }

    @Nullable
    public static String sanitizeFilename(@Nullable String filename) {
        return sanitizeFilename(filename, null);
    }

    @Nullable
    public static String sanitizeFilename(@Nullable String filename, @Nullable String replacement) {
        return sanitizeFilename(filename, replacement, SANITIZE_FLAG_UNIX_ILLEGAL_CHARS | SANITIZE_FLAG_UNIX_RESERVED);
    }

    @Nullable
    public static String sanitizeFilename(@Nullable String filename, @Nullable String replacement, @SanitizeFlags int flags) {
        if (filename == null) {
            return null;
        }
        if (replacement == null) {
            replacement = "";
        }
        boolean spaces = (flags & SANITIZE_FLAG_SPACE) != 0;
        boolean unixIllegal = (flags & SANITIZE_FLAG_UNIX_ILLEGAL_CHARS) != 0;
        boolean unixReserved = (flags & SANITIZE_FLAG_UNIX_RESERVED) != 0;
        boolean macOsIllegal = (flags & SANITIZE_FLAG_MAC_OS_ILLEGAL_CHARS) != 0;
        boolean ntfsIllegal = (flags & SANITIZE_FLAG_NTFS_ILLEGAL_CHARS) != 0;
        boolean fatIllegal = (flags & SANITIZE_FLAG_FAT_ILLEGAL_CHARS) != 0;
        boolean windowsReserved = (flags & SANITIZE_FLAG_WINDOWS_RESERVED) != 0;
        String illegal = "[\n\r"; // Always replace newlines
        if (fatIllegal) {
            illegal += "\\\\/:*?\"<>|^\u0000-\u001f\u0080-\u009f";
        } else if (ntfsIllegal) {
            illegal += "\\\\/:*?\"<>|\u0000-\u001f\u0080-\u009f";
        } else if (macOsIllegal && unixIllegal) {
            illegal += ":/";
        } else if (macOsIllegal) {
            illegal += ":";
        } else if (unixIllegal) {
            illegal += "/";
        }
        if (spaces) {
            illegal += " ";
        }
        illegal += "]";
        filename = filename.trim().replaceAll(illegal, replacement);
        if (filename.isEmpty()) {
            return null;
        }
        if (unixReserved && (filename.equals(".") || filename.equals(".."))) {
            return null;
        }
        if (windowsReserved && filename.matches("^(con|prn|aux|nul|com[0-9]|lpt[0-9])(\\..*)?$")) {
            return null;
        }
        if (fatIllegal) {
            // Supports only 255 chars
            int maxLimit = Math.min(filename.length(), 255);
            return filename.substring(0, maxLimit);
        }
        if (ntfsIllegal) {
            // Supports only 256 chars
            int maxLimit = Math.min(filename.length(), 256);
            return filename.substring(0, maxLimit);
        }
        return filename;
    }

    /**
     * Same as path normalization except that it does not attempt to follow {@code ..}.
     *
     * @param path     Path to sanitize
     * @param omitRoot Whether to omit root when {@code path} is not {@code /}
     * @return Sanitized path on success, {@code null} when the final result is empty.
     */
    @Contract("null, _ -> null")
    @Nullable
    public static String sanitize(@Nullable String path, boolean omitRoot) {
        if (path == null) {
            return null;
        }
        if (path.isEmpty()) {
            return null;
        }
        path = path.replaceAll("[\r\n]", "");
        boolean isAbsolute = path.startsWith(File.separator);
        String[] parts = path.split(File.separator);
        List<String> newParts = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) continue;
            newParts.add(part);
        }
        path = TextUtils.join(File.separator, newParts);
        if (isAbsolute) {
            if (path.isEmpty()) {
                return File.separator;
            }
            if (!omitRoot) {
                return File.separator + path;
            }
        }
        return path.isEmpty() ? null : path;
    }

    /**
     * Normalize the given path. It resolves {@code ..} to find the ultimate path which may or may not be the real path
     * since it does not check for symbolic links.
     *
     * @param path Path to normalize
     * @return Normalized path on success, {@code null} when the final result is empty.
     */
    @Contract("null -> null")
    @Nullable
    public static String normalize(@Nullable String path) {
        if (path == null) {
            return null;
        }
        if (path.isEmpty()) {
            return null;
        }
        path = path.replaceAll("[\r\n]", "");
        boolean isAbsolute = path.startsWith(File.separator);
        String[] parts = path.split(File.separator);
        Stack<String> newParts = new Stack<>();
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (!part.equals("..") || (!isAbsolute && newParts.isEmpty()) || (!newParts.isEmpty() && "..".equals(newParts.peek()))) {
                newParts.push(part);
            } else if (!newParts.isEmpty()) {
                newParts.pop();
            }
        }
        path = TextUtils.join(File.separator, newParts);
        if (isAbsolute) {
            return File.separator + path;
        }
        return path.isEmpty() ? null : path;
    }

    /**
     * Return the last segment from the given path. If the path has a trailing `/`, it removes it and attempt to find
     * the last segment again. If it contains only `/` or no `/` at all, it returns empty string.
     * <p>
     * TODO: It should return null when no last path segment is found
     *
     * @param path An abstract path, may or may not start and/or end with `/`.
     */
    @AnyThread
    @NonNull
    public static String getLastPathSegment(@NonNull String path) {
        path = sanitize(path, false);
        // path has no trailing / or .
        if (path == null || path.equals(File.separator)) {
            return "";
        }
        int separatorIndex = path.lastIndexOf(File.separator);
        if (separatorIndex == -1) {
            // There are no `/` in the string, so return as is.
            return path;
        }
        // There are path components, so return the last one.
        String lastPart = path.substring(separatorIndex + 1);
        if (lastPart.equals("..")) {
            // Invalid part
            return "";
        }
        return lastPart;
    }

    @NonNull
    public static String removeLastPathSegment(@NonNull String path) {
        if (path.isEmpty()) {
            return "";
        }
        boolean isAbsolute = path.startsWith(File.separator);
        String[] parts = path.split(File.separator);
        Stack<String> newParts = new Stack<>();
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) continue;
            newParts.push(part);
        }
        if (!newParts.isEmpty() && !newParts.peek().equals("..")) {
            newParts.pop();
        }
        path = TextUtils.join(File.separator, newParts);
        if (isAbsolute) {
            return File.separator + path;
        }
        return path;
    }

    @NonNull
    public static String appendPathSegment(@NonNull String path, @NonNull String lastPathSegment) {
        if (lastPathSegment.isEmpty()) {
            return path;
        }
        lastPathSegment = lastPathSegment.replaceAll("[\r\n]", "");
        String[] parts = lastPathSegment.split(File.separator);
        List<String> newParts = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) continue;
            newParts.add(part);
        }
        String name = TextUtils.join(File.separator, newParts);
        if (name.isEmpty()) {
            return path;
        }
        if (path.endsWith(File.separator)) {
            return path + name;
        }
        return path + File.separator + name;
    }

    @AnyThread
    @NonNull
    public static String trimPathExtension(@NonNull String path) {
        if (path.isEmpty()) {
            return "";
        }
        boolean isAbsolute = path.startsWith(File.separator);
        String[] parts = path.split(File.separator);
        Stack<String> newParts = new Stack<>();
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) continue;
            newParts.push(part);
        }
        if (newParts.isEmpty()) {
            return isAbsolute ? File.separator : "";
        }
        String lastPart = newParts.peek();
        if (!lastPart.equals("..")) {
            int lastIndexOfDot = lastPart.lastIndexOf('.');
            int lastIndexOfPath = lastPart.length() - 1;
            if (lastIndexOfDot != 0 && lastIndexOfDot != -1 && lastIndexOfDot != lastIndexOfPath) {
                newParts.pop();
                newParts.push(lastPart.substring(0, lastIndexOfDot));
            }
        }
        path = TextUtils.join(File.separator, newParts);
        if (isAbsolute) {
            return File.separator + path;
        }
        return path;
    }

    @AnyThread
    @Nullable
    public static String getPathExtension(@NonNull String path) {
        return getPathExtension(path, true);
    }

    @AnyThread
    @Nullable
    public static String getPathExtension(@NonNull String path, boolean forceLowercase) {
        String str = getLastPathSegment(path);
        int lastIndexOfDot = str.lastIndexOf('.');
        if (lastIndexOfDot == -1 || lastIndexOfDot == str.length() - 1) return null;
        String extension = str.substring(lastIndexOfDot + 1);
        return forceLowercase ? extension.toLowerCase(Locale.ROOT) : extension;
    }

    public static Uri appendPathSegment(@NonNull Uri uri, @NonNull String lastPathSegment) {
        return new Uri.Builder()
                .scheme(uri.getScheme())
                .authority(uri.getAuthority())
                .path(sanitize(uri.getPath() + File.separator + lastPathSegment, false))
                .build();
    }

    public static Uri removeLastPathSegment(@NonNull Uri uri) {
        String path = uri.getPath();
        if (path.equals(File.separator)) return uri;
        return new Uri.Builder()
                .scheme(uri.getScheme())
                .authority(uri.getAuthority())
                .path(removeLastPathSegment(path))
                .build();
    }

    @NonNull
    public static String findNextBestDisplayName(@NonNull Path basePath, @NonNull String prefix,
                                                 @Nullable String extension) {
        return findNextBestDisplayName(basePath, prefix, extension, 1);
    }

    @NonNull
    public static String findNextBestDisplayName(@NonNull Path basePath, @NonNull String prefix,
                                                 @Nullable String extension, int initialIndex) {
        if (TextUtils.isEmpty(extension)) {
            extension = "";
        } else extension = "." + extension;
        String displayName = prefix + extension;
        int i = initialIndex;
        // We need to find the next best file name if current exists
        while (basePath.hasFile(displayName)) {
            displayName = String.format(Locale.ROOT, "%s (%d)%s", prefix, i, extension);
            ++i;
        }
        return displayName;
    }

    public static long size(@Nullable Path root) {
        if (root == null) {
            return 0;
        }
        if (root.isFile()) {
            return root.length();
        }
        if (root.isSymbolicLink()) {
            return 0;
        }
        if (!root.isDirectory()) {
            // Other types of files aren't supported
            return 0;
        }
        long length = 0;
        Path[] files = root.listFiles();
        for (Path file : files) {
            if (ThreadUtils.isInterrupted()) {
                // Size could be too long
                return length;
            }
            length += size(file);
        }
        return length;
    }

    public static void chmod(@NonNull Path path, int mode) throws ErrnoException {
        ExtendedFile file = path.getFile();
        if (file == null) {
            throw new ErrnoException("Supplied path is not a Linux path.", OsConstants.EBADF);
        }
        file.setMode(mode);
    }

    public static void chown(@NonNull Path path, int uid, int gid) throws ErrnoException {
        ExtendedFile file = path.getFile();
        if (file == null) {
            throw new ErrnoException("Supplied path is not a Linux path.", OsConstants.EBADF);
        }
        file.setUidGid(uid, gid);
    }

    /**
     * Set owner and mode of given path.
     *
     * @param mode to apply through {@code chmod}
     * @param uid  to apply through {@code chown}, or -1 to leave unchanged
     * @param gid  to apply through {@code chown}, or -1 to leave unchanged
     */
    public static void setPermissions(@NonNull Path path, int mode, int uid, int gid) throws ErrnoException {
        chmod(path, mode);
        if (uid >= 0 || gid >= 0) {
            chown(path, uid, gid);
        }
    }

    /**
     * Same as {@link #getAll(Path, Path, String[], String[], boolean)} except that all nullable fields are set to
     * {@code null} and following symbolic link is disabled.
     *
     * @param source All files and directories inside the path is listed, including the source file itself.
     * @return List of all files and directories inside {@code source} (inclusive).
     */
    @NonNull
    public static List<Path> getAll(@NonNull Path source) {
        return getAll(null, source, null, null, false);
    }

    /**
     * Get a list of files and directories inside {@code source} including the source file itself. This method is fully
     * capable of handling path filters as regular expressions and can follow symbolic links. It uses a non-recursive
     * algorithm and should be much faster than a recursive implementation.
     * <p>
     * <b>Note:</b> Currently, it can only retrieve regular files as well as directories. Any other file formats (e.g.
     * FIFO) are not currently supported.
     *
     * @param base        Base path is the path in respect to which {@code filters} and {@code exclusions} are applied.
     *                    If it is {@code null}, no base path is considered.
     * @param source      All files and directories inside the path is listed, including the source file itself.
     * @param filters     Filters to be applied. No filters are applied if it's set to {@code null}. The filters are
     *                    expected to be regular expressions and are mutually exclusive.
     * @param exclusions  Same as {@code filters}, except that it ignores the given patterns.
     * @param followLinks Whether to follow symbolic links. If disabled, a linked directory will be added as a regular
     *                    file.
     * @return List of files and directories inside {@code source} (inclusive).
     */
    @NonNull
    public static List<Path> getAll(@Nullable Path base, @NonNull Path source, @Nullable String[] filters,
                                    @Nullable String[] exclusions, boolean followLinks) {
        Objects.requireNonNull(source);
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
        // Start collecting files
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
                || (isUnderFilter(pathname, base, filterPatterns) && !willExclude(pathname, base, exclusionPatterns)));
        if (fileList.length == 0) {
            // Add this directory nonetheless if it matches one of the filters, no symlink checks needed
            if (isUnderFilter(source, base, filterPatterns) && !willExclude(source, base, exclusionPatterns)) {
                allFiles.add(source);
            }
            return allFiles;
        } else {
            // Has children, don't check for filters, just add the directory
            allFiles.add(source);
        }
        // Declare a collection of stored directories
        LinkedList<Path> dirCheckList = new LinkedList<>();
        for (Path curFile : fileList) {
            if (curFile.isFile()) { // OsConstants#S_ISREG
                allFiles.add(curFile);
            } else if (curFile.isDirectory()) { // OsConstants#S_ISDIR
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
                    || (isUnderFilter(pathname, base, filterPatterns) && !willExclude(pathname, base, exclusionPatterns)));
            if (removedDirFileList.length == 0) {
                // Add this directory nonetheless if it matches one of the filters, no symlink checks needed
                if (isUnderFilter(removedDir, base, filterPatterns) && !willExclude(removedDir, base, exclusionPatterns)) {
                    allFiles.add(removedDir);
                }
                continue;
            } else {
                // Has children
                allFiles.add(removedDir);
            }
            for (Path curFile : removedDirFileList) {
                if (curFile.isFile()) { // OsConstants#S_ISREG
                    allFiles.add(curFile);
                } else if (curFile.isDirectory()) { // OsConstants#S_ISDIR
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

    public static boolean isUnderFilter(@NonNull Path file, @Nullable Path basePath, @Nullable Pattern[] filters) {
        if (filters == null) return true;
        String fileStr = basePath == null ? file.getUri().getPath() : relativePath(file, basePath);
        for (Pattern filter : filters) {
            if (filter.matcher(fileStr).matches()) return true;
        }
        return false;
    }

    public static boolean willExclude(@NonNull Path file, @Nullable Path basePath, @Nullable Pattern[] exclude) {
        if (exclude == null) return false;
        String fileStr = basePath == null ? file.getUri().getPath() : relativePath(file, basePath);
        for (Pattern excludeRegex : exclude) {
            if (excludeRegex.matcher(fileStr).matches()) return true;
        }
        return false;
    }

    @NonNull
    public static String relativePath(@NonNull Path file, @NonNull Path basePath) {
        String baseDir = basePath.getUri().getPath() + (basePath.isDirectory() ? File.separator : "");
        String targetPath = file.getUri().getPath() + (file.isDirectory() ? File.separator : "");
        return relativePath(targetPath, baseDir);
    }

    @NonNull
    public static String relativePath(@NonNull String targetPath, @NonNull String baseDir) {
        return relativePath(targetPath, baseDir, File.separator);
    }

    @VisibleForTesting
    @NonNull
    public static String relativePath(@NonNull String targetPath, @NonNull String baseDir, @NonNull String separator) {
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
