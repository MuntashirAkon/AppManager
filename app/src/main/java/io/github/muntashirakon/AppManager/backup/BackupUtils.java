package io.github.muntashirakon.AppManager.backup;

import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class BackupUtils {
    @Nullable
    public static MetadataManager.MetadataV1 getBackupInfo(String packageName) {
        try (MetadataManager metadataManager = MetadataManager.getInstance(packageName)) {
            metadataManager.readMetadata();
            return metadataManager.getMetadataV1();
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    @NonNull
    public static List<String> getBackupApplications() {
        File backupPath = BackupStorageManager.getBackupDirectory();
        List<String> packages;
        String[] files = backupPath.list((dir, name) -> new File(dir, name).isDirectory());
        if (files != null) packages = new ArrayList<>(Arrays.asList(files));
        else return new ArrayList<>();
        packages.remove(BackupStorageManager.APK_SAVING_DIRECTORY);
        for (Iterator<String> it = packages.iterator(); it.hasNext(); ) {
            if (!MetadataManager.hasMetadata(it.next())) it.remove();
        }
        return packages;
    }
}
