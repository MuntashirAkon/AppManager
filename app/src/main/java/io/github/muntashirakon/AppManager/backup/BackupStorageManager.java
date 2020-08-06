package io.github.muntashirakon.AppManager.backup;

import android.annotation.SuppressLint;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

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
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.runner.RootShellRunner;
import io.github.muntashirakon.AppManager.rules.RulesImporter;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;

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

    private static final String SOURCE_PREFIX = "source";
    private static final String DATA_PREFIX = "data";
    private static final String BACKUP_FILE_PREFIX = ".tar.gz";
    private static final String RULES_TSV = "rules.am.tsv";
    private static final String TMP_BACKUP_SUFFIX = "~";
    private static final String APK_SAVING_DIRECTORY = "apks";
    @SuppressLint("SdCardPath")
    private static final File DEFAULT_BACKUP_PATH = new File("/sdcard/AppManager");

    private static BackupStorageManager instance;
    public static BackupStorageManager getInstance(String packageName) {
        if (instance == null) instance = new BackupStorageManager(packageName);
        else if (!instance.packageName.equals(packageName)) {
            instance.close();
            instance = new BackupStorageManager(packageName);
        }
        return instance;
    }

    @NonNull
    public static File getBackupPath(@NonNull String packageName) {
        return new File(DEFAULT_BACKUP_PATH, packageName);
    }

    @NonNull
    public static File getTemporaryBackupPath(@NonNull String packageName) {
        return new File(DEFAULT_BACKUP_PATH, packageName + TMP_BACKUP_SUFFIX);
    }

    @NonNull
    public static File getApkBackupDirectory() {
        return new File(DEFAULT_BACKUP_PATH, APK_SAVING_DIRECTORY);
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
        if (flags == BACKUP_NOTHING) return false;
        MetadataManager.MetadataV1 metadataV1;
        try {
            // Override existing metadata
            metadataV1 = metadataManager.setupMetadata(flags);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        // Create a new temporary directory
        File tmpBackupPath = getTemporaryBackupPath(packageName);
        if (!tmpBackupPath.exists()) {
            if (!tmpBackupPath.mkdirs()) return false;
        }
        // Backup source
        File backupFile = new File(tmpBackupPath, SOURCE_PREFIX + BACKUP_FILE_PREFIX);
        if (!metadataV1.sourceDir.equals("")) {
            if (!RootShellRunner.runCommand(String.format("cd \"%s\" && tar -czf \"%s\" .",
                    metadataV1.sourceDir, backupFile.getAbsolutePath())).isSuccessful()) {
                return cleanup(tmpBackupPath);
            }
            if (backupFile.exists()) {
                metadataV1.sourceDirSha256Checksum = PackageUtils.getSha256Checksum(backupFile);
            } else return cleanup(tmpBackupPath);
        }
        // Backup data
        for (int i = 0; i<metadataV1.dataDirs.length; ++i) {
            backupFile = new File(tmpBackupPath, DATA_PREFIX + i + BACKUP_FILE_PREFIX);
            StringBuilder sb = new StringBuilder("tar -czf \"").append(backupFile.getAbsolutePath()).append("\" \"")
                    .append(metadataV1.dataDirs[i]).append("\"");
            if ((flags & BACKUP_EXCLUDE_CACHE) != 0) {
                sb.append(" --exclude=\"").append(metadataV1.dataDirs[i].substring(1))
                        .append(File.separatorChar).append("cache").append("\"");
                sb.append(" --exclude=\"").append(metadataV1.dataDirs[i].substring(1))
                        .append(File.separatorChar).append("code_cache").append("\"");
            }
            if (!RootShellRunner.runCommand(sb.toString()).isSuccessful()) {
                return cleanup(tmpBackupPath);
            }
            if (backupFile.exists()) {
                metadataV1.dataDirsSha256Checksum[i] = PackageUtils.getSha256Checksum(backupFile);
            } else return cleanup(tmpBackupPath);
        }
        // Export rules
        if (metadataV1.hasRules) {
            File rulesFile = new File(tmpBackupPath, RULES_TSV);
            try (OutputStream outputStream = new FileOutputStream(rulesFile);
                 ComponentsBlocker cb = ComponentsBlocker.getInstance(AppManager.getContext(), packageName)) {
                for (RulesStorageManager.Entry entry: cb.getAll()) {
                    outputStream.write(String.format("%s\t%s\t%s\t%s\n", packageName, entry.name, entry.type.name(), entry.extra).getBytes());
                }
            } catch (IOException e) {
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
            e.printStackTrace();
            return cleanup(tmpBackupPath);
        }
        // Replace current backup:
        // There's hardly any chance of getting a false here but checks are done anyway.
        if (delete_backup() && tmpBackupPath.renameTo(backupPath)) {
            return true;
        }
        return cleanup(tmpBackupPath);
    }

    @SuppressLint("SdCardPath")
    public boolean restore() {
        if (flags == BACKUP_NOTHING) return false;
        MetadataManager.MetadataV1 metadataV1;
        try {
            metadataManager.readMetadata();
            metadataV1 = metadataManager.getMetadataV1();
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
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
            File backupSourceFile = new File(backupPath, SOURCE_PREFIX + BACKUP_FILE_PREFIX);
            if (isInstalled) {
                // Check signature if installed: Should be checked before calling this method if it is enabled
                List<String> certChecksum = Arrays.asList(PackageUtils.getSigningCertSha256Checksum(packageInfo));
                boolean isVerified = true;
                for (String checksum : metadataV1.certSha256Checksum) {
                    if (certChecksum.contains(checksum)) continue;
                    isVerified = false;
                    if (!noChecksumCheck) return false;
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
                if (metadataV1.sourceDir.equals("")) return false;  // Source cannot be empty
                if (!backupSourceFile.exists()) return false;  // No source backup found
                String checksum = PackageUtils.getSha256Checksum(backupSourceFile);
                if (!checksum.equals(metadataV1.sourceDirSha256Checksum))
                    return false;  // Checksum mismatched
            }
            if (reinstallNeeded) {
                // A complete reinstall needed, first uninstall the package with -k and then install
                // the package again with -r
                if (!RunnerUtils.uninstallPackageWithoutData(packageName).isSuccessful())
                    return false;  // Failed to uninstall package
            }
            // Extract the package
            File packageStagingDirectory = new File("/data/local/tmp");
            if (!packageStagingDirectory.exists()) packageStagingDirectory = backupPath;
            File baseApk = new File(packageStagingDirectory, "base.apk");  // FIXME: Remove hardcoded base.apk
            if (baseApk.exists()) //noinspection ResultOfMethodCallIgnored
                baseApk.delete();
            if (!RootShellRunner.runCommand(String.format("tar -xzf \"%s\" ./base.apk -C \"%s\"",
                    backupSourceFile, packageStagingDirectory.getAbsolutePath())).isSuccessful()) {
                return false;
            }
            // A normal update will do it now
            if (!RunnerUtils.installPackage(baseApk.getAbsolutePath()).isSuccessful())
                return false;  // Failed to (re)install package
            // Get package info
            try {
                packageInfo = AppManager.getContext().getPackageManager().getPackageInfo(packageName, flagSigningInfo);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                return false;  // Failed to (re)install package
            }
            if (packageInfo == null) return false;  // Failed to (re)install package
            // Restore source: Get installed source directory and copy backups directly
            String sourceDir = new File(packageInfo.applicationInfo.publicSourceDir).getParent();
            // TODO: Handle split apk
            if (!RootShellRunner.runCommand(String.format("tar -xzf \"%s\" -C \"%s\"",
                    backupSourceFile, sourceDir)).isSuccessful()) {
                return false;  // Failed to restore source files
            }
        }
        if ((flags & BACKUP_DATA) != 0) {
            // Data restore is requested: Data restore is only possible if the app is actually
            // installed. So, check if it's installed first.
            if (!isInstalled) return false;
            String backupDataFile = backupPath.getAbsolutePath() + File.separatorChar + DATA_PREFIX + "%d" + BACKUP_FILE_PREFIX;
            if (!noChecksumCheck) {
                // Verify integrity of the data backups
                String checksum;
                for (int i = 0; i < metadataV1.dataDirs.length; ++i) {
                    checksum = PackageUtils.getSha256Checksum(new File(String.format(backupDataFile, i)));
                    if (!checksum.equals(metadataV1.dataDirsSha256Checksum[i])) return false;
                }
            }
            // Force stop app before restoring backups
            RunnerUtils.forceStopPackage(packageName);
            // Restore backups
            String dataSource;
            for (int i = 0; i<metadataV1.dataDirs.length; ++i) {
                dataSource = metadataV1.dataDirs[i];
                // Skip restoring external data if requested
                if ((flags & BACKUP_EXT_DATA) == 0 && dataSource.startsWith("/storage") && dataSource.startsWith("/sdcard")) continue;
                if (!RootShellRunner.runCommand(String.format("tar -xzf \"%s\" -C /", String.format(backupDataFile, i))).isSuccessful()) {
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
            if (!isInstalled) return false;
            File rulesFile = new File(backupPath, RULES_TSV);
            if (rulesFile.exists()) {
                try (RulesImporter importer = new RulesImporter(Arrays.asList(RulesStorageManager.Type.values()))) {
                    importer.addRulesFromUri(Uri.fromFile(rulesFile));
                    importer.setPackagesToImport(Collections.singletonList(packageName));
                    importer.applyRules();
                } catch (IOException e) {
                    // Failed to import rules
                    e.printStackTrace();
                    return false;
                }
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
}
