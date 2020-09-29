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
import android.content.pm.PermissionInfo;
import android.net.Uri;
import android.os.Build;
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
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerShell;
import io.github.muntashirakon.AppManager.logs.Log;
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

public class BackupManager {
    public static final String TAG = "BackupManager";

    private static final String EXT_DATA = "/Android/data/";
    private static final String EXT_MEDIA = "/Android/media/";
    private static final String EXT_OBB = "/Android/obb/";
    private static final String[] CACHE_DIRS = new String[]{"cache", "code_cache"};
    private static final String SOURCE_PREFIX = "source";
    private static final String DATA_PREFIX = "data";
    private static final String RULES_TSV = "rules.am.tsv";
    private static final String PERMS_TSV = "perms.am.tsv";

    @NonNull
    private static String getExt(@TarUtils.TarType String tarType) {
        if (TarUtils.TAR_BZIP2.equals(tarType)) return ".tar.bz2";
        else return ".tar.gz";
    }

    /**
     * @param packageName Package name of the app
     * @param flags       One or more of the {@link BackupFlags.BackupFlag}
     */
    @NonNull
    public static BackupManager getNewInstance(String packageName, int flags) {
        return new BackupManager(packageName, flags);
    }

    @NonNull
    private String packageName;
    @NonNull
    private MetadataManager metadataManager;
    @NonNull
    private BackupFlags requestedFlags;
    @NonNull
    private int[] userHandles;

    protected BackupManager(@NonNull String packageName, int flags) {
        this.packageName = packageName;
        metadataManager = MetadataManager.getNewInstance();
        requestedFlags = new BackupFlags(flags);
        if (requestedFlags.backupAllUsers()) {
            userHandles = Users.getUsersHandles();
        } else userHandles = new int[]{Users.getCurrentUserHandle()};
        Log.e(TAG, "Users: " + Arrays.toString(userHandles));
    }

    public boolean backup(@Nullable String[] backupNames) {
        if (requestedFlags.isEmpty()) {
            Log.e(BackupOp.TAG, "Backup is requested without any flags.");
            return false;
        }
        backupNames = getProcessedBackupNames(backupNames);
        BackupFiles[] backupFilesList = new BackupFiles[userHandles.length];
        for (int i = 0; i < userHandles.length; ++i) {
            backupFilesList[i] = new BackupFiles(packageName, userHandles[i], backupNames);
        }
        BackupOp backupOp;
        for (int i = 0; i < userHandles.length; ++i) {
            BackupFiles.BackupFile[] backupFiles = requestedFlags.backupMultiple() ?
                    backupFilesList[i].getFreshBackupPaths() :
                    backupFilesList[i].getBackupPaths(true);
            for (BackupFiles.BackupFile backupFile : backupFiles) {
                try {
                    backupOp = new BackupOp(backupFile, userHandles[i]);
                    if (!backupOp.runBackup()) return false;
                } catch (BackupException e) {
                    Log.e(BackupOp.TAG, e.getMessage(), e);
                    return false;
                }
            }
        }
        return true;
    }

    @Nullable
    private String[] getProcessedBackupNames(@Nullable String[] backupNames) {
        if (requestedFlags.backupMultiple()) {
            // Multiple backups requested
            if (backupNames == null) {
                // Create a singleton backupNames array with current time
                backupNames = new String[]{new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss",
                        Locale.ROOT).format(Calendar.getInstance().getTime())};
            }
            for (int i = 0; i < backupNames.length; ++i) {
                // Replace illegal characters
                backupNames[i] = backupNames[i].trim().replaceAll("[\\\\/?\"<>|\\s]+", "_");  // [\\/:?"<>|\s]
            }
            Log.e(TAG, "Backup names: " + Arrays.toString(backupNames));
            return backupNames;
        } else return null; // Overwrite existing backup
    }

    /**
     * Restore a single backup but could be for all users
     *
     * @param backupNames Backup names is a singleton array consisting of the full name of a backup.
     *                    Full name means backup name along with the user handle, ie., for user 0,
     *                    the full name of base backup is {@code 0} and the full name of another
     *                    backup {@code foo} is {@code 0_foo}.
     * @return {@code true} on success and {@code false} on failure
     */
    public boolean restore(@Nullable String[] backupNames) {
        if (requestedFlags.isEmpty()) {
            Log.e(RestoreOp.TAG, "Restore is requested without any flags.");
            return false;
        }
        if (backupNames != null && backupNames.length != 1) {
            Log.e(RestoreOp.TAG, "Restore is requested from more than one backups!");
            return false;
        }
        int backupUserHandle = -1;
        if (backupNames != null) {
            // Strip userHandle from backup name
            String backupName = BackupUtils.getShortBackupName(backupNames[0]);
            backupUserHandle = BackupUtils.getUserHandleFromBackupName(backupNames[0]);
            if (backupName != null) {
                // There's a backup name, not just user handle
                backupNames = new String[]{backupName};
            } else {
                // There's only user handle. Set backupNames to null to restore base backup goes
                // by the name
                backupNames = null;
            }
        }
        for (int userHandle : userHandles) {
            // Set backup userHandle to the userHandle we're working with.
            // This value is only set if backupNames is null or it consisted of only user handle
            if (backupUserHandle == -1) backupUserHandle = userHandle;
            BackupFiles backupFiles = new BackupFiles(packageName, backupUserHandle, backupNames);
            BackupFiles.BackupFile[] backupFileList = backupFiles.getBackupPaths(false);
            // Only restore from the first backup though we shouldn't have more than one backup.
            if (backupFileList.length > 0) {
                if (backupFileList.length > 1) {
                    Log.w(RestoreOp.TAG, "More than one backups found!");
                }
                try {
                    RestoreOp restoreOp = new RestoreOp(backupFileList[0], userHandle);
                    if (!restoreOp.runRestore()) return false;
                } catch (BackupException e) {
                    e.printStackTrace();
                    Log.e(RestoreOp.TAG, e.getMessage(), e);
                    return false;
                }
            } else {
                Log.e(RestoreOp.TAG, "No backups found.");
            }
        }
        return true;
    }

    public boolean deleteBackup(@Nullable String[] backupNames) {
        if (backupNames == null) {
            // No backup names supplied, use user handle
            for (int userHandle : userHandles) {
                BackupFiles backupFiles = new BackupFiles(packageName, userHandle, null);
                BackupFiles.BackupFile[] backupFileList = backupFiles.getBackupPaths(false);
                for (BackupFiles.BackupFile backupFile : backupFileList) {
                    if (!backupFile.delete()) return false;
                }
            }
        } else {
            // backupNames is not null but that doesn't mean that it's not empty,
            // requested for only single backups
            for (String backupName : backupNames) {
                new BackupFiles.BackupFile(
                        new PrivilegedFile(BackupFiles.getPackagePath(packageName), backupName),
                        false
                ).delete();
            }
        }
        return true;
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

    class BackupOp {
        static final String TAG = "BackupOp";

        @NonNull
        private final BackupFiles.BackupFile backupFile;
        @NonNull
        private final MetadataManager metadataManager;
        @NonNull
        private final BackupFlags backupFlags;
        @NonNull
        private final MetadataManager.Metadata metadata;
        @NonNull
        private final PackageInfo packageInfo;
        @NonNull
        private final ApplicationInfo applicationInfo;
        @NonNull
        private final PrivilegedFile tmpBackupPath;
        private final int userHandle;

        BackupOp(@NonNull BackupFiles.BackupFile backupFile, int userHandle) throws BackupException {
            this.backupFile = backupFile;
            this.userHandle = userHandle;
            this.metadataManager = BackupManager.this.metadataManager;
            this.backupFlags = BackupManager.this.requestedFlags;
            this.tmpBackupPath = backupFile.getBackupPath();
            try {
                packageInfo = ApiSupporter.getInstance(LocalServer.getInstance()).getPackageInfo(
                        packageName, PackageManager.GET_META_DATA | PackageUtils.flagSigningInfo
                                | PackageManager.GET_PERMISSIONS, userHandle);
                this.applicationInfo = packageInfo.applicationInfo;
                // Override existing metadata
                this.metadata = metadataManager.setupMetadata(packageInfo, userHandle, backupFlags);
            } catch (Exception e) {
                backupFile.cleanup();
                throw new BackupException("Failed to setup metadata.", e);
            }
        }

        boolean runBackup() {
            // Fail backup if the app has items in Android KeyStore
            // TODO(#82): Implement a clever mechanism to retrieve keys from Android keystore
            if (backupFlags.backupData() && metadata.keyStore) {
                Log.e(TAG, "Cannot backup app as it has keystore items.");
                return backupFile.cleanup();
            }
            try {
                // Backup source
                if (backupFlags.backupSource()) backupSource();
                // Backup data
                if (backupFlags.backupData()) backupData();
                // Backup permissions
                if (backupFlags.backupPermissions()) backupPermissions();
                // Export rules
                if (metadata.hasRules) backupRules();
            } catch (BackupException e) {
                Log.e(TAG, e.getMessage(), e);
                return backupFile.cleanup();
            }
            // Set backup time
            metadata.backupTime = System.currentTimeMillis();
            // Write modified metadata
            metadataManager.setMetadata(metadata);
            try {
                metadataManager.writeMetadata(backupFile);
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Failed to write metadata.", e);
                return backupFile.cleanup();
            }
            // Replace current backup:
            // There's hardly any chance of getting a false here but checks are done anyway.
            if (backupFile.commit()) {
                return true;
            }
            Log.e(TAG, "Unknown error occurred. This message should never be printed.");
            return backupFile.cleanup();
        }

        private void backupSource() throws BackupException {
            final File dataAppPath = OsEnvironment.getDataAppDirectory();
            final File sourceFile = new File(tmpBackupPath, SOURCE_PREFIX + getExt(metadata.tarType) + ".");
            String sourceDir = PackageUtils.getSourceDir(applicationInfo);
            if (dataAppPath.getAbsolutePath().equals(sourceDir)) {
                // Backup only the apk file (no split apk support for this type of apk)
                sourceDir = new File(sourceDir, metadata.apkName).getAbsolutePath();
            }
            File[] sourceFiles = TarUtils.create(metadata.tarType, new File(sourceDir), sourceFile,
                    backupFlags.backupOnlyApk() ? new String[]{"*.apk"} : null, null, null);
            if (sourceFiles.length == 0) {
                throw new BackupException("Source backup is requested but no source directory has been backed up.");
            }
            metadata.sourceSha256Checksum = BackupUtils.getSha256Sum(sourceFiles);
        }

        private void backupData() throws BackupException {
            File sourceFile;
            for (int i = 0; i < metadata.dataDirs.length; ++i) {
                sourceFile = new File(tmpBackupPath, DATA_PREFIX + i + getExt(metadata.tarType) + ".");
                File[] dataFiles = TarUtils.create(metadata.tarType, new File(metadata.dataDirs[i]), sourceFile,
                        null, null, backupFlags.excludeCache() ? CACHE_DIRS : null);
                if (dataFiles.length == 0) {
                    throw new BackupException("Failed to backup data directory at " + metadata.dataDirs[i]);
                }
                metadata.dataSha256Checksum[i] = BackupUtils.getSha256Sum(dataFiles);
            }
        }

        private void backupPermissions() throws BackupException {
            File permsFile = new File(tmpBackupPath, PERMS_TSV);
            String[] permissions = packageInfo.requestedPermissions;
            int[] permissionFlags = packageInfo.requestedPermissionsFlags;
            if (permissions == null) return;
            PackageManager pm = AppManager.getContext().getPackageManager();
            PermissionInfo info;
            int basePermissionType;
            int protectionLevels;
            try (OutputStream outputStream = new FileOutputStream(permsFile)) {
                for (int i = 0; i < permissions.length; ++i) {
                    try {
                        info = pm.getPermissionInfo(permissions[i], 0);
                        basePermissionType = PackageUtils.getBasePermissionType(info);
                        protectionLevels = PackageUtils.getProtectionLevel(info);
                        if (basePermissionType != PermissionInfo.PROTECTION_DANGEROUS
                                && (protectionLevels & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) == 0) {
                            // Don't include permissions that are neither dangerous nor development
                            continue;
                        }
                        outputStream.write(String.format("%s\t%s\t%s\t%s\n", packageName, permissions[i],
                                RulesStorageManager.Type.PERMISSION, (permissionFlags[i]
                                        & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0).getBytes());
                    } catch (PackageManager.NameNotFoundException ignore) {
                    }
                }
            } catch (IOException e) {
                throw new BackupException("Error during creating permission file.", e);
            }
        }

        private void backupRules() throws BackupException {
            File rulesFile = new File(tmpBackupPath, RULES_TSV);
            try (OutputStream outputStream = new FileOutputStream(rulesFile);
                 ComponentsBlocker cb = ComponentsBlocker.getInstance(packageName, userHandle)) {
                for (RulesStorageManager.Entry entry : cb.getAll()) {
                    // TODO: Do it in ComponentUtils
                    outputStream.write(String.format("%s\t%s\t%s\t%s\n", packageName, entry.name,
                            entry.type.name(), entry.extra).getBytes());
                }
            } catch (IOException e) {
                throw new BackupException("Rules backup is requested but encountered an error during fetching rules.", e);
            }
        }
    }

    class RestoreOp {
        static final String TAG = "RestoreOp";

        @NonNull
        private final BackupFlags backupFlags;
        @NonNull
        private final BackupFlags requestedFlags;
        @NonNull
        private final MetadataManager.Metadata metadata;
        @NonNull
        private final PrivilegedFile backupPath;
        @NonNull
        private final ApiSupporter apiSupporter;
        @Nullable
        private PackageInfo packageInfo;
        private final int userHandle;
        private boolean isInstalled;

        RestoreOp(@NonNull BackupFiles.BackupFile backupFile, int userHandle) throws BackupException {
            this.requestedFlags = BackupManager.this.requestedFlags;
            this.backupPath = backupFile.getBackupPath();
            this.userHandle = userHandle;
            this.apiSupporter = ApiSupporter.getInstance(LocalServer.getInstance());
            try {
                metadataManager.readMetadata(backupFile);
                metadata = metadataManager.getMetadata();
                backupFlags = metadata.flags;
            } catch (JSONException e) {
                throw new BackupException("Failed to read metadata. Possibly due to malformed json file.", e);
            }
            // Check user handle
            if (metadata.userHandle != userHandle) {
                Log.w(TAG, "Using different user handle.");
            }
            // Get package info
            packageInfo = null;
            try {
                packageInfo = apiSupporter.getPackageInfo(packageName, PackageUtils.flagSigningInfo, userHandle);
            } catch (Exception ignore) {
            }
            isInstalled = packageInfo != null;
        }

        boolean runRestore() {
            try {
                if (requestedFlags.backupSource()) restoreSource();
                if (requestedFlags.backupData()) restoreData();
                if (requestedFlags.backupPermissions()) restorePermissions();
                if (requestedFlags.backupRules()) restoreRules();
            } catch (BackupException e) {
                Log.e(TAG, e.getMessage(), e);
                return false;
            }
            return true;
        }

        private void restoreSource() throws BackupException {
            if (!backupFlags.backupSource()) {
                throw new BackupException("Source restore is requested but backup doesn't contain any source files.");
            }
            File[] backupSourceFiles = getSourceFiles(backupPath);
            if (backupSourceFiles == null) {
                // No source backup found
                throw new BackupException("Source restore is requested but there are no source files.");
            }
            boolean reinstallNeeded = false;
            if (packageInfo != null) {
                // Check signature of the installed app
                List<String> certChecksum = Arrays.asList(PackageUtils.getSigningCertSha256Checksum(packageInfo));
                boolean isVerified = true;
                for (String checksum : metadata.certSha256Checksum) {
                    if (certChecksum.contains(checksum)) continue;
                    isVerified = false;
                    if (!requestedFlags.skipSignatureCheck()) {
                        throw new BackupException("Signing info verification failed." +
                                "\nInstalled: " + certChecksum.toString() +
                                "\nBackup: " + Arrays.toString(metadata.certSha256Checksum));
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
            if (!requestedFlags.skipSignatureCheck()) {
                String checksum = BackupUtils.getSha256Sum(backupSourceFiles);
                if (!checksum.equals(metadata.sourceSha256Checksum)) {
                    throw new BackupException("Source file verification failed." +
                            "\nFiles: " + checksum +
                            "\nMetadata: " + metadata.sourceSha256Checksum);
                }
            }
            if (reinstallNeeded) {
                // A complete reinstall needed, first uninstall the package with -k and then install
                // the package again with -r
                if (!RunnerUtils.uninstallPackageWithoutData(packageName, userHandle).isSuccessful()) {
                    throw new BackupException("An uninstall was necessary but couldn't perform it.");
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
            if (!TarUtils.extract(metadata.tarType, backupSourceFiles, packageStagingDirectory, allApkNames, null)) {
                throw new BackupException("Failed to extract the apk file(s).");
            }
            // A normal update will do it now
            PackageInstallerShell packageInstaller = new PackageInstallerShell(userHandle);
            if (!packageInstaller.install(allApks, packageName)) {
                deleteFiles(allApks);
                throw new BackupException("A (re)install was necessary but couldn't perform it.");
            }
            deleteFiles(allApks);  // Clean up apk files
            // Get package info, again
            try {
                packageInfo = apiSupporter.getPackageInfo(packageName, PackageUtils.flagSigningInfo, userHandle);
                isInstalled = true;
            } catch (Exception e) {
                throw new BackupException("Apparently the install wasn't complete in the previous section.", e);
            }
            // Get instruction set
            // TODO(10/9/20): Investigate support for unmatched instruction set as well
            final String instructionSet = VMRuntime.getInstructionSet(Build.SUPPORTED_ABIS[0]);
            final File dataAppPath = OsEnvironment.getDataAppDirectory();
            final File sourceDir = new File(PackageUtils.getSourceDir(packageInfo.applicationInfo));
            // Restore source directory only if instruction set is matched or app path is not /data/app
            // Or only apk restoring is requested
            if (!requestedFlags.backupOnlyApk()  // Only apk restoring is not requested
                    && metadata.instructionSet.equals(instructionSet)  // Instruction set matched
                    && !dataAppPath.equals(sourceDir)) {  // Path is not /data/app
                // Restore source: Get installed source directory and copy backups directly
                if (!TarUtils.extract(metadata.tarType, backupSourceFiles, sourceDir, null, null)) {
                    throw new BackupException("Failed to restore the source files.");
                }
                // Restore permissions
                Runner.runCommand(new String[]{"restorecon", "-R", sourceDir.getAbsolutePath()});
            } else {
                Log.w(TAG, "Skipped restoring files due to mismatched architecture or the path is /data/app or only apk restoring is requested.");
            }
        }

        @SuppressLint("SdCardPath")
        private void restoreData() throws BackupException {
            // Data restore is requested: Data restore is only possible if the app is actually
            // installed. So, check if it's installed first.
            if (packageInfo == null) {
                throw new BackupException("Data restore is requested but the app isn't installed.");
            }
            File[] dataFiles;
            if (!requestedFlags.skipSignatureCheck()) {
                // Verify integrity of the data backups
                String checksum;
                for (int i = 0; i < metadata.dataDirs.length; ++i) {
                    dataFiles = getDataFiles(backupPath, i);
                    if (dataFiles == null) {
                        throw new BackupException("Data restore is requested but there are no data files for index " + i + ".");
                    }
                    checksum = BackupUtils.getSha256Sum(dataFiles);
                    if (!checksum.equals(metadata.dataSha256Checksum[i])) {
                        throw new BackupException("Data file verification failed for index " + i + "." +
                                "\nFiles: " + checksum +
                                "\nMetadata: " + metadata.dataSha256Checksum[i]);
                    }
                }
            }
            // Force stop app
            RunnerUtils.forceStopPackage(packageName, RunnerUtils.USER_ALL);
            // Clear app data
            RunnerUtils.clearPackageData(packageName, userHandle);
            // Restore backups
            String dataSource;
            boolean isExternal;
            for (int i = 0; i < metadata.dataDirs.length; ++i) {
                dataSource = Utils.replaceOnce(metadata.dataDirs[i], "/" + metadata.userHandle + "/", "/" + userHandle + "/");
                dataFiles = getDataFiles(backupPath, i);
                Pair<Integer, Integer> uidAndGid = null;
                if (RunnerUtils.fileExists(dataSource)) {
                    uidAndGid = BackupUtils.getUidAndGid(dataSource, packageInfo.applicationInfo.uid);
                }
                if (dataFiles == null) {
                    throw new BackupException("Data restore is requested but there are no data files for index " + i + ".");
                }
                // External storage checks
                if (dataSource.startsWith("/storage") || dataSource.startsWith("/sdcard")) {
                    isExternal = true;
                    // Skip if external data restore is not requested
                    if (!requestedFlags.backupExtData() && dataSource.contains(EXT_DATA))
                        continue;
                    // Skip if media/obb restore not requested
                    if (!requestedFlags.backupMediaObb() && (dataSource.contains(EXT_MEDIA)
                            || dataSource.contains(EXT_OBB))) continue;
                } else isExternal = false;
                // Fix problem accessing external directory in Android API < 23
                if (Build.VERSION.SDK_INT < 23) {
                    if (dataSource.contains("/storage/emulated/")) {
                        dataSource = dataSource.replace("/storage/emulated/", "/mnt/shell/emulated/");
                    }
                }
                // Create data folder if not exists
                PrivilegedFile dataSourceFile = new PrivilegedFile(dataSource);
                if (!dataSourceFile.exists()) {
                    // FIXME(10/9/20): Check if the media is mounted and readable before running
                    //  mkdir, otherwise it may create a folder to a path that will be gone
                    //  after a restart
                    if (!dataSourceFile.mkdirs()) {
                        throw new BackupException("Failed to create data folder for index " + i + ".");
                    }
                }
                // Extract data to the data directory
                if (!TarUtils.extract(metadata.tarType, dataFiles, dataSourceFile,
                        null, requestedFlags.excludeCache() ? CACHE_DIRS : null)) {
                    throw new BackupException("Failed to restore data files for index " + i + ".");
                }
                // Fix UID and GID
                if (uidAndGid != null && !Runner.runCommand(String.format(Runner.TOYBOX + " chown -R %d:%d \"%s\"", uidAndGid.first, uidAndGid.second, dataSource)).isSuccessful()) {
                    throw new BackupException("Failed to restore ownership info for index " + i + ".");
                }
                // Restore permissions
                if (!isExternal) Runner.runCommand(new String[]{"restorecon", "-R", dataSource});
            }
        }

        private void restorePermissions() throws BackupException {
            // Apply rules
            if (!isInstalled) {
                throw new BackupException("Permission restore is requested but the app isn't installed.");
            }
            File permsFile = new File(backupPath, PERMS_TSV);
            if (permsFile.exists()) {
                try (RulesImporter importer = new RulesImporter(Arrays.asList(RulesStorageManager.Type.values()))) {
                    importer.addRulesFromUri(Uri.fromFile(permsFile), userHandle);
                    importer.setPackagesToImport(Collections.singletonList(packageName));
                    importer.applyRules();
                } catch (IOException e) {
                    throw new BackupException("Failed to restore permissions.", e);
                }
            } // else there are no permissions, just skip
        }

        private void restoreRules() throws BackupException {
            // Apply rules
            if (!isInstalled) {
                throw new BackupException("Rules restore is requested but the app isn't installed.");
            }
            File rulesFile = new File(backupPath, RULES_TSV);
            if (rulesFile.exists()) {
                try (RulesImporter importer = new RulesImporter(Arrays.asList(RulesStorageManager.Type.values()))) {
                    importer.addRulesFromUri(Uri.fromFile(rulesFile), userHandle);
                    importer.setPackagesToImport(Collections.singletonList(packageName));
                    importer.applyRules();
                } catch (IOException e) {
                    throw new BackupException("Failed to restore rules file.", e);
                }
            } else if (metadata.hasRules) {
                throw new BackupException("Rules file is missing.");
            } // else there are no rules, just skip
        }
    }

    public static class BackupException extends Throwable {
        @NonNull
        private final String detailMessage;

        public BackupException(@NonNull String message) {
            super(message);
            this.detailMessage = message;
        }

        public BackupException(@NonNull String message, @NonNull Throwable cause) {
            super(message, cause);
            detailMessage = message;
        }

        @NonNull
        @Override
        public String getMessage() {
            return detailMessage;
        }
    }
}
