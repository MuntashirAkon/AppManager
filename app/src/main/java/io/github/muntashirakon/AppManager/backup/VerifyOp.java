// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.io.Path;

import static io.github.muntashirakon.AppManager.backup.BackupManager.DATA_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.KEYSTORE_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.SOURCE_PREFIX;

@WorkerThread
class VerifyOp implements Closeable {
    static final String TAG = VerifyOp.class.getSimpleName();

    @NonNull
    private final BackupFlags backupFlags;
    @NonNull
    private final MetadataManager.Metadata metadata;
    @NonNull
    private final Path backupPath;
    @NonNull
    private final BackupFiles.BackupFile backupFile;
    @NonNull
    private final Crypto crypto;
    @NonNull
    private final BackupFiles.Checksum checksum;
    private final List<Path> decryptedFiles = new ArrayList<>();

    VerifyOp(@NonNull MetadataManager metadataManager, @NonNull BackupFiles.BackupFile backupFile)
            throws BackupException {
        this.backupFile = backupFile;
        this.backupPath = this.backupFile.getBackupPath();
        try {
            metadataManager.readMetadata(this.backupFile);
            metadata = metadataManager.getMetadata();
            this.backupFlags = metadata.flags;
        } catch (IOException e) {
            throw new BackupException("Could not read metadata. Possibly due to a malformed json file.", e);
        }
        // Setup crypto
        if (!CryptoUtils.isAvailable(metadata.crypto)) {
            throw new BackupException("Mode " + metadata.crypto + " is currently unavailable.");
        }
        try {
            crypto = CryptoUtils.getCrypto(metadata);
        } catch (CryptoException e) {
            throw new BackupException("Could not get crypto " + metadata.crypto, e);
        }
        Path checksumFile;
        try {
            checksumFile = this.backupFile.getChecksumFile(metadata.crypto);
        } catch (IOException e) {
            throw new BackupException("Could not get encrypted checksum.txt file.", e);
        }
        // Decrypt checksum
        try {
            synchronized (Crypto.class) {
                crypto.decrypt(new Path[]{checksumFile});
                decryptedFiles.addAll(Arrays.asList(crypto.getNewFiles()));
            }
        } catch (IOException e) {
            throw new BackupException("Could not decrypt " + checksumFile.getName(), e);
        }
        // Get checksums
        try {
            checksumFile = this.backupFile.getChecksumFile(CryptoUtils.MODE_NO_ENCRYPTION);
            this.checksum = new BackupFiles.Checksum(checksumFile, "r");
        } catch (Throwable e) {
            this.backupFile.cleanup();
            throw new BackupException("Could not get checksums.", e);
        }
        // Verify metadata
        Path metadataFile;
        try {
            metadataFile = this.backupFile.getMetadataFile();
        } catch (IOException e) {
            throw new BackupException("Could not get metadata file.", e);
        }
        String checksum = DigestUtils.getHexDigest(metadata.checksumAlgo, metadataFile);
        if (!checksum.equals(this.checksum.get(metadataFile.getName()))) {
            throw new BackupException("Could not verify metadata." +
                    "\nFile: " + metadataFile.getName() +
                    "\nFound: " + checksum +
                    "\nRequired: " + this.checksum.get(metadataFile.getName()));
        }
    }

    @Override
    public void close() {
        Log.d(TAG, "Close called");
        crypto.close();
        for (Path file : decryptedFiles) {
            Log.d(TAG, "Deleting " + file);
            file.delete();
        }
    }

    void verify() throws BackupException {
        try {
            // No need to check master key as it varies from device to device and APK signing key checksum as it would
            // remain intact if the APK files are not modified.
            if (backupFlags.backupApkFiles()) {
                verifyApkFiles();
            }
            if (backupFlags.backupData()) {
                verifyData();
                if (metadata.keyStore) {
                    verifyKeyStore();
                }
            }
            if (backupFlags.backupExtras()) {
                verifyExtras();
            }
            if (backupFlags.backupRules()) {
                verifyRules();
            }
        } catch (BackupException e) {
            throw e;
        } catch (Throwable th) {
            throw new BackupException("Unknown error occurred", th);
        }
    }

    private void verifyApkFiles() throws BackupException {
        Path[] backupSourceFiles = getSourceFiles(backupPath);
        if (backupSourceFiles.length == 0) {
            // No APK files found
            throw new BackupException("Backup does not contain any APK files.");
        }
        String checksum;
        for (Path file : backupSourceFiles) {
            checksum = DigestUtils.getHexDigest(metadata.checksumAlgo, file);
            if (!checksum.equals(this.checksum.get(file.getName()))) {
                throw new BackupException("Could not verify APK files." +
                        "\nFile: " + file.getName() +
                        "\nFound: " + checksum +
                        "\nRequired: " + this.checksum.get(file.getName()));
            }
        }
    }

    private void verifyKeyStore() throws BackupException {
        Path[] keyStoreFiles = getKeyStoreFiles(backupPath);
        if (keyStoreFiles.length == 0) {
            throw new BackupException("KeyStore files do not exist.");
        }
        String checksum;
        for (Path file : keyStoreFiles) {
            checksum = DigestUtils.getHexDigest(metadata.checksumAlgo, file);
            if (!checksum.equals(this.checksum.get(file.getName()))) {
                throw new BackupException("Could not verify KeyStore files." +
                        "\nFile: " + file.getName() +
                        "\nFound: " + checksum +
                        "\nRequired: " + this.checksum.get(file.getName()));
            }
        }
    }

    private void verifyData() throws BackupException {
        Path[] dataFiles;
        String checksum;
        for (int i = 0; i < metadata.dataDirs.length; ++i) {
            dataFiles = getDataFiles(backupPath, i);
            if (dataFiles.length == 0) {
                throw new BackupException("No data files at index " + i + ".");
            }
            for (Path file : dataFiles) {
                checksum = DigestUtils.getHexDigest(metadata.checksumAlgo, file);
                if (!checksum.equals(this.checksum.get(file.getName()))) {
                    throw new BackupException("Could not verify data files at index " + i + "." +
                            "\nFile: " + file.getName() +
                            "\nFound: " + checksum +
                            "\nRequired: " + this.checksum.get(file.getName()));
                }
            }
        }
    }

    private void verifyExtras() throws BackupException {
        Path miscFile;
        try {
            miscFile = backupFile.getMiscFile(metadata.crypto);
        } catch (IOException ignore) {
            // There are no permissions, just skip
            return;
        }
        String checksum = DigestUtils.getHexDigest(metadata.checksumAlgo, miscFile);
        if (!checksum.equals(this.checksum.get(miscFile.getName()))) {
            throw new BackupException("Could not verify extras." +
                    "\nFile: " + miscFile.getName() +
                    "\nFound: " + checksum +
                    "\nRequired: " + this.checksum.get(miscFile.getName()));
        }
    }

    private void verifyRules() throws BackupException {
        Path rulesFile;
        try {
            rulesFile = backupFile.getRulesFile(metadata.crypto);
        } catch (IOException e) {
            if (metadata.hasRules) {
                throw new BackupException("Rules file is missing.", e);
            } else {
                // There are no rules, just skip
                return;
            }
        }
        String checksum = DigestUtils.getHexDigest(metadata.checksumAlgo, rulesFile);
        if (!checksum.equals(this.checksum.get(rulesFile.getName()))) {
            throw new BackupException("Could not verify rules file." +
                    "\nFile: " + rulesFile.getName() +
                    "\nFound: " + checksum +
                    "\nRequired: " + this.checksum.get(rulesFile.getName()));
        }
    }

    @NonNull
    private Path[] getSourceFiles(@NonNull Path backupPath) {
        String mode = CryptoUtils.getExtension(metadata.crypto);
        return backupPath.listFiles((dir, name) -> name.startsWith(SOURCE_PREFIX) && name.endsWith(mode));
    }

    @NonNull
    private Path[] getKeyStoreFiles(@NonNull Path backupPath) {
        String mode = CryptoUtils.getExtension(metadata.crypto);
        return backupPath.listFiles((dir, name) -> name.startsWith(KEYSTORE_PREFIX) && name.endsWith(mode));
    }

    @NonNull
    private Path[] getDataFiles(@NonNull Path backupPath, int index) {
        String mode = CryptoUtils.getExtension(metadata.crypto);
        final String dataPrefix = DATA_PREFIX + index;
        return backupPath.listFiles((dir, name) -> name.startsWith(dataPrefix) && name.endsWith(mode));
    }
}