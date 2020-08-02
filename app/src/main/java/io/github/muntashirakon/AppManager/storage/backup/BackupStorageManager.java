package io.github.muntashirakon.AppManager.storage.backup;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;

import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.runner.RootShellRunner;
import io.github.muntashirakon.AppManager.storage.RulesStorageManager;
import io.github.muntashirakon.AppManager.storage.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class BackupStorageManager implements AutoCloseable {
    @IntDef(flag = true, value = {
            BACKUP_NOTHING,
            BACKUP_APK,
            BACKUP_DATA,
            BACKUP_EXT_DATA,
            BACKUP_EXCLUDE_CACHE,
            BACKUP_RULES
    })
    public @interface BackupFlags {}
    public static final int BACKUP_NOTHING = 0;
    public static final int BACKUP_APK = 1 << 0;
    public static final int BACKUP_DATA = 1 << 1;
    public static final int BACKUP_EXT_DATA = 1 << 2;
    public static final int BACKUP_EXCLUDE_CACHE = 1 << 3;
    public static final int BACKUP_RULES = 1 << 4;

    private static final String SOURCE_PREFIX = "source";
    private static final String DATA_PREFIX = "data";
    private static final String BACKUP_FILE_PREFIX = ".tar.gz";
    private static final String RULES_TSV = "rules.am.tsv";
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
    public static File getBackupPath(String packageName) {
        return new File(DEFAULT_BACKUP_PATH, packageName);
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
        if (backupPath.exists()) {
            try {
                metadataManager.readMetadata();
            } catch (Exception e) {
                e.printStackTrace();
                // It was a malformed backup, delete it
                delete_backup();
            }
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
        // Delete old backup
        delete_backup();
        if (!backupPath.exists()) {
            if (!backupPath.mkdirs()) return false;
        }
        // Backup source
        File backupFile = new File(backupPath, SOURCE_PREFIX + BACKUP_FILE_PREFIX);
        if (!metadataV1.sourceDir.equals("")) {
            // FIXME: cd to the directory first (only for source)
            if (!RootShellRunner.runCommand(String.format("tar -czf \"%s\" \"%s\"",
                    backupFile.getAbsolutePath(), metadataV1.sourceDir)).isSuccessful()) {
                return false;
            }
            if (backupFile.exists()) {
                metadataV1.sourceDirSha256Checksum = PackageUtils.getSha256Checksum(backupFile);
            } else return false;
        }
        // Backup data
        for (int i = 0; i<metadataV1.dataDirs.length; ++i) {
            backupFile = new File(backupPath, DATA_PREFIX + i + BACKUP_FILE_PREFIX);
            StringBuilder sb = new StringBuilder("tar -czf \"").append(backupFile.getAbsolutePath()).append("\" \"")
                    .append(metadataV1.dataDirs[i]).append("\"");
            if ((flags & BACKUP_EXCLUDE_CACHE) != 0) {
                sb.append(" --exclude=\"").append(metadataV1.dataDirs[i].substring(1))
                        .append(File.separatorChar).append("cache").append("\"");
                sb.append(" --exclude=\"").append(metadataV1.dataDirs[i].substring(1))
                        .append(File.separatorChar).append("code_cache").append("\"");
            }
            if (!RootShellRunner.runCommand(sb.toString()).isSuccessful()) {
                return false;
            }
            if (backupFile.exists()) {
                metadataV1.dataDirsSha256Checksum[i] = PackageUtils.getSha256Checksum(backupFile);
            } else return false;
        }
        // Export rules
        if (metadataV1.hasRules) {
            String rulesFile = backupPath.getAbsolutePath() + File.separatorChar + RULES_TSV;
            try (OutputStream outputStream = new FileOutputStream(new File(rulesFile));
                 ComponentsBlocker cb = ComponentsBlocker.getInstance(AppManager.getContext(), packageName)) {
                for (RulesStorageManager.Entry entry: cb.getAll()) {
                    outputStream.write(String.format("%s\t%s\t%s\t%s\n", packageName, entry.name, entry.type.name(), entry.extra).getBytes());
                }
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        metadataV1.backupTime = System.currentTimeMillis();
        // Write modified metadata
        metadataManager.setMetadataV1(metadataV1);
        try {
            metadataManager.writeMetadata();
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean restore() {
        // TODO Handle system apps
        return false;
    }

    public boolean delete_backup() {
        if (backupPath.exists()) return IOUtils.deleteDir(backupPath);
        else return true;
    }

    @Override
    public void close() {
        metadataManager.close();
    }
}
