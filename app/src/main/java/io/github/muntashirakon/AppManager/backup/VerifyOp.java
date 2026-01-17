// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.IOException;

import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
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
    private final BackupMetadataV5.Info mBackupInfo;
    @NonNull
    private final BackupMetadataV5.Metadata mBackupMetadata;
    @NonNull
    private final BackupItems.BackupItem mBackupItem;
    @NonNull
    private final BackupItems.Checksum mChecksum;

    VerifyOp(@NonNull BackupItems.BackupItem backupItem) throws BackupException {
        mBackupItem = backupItem;
        try {
            mBackupInfo = mBackupItem.getInfo();
            mBackupFlags = mBackupInfo.flags;
        } catch (IOException e) {
            mBackupItem.cleanup();
            throw new BackupException("Could not read backup info. Possibly due to a malformed json file.", e);
        }
        // Setup crypto
        if (!CryptoUtils.isAvailable(mBackupInfo.crypto)) {
            mBackupItem.cleanup();
            throw new BackupException("Mode " + mBackupInfo.crypto + " is currently unavailable.");
        }
        try {
            mBackupItem.setCrypto(mBackupInfo.getCrypto());
        } catch (CryptoException e) {
            mBackupItem.cleanup();
            throw new BackupException("Could not get crypto " + mBackupInfo.crypto, e);
        }
        try {
            mBackupMetadata = mBackupItem.getMetadata(mBackupInfo).metadata;
        } catch (IOException e) {
            mBackupItem.cleanup();
            throw new BackupException("Could not read backup metadata. Possibly due to a malformed json file.", e);
        }
        // Get checksums
        try {
            mChecksum = mBackupItem.getChecksum();
        } catch (Throwable e) {
            mBackupItem.cleanup();
            throw new BackupException("Could not get checksums.", e);
        }
        // Verify metadata
        try {
            verifyMetadata();
        } catch (BackupException e) {
            mBackupItem.cleanup();
            throw e;
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
                if (mBackupMetadata.keyStore) {
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

    private void verifyMetadata() throws BackupException {
        boolean isV5AndUp = mBackupItem.isV5AndUp();
        if (isV5AndUp) {
            Path infoFile;
            try {
                infoFile = mBackupItem.getInfoFile();
            } catch (IOException e) {
                throw new BackupException("Could not get metadata file.", e);
            }
            String checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, infoFile);
            if (!checksum.equals(mChecksum.get(infoFile.getName()))) {
                throw new BackupException("Couldn't verify metadata file." +
                        "\nFile: " + infoFile +
                        "\nFound: " + checksum +
                        "\nRequired: " + mChecksum.get(infoFile.getName()));
            }
        }
        Path metadataFile;
        try {
            metadataFile = isV5AndUp ? mBackupItem.getMetadataV5File(false) : mBackupItem.getMetadataV2File();
        } catch (IOException e) {
            throw new BackupException("Could not get metadata file.", e);
        }
        String checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, metadataFile);
        if (!checksum.equals(mChecksum.get(metadataFile.getName()))) {
            throw new BackupException("Couldn't verify metadata file." +
                    "\nFile: " + metadataFile +
                    "\nFound: " + checksum +
                    "\nRequired: " + mChecksum.get(metadataFile.getName()));
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
            checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, file);
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
            checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, file);
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
        for (int i = 0; i < mBackupMetadata.dataDirs.length; ++i) {
            dataFiles = mBackupItem.getDataFiles(i);
            if (dataFiles.length == 0) {
                throw new BackupException("No data files at index " + i + ".");
            }
            for (Path file : dataFiles) {
                checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, file);
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
        String checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, miscFile);
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
            if (mBackupMetadata.hasRules) {
                throw new BackupException("Rules file is missing.", e);
            } else {
                // There are no rules, just skip
                return;
            }
        }
        String checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, rulesFile);
        if (!checksum.equals(mChecksum.get(rulesFile.getName()))) {
            throw new BackupException("Could not verify rules file." +
                    "\nFile: " + rulesFile.getName() +
                    "\nFound: " + checksum +
                    "\nRequired: " + mChecksum.get(rulesFile.getName()));
        }
    }
}