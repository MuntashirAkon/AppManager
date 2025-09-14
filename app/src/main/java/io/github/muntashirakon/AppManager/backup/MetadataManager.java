// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Pair;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV2;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.io.Path;

public final class MetadataManager {
    public static final String TAG = MetadataManager.class.getSimpleName();
    public static final int CURRENT_BACKUP_META_VERSION = 4;

    public static final String META_V2_FILE = "meta_v2.am.json";
    // New scheme
    public static final String INFO_V5_FILE = "info_v5.am.json"; // unencrypted
    public static final String META_V5_FILE = "meta_v5.am.json"; // encrypted

    private MetadataManager() {
    }

    @NonNull
    @WorkerThread
    public static BackupMetadataV5.Info readInfo(@NonNull BackupItems.BackupItem backupItem) throws IOException {
        boolean v5AndUp = backupItem.isV5AndUp();
        Path infoFile = v5AndUp ? backupItem.getInfoFile() : backupItem.getMetadataV2File();
        String infoString = infoFile.getContentAsString();
        JSONObject jsonObject;
        if (TextUtils.isEmpty(infoString)) {
            throw new IOException("Empty JSON string for path " + infoFile);
        }
        try {
            jsonObject = new JSONObject(infoString);
            if (!v5AndUp) {
                // Info is a subset of meta_v2.am.json except for backup_name (format: <user_id>[_<backup_name>])
                jsonObject.put("backup_name", backupItem.backupName);
            }
            BackupMetadataV5.Info info = new BackupMetadataV5.Info(jsonObject);
            info.backupItem = backupItem;
            return info;
        } catch (JSONException e) {
            throw new IOException(e.getMessage() + " for path " + infoFile, e);
        }
    }

    @NonNull
    @WorkerThread
    public static BackupMetadataV5 readMetadataV5(@NonNull BackupItems.BackupItem backupItem) throws IOException {
        BackupMetadataV5.Info info = readInfo(backupItem);
        return readMetadataV5(backupItem, info);
    }

    @NonNull
    @WorkerThread
    public static BackupMetadataV5 readMetadataV5(@NonNull BackupItems.BackupItem backupItem,
                                                  @NonNull BackupMetadataV5.Info backupInfo)
            throws IOException {
        boolean isV5AndLater = backupItem.isV5AndUp();
        if (isV5AndLater) {
            // Need to setup crypto in order to decrypt meta_v5.am.json
            setCrypto(backupItem, backupInfo);
        }
        Path metadataFile = isV5AndLater ? backupItem.getMetadataV5File() : backupItem.getMetadataV2File();
        String metadataString = metadataFile.getContentAsString();
        JSONObject jsonObject;
        if (TextUtils.isEmpty(metadataString)) {
            throw new IOException("Empty JSON string for path " + metadataFile);
        }
        try {
            jsonObject = new JSONObject(metadataString);
            BackupMetadataV5.Metadata metadata = new BackupMetadataV5.Metadata(jsonObject);
            return new BackupMetadataV5(backupInfo, metadata);
        } catch (JSONException e) {
            throw new IOException(e.getMessage() + " for path " + metadataFile, e);
        }
    }

    @WorkerThread
    @NonNull
    public static Pair<String, String> writeMetadataV2(@NonNull BackupMetadataV2 metadata, @NonNull BackupItems.BackupItem backupFile) throws IOException {
        Path metadataFile = backupFile.getMetadataV2File();
        try (OutputStream outputStream = metadataFile.openOutputStream()) {
            outputStream.write(metadata.serializeToJson().toString(4).getBytes());
            return new Pair<>(metadataFile.getName(), DigestUtils.getHexDigest(
                    metadata.checksumAlgo, metadataFile));
        } catch (JSONException e) {
            throw new IOException(e.getMessage() + " for path " + backupFile.getBackupPath());
        }
    }

    @WorkerThread
    @NonNull
    public static Map<String, String> writeMetadata(@NonNull BackupMetadataV5 metadata, @NonNull BackupItems.BackupItem backupFile) throws IOException {
        if (!backupFile.isBackupMode()) {
            throw new IOException("Backup is in read-only mode.");
        }
        if (metadata.info.version >= 5) {
            // v5 and up
            return writeMetadataV5(metadata, backupFile);
        } else {
            // Old style backup
            return writeMetadataV2(metadata, backupFile);
        }
    }

    @WorkerThread
    @NonNull
    private static Map<String, String> writeMetadataV2(@NonNull BackupMetadataV5 metadata, @NonNull BackupItems.BackupItem backupFile) throws IOException {
        Path metadataFile = backupFile.getMetadataV2File();
        try (OutputStream outputStream = metadataFile.openOutputStream()) {
            JSONObject metadataObject = metadata.info.serializeToJson();
            JSONUtils.putAll(metadataObject, metadata.metadata.serializeToJson());
            // Info is a subset of meta_v2.am.json except for backup_name
            metadataObject.remove("backup_name");
            outputStream.write(metadataObject.toString(4).getBytes());
            return Collections.singletonMap(metadataFile.getName(), DigestUtils.getHexDigest(
                    metadata.info.checksumAlgo, metadataFile));
        } catch (JSONException e) {
            throw new IOException(e.getMessage() + " for path " + backupFile.getBackupPath(), e);
        }
    }

    @WorkerThread
    @NonNull
    private static Map<String, String> writeMetadataV5(@NonNull BackupMetadataV5 metadata, @NonNull BackupItems.BackupItem backupFile) throws IOException {
        Map<String, String> filenameChecksumMap = new LinkedHashMap<>(2);
        Path metadataFile = backupFile.getMetadataV5File();
        try (OutputStream outputStream = metadataFile.openOutputStream()) {
            outputStream.write(metadata.metadata.serializeToJson().toString(4).getBytes());
            filenameChecksumMap.put(metadataFile.getName(), DigestUtils.getHexDigest(
                    metadata.info.checksumAlgo, metadataFile));
        } catch (JSONException e) {
            throw new IOException(e.getMessage() + " for file " + metadataFile, e);
        }
        Path infoFile = backupFile.getInfoFile();
        try (OutputStream outputStream = infoFile.openOutputStream()) {
            outputStream.write(metadata.info.serializeToJson().toString(4).getBytes());
            filenameChecksumMap.put(infoFile.getName(), DigestUtils.getHexDigest(
                    metadata.info.checksumAlgo, infoFile));
        } catch (JSONException e) {
            throw new IOException(e.getMessage() + " for file " + infoFile, e);
        }
        return filenameChecksumMap;
    }

    private static void setCrypto(@NonNull BackupItems.BackupItem backupItem, @NonNull BackupMetadataV5.Info backupInfo) throws IOException {
        if (!CryptoUtils.isAvailable(backupInfo.crypto)) {
            throw new IOException("Mode " + backupInfo.crypto + " is currently unavailable.");
        }
        try {
            backupItem.setCrypto(backupInfo.getCrypto());
        } catch (CryptoException e) {
            throw new IOException("Failed to get crypto " + backupInfo.crypto, e);
        }
    }
}
