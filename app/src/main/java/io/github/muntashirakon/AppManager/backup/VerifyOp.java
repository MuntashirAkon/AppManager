// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.IOException;

import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV2;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.io.Path;

@WorkerThread
class VerifyOp implements Closeable {
    static final String TAG = VerifyOp.class.getSimpleName();

    @NonNull
    private final BackupFlags mBackupFlags;
    @NonNull
    private final BackupMetadataV2 mMetadata;
    @NonNull
    private final BackupItems.BackupItem mBackupItem;
    @NonNull
    private final BackupItems.Checksum mChecksum;

    VerifyOp(@NonNull BackupItems.BackupItem backupItem) throws BackupException {
        mBackupItem = backupItem;
        try {
            mMetadata = mBackupItem.getMetadataV2();
            mBackupFlags = mMetadata.flags;
        } catch (IOException e) {
            mBackupItem.cleanup();
            throw new BackupException("Could not read metadata. Possibly due to a malformed json file.", e);
        }
        // Setup crypto
        if (!CryptoUtils.isAvailable(mMetadata.crypto)) {
            mBackupItem.cleanup();
            throw new BackupException("Mode " + mMetadata.crypto + " is currently unavailable.");
        }
        try {
            mBackupItem.setCrypto(CryptoUtils.getCrypto(mMetadata));
        } catch (CryptoException e) {
            mBackupItem.cleanup();
            throw new BackupException("Could not get crypto " + mMetadata.crypto, e);
        }
        // Get checksums
        try {
            mChecksum = mBackupItem.getChecksum();
        } catch (Throwable e) {
            mBackupItem.cleanup();
            throw new BackupException("Could not get checksums.", e);
        }
        // Verify metadata
        Path metadataFile;
        try {
            metadataFile = mBackupItem.getMetadataV2File();
        } catch (IOException e) {
            mBackupItem.cleanup();
            throw new BackupException("Could not get metadata file.", e);
        }
        String checksum = DigestUtils.getHexDigest(mMetadata.checksumAlgo, metadataFile);
        if (!checksum.equals(mChecksum.get(metadataFile.getName()))) {
            mBackupItem.cleanup();
            throw new BackupException("Could not verify metadata." +
                    "\nFile: " + metadataFile.getName() +
                    "\nFound: " + checksum +
                    "\nRequired: " + mChecksum.get(metadataFile.getName()));
        }
    }

    @Override
    public void close() {
        Log.d(TAG, "Close called");
        mChecksum.close();
        mBackupItem.cleanup();
    }

    void verify() throws BackupException {
        try {
            // No need to check master key as it varies from device to device and APK signing key checksum as it would
            // remain intact if the APK files are not modified.
            if (mBackupFlags.backupApkFiles()) {
                verifyApkFiles();
            }
            if (mBackupFlags.backupData()) {
                verifyData();
                if (mMetadata.keyStore) {
                    verifyKeyStore();
                }
            }
            if (mBackupFlags.backupExtras()) {
                verifyExtras();
            }
            if (mBackupFlags.backupRules()) {
                verifyRules();
            }
        } catch (BackupException e) {
            throw e;
        } catch (Throwable th) {
            throw new BackupException("Unknown error occurred", th);
        }
    }

    private void verifyApkFiles() throws BackupException {
        Path[] backupSourceFiles = mBackupItem.getSourceFiles();
        if (backupSourceFiles.length == 0) {
            // No APK files found
            throw new BackupException("Backup does not contain any APK files.");
        }
        String checksum;
        for (Path file : backupSourceFiles) {
            checksum = DigestUtils.getHexDigest(mMetadata.checksumAlgo, file);
            if (!checksum.equals(mChecksum.get(file.getName()))) {
                throw new BackupException("Could not verify APK files." +
                        "\nFile: " + file.getName() +
                        "\nFound: " + checksum +
                        "\nRequired: " + mChecksum.get(file.getName()));
            }
        }
    }

    private void verifyKeyStore() throws BackupException {
        Path[] keyStoreFiles = mBackupItem.getKeyStoreFiles();
        if (keyStoreFiles.length == 0) {
            // Not having KeyStore backups is fine.
            return;
        }
        String checksum;
        for (Path file : keyStoreFiles) {
            checksum = DigestUtils.getHexDigest(mMetadata.checksumAlgo, file);
            if (!checksum.equals(mChecksum.get(file.getName()))) {
                throw new BackupException("Could not verify KeyStore files." +
                        "\nFile: " + file.getName() +
                        "\nFound: " + checksum +
                        "\nRequired: " + mChecksum.get(file.getName()));
            }
        }
    }

    private void verifyData() throws BackupException {
        Path[] dataFiles;
        String checksum;
        for (int i = 0; i < mMetadata.dataDirs.length; ++i) {
            dataFiles = mBackupItem.getDataFiles(i);
            if (dataFiles.length == 0) {
                throw new BackupException("No data files at index " + i + ".");
            }
            for (Path file : dataFiles) {
                checksum = DigestUtils.getHexDigest(mMetadata.checksumAlgo, file);
                if (!checksum.equals(mChecksum.get(file.getName()))) {
                    throw new BackupException("Could not verify data files at index " + i + "." +
                            "\nFile: " + file.getName() +
                            "\nFound: " + checksum +
                            "\nRequired: " + mChecksum.get(file.getName()));
                }
            }
        }
    }

    private void verifyExtras() throws BackupException {
        Path miscFile;
        try {
            miscFile = mBackupItem.getMiscFile();
        } catch (IOException ignore) {
            // There are no permissions, just skip
            return;
        }
        String checksum = DigestUtils.getHexDigest(mMetadata.checksumAlgo, miscFile);
        if (!checksum.equals(mChecksum.get(miscFile.getName()))) {
            throw new BackupException("Could not verify extras." +
                    "\nFile: " + miscFile.getName() +
                    "\nFound: " + checksum +
                    "\nRequired: " + mChecksum.get(miscFile.getName()));
        }
    }

    private void verifyRules() throws BackupException {
        Path rulesFile;
        try {
            rulesFile = mBackupItem.getRulesFile();
        } catch (IOException e) {
            if (mMetadata.hasRules) {
                throw new BackupException("Rules file is missing.", e);
            } else {
                // There are no rules, just skip
                return;
            }
        }
        String checksum = DigestUtils.getHexDigest(mMetadata.checksumAlgo, rulesFile);
        if (!checksum.equals(mChecksum.get(rulesFile.getName()))) {
            throw new BackupException("Could not verify rules file." +
                    "\nFile: " + rulesFile.getName() +
                    "\nFound: " + checksum +
                    "\nRequired: " + mChecksum.get(rulesFile.getName()));
        }
    }
}