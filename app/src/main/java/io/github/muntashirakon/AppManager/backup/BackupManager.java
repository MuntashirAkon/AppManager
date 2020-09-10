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

package io.github.muntashirakon.AppManager.backup;

import android.annotation.SuppressLint;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import dalvik.system.VMRuntime;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerShell;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.rules.RulesImporter;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.servermanager.ApiSupporter;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.types.FreshFile;
import io.github.muntashirakon.AppManager.types.PrivilegedFile;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public class BackupManager implements AutoCloseable {
    private static final String EXT_DATA = "/Android/data/";
    private static final String EXT_MEDIA = "/Android/media/";
    private static final String EXT_OBB = "/Android/obb/";
    private static final String[] CACHE_DIRS = new String[]{"cache", "code_cache"};
    private static final String SOURCE_PREFIX = "source";
    private static final String DATA_PREFIX = "data";
    private static final String BACKUP_FILE_SUFFIX = ".tar.gz";
    private static final String RULES_TSV = "rules.am.tsv";

    private static BackupManager instance;

    /**
     * @param packageName Package name of the app
     * @param flags       One or more of the {@link BackupFlags.BackupFlag}
     * @param backupNames A singleton array containing a backup name or {@code null} to use default
     */
    public static BackupManager getInstance(String packageName, int flags, @Nullable String[] backupNames) {
        if (instance == null) instance = new BackupManager(packageName, flags, backupNames);
        else if (!instance.packageName.equals(packageName)) {
            instance.close();
            instance = new BackupManager(packageName, flags, backupNames);
        }
        return instance;
    }

    @NonNull
    private String packageName;
    @NonNull
    private MetadataManager metadataManager;
    @NonNull
    private BackupFlags requestedFlags;
    @NonNull
    private BackupFiles[] backupFilesList;
    @NonNull
    private int[] userHandles;

    protected BackupManager(@NonNull String packageName, int flags, @Nullable String[] backupNames) {
        this.packageName = packageName;
        metadataManager = MetadataManager.getInstance(packageName);
        requestedFlags = new BackupFlags(flags);
        if (requestedFlags.backupAllUsers()) {
            userHandles = Users.getUsers();
        } else userHandles = new int[]{Users.getCurrentUser()};
        Log.e("BSM", "Users: " + Arrays.toString(userHandles));
        if (requestedFlags.backupMultiple()) {
            // Multiple backups requested
            if (backupNames == null) {
                // Create a singleton backupNames array with current time
                backupNames = new String[]{new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss",
                        Locale.ROOT).format(Calendar.getInstance().getTime())};
            }
            for (int i = 0; i < backupNames.length; ++i) {
                if (TextUtils.isDigitsOnly(backupNames[i])) {
                    // Append “Backup_” if backup name is a number
                    backupNames[i] = "Backup_" + backupNames[i];
                }
                // Replace spaces with underscore if exists
                backupNames[i] = backupNames[i].replace(' ', '_');
            }
            Log.e("BSM", "Backup names: " + Arrays.toString(backupNames));
        } else backupNames = null;  // Overwrite existing backup
        backupFilesList = new BackupFiles[userHandles.length];
        for (int i = 0; i < userHandles.length; ++i) {
            backupFilesList[i] = new BackupFiles(packageName, userHandles[i], backupNames);
        }
    }

    public boolean backup() {
        if (requestedFlags.isEmpty()) {
            Log.e("BSM - Backup", "Backup is requested without any flags.");
            return false;
        }
        for (int i = 0; i < userHandles.length; ++i) {
            BackupFiles.BackupFile[] backupFiles = requestedFlags.backupMultiple() ?
                    backupFilesList[i].getFreshBackupPaths() :
                    backupFilesList[i].getBackupPaths(true);
            for (BackupFiles.BackupFile backupFile : backupFiles) {
                if (!backup(backupFile, userHandles[i])) return false;
            }
        }
        return true;
    }

    public boolean backup(BackupFiles.BackupFile backupFile, int userHandle) {
        final MetadataManager.Metadata metadata;
        final PackageInfo packageInfo;
        final ApplicationInfo applicationInfo;
        try {
            packageInfo = ApiSupporter.getInstance(LocalServer.getInstance()).getPackageInfo(
                    packageName, PackageManager.GET_META_DATA | PackageUtils.flagSigningInfo,
                    userHandle);
            applicationInfo = packageInfo.applicationInfo;
            // Override existing metadata
            metadata = metadataManager.setupMetadata(packageInfo, userHandle, requestedFlags);
        } catch (Exception e) {
            Log.e("BSM - Backup", "Failed to setup metadata.", e);
            return false;
        }
        // Fail backup if the app has items in Android KeyStore
        // TODO(#82): Implement a clever mechanism to retrieve keys from Android keystore
        if (metadata.keyStore) {
            Log.e("BSM - Backup", "Cannot backup app as it has keystore items.");
            return false;
        }
        // Create a new temporary directory
        PrivilegedFile tmpBackupPath = backupFile.getBackupPath();
        // Backup source
        File dataAppPath = OsEnvironment.getDataAppDirectory();
        File sourceFile;
        if (requestedFlags.backupSource()) {
            sourceFile = new File(tmpBackupPath, SOURCE_PREFIX + BACKUP_FILE_SUFFIX + ".");
            String sourceDir = PackageUtils.getSourceDir(applicationInfo);
            if (dataAppPath.getAbsolutePath().equals(sourceDir)) {
                // Backup only the apk file (no split apk support for this type of apk)
                sourceDir = new File(sourceDir, metadata.apkName).getAbsolutePath();
            }
            File[] sourceFiles = TarUtils.create(TarUtils.TAR_GZIP, new File(sourceDir), sourceFile,
                    requestedFlags.backupOnlyApk() ? new String[]{"*.apk"} : null, null, null);
            if (sourceFiles.length == 0) {
                Log.e("BSM - Backup", "Source backup is requested but no source directory has been backed up.");
                return backupFile.cleanup();
            }
            metadata.sourceSha256Checksum = BackupUtils.getSha256Sum(sourceFiles);
        }
        // Backup data
        for (int i = 0; i < metadata.dataDirs.length; ++i) {
            sourceFile = new File(tmpBackupPath, DATA_PREFIX + i + BACKUP_FILE_SUFFIX + ".");
            File[] dataFiles = TarUtils.create(TarUtils.TAR_GZIP, new File(metadata.dataDirs[i]), sourceFile,
                    null, null, requestedFlags.excludeCache() ? CACHE_DIRS : null);
            if (dataFiles.length == 0) {
                Log.e("BSM - Backup", "Failed to backup data directory at " + metadata.dataDirs[i]);
                return backupFile.cleanup();
            }
            metadata.dataSha256Checksum[i] = BackupUtils.getSha256Sum(dataFiles);
        }
        // Export rules
        if (metadata.hasRules) {
            File rulesFile = new File(tmpBackupPath, RULES_TSV);
            try (OutputStream outputStream = new FileOutputStream(rulesFile);
                 ComponentsBlocker cb = ComponentsBlocker.getInstance(packageName)) {
                for (RulesStorageManager.Entry entry : cb.getAll()) {
                    // TODO: Do it in ComponentUtils
                    outputStream.write(String.format("%s\t%s\t%s\t%s\n", packageName, entry.name,
                            entry.type.name(), entry.extra).getBytes());
                }
            } catch (IOException e) {
                Log.e("BSM - Backup", "Rules backup is requested but encountered an error during fetching rules.");
                e.printStackTrace();
                return backupFile.cleanup();
            }
        }
        metadata.backupTime = System.currentTimeMillis();
        // Write modified metadata
        metadataManager.setMetadata(metadata);
        try {
            metadataManager.writeMetadata(backupFile);
        } catch (IOException | JSONException e) {
            Log.e("BSM - Backup", "Failed to write metadata due to " + e.toString());
            e.printStackTrace();
            return backupFile.cleanup();
        }
        // Replace current backup:
        // There's hardly any chance of getting a false here but checks are done anyway.
        if (backupFile.commit()) {
            return true;
        }
        Log.e("BSM - Backup", "Unknown error occurred. This message should never be printed.");
        return backupFile.cleanup();
    }

    public boolean restore() {
        if (requestedFlags.isEmpty()) {
            Log.e("BSM - Backup", "Backup is requested without any flags.");
            return false;
        }
        for (int i = 0; i < userHandles.length; ++i) {
            BackupFiles.BackupFile[] backupFiles = backupFilesList[i].getBackupPaths(false);
            // Only restore from the first backup file
            if (backupFiles.length > 0) {
                if (!restore(backupFiles[0], userHandles[i])) return false;
            }
        }
        return true;
    }

    @SuppressLint({"SdCardPath", "WrongConstant", "DefaultLocale"})
    public boolean restore(BackupFiles.BackupFile backupFile, int userHandle) {
        MetadataManager.Metadata metadata;
        try {
            metadataManager.readMetadata(backupFile);
            metadata = metadataManager.getMetadata();
        } catch (JSONException e) {
            Log.e("BSM - Restore", "Failed to read metadata. Possibly due to malformed json file.");
            e.printStackTrace();
            return false;
        }
        // Check user handle
        if (metadata.userHandle != userHandle) {
            Log.w("BSM - Restore", "Using different user handle.");
        }
        ApiSupporter apiSupporter = ApiSupporter.getInstance(LocalServer.getInstance());
        PrivilegedFile backupPath = backupFile.getBackupPath();
        // Get instruction set
        String instructionSet = VMRuntime.getInstructionSet(Build.SUPPORTED_ABIS[0]);
        File dataAppPath = OsEnvironment.getDataAppDirectory();
        // Get package info
        PackageInfo packageInfo = null;
        try {
            packageInfo = apiSupporter.getPackageInfo(packageName, PackageUtils.flagSigningInfo, userHandle);
        } catch (Exception ignore) {
        }
        boolean isInstalled = packageInfo != null;
        boolean noChecksumCheck = requestedFlags.noSignatureCheck();
        if (requestedFlags.backupSource()) {
            // Restoring apk requested
            boolean reinstallNeeded = false;
            File[] backupSourceFiles = getSourceFiles(backupPath);
            if (backupSourceFiles == null) {
                // No source backup found
                Log.e("BSM - Restore", "Source restore is requested but there are no source files.");
                return false;
            }
            if (isInstalled) {
                // Check signature if installed: Should be checked before calling this method if it is enabled
                List<String> certChecksum = Arrays.asList(PackageUtils.getSigningCertSha256Checksum(packageInfo));
                boolean isVerified = true;
                for (String checksum : metadata.certSha256Checksum) {
                    if (certChecksum.contains(checksum)) continue;
                    isVerified = false;
                    if (!noChecksumCheck) {
                        Log.e("BSM - Restore", "Signing info verification failed." +
                                "\nInstalled: " + certChecksum.toString() +
                                "\nBackup: " + Arrays.toString(metadata.certSha256Checksum));
                        return false;
                    }
                }
                if (!isVerified) {
                    // Signature verification failed but still here because signature check is disabled.
                    // The only way to restore is to reinstall the app
                    reinstallNeeded = true;
                } else if (PackageUtils.getVersionCode(packageInfo) > metadata.versionCode) {
                    // Installed package has higher version code. The only way to downgrade is to
                    // reinstall the package.
                    reinstallNeeded = true;
                }
            }
            if (!noChecksumCheck) {
                String checksum = BackupUtils.getSha256Sum(backupSourceFiles);
                if (!checksum.equals(metadata.sourceSha256Checksum)) {
                    Log.e("BSM - Restore", "Source file verification failed." +
                            "\nFiles: " + checksum +
                            "\nMetadata: " + metadata.sourceSha256Checksum);
                    return false;
                }
            }
            if (reinstallNeeded) {
                // A complete reinstall needed, first uninstall the package with -k and then install
                // the package again with -r
                if (!RunnerUtils.uninstallPackageWithoutData(packageName, userHandle).isSuccessful()) {
                    Log.e("BSM - Restore", "An uninstall was necessary but couldn't perform it.");
                    return false;
                }
            }
            // Setup package staging directory
            File packageStagingDirectory = PackageUtils.PACKAGE_STAGING_DIRECTORY;
            if (!RunnerUtils.fileExists(packageStagingDirectory)) {
                packageStagingDirectory = backupPath;
            }
            // Setup apk files, including split apk
            FreshFile baseApk = new FreshFile(packageStagingDirectory, metadata.apkName);
            final int splitCount = metadata.splitNames.length;
            String[] allApkNames = new String[splitCount + 1];
            FreshFile[] allApks = new FreshFile[splitCount + 1];
            allApks[0] = baseApk;
            allApkNames[0] = metadata.apkName;
            for (int i = 1; i < allApkNames.length; ++i) {
                allApkNames[i] = metadata.splitNames[i - 1];
                allApks[i] = new FreshFile(packageStagingDirectory, allApkNames[i]);
            }
            // Extract apk files to the package staging directory
            if (!TarUtils.extract(TarUtils.TAR_GZIP, backupSourceFiles, packageStagingDirectory, allApkNames, null)) {
                Log.e("BSM - Restore", "Failed to extract the apk file(s).");
                return false;
            }
            // A normal update will do it now
            PackageInstallerShell packageInstaller = new PackageInstallerShell(userHandle);
            if (!packageInstaller.installMultiple(allApks, packageName)) {
                Log.e("BSM - Restore", "A (re)install was necessary but couldn't perform it.");
                deleteFiles(allApks);
                return false;
            }
            deleteFiles(allApks);
            // Get package info
            try {
                packageInfo = apiSupporter.getPackageInfo(packageName, PackageUtils.flagSigningInfo, userHandle);
                isInstalled = true;
            } catch (Exception e) {
                Log.e("BSM - Restore", "Apparently the install wasn't complete in the previous section.");
                e.printStackTrace();
                return false;
            }
            File sourceDir = new File(PackageUtils.getSourceDir(packageInfo.applicationInfo));
            // Restore source directory only if instruction set is matched or app path is not /data/app
            // Or only apk restoring is requested
            if (!requestedFlags.backupOnlyApk()  // Only apk restoring is not requested
                    && metadata.instructionSet.equals(instructionSet)  // Instruction set matched
                    && !dataAppPath.equals(sourceDir)) {  // Path is not /data/app
                // Restore source: Get installed source directory and copy backups directly
                if (!TarUtils.extract(TarUtils.TAR_GZIP, backupSourceFiles, sourceDir, null, null)) {
                    Log.e("BSM - Restore", "Failed to restore the source files.");
                    return false;  // Failed to restore source files
                }
                // Restore permissions
                Runner.runCommand(new String[]{ "restorecon", "-R", sourceDir.getAbsolutePath()});
            } else {
                Log.e("BSM - Restore", "Skipped restoring files due to mismatched architecture or the path is /data/app or only apk restoring is requested.");
            }
        }
        if (requestedFlags.backupData()) {
            // Data restore is requested: Data restore is only possible if the app is actually
            // installed. So, check if it's installed first.
            if (!isInstalled) {
                Log.e("BSM - Restore", "Data restore is requested but the app isn't installed.");
                return false;
            }
            File[] dataFiles;
            if (!noChecksumCheck) {
                // Verify integrity of the data backups
                String checksum;
                for (int i = 0; i < metadata.dataDirs.length; ++i) {
                    dataFiles = getDataFiles(backupPath, i);
                    if (dataFiles == null) {
                        Log.e("BSM - Restore", "Data restore is requested but there are no data files for index " + i + ".");
                        return false;
                    }
                    checksum = BackupUtils.getSha256Sum(dataFiles);
                    if (!checksum.equals(metadata.dataSha256Checksum[i])) {
                        Log.e("BSM - Restore", "Data file verification failed for index " + i + "." +
                                "\nFiles: " + checksum +
                                "\nMetadata: " + metadata.dataSha256Checksum[i]);
                        return false;
                    }
                }
            }
            // Force stop app before restoring backups
            RunnerUtils.forceStopPackage(packageName, RunnerUtils.USER_ALL);
            // Restore backups
            String dataSource;
            for (int i = 0; i < metadata.dataDirs.length; ++i) {
                dataSource = Utils.replaceOnce(metadata.dataDirs[i], "/" + metadata.userHandle + "/", "/" + userHandle + "/");
                dataFiles = getDataFiles(backupPath, i);
                Pair<Integer, Integer> uidAndGid = null;
                if (RunnerUtils.fileExists(dataSource)) {
                    uidAndGid = BackupUtils.getUidAndGid(dataSource, packageInfo.applicationInfo.uid);
                }
                if (dataFiles == null) {
                    Log.e("BSM - Restore", "Data restore is requested but there are no data files for index " + i + ".");
                    return false;
                }
                // External storage checks
                if (dataSource.startsWith("/storage") || dataSource.startsWith("/sdcard")) {
                    // Skip if external data restore is not requested
                    if (!requestedFlags.backupExtData() && dataSource.contains(EXT_DATA))
                        continue;
                    // Skip if media/obb restore not requested
                    if (!requestedFlags.backupMediaObb() && (dataSource.contains(EXT_MEDIA)
                            || dataSource.contains(EXT_OBB))) continue;
                }
                // Fix problem accessing external directory in Android API < 23
                if (Build.VERSION.SDK_INT < 23) {
                    if (dataSource.contains("/storage/emulated/")) {
                        dataSource = dataSource.replace("/storage/emulated/", "/mnt/shell/emulated/");
                    }
                }
                // Create data folder if not exists
                PrivilegedFile dataSourceFile = new PrivilegedFile(dataSource);
                if (!dataSourceFile.exists()) {
                    if (!dataSourceFile.mkdirs()) {
                        Log.e("BSM - Restore", "Failed to create data folder for index " + i + ".");
                        return false;
                    }
                }
                // Extract data to the data directory
                if (!TarUtils.extract(TarUtils.TAR_GZIP, dataFiles, dataSourceFile,
                        null, requestedFlags.excludeCache() ? CACHE_DIRS : null)) {
                    Log.e("BSM - Restore", "Failed to restore data files for index " + i + ".");
                    return false;
                }
                // Fix UID and GID
                if (uidAndGid != null && !Runner.runCommand(String.format(Runner.TOYBOX + " chown -R %d:%d \"%s\"", uidAndGid.first, uidAndGid.second, dataSource)).isSuccessful()) {
                    Log.e("BSM - Restore", "Failed to get restore owner for index " + i + ".");
                    return false;
                }
                // Restore permissions
                Runner.runCommand(new String[]{"restorecon", "-R", dataSource});
            }
        }
        if (requestedFlags.backupRules()) {
            // Apply rules
            if (!isInstalled) {
                Log.e("BSM - Restore", "Rules restore is requested but the app isn't installed.");
                return false;
            }
            File rulesFile = new File(backupPath, RULES_TSV);
            if (rulesFile.exists()) {
                // FIXME: Import rules for user handle
                try (RulesImporter importer = new RulesImporter(Arrays.asList(RulesStorageManager.Type.values()))) {
                    importer.addRulesFromUri(Uri.fromFile(rulesFile));
                    importer.setPackagesToImport(Collections.singletonList(packageName));
                    importer.applyRules();
                } catch (IOException e) {
                    Log.e("BSM - Restore", "Failed to restore rules file.");
                    e.printStackTrace();
                    return false;
                }
            } else if (metadata.hasRules) {
                Log.e("BSM - Restore", "Rules file is missing.");
                return false;
            } // else there are no rules, just skip instead of returning false
        }
        return true;
    }

    public boolean deleteBackup() {
        for (int i = 0; i < userHandles.length; ++i) {
            BackupFiles.BackupFile[] backupFiles = backupFilesList[i].getBackupPaths(false);
            for (BackupFiles.BackupFile backupFile : backupFiles) {
                if (!backupFile.delete()) return false;
            }
        }
        return true;
    }

    @Override
    public void close() {
        metadataManager.close();
    }

    @Nullable
    private File[] getSourceFiles(@NonNull File backupPath) {
        return backupPath.listFiles((dir, name) -> name.startsWith(SOURCE_PREFIX));
    }

    @Nullable
    private File[] getDataFiles(@NonNull File backupPath, int index) {
        final String dataPrefix = DATA_PREFIX + index;
        return backupPath.listFiles((dir, name) -> name.startsWith(dataPrefix));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void deleteFiles(@NonNull FreshFile[] files) {
        for (FreshFile file : files) file.delete();
    }
}
