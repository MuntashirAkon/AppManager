// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;

import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV2;
import io.github.muntashirakon.io.Path;

public final class MetadataManager {
    public static final String TAG = MetadataManager.class.getSimpleName();

    public static final String META_V2_FILE = "meta_v2.am.json";

    private MetadataManager() {
    }

    @NonNull
    @WorkerThread
    public static BackupMetadataV2 readMetadataV2(@NonNull BackupItems.BackupItem backupItem) throws IOException {
        String metadataString = backupItem.getMetadataV2File().getContentAsString();
        if (TextUtils.isEmpty(metadataString)) {
            throw new IOException("Empty JSON string for path " + backupItem.getBackupPath());
        }
        try {
            JSONObject rootObject = new JSONObject(metadataString);
            BackupMetadataV2 metadata = new BackupMetadataV2(rootObject);
            metadata.backupItem = backupItem;
            metadata.backupName = backupItem.backupName;
            return metadata;
        } catch (JSONException e) {
            throw new IOException(e.getMessage() + " for path " + backupItem.getBackupPath());
        }
    }

    @WorkerThread
    public static void writeMetadataV2(@NonNull BackupMetadataV2 metadata, @NonNull BackupItems.BackupItem backupFile) throws IOException {
        Path metadataFile = backupFile.getMetadataV2File();
        try (OutputStream outputStream = metadataFile.openOutputStream()) {
            outputStream.write(metadata.serializeToJson().toString(4).getBytes());
        } catch (JSONException e) {
            throw new IOException(e.getMessage() + " for path " + backupFile.getBackupPath());
        }
    }
}
