// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.adb;

import static io.github.muntashirakon.AppManager.backup.adb.BackupCategories.CAT_EXT;
import static io.github.muntashirakon.AppManager.backup.adb.BackupCategories.CAT_INT_CE;
import static io.github.muntashirakon.AppManager.backup.adb.BackupCategories.CAT_INT_DE;
import static io.github.muntashirakon.AppManager.backup.adb.BackupCategories.CAT_OBB;
import static io.github.muntashirakon.AppManager.backup.adb.BackupCategories.CAT_SRC;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.os.Build;
import android.util.StringBuilderPrinter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.PackageInfoCompat;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.apk.signing.SignerInfo;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.SplitInputStream;

public class AndroidBackupCreator implements AutoCloseable {
    public static final String TAG = AndroidBackupCreator.class.getSimpleName();

    public static void fromTar(@NonNull Path tarSource, @NonNull Path abDest, @Nullable char[] password, int api,
                               boolean compress)
            throws IOException {
        int backupFileVersion = Constants.getBackupFileVersionFromApi(api);
        AndroidBackupHeader header = new AndroidBackupHeader(backupFileVersion, compress, password);
        try (InputStream is = tarSource.openInputStream();
             OutputStream realOs = header.write(abDest.openOutputStream())) {
            IoUtils.copy(is, realOs);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            ExUtils.rethrowAsIOException(e);
        }
    }

    @NonNull
    private final Path mWorkingDir;
    @NonNull
    private final String mPackageName;
    @NonNull
    private final PackageInfo mPackageInfo;
    @Nullable
    private final String mInstallerPackage;
    private final Map<Integer, List<Path>> mCategoryFilesMap;
    @NonNull
    @TarUtils.TarType
    private final String mTarType;
    private final List<Path> mFilesToBeDeleted = new ArrayList<>();

    public AndroidBackupCreator(@NonNull Map<Integer, List<Path>> categoryFilesMap,
                                @NonNull Path temporaryDir,
                                @NonNull PackageInfo packageInfo,
                                @Nullable String installerPackage,
                                @NonNull @TarUtils.TarType String tarType) {
        mCategoryFilesMap = new HashMap<>(categoryFilesMap);
        mWorkingDir = temporaryDir;
        mPackageInfo = packageInfo;
        mPackageName = packageInfo.packageName;
        mInstallerPackage = installerPackage;
        mTarType = tarType;
    }

    @Override
    public void close() {
        for (Path file : mFilesToBeDeleted) {
            file.delete();
        }
    }

    public Path getBackupFile(int dataIndex) throws IOException {
        // Create temporary merged TAR file
        String backupFilename = BackupUtils.getDataFilePrefix(dataIndex, null);
        Path tempTarFile = mWorkingDir.createNewFile(backupFilename + ".tar", null);
        mFilesToBeDeleted.add(tempTarFile);
        Path backupFile = mWorkingDir.createNewFile(backupFilename + ".ab", null);

        // Merge all category files into a single TAR
        mergeCategoryFilesIntoTar(tempTarFile);

        // Convert to AB file
        fromTar(tempTarFile, backupFile, null, Build.VERSION.SDK_INT, true);
        return backupFile;
    }

    private void mergeCategoryFilesIntoTar(@NonNull Path outputTarFile) throws IOException {
        try (OutputStream fos = outputTarFile.openOutputStream();
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             TarArchiveOutputStream taos = new TarArchiveOutputStream(bos)) {
            taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

            // Add manifest file first
            addManifestEntry(taos);

            // Process each category
            for (Map.Entry<Integer, List<Path>> entry : mCategoryFilesMap.entrySet()) {
                int category = entry.getKey();
                List<Path> files = entry.getValue();
                if (files != null && !files.isEmpty()) {
                    processCategoryFiles(taos, category, files);
                }
            }

            taos.finish();
        }
    }

    private void addManifestEntry(@NonNull TarArchiveOutputStream taos) throws IOException {
        String manifestPath = Constants.APPS_PREFIX + mPackageName + File.separator + Constants.BACKUP_MANIFEST_FILENAME;
        byte[] manifestContent = getManifestBytes(mCategoryFilesMap.get(CAT_SRC) != null);

        TarArchiveEntry manifestEntry = new TarArchiveEntry(manifestPath);
        manifestEntry.setSize(manifestContent.length);
        manifestEntry.setMode(0600); // rw-------
        manifestEntry.setModTime(0); // See AppMetadataBackupWriter.java

        taos.putArchiveEntry(manifestEntry);
        taos.write(manifestContent);
        taos.closeArchiveEntry();
    }

    // See AppMetadataBackupWriter#getManifestBytes(PackageInfo, boolean)
    @NonNull
    private byte[] getManifestBytes(boolean withApk) {
        StringBuilder builder = new StringBuilder(4096);
        StringBuilderPrinter printer = new StringBuilderPrinter(builder);
        printer.println(Integer.toString(Constants.BACKUP_MANIFEST_VERSION));
        printer.println(mPackageName);
        printer.println(Long.toString(PackageInfoCompat.getLongVersionCode(mPackageInfo)));
        printer.println(Integer.toString(Build.VERSION.SDK_INT));
        printer.println((mInstallerPackage != null) ? mInstallerPackage : "");
        printer.println(withApk ? "1" : "0");

        // Write the signature block.
        SignerInfo signerInfo = PackageUtils.getSignerInfo(mPackageInfo, true);
        if (signerInfo == null || signerInfo.getCurrentSignerCerts() == null) {
            printer.println("0");
        } else {
            // Retrieve the newest signatures to write.
            try {
                X509Certificate[] signerCerts = signerInfo.getCurrentSignerCerts();
                Signature[] signatures = new Signature[signerCerts.length];
                for (int i = 0; i < signatures.length; ++i) {
                    signatures[i] = new Signature(signerCerts[i].getEncoded());
                }
                printer.println(Integer.toString(signerCerts.length));
                for (Signature sig : signatures) {
                    printer.println(sig.toCharsString());
                }
            } catch (CertificateEncodingException e) {
                // Fall back to 0
                printer.println("0");
            }
        }
        return builder.toString().getBytes();
    }

    private void processCategoryFiles(@NonNull TarArchiveOutputStream taos,
                                      int category,
                                      @NonNull List<Path> files) throws IOException {
        try (SplitInputStream sis = new SplitInputStream(files);
             BufferedInputStream bis = new BufferedInputStream(sis);
             InputStream decompressedStream = TarUtils.createDecompressedStream(bis, mTarType);
             TarArchiveInputStream tis = new TarArchiveInputStream(decompressedStream)) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                String transformedPath = transformEntryPath(entry.getName(), category);
                byte linkFlag;
                if (entry.isSymbolicLink()) {
                    linkFlag = TarConstants.LF_SYMLINK;
                } else if (entry.isDirectory()) {
                    linkFlag = TarConstants.LF_DIR;
                } else linkFlag = TarConstants.LF_NORMAL;
                // Create new entry with transformed path
                TarArchiveEntry newEntry = new TarArchiveEntry(transformedPath, linkFlag);
                newEntry.setSize(entry.getSize());
                newEntry.setMode(entry.getMode());
                newEntry.setModTime(entry.getModTime());
                newEntry.setUserId(entry.getUserId());
                newEntry.setGroupId(entry.getGroupId());
                newEntry.setUserName(entry.getUserName());
                newEntry.setGroupName(entry.getGroupName());
                if (entry.isSymbolicLink()) {
                    newEntry.setLinkName(entry.getLinkName());
                }
                taos.putArchiveEntry(newEntry);
                if (linkFlag == TarConstants.LF_NORMAL) {
                    IoUtils.copy(tis, taos);
                }
                taos.closeArchiveEntry();
            }
        }
    }

    @NonNull
    private String transformEntryPath(@NonNull String originalPath, int category) {
        String basePath = Constants.APPS_PREFIX + mPackageName + File.separator;
        if (originalPath.endsWith(File.separator)) {
            // AB expects no trailing slashes
            originalPath = originalPath.substring(0, originalPath.length() - 1);
        }
        switch (category) {
            case CAT_SRC:
                return basePath + Constants.APK_TREE_TOKEN + File.separator + originalPath;
            case CAT_INT_CE:
                return transformInternalPath(basePath, originalPath, false);
            case CAT_INT_DE:
                return transformInternalPath(basePath, originalPath, true);
            case CAT_EXT:
                return basePath + Constants.MANAGED_EXTERNAL_TREE_TOKEN + File.separator + originalPath;
            case CAT_OBB:
                return basePath + Constants.OBB_TREE_TOKEN + File.separator + originalPath;
            default:
                throw new IllegalArgumentException("Invalid category: " + category);
        }
    }

    @NonNull
    private String transformInternalPath(@NonNull String basePath,
                                         @NonNull String path,
                                         boolean isDE) {
        String prefix;
        if (path.startsWith("files/")) {
            prefix = isDE ? Constants.DEVICE_FILES_TREE_TOKEN : Constants.FILES_TREE_TOKEN;
            path = path.substring(6); // Remove "files/"
        } else if (path.startsWith("databases/")) {
            prefix = isDE ? Constants.DEVICE_DATABASE_TREE_TOKEN : Constants.DATABASE_TREE_TOKEN;
            path = path.substring(10); // Remove "databases/"
        } else if (path.startsWith("shared_prefs/")) {
            prefix = isDE ? Constants.DEVICE_SHAREDPREFS_TREE_TOKEN : Constants.SHAREDPREFS_TREE_TOKEN;
            path = path.substring(13); // Remove "shared_prefs/"
        } else if (path.startsWith("no_backup/")) {
            prefix = isDE ? Constants.DEVICE_NO_BACKUP_TREE_TOKEN : Constants.NO_BACKUP_TREE_TOKEN;
            path = path.substring(10); // Remove "no_backup/"
        } else if (path.startsWith("caches/")) {
            prefix = isDE ? Constants.DEVICE_CACHE_TREE_TOKEN : Constants.CACHE_TREE_TOKEN;
            path = path.substring(7); // Remove "caches/"
        } else prefix = isDE ? Constants.DEVICE_ROOT_TREE_TOKEN : Constants.ROOT_TREE_TOKEN;
        return basePath + prefix + File.separator + path;
    }
}
