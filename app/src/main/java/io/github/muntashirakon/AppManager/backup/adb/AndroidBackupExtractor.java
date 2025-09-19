// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.adb;

import static io.github.muntashirakon.AppManager.backup.adb.BackupCategories.CAT_EXT;
import static io.github.muntashirakon.AppManager.backup.adb.BackupCategories.CAT_INT_CE;
import static io.github.muntashirakon.AppManager.backup.adb.BackupCategories.CAT_INT_DE;
import static io.github.muntashirakon.AppManager.backup.adb.BackupCategories.CAT_OBB;
import static io.github.muntashirakon.AppManager.backup.adb.BackupCategories.CAT_SRC;
import static io.github.muntashirakon.AppManager.backup.adb.BackupCategories.CAT_UNK;
import static io.github.muntashirakon.AppManager.utils.TarUtils.DEFAULT_SPLIT_SIZE;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.SplitOutputStream;

public class AndroidBackupExtractor implements AutoCloseable {
    public static final String TAG = AndroidBackupExtractor.class.getSimpleName();

    public static void toTar(@NonNull Path abSource, @NonNull Path tarDest, @Nullable char[] password)
            throws IOException {
        AndroidBackupHeader header = new AndroidBackupHeader(password);
        try (OutputStream os = tarDest.openOutputStream();
             InputStream realIs = header.read(abSource.openInputStream())) {
            IoUtils.copy(realIs, os);
        } catch (Exception e) {
            ExUtils.rethrowAsIOException(e);
        }
    }

    private final Path mWorkingDir;
    private final Map<Integer, List<TargetTarEntry>> mCategoryTargetEntriesMap = new HashMap<>();
    private final List<Path> mFilesToBeDeleted = new ArrayList<>();

    public AndroidBackupExtractor(@NonNull Path abFile, @NonNull Path temporaryDir, @NonNull String packageName) throws IOException {
        mWorkingDir = temporaryDir;
        String relativeDirInAb = Constants.APPS_PREFIX + packageName + File.separator;
        String abFilename = Paths.trimPathExtension(abFile.getName());
        Path tarFile = temporaryDir.createNewFile(abFilename + ".tar", null);
        mFilesToBeDeleted.add(tarFile);
        Path dest = temporaryDir.createNewDirectory(abFilename);
        mFilesToBeDeleted.add(dest);
        toTar(abFile, tarFile, null);
        try (InputStream fis = tarFile.openInputStream();
             TarArchiveInputStream tis = new TarArchiveInputStream(fis)) {
            String realDestPath = dest.getRealFilePath();
            int relDirSize = relativeDirInAb.length();
            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                String filename = Paths.normalize(entry.getName());
                // Early zip slip vulnerability check to avoid creating any files at all
                if (filename == null || filename.startsWith("../")) {
                    throw new IOException("Zip slip vulnerability detected!" +
                            "\nExpected dest: " + new File(realDestPath, entry.getName()) +
                            "\nActual path: " + (filename != null ? new File(realDestPath, filename) : realDestPath));
                }
                if (!filename.startsWith(relativeDirInAb)) {
                    throw new IOException("Unsupported file in AB: " + filename);
                }
                // Remove apps/{packageName}/ part
                filename = filename.substring(relDirSize);
                Path file;
                if (entry.isDirectory()) {
                    file = dest.createDirectoriesIfRequired(filename);
                } else file = dest.createNewArbitraryFile(filename, null);
                // Check if the given entry is a link.
                if (entry.isSymbolicLink() && file.getFilePath() != null) {
                    String linkName = entry.getLinkName();
                    file.delete();
                    file.createNewSymbolicLink(linkName);
                } else {
                    // Zip slip vulnerability might still be present
                    String realFilePath = file.getRealFilePath();
                    if (realDestPath != null && realFilePath != null && !realFilePath.startsWith(realDestPath)) {
                        throw new IOException("Zip slip vulnerability detected!" +
                                "\nExpected dest: " + new File(realDestPath, entry.getName()) +
                                "\nActual path: " + realFilePath);
                    }
                    if (!entry.isDirectory()) {
                        try (OutputStream os = file.openOutputStream()) {
                            IoUtils.copy(tis, os);
                        }
                    }
                }

                // Categorize and build TarArchiveEntry
                int category = getCategory(filename);
                if (category == CAT_UNK && filename.equals(Constants.BACKUP_MANIFEST_FILENAME)) {
                    // Ignore manifest file
                    continue;
                }
                TarArchiveEntry targetEntry = getTargetArchiveEntry(entry, filename);
                List<TargetTarEntry> targetTarEntries = mCategoryTargetEntriesMap.get(category);
                if (targetTarEntries == null) {
                    targetTarEntries = new ArrayList<>();
                    mCategoryTargetEntriesMap.put(category, targetTarEntries);
                }
                targetTarEntries.add(new TargetTarEntry(file, filename, category, targetEntry));
            }
        }
        // Validate UNK entries
        if (mCategoryTargetEntriesMap.get(CAT_UNK) != null) {
            Log.w(TAG, "Unknown entries: " + mCategoryTargetEntriesMap.get(CAT_UNK));
            throw new IOException("Unknown/unsupported entries detected.");
        }
    }

    @Override
    public void close() {
        for (Path file : mFilesToBeDeleted) {
            file.delete();
        }
    }

    @Nullable
    public Path[] getSourceFiles(@NonNull String extension, @TarUtils.TarType String tarType)
            throws IOException {
        return getFiles(CAT_SRC, 0, extension, tarType);
    }

    @Nullable
    public Path[] getInternalCeDataFiles(int dataIndex, @NonNull String extension,
                                         @TarUtils.TarType String tarType) throws IOException {
        return getFiles(CAT_INT_CE, dataIndex, extension, tarType);
    }

    @Nullable
    public Path[] getInternalDeDataFiles(int dataIndex, @NonNull String extension,
                                         @TarUtils.TarType String tarType) throws IOException {
        return getFiles(CAT_INT_DE, dataIndex, extension, tarType);
    }

    @Nullable
    public Path[] getExternalDataFiles(int dataIndex, @NonNull String extension,
                                       @TarUtils.TarType String tarType) throws IOException {
        return getFiles(CAT_EXT, dataIndex, extension, tarType);
    }

    @Nullable
    public Path[] getObbFiles(int dataIndex, @NonNull String extension,
                              @TarUtils.TarType String tarType) throws IOException {
        return getFiles(CAT_OBB, dataIndex, extension, tarType);
    }

    @Nullable
    public Path[] getFiles(int category,
                           int dataIndex,
                           @NonNull String extension,
                           @TarUtils.TarType String tarType) throws IOException {
        if (category >= CAT_UNK) {
            throw new IllegalArgumentException("Invalid category: " + category);
        }
        List<TargetTarEntry> targetTarEntries = mCategoryTargetEntriesMap.get(category);
        if (targetTarEntries == null) {
            return null;
        }
        String filePrefix = category == CAT_SRC ? BackupUtils.getSourceFilePrefix(extension) :
                BackupUtils.getDataFilePrefix(dataIndex, extension);
        try (SplitOutputStream sos = new SplitOutputStream(mWorkingDir, filePrefix, DEFAULT_SPLIT_SIZE);
             BufferedOutputStream bos = new BufferedOutputStream(sos);
             OutputStream os = TarUtils.createCompressedStream(bos, tarType)) {
            try (TarArchiveOutputStream tos = new TarArchiveOutputStream(os)) {
                tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
                for (TargetTarEntry entry : targetTarEntries) {
                    if (entry.targetEntry.isSymbolicLink()) {
                        // Add the link as is
                        tos.putArchiveEntry(entry.targetEntry);
                    } else {
                        tos.putArchiveEntry(entry.targetEntry);
                        if (!entry.targetEntry.isDirectory()) {
                            try (InputStream is = entry.sourceFile.openInputStream()) {
                                IoUtils.copy(is, tos);
                            }
                        }
                    }
                    tos.closeArchiveEntry();
                }
                tos.finish();
            } finally {
                os.close();
            }
            return Paths.getSortedPaths(sos.getFiles().toArray(new Path[0]));
        }
    }

    private int getCategory(@NonNull String filename) {
        //noinspection SuspiciousRegexArgument Not on Windows
        String firstPart = filename.split(File.separator, 2)[0];
        switch (firstPart) {
            case Constants.APK_TREE_TOKEN:
                return CAT_SRC;
            case Constants.OBB_TREE_TOKEN:
                return CAT_OBB;
            case Constants.MANAGED_EXTERNAL_TREE_TOKEN:
                return CAT_EXT;
            case Constants.ROOT_TREE_TOKEN:
            case Constants.FILES_TREE_TOKEN:
            case Constants.NO_BACKUP_TREE_TOKEN:
            case Constants.DATABASE_TREE_TOKEN:
            case Constants.SHAREDPREFS_TREE_TOKEN:
            case Constants.CACHE_TREE_TOKEN:
                return CAT_INT_CE;
            case Constants.DEVICE_ROOT_TREE_TOKEN:
            case Constants.DEVICE_FILES_TREE_TOKEN:
            case Constants.DEVICE_NO_BACKUP_TREE_TOKEN:
            case Constants.DEVICE_DATABASE_TREE_TOKEN:
            case Constants.DEVICE_SHAREDPREFS_TREE_TOKEN:
            case Constants.DEVICE_CACHE_TREE_TOKEN:
                return CAT_INT_DE;
            default:
                return CAT_UNK;
        }
    }

    @NonNull
    private TarArchiveEntry getTargetArchiveEntry(@NonNull TarArchiveEntry src, @NonNull String filename) {
        String realFilename = getRealFilename(filename);
        if (src.isSymbolicLink()) {
            TarArchiveEntry dst = new TarArchiveEntry(realFilename, TarConstants.LF_SYMLINK);
            dst.setLinkName(src.getLinkName());
            return dst;
        }
        // Regular file/folder
        byte flag = src.isDirectory() ? TarConstants.LF_DIR : TarConstants.LF_NORMAL;
        TarArchiveEntry dst = new TarArchiveEntry(realFilename, flag);
        dst.setSize(src.getSize());
        dst.setMode(src.getMode());
        dst.setModTime(src.getModTime());
        dst.setUserId(src.getUserId());
        dst.setGroupId(src.getGroupId());
        dst.setUserName(src.getUserName());
        dst.setGroupName(src.getGroupName());
        return dst;
    }

    @NonNull
    private String getRealFilename(@NonNull String filename) {
        //noinspection SuspiciousRegexArgument Not on Windows
        String[] parts = filename.split(File.separator, 2);
        String firstPart = parts[0];
        String secondPart = parts[1];
        switch (firstPart) {
            case Constants.FILES_TREE_TOKEN:
            case Constants.DEVICE_FILES_TREE_TOKEN:
                return "files/" + secondPart;
            case Constants.NO_BACKUP_TREE_TOKEN:
            case Constants.DEVICE_NO_BACKUP_TREE_TOKEN:
                return "no_backup/" + secondPart;
            case Constants.DATABASE_TREE_TOKEN:
            case Constants.DEVICE_DATABASE_TREE_TOKEN:
                return "databases/" + secondPart;
            case Constants.SHAREDPREFS_TREE_TOKEN:
            case Constants.DEVICE_SHAREDPREFS_TREE_TOKEN:
                return "shared_prefs/" + secondPart;
            case Constants.CACHE_TREE_TOKEN:
            case Constants.DEVICE_CACHE_TREE_TOKEN:
                return "caches/" + secondPart;
            default:
                return secondPart;
        }
    }

    private static class TargetTarEntry {
        @NonNull
        public final Path sourceFile;
        @NonNull
        public final String sourceFilename;
        public final int category;
        @NonNull
        public final TarArchiveEntry targetEntry;

        private TargetTarEntry(@NonNull Path sourceFile, @NonNull String sourceFilename,
                               int category, @NonNull TarArchiveEntry targetEntry) {
            this.sourceFile = sourceFile;
            this.sourceFilename = sourceFilename;
            this.category = category;
            this.targetEntry = targetEntry;
        }

        @NonNull
        @Override
        public String toString() {
            return sourceFilename;
        }
    }
}
