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
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerShell;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.rules.RulesImporter;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.RootShellRunner;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.types.FreshFile;
import io.github.muntashirakon.AppManager.types.PrivilegedFile;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class BackupStorageManager implements AutoCloseable {
    private static final String CMD_SOURCE_DIR_BACKUP = "cd \"%s\" && tar -czf - . | split -b 1G - \"%s\"";  // src, dest
    private static final String CMD_SOURCE_DIR_APK_ONLY_BACKUP = "cd \"%s\" && tar -czf - ./*.apk | split -b 1G - \"%s\"";  // src, dest
    private static final String CMD_DATA_DIR_BACKUP = "tar -czf - \"%s\" %s | split -b 1G - \"%s\"";  // src, exclude, dest
    private static final String SOURCE_PREFIX = "source";
    private static final String DATA_PREFIX = "data";
    private static final String BACKUP_FILE_SUFFIX = ".tar.gz";
    private static final String RULES_TSV = "rules.am.tsv";

    private static BackupStorageManager instance;

    /**
     * @param packageName Package name of the app
     * @param flags       One or more of the {@link BackupFlags.BackupFlag}
     * @param backupNames A singleton array containing a backup name or {@code null} to use default
     */
    public static BackupStorageManager getInstance(String packageName, int flags, @Nullable String[] backupNames) {
        if (instance == null) instance = new BackupStorageManager(packageName, flags, backupNames);
        else if (!instance.packageName.equals(packageName)) {
            instance.close();
            instance = new BackupStorageManager(packageName, flags, backupNames);
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

    protected BackupStorageManager(@NonNull String packageName, int flags, @Nullable String[] backupNames) {
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
            BackupFiles.BackupFile[] backupFiles = backupFilesList[i].getFreshBackupPaths();
            for (BackupFiles.BackupFile backupFile : backupFiles) {
                if (!backup(backupFile, userHandles[i])) return false;
            }
        }
        return true;
    }

    public boolean backup(BackupFiles.BackupFile backupFile, int userHandle) {
        MetadataManager.Metadata metadata;
        try {
            // Override existing metadata
            metadata = metadataManager.setupMetadata(userHandle, requestedFlags);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("BSM - Backup", "Failed to setup metadata.");
            e.printStackTrace();
            return false;
        }
        // Create a new temporary directory
        PrivilegedFile tmpBackupPath = backupFile.getBackupPath();
        // Backup source
        File dataAppPath = OsEnvironment.getDataAppDirectory();
        File sourceFile = new File(tmpBackupPath, SOURCE_PREFIX + BACKUP_FILE_SUFFIX + ".");
        if (!metadata.sourceDir.equals("")) {
            String sourceDir = metadata.sourceDir;
            if (dataAppPath.getAbsolutePath().equals(sourceDir)) {
                // Backup only the apk file (no split apk support for this type of apk)
                sourceDir = new File(sourceDir, metadata.apkName).getAbsolutePath();
            }
            String command = String.format(requestedFlags.backupOnlyApk() ? CMD_SOURCE_DIR_APK_ONLY_BACKUP : CMD_SOURCE_DIR_BACKUP, sourceDir, sourceFile.getAbsolutePath());
            if (!RootShellRunner.runCommand(command).isSuccessful()) {
                Log.e("BSM - Backup", "Failed to backup source directory. " + RootShellRunner.getLastResult().getOutput());
                return backupFile.cleanup();
            }
            File[] sourceFiles = getSourceFiles(tmpBackupPath);
            if (sourceFiles == null) {
                Log.e("BSM - Backup", "Source backup is requested but no source directory has been backed up.");
                return backupFile.cleanup();
            }
            metadata.sourceDirSha256Checksum = getSha256Sum(sourceFiles);
        }
        // Backup data
        for (int i = 0; i < metadata.dataDirs.length; ++i) {
            sourceFile = new File(tmpBackupPath, DATA_PREFIX + i + BACKUP_FILE_SUFFIX + ".");
            StringBuilder excludePaths = new StringBuilder();
            if (requestedFlags.excludeCache()) {
                excludePaths.append(" --exclude=\"").append(metadata.dataDirs[i].substring(1))
                        .append(File.separatorChar).append("cache").append("\"");
                excludePaths.append(" --exclude=\"").append(metadata.dataDirs[i].substring(1))
                        .append(File.separatorChar).append("code_cache").append("\"");
            }
            if (!RootShellRunner.runCommand(String.format(CMD_DATA_DIR_BACKUP,
                    metadata.dataDirs[i], excludePaths.toString(), sourceFile.getAbsolutePath()))
                    .isSuccessful()) {
                Log.e("BSM - Backup", "Failed to backup data directory at " + metadata.dataDirs[i]);
                return backupFile.cleanup();
            }
            File[] dataFiles = getDataFiles(tmpBackupPath, i);
            if (dataFiles == null) {
                Log.e("BSM - Backup", "Data backup is requested but no data directory has been backed up.");
                return backupFile.cleanup();
            }
            metadata.dataDirsSha256Checksum[i] = getSha256Sum(dataFiles);
        }
        // Export rules
        if (metadata.hasRules) {
            File rulesFile = new File(tmpBackupPath, RULES_TSV);
            try (OutputStream outputStream = new FileOutputStream(rulesFile);
                 ComponentsBlocker cb = ComponentsBlocker.getInstance(packageName)) {
                for (RulesStorageManager.Entry entry : cb.getAll()) {
                    // TODO: Do it in ComponentUtils
                    outputStream.write(String.format("%s\t%s\t%s\t%s\n", packageName, entry.name, entry.type.name(), entry.extra).getBytes());
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
            BackupFiles.BackupFile[] backupFiles = backupFilesList[i].getBackupPaths();
            for (BackupFiles.BackupFile backupFile : backupFiles) {
                if (!restore(backupFile, userHandles[i])) return false;
            }
        }
        return true;
    }

    @SuppressLint({"SdCardPath", "WrongConstant", "DefaultLocale"})
    public boolean restore(BackupFiles.BackupFile backupFile, int userHandle) {
        if (requestedFlags.isEmpty()) {
            Log.e("BSM - Restore", "Restore is requested without any flags.");
            return false;
        }
        MetadataManager.Metadata metadata;
        try {
            metadataManager.readMetadata(backupFile);
            metadata = metadataManager.getMetadata();
        } catch (JSONException e) {
            Log.e("BSM - Restore", "Failed to read metadata. Possibly due to malformed json file.");
            e.printStackTrace();
            return false;
        }
        PrivilegedFile backupPath = backupFile.getBackupPath();
        // Get instruction set
        String instructionSet = VMRuntime.getInstructionSet(Build.SUPPORTED_ABIS[0]);
        File dataAppPath = OsEnvironment.getDataAppDirectory();
        // Get package info
        PackageInfo packageInfo = null;
        try {
            packageInfo = AppManager.getContext().getPackageManager().getPackageInfo(packageName, PackageUtils.flagSigningInfo);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
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
            StringBuilder cmdSources = new StringBuilder();
            for (File file : backupSourceFiles)
                cmdSources.append(" \"").append(file.getAbsolutePath()).append("\"");
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
                // Check integrity of source
                if (TextUtils.isEmpty(metadata.sourceDir)) {
                    Log.e("BSM - Restore", "Source restore is requested but source_dir metadata is empty.");
                    return false;
                }
                String checksum = getSha256Sum(backupSourceFiles);
                if (!checksum.equals(metadata.sourceDirSha256Checksum)) {
                    Log.e("BSM - Restore", "Source file verification failed." +
                            "\nFiles: " + checksum +
                            "\nMetadata: " + metadata.sourceDirSha256Checksum);
                    return false;
                }
            }
            if (reinstallNeeded) {
                // A complete reinstall needed, first uninstall the package with -k and then install
                // the package again with -r
                if (!RunnerUtils.uninstallPackageWithoutData(packageName).isSuccessful()) {
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
            String[] splitApkNames = new String[metadata.splitSources.length];
            FreshFile[] allApks = new FreshFile[splitApkNames.length + 1];
            allApks[0] = baseApk;
            for (int i = 0; i < splitApkNames.length; ++i) {
                splitApkNames[i] = new File(metadata.splitSources[i]).getName();
                allApks[i + 1] = new FreshFile(packageStagingDirectory, splitApkNames[i]);
            }
            // Extract apk files to the package staging directory
            StringBuilder sb = new StringBuilder("./").append(metadata.apkName);
            for (String splitName : splitApkNames) sb.append(" ./").append(splitName);
            if (!RootShellRunner.runCommand(String.format("cat %s | tar -xzf - %s -C \"%s\"",
                    cmdSources, sb.toString(), packageStagingDirectory.getAbsolutePath())).isSuccessful()) {
                Log.e("BSM - Restore", "Failed to extract the apk file(s).");
                return false;
            }
            // A normal update will do it now
            if (!PackageInstallerShell.getInstance().installMultiple(allApks, packageName)) {
                Log.e("BSM - Restore", "A (re)install was necessary but couldn't perform it.");
                deleteFiles(allApks);
                return false;
            }
            deleteFiles(allApks);
            // Get package info
            try {
                packageInfo = AppManager.getContext().getPackageManager().getPackageInfo(packageName, PackageUtils.flagSigningInfo);
                isInstalled = packageInfo != null;
            } catch (PackageManager.NameNotFoundException e) {
                Log.e("BSM - Restore", "Apparently the install wasn't complete in the previous section.");
                e.printStackTrace();
                return false;
            }
            if (packageInfo == null) {
                Log.e("BSM - Restore", "Apparently the install wasn't complete in the previous section.");
                return false;
            }
            String sourceDir = new File(packageInfo.applicationInfo.publicSourceDir).getParent();
            // Restore source directory only if instruction set is matched or app path is not /data/app
            // Or only apk restoring is requested
            if (!requestedFlags.backupOnlyApk()  // Only apk restoring is not requested
                    && metadata.instructionSet.equals(instructionSet)  // Instruction set matched
                    && !dataAppPath.getAbsolutePath().equals(sourceDir)) {  // Path is not /data/app
                // Restore source: Get installed source directory and copy backups directly
                if (!RootShellRunner.runCommand(String.format("cat %s | tar -xzf - -C \"%s\"",
                        cmdSources, sourceDir)).isSuccessful()) {
                    Log.e("BSM - Restore", "Failed to restore the source files.");
                    return false;  // Failed to restore source files
                }
                // Restore permissions
                RootShellRunner.runCommand(String.format("restorecon -R \"%s\"", sourceDir));
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
                    checksum = getSha256Sum(dataFiles);
                    if (!checksum.equals(metadata.dataDirsSha256Checksum[i])) {
                        Log.e("BSM - Restore", "Data file verification failed for index " + i + "." +
                                "\nFiles: " + checksum +
                                "\nMetadata: " + metadata.dataDirsSha256Checksum[i]);
                        return false;
                    }
                }
            }
            // Force stop app before restoring backups
            RunnerUtils.forceStopPackage(packageName);
            // Restore backups
            String dataSource;
            for (int i = 0; i < metadata.dataDirs.length; ++i) {
                dataSource = metadata.dataDirs[i];
                dataFiles = getDataFiles(backupPath, i);
                Pair<Integer, Integer> uidAndGid = null;
                if (RunnerUtils.fileExists(dataSource)) {
                    uidAndGid = getUidAndGid(dataSource, packageInfo.applicationInfo.uid);
                }
                if (dataFiles == null) {
                    Log.e("BSM - Restore", "Data restore is requested but there are no data files for index " + i + ".");
                    return false;
                }
                StringBuilder cmdData = new StringBuilder();
                // FIXME: Fix API 23 external storage issue
                for (File file : dataFiles) {
                    cmdData.append(" \"").append(file.getAbsolutePath()).append("\"");
                }
                // Skip restoring external data if requested
                if (!requestedFlags.backupExtData() && dataSource.startsWith("/storage") && dataSource.startsWith("/sdcard"))
                    continue;
                if (!RootShellRunner.runCommand(String.format("cat %s | tar -xzf - -C /", cmdData.toString())).isSuccessful()) {
                    Log.e("BSM - Restore", "Failed to restore data files for index " + i + ".");
                    return false;
                }
                if (requestedFlags.excludeCache()) {
                    // Clear cache if exists: return value is not important for us
                    RootShellRunner.runCommand(String.format("rm -rf %s/cache %s/code_cache", dataSource, dataSource));
                }
                // Fix UID and GID
                if (uidAndGid != null && !RootShellRunner.runCommand(String.format("chown -R %d:%d \"%s\"", uidAndGid.first, uidAndGid.second, dataSource)).isSuccessful()) {
                    Log.e("BSM - Restore", "Failed to get restore owner for index " + i + ".");
                    return false;
                }
                // Restore permissions
                RootShellRunner.runCommand(String.format("restorecon -R \"%s\"", dataSource));
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
            BackupFiles.BackupFile[] backupFiles = backupFilesList[i].getFreshBackupPaths();
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

    @NonNull
    public Pair<Integer, Integer> getUidAndGid(String filepath, int uid) {
        // Default UID and GID should be the same as the kernel user ID, and will fallback to it
        // if the stat command fails
        Pair<Integer, Integer> defaultUidGid = new Pair<>(uid, uid);
        Runner.Result result = RootShellRunner.runCommand(String.format("stat -c \"%%u %%g\" \"%s\"", filepath));
        if (!result.isSuccessful()) return defaultUidGid;
        String[] uidGid = result.getOutput().split(" ");
        if (uidGid.length != 2) return defaultUidGid;
        // Fix for Magisk bug
        if (uidGid[0].equals("0")) return defaultUidGid;
        try {
            // There could be other underlying bugs as well
            return new Pair<>(Integer.parseInt(uidGid[0]), Integer.parseInt(uidGid[1]));
        } catch (Exception e) {
            return defaultUidGid;
        }
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

    @NonNull
    private String getSha256Sum(@NonNull File[] files) {
        if (files.length == 1) return PackageUtils.getSha256Checksum(files[0]);

        StringBuilder checksums = new StringBuilder();
        for (File file : files) {
            String checksum = PackageUtils.getSha256Checksum(file);
            checksums.append(checksum);
        }
        return PackageUtils.getSha256Checksum(checksums.toString().getBytes());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void deleteFiles(@NonNull FreshFile[] files) {
        for (FreshFile file : files) file.delete();
    }
}
