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
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import dalvik.system.VMRuntime;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.rules.RulesImporter;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.RootShellRunner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class BackupStorageManager implements AutoCloseable {
    @IntDef(flag = true, value = {
            BACKUP_NOTHING,
            BACKUP_APK,
            BACKUP_DATA,
            BACKUP_EXT_DATA,
            BACKUP_EXCLUDE_CACHE,
            BACKUP_RULES,
            BACKUP_NO_SIGNATURE_CHECK
    })
    public @interface BackupFlags {}
    public static final int BACKUP_NOTHING = 0;
    @SuppressWarnings("PointlessBitwiseExpression")
    public static final int BACKUP_APK = 1 << 0;
    public static final int BACKUP_DATA = 1 << 1;
    public static final int BACKUP_EXT_DATA = 1 << 2;
    public static final int BACKUP_EXCLUDE_CACHE = 1 << 3;
    public static final int BACKUP_RULES = 1 << 4;
    public static final int BACKUP_NO_SIGNATURE_CHECK = 1 << 5;

    private static final String CMD_SOURCE_DIR_BACKUP = "cd \"%s\" && tar -czf - . | split -b 1G - \"%s\"";  // src, dest
    private static final String CMD_DATA_DIR_BACKUP = "tar -czf - \"%s\" %s | split -b 1G - \"%s\"";  // src, exclude, dest
    private static final String SOURCE_PREFIX = "source";
    private static final String DATA_PREFIX = "data";
    private static final String BACKUP_FILE_SUFFIX = ".tar.gz";
    private static final String RULES_TSV = "rules.am.tsv";
    private static final String TMP_BACKUP_SUFFIX = "~";
    static final String APK_SAVING_DIRECTORY = "apks";
    private static final File DEFAULT_BACKUP_PATH = new File(Environment.getExternalStorageDirectory(), "AppManager");

    private static BackupStorageManager instance;
    public static BackupStorageManager getInstance(String packageName) {
        if (instance == null) instance = new BackupStorageManager(packageName);
        else if (!instance.packageName.equals(packageName)) {
            instance.close();
            instance = new BackupStorageManager(packageName);
        }
        return instance;
    }

    public static File getBackupDirectory() {
        return DEFAULT_BACKUP_PATH;
    }

    @NonNull
    public static File getBackupPath(@NonNull String packageName) {
        return new File(getBackupDirectory(), packageName);
    }

    @NonNull
    public static File getTemporaryBackupPath(@NonNull String packageName) {
        return new File(getBackupDirectory(), packageName + TMP_BACKUP_SUFFIX);
    }

    @NonNull
    public static File getApkBackupDirectory() {
        return new File(getBackupDirectory(), APK_SAVING_DIRECTORY);
    }

    private @NonNull String packageName;
    private @NonNull MetadataManager metadataManager;
    private @NonNull File backupPath;
    private @BackupFlags int flags;
    protected BackupStorageManager(@NonNull String packageName) {
        this.packageName = packageName;
        metadataManager = MetadataManager.getInstance(packageName);
        backupPath = getBackupPath(packageName);
        flags = BACKUP_NOTHING;
        if (!backupPath.exists()) {
            //noinspection ResultOfMethodCallIgnored
            backupPath.mkdirs();
        }
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public boolean backup() {
        if (flags == BACKUP_NOTHING) {
            Log.e("BSM - Backup", "Backup is requested without any flags.");
            return false;
        }
        MetadataManager.MetadataV1 metadataV1;
        try {
            // Override existing metadata
            metadataV1 = metadataManager.setupMetadata(flags);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("BSM - Backup", "Failed to setup metadata.");
            e.printStackTrace();
            return false;
        }
        // Create a new temporary directory
        File tmpBackupPath = getTemporaryBackupPath(packageName);
        if (!tmpBackupPath.exists()) {
            if (!tmpBackupPath.mkdirs()) {
                Log.e("BSM - Backup", "Failed to create temporary path at " + tmpBackupPath);
                return false;
            }
        }
        // Backup source
        File dataAppPath = OsEnvironment.getDataAppDirectory();
        File backupFile = new File(tmpBackupPath, SOURCE_PREFIX + BACKUP_FILE_SUFFIX + ".");
        if (!metadataV1.sourceDir.equals("")) {
            String sourceDir = metadataV1.sourceDir;
            if (dataAppPath.getAbsolutePath().equals(sourceDir)) {
                // Backup only the apk file (no split apk support for this type of apk)
                sourceDir = new File(sourceDir, metadataV1.apkName).getAbsolutePath();
            }
            String command = String.format(CMD_SOURCE_DIR_BACKUP, sourceDir, backupFile.getAbsolutePath());
            if (!RootShellRunner.runCommand(command).isSuccessful()) {
                Log.e("BSM - Backup", "Failed to backup source directory. " + RootShellRunner.getLastResult().getOutput());
                return cleanup(tmpBackupPath);
            }
            File[] sourceFiles = getSourceFiles(tmpBackupPath);
            if (sourceFiles == null) {
                Log.e("BSM - Backup", "Source backup is requested but no source directory has been backed up.");
                return cleanup(tmpBackupPath);
            }
            metadataV1.sourceDirSha256Checksum = getSha256Sum(sourceFiles);
        }
        // Backup data
        for (int i = 0; i<metadataV1.dataDirs.length; ++i) {
            backupFile = new File(tmpBackupPath, DATA_PREFIX + i + BACKUP_FILE_SUFFIX + ".");
            StringBuilder excludePaths = new StringBuilder();
            if ((flags & BACKUP_EXCLUDE_CACHE) != 0) {
                excludePaths.append(" --exclude=\"").append(metadataV1.dataDirs[i].substring(1))
                        .append(File.separatorChar).append("cache").append("\"");
                excludePaths.append(" --exclude=\"").append(metadataV1.dataDirs[i].substring(1))
                        .append(File.separatorChar).append("code_cache").append("\"");
            }
            if (!RootShellRunner.runCommand(String.format(CMD_DATA_DIR_BACKUP,
                    metadataV1.dataDirs[i], excludePaths.toString(), backupFile.getAbsolutePath()))
                    .isSuccessful()) {
                Log.e("BSM - Backup", "Failed to backup data directory at " + metadataV1.dataDirs[i]);
                return cleanup(tmpBackupPath);
            }
            File[] dataFiles = getDataFiles(tmpBackupPath, i);
            if (dataFiles == null) {
                Log.e("BSM - Backup", "Data backup is requested but no data directory has been backed up.");
                return cleanup(tmpBackupPath);
            }
            metadataV1.dataDirsSha256Checksum[i] = getSha256Sum(dataFiles);
        }
        // Export rules
        if (metadataV1.hasRules) {
            File rulesFile = new File(tmpBackupPath, RULES_TSV);
            try (OutputStream outputStream = new FileOutputStream(rulesFile);
                 ComponentsBlocker cb = ComponentsBlocker.getInstance(packageName)) {
                for (RulesStorageManager.Entry entry: cb.getAll()) {
                    outputStream.write(String.format("%s\t%s\t%s\t%s\n", packageName, entry.name, entry.type.name(), entry.extra).getBytes());
                }
            } catch (IOException e) {
                Log.e("BSM - Backup", "Rules backup is requested but encountered an error during fetching rules.");
                e.printStackTrace();
                return cleanup(tmpBackupPath);
            }
        }
        metadataV1.backupTime = System.currentTimeMillis();
        // Write modified metadata
        metadataManager.setMetadataV1(metadataV1);
        try {
            metadataManager.writeMetadata();
        } catch (IOException | JSONException e) {
            Log.e("BSM - Backup", "Failed to write metadata due to " + e.toString());
            e.printStackTrace();
            return cleanup(tmpBackupPath);
        }
        // Replace current backup:
        // There's hardly any chance of getting a false here but checks are done anyway.
        if (delete_backup() && tmpBackupPath.renameTo(backupPath)) {
            return true;
        }
        Log.e("BSM - Backup", "Unknown error occurred. This message should never be printed.");
        return cleanup(tmpBackupPath);
    }

    @SuppressLint("SdCardPath")
    public boolean restore() {
        if (flags == BACKUP_NOTHING) {
            Log.e("BSM - Restore", "Restore is requested without any flags.");
            return false;
        }
        MetadataManager.MetadataV1 metadataV1;
        try {
            metadataManager.readMetadata();
            metadataV1 = metadataManager.getMetadataV1();
        } catch (JSONException e) {
            Log.e("BSM - Restore", "Failed to read metadata. Possibly due to malformed json file.");
            e.printStackTrace();
            return false;
        }
        // Get instruction set
        String instructionSet = VMRuntime.getInstructionSet(Build.SUPPORTED_ABIS[0]);
        File dataAppPath = OsEnvironment.getDataAppDirectory();
        // Get package info
        PackageInfo packageInfo = null;
        int flagSigningInfo;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            flagSigningInfo = PackageManager.GET_SIGNING_CERTIFICATES;
        else flagSigningInfo = PackageManager.GET_SIGNATURES;
        try {
            packageInfo = AppManager.getContext().getPackageManager().getPackageInfo(packageName, flagSigningInfo);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        boolean isInstalled = packageInfo != null;
        boolean noChecksumCheck = 0 != (flags & BACKUP_NO_SIGNATURE_CHECK);
        if ((flags & BACKUP_APK) != 0) {
            // Restoring apk requested
            boolean reinstallNeeded = false;
            File[] backupSourceFiles = getSourceFiles(backupPath);
            if (backupSourceFiles == null) {
                // No source backup found
                Log.e("BSM - Restore", "Source restore is requested but there are no source files.");
                return false;
            }
            StringBuilder cmdSources = new StringBuilder();
            for (File file: backupSourceFiles) cmdSources.append(" \"").append(file.getAbsolutePath()).append("\"");
            if (isInstalled) {
                // Check signature if installed: Should be checked before calling this method if it is enabled
                List<String> certChecksum = Arrays.asList(PackageUtils.getSigningCertSha256Checksum(packageInfo));
                boolean isVerified = true;
                for (String checksum : metadataV1.certSha256Checksum) {
                    if (certChecksum.contains(checksum)) continue;
                    isVerified = false;
                    if (!noChecksumCheck) {
                        Log.e("BSM - Restore", "Signing info verification failed." +
                                "\nInstalled: " + certChecksum.toString() +
                                "\nBackup: " + Arrays.toString(metadataV1.certSha256Checksum));
                        return false;
                    }
                }
                if (!isVerified) {
                    // Signature verification failed but still here because signature check is disabled.
                    // The only way to restore is to reinstall the app
                    reinstallNeeded = true;
                } else if (PackageUtils.getVersionCode(packageInfo) > metadataV1.versionCode) {
                    // Installed package has higher version code. The only way to downgrade is to
                    // reinstall the package.
                    reinstallNeeded = true;
                }
            }
            if (!noChecksumCheck) {
                // Check integrity of source
                if (TextUtils.isEmpty(metadataV1.sourceDir)) {
                    Log.e("BSM - Restore", "Source restore is requested but source_dir metadata is empty.");
                    return false;
                }
                String checksum = getSha256Sum(backupSourceFiles);
                if (!checksum.equals(metadataV1.sourceDirSha256Checksum)) {
                    Log.e("BSM - Restore", "Source file verification failed." +
                            "\nFiles: " + checksum +
                            "\nMetadata: " + metadataV1.sourceDirSha256Checksum);
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
            // Extract the package
            File packageStagingDirectory = new File("/data/local/tmp");
            if (!packageStagingDirectory.exists()) packageStagingDirectory = backupPath;
            File baseApk = new File(packageStagingDirectory, metadataV1.apkName);
            if (baseApk.exists()) //noinspection ResultOfMethodCallIgnored
                baseApk.delete();
            // TODO: Handle split apk
            if (!RootShellRunner.runCommand(String.format("cat %s | tar -xzf - ./%s -C \"%s\"",
                    cmdSources, metadataV1.apkName, packageStagingDirectory.getAbsolutePath())).isSuccessful()) {
                Log.e("BSM - Restore", "Failed to extract the apk file(s).");
                return false;
            }
            // A normal update will do it now
            if (!RunnerUtils.installPackage(baseApk.getAbsolutePath()).isSuccessful()) {
                Log.e("BSM - Restore", "A (re)install was necessary but couldn't perform it.");
                return false;
            }
            // Get package info
            try {
                packageInfo = AppManager.getContext().getPackageManager().getPackageInfo(packageName, flagSigningInfo);
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
            // Restore source directory only if instruction set is matched or app path is not /data/app
            String sourceDir = new File(packageInfo.applicationInfo.publicSourceDir).getParent();
            if (metadataV1.instructionSet.equals(instructionSet) && !dataAppPath.getAbsolutePath().equals(sourceDir)) {
                // Restore source: Get installed source directory and copy backups directly
                if (!RootShellRunner.runCommand(String.format("cat %s | tar -xzf - -C \"%s\"",
                        cmdSources, sourceDir)).isSuccessful()) {
                    Log.e("BSM - Restore", "Failed to restore the source files.");
                    return false;  // Failed to restore source files
                }
            } else Log.e("BSM - Restore", "Skipped restoring files due to mismatched architecture or the path is /data/app");
        }
        if ((flags & BACKUP_DATA) != 0) {
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
                for (int i = 0; i < metadataV1.dataDirs.length; ++i) {
                    dataFiles = getDataFiles(backupPath, i);
                    if (dataFiles == null) {
                        Log.e("BSM - Restore", "Data restore is requested but there are no data files for index " + i + ".");
                        return false;
                    }
                    checksum = getSha256Sum(dataFiles);
                    if (!checksum.equals(metadataV1.dataDirsSha256Checksum[i])) {
                        Log.e("BSM - Restore", "Data file verification failed for index " + i + "." +
                                "\nFiles: " + checksum +
                                "\nMetadata: " + metadataV1.dataDirsSha256Checksum[i]);
                        return false;
                    }
                }
            }
            // Force stop app before restoring backups
            RunnerUtils.forceStopPackage(packageName);
            // Restore backups
            String dataSource;
            for (int i = 0; i<metadataV1.dataDirs.length; ++i) {
                dataSource = metadataV1.dataDirs[i];
                dataFiles = getDataFiles(backupPath, i);
                if (dataFiles == null) {
                    Log.e("BSM - Restore", "Data restore is requested but there are no data files for index " + i + ".");
                    return false;
                }
                StringBuilder cmdData = new StringBuilder();
                // FIXME: Fix API 23 external storage issue
                for (File file: dataFiles) cmdData.append(" \"").append(file.getAbsolutePath()).append("\"");
                // Skip restoring external data if requested
                if ((flags & BACKUP_EXT_DATA) == 0 && dataSource.startsWith("/storage") && dataSource.startsWith("/sdcard")) continue;
                if (!RootShellRunner.runCommand(String.format("cat %s | tar -xzf - -C /", cmdData.toString())).isSuccessful()) {
                    Log.e("BSM - Restore", "Failed to restore data files for index " + i + ".");
                    return false;
                }
                if ((flags & BACKUP_EXCLUDE_CACHE) != 0) {
                    // Clear cache if exists: return value is not important for us
                    RootShellRunner.runCommand(String.format("rm -rf %s/cache %s/code_cache", dataSource, dataSource));
                }
            }
        }
        if ((flags & BACKUP_RULES) != 0) {
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
            } else if (metadataV1.hasRules) {
                Log.e("BSM - Restore", "Rules file is missing.");
                return false;
            } // else there are no rules, just skip instead of returning false
        }
        return true;
    }

    public boolean delete_backup() {
        if (backupPath.exists()) return IOUtils.deleteDir(backupPath);
        else return true;
    }

    @Override
    public void close() {
        metadataManager.close();
    }

    private boolean cleanup(@NonNull File backupPath) {
        if (backupPath.exists()) IOUtils.deleteDir(backupPath);
        return false;
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
}
