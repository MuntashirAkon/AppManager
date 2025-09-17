// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.io.Path;

public final class MetadataManager {
    public static final String TAG = MetadataManager.class.getSimpleName();
    private static int currentBackupMetaVersion = BuildConfig.DEBUG ? 5 : 4;

    public static final String META_V2_FILE = "meta_v2.am.json";
    // New scheme
    public static final String INFO_V5_FILE = "info_v5.am.json"; // unencrypted
    public static final String META_V5_FILE = "meta_v5.am.json"; // encrypted

    private MetadataManager() {
    }

    @VisibleForTesting
    public static void setCurrentBackupMetaVersion(int version) {
        currentBackupMetaVersion = version;
    }

    public static int getCurrentBackupMetaVersion() {
        return currentBackupMetaVersion;
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
            BackupMetadataV5.Info info = new BackupMetadataV5.Info(jsonObject);
            info.setBackupItem(backupItem);
            return info;
        } catch (JSONException e) {
            throw new IOException(e.getMessage() + " for path " + infoFile, e);
        }
    }

    @NonNull
    @WorkerThread
    public static BackupMetadataV5 readMetadata(@NonNull BackupItems.BackupItem backupItem) throws IOException {
        BackupMetadataV5.Info info = readInfo(backupItem);
        return readMetadata(backupItem, info);
    }

    @NonNull
    @WorkerThread
    public static BackupMetadataV5 readMetadata(@NonNull BackupItems.BackupItem backupItem,
                                                @NonNull BackupMetadataV5.Info backupInfo)
            throws IOException {
        boolean v5AndUp = backupItem.isV5AndUp();
        if (v5AndUp) {
            // Need to setup crypto in order to decrypt meta_v5.am.json
            setCrypto(backupItem, backupInfo);
        }
        Path metadataFile = v5AndUp ? backupItem.getMetadataV5File(true) : backupItem.getMetadataV2File();
        String metadataString = metadataFile.getContentAsString();
        JSONObject jsonObject;
        if (TextUtils.isEmpty(metadataString)) {
            throw new IOException("Empty JSON string for path " + metadataFile);
        }
        try {
            jsonObject = new JSONObject(metadataString);
            if (!v5AndUp) {
                // Meta is a subset of meta_v2.am.json except for backup_name
                jsonObject.put("backup_name", backupItem.getBackupName());
            }
            BackupMetadataV5.Metadata metadata = new BackupMetadataV5.Metadata(jsonObject);
            return new BackupMetadataV5(backupInfo, metadata);
        } catch (JSONException e) {
            throw new IOException(e.getMessage() + " for path " + metadataFile, e);
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
        Path metadataFile = backupFile.getMetadataV5File(true);
        try (OutputStream outputStream = metadataFile.openOutputStream()) {
            outputStream.write(metadata.metadata.serializeToJson().toString(4).getBytes());
        } catch (JSONException e) {
            throw new IOException(e.getMessage() + " for file " + metadataFile, e);
        }
        // Encrypt the metadata
        Path encryptedMetadataFile = backupFile.encrypt(new Path[]{metadataFile})[0];
        filenameChecksumMap.put(encryptedMetadataFile.getName(), DigestUtils.getHexDigest(
                metadata.info.checksumAlgo, encryptedMetadataFile));
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
