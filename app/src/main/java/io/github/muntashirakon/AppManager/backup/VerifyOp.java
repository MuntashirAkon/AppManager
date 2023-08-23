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
    private final BackupFlags mBackupFlags;
    @NonNull
    private final MetadataManager.Metadata mMetadata;
    @NonNull
    private final Path mBackupPath;
    @NonNull
    private final BackupFiles.BackupFile mBackupFile;
    @NonNull
    private final Crypto mCrypto;
    @NonNull
    private final BackupFiles.Checksum mChecksum;
    private final List<Path> mDecryptedFiles = new ArrayList<>();

    VerifyOp(@NonNull MetadataManager metadataManager, @NonNull BackupFiles.BackupFile backupFile)
            throws BackupException {
        mBackupFile = backupFile;
        mBackupPath = mBackupFile.getBackupPath();
        try {
            metadataManager.readMetadata(mBackupFile);
            mMetadata = metadataManager.getMetadata();
            mBackupFlags = mMetadata.flags;
        } catch (IOException e) {
            throw new BackupException("Could not read metadata. Possibly due to a malformed json file.", e);
        }
        // Setup crypto
        if (!CryptoUtils.isAvailable(mMetadata.crypto)) {
            throw new BackupException("Mode " + mMetadata.crypto + " is currently unavailable.");
        }
        try {
            mCrypto = CryptoUtils.getCrypto(mMetadata);
        } catch (CryptoException e) {
            throw new BackupException("Could not get crypto " + mMetadata.crypto, e);
        }
        Path checksumFile;
        try {
            checksumFile = mBackupFile.getChecksumFile(mMetadata.crypto);
        } catch (IOException e) {
            throw new BackupException("Could not get encrypted checksum.txt file.", e);
        }
        // Decrypt checksum
        try {
            synchronized (Crypto.class) {
                mCrypto.decrypt(new Path[]{checksumFile});
                mDecryptedFiles.addAll(Arrays.asList(mCrypto.getNewFiles()));
            }
        } catch (IOException e) {
            throw new BackupException("Could not decrypt " + checksumFile.getName(), e);
        }
        // Get checksums
        try {
            mChecksum = mBackupFile.getChecksum(CryptoUtils.MODE_NO_ENCRYPTION);
        } catch (Throwable e) {
            mBackupFile.cleanup();
            throw new BackupException("Could not get checksums.", e);
        }
        // Verify metadata
        Path metadataFile;
        try {
            metadataFile = mBackupFile.getMetadataFile();
        } catch (IOException e) {
            throw new BackupException("Could not get metadata file.", e);
        }
        String checksum = DigestUtils.getHexDigest(mMetadata.checksumAlgo, metadataFile);
        if (!checksum.equals(mChecksum.get(metadataFile.getName()))) {
            throw new BackupException("Could not verify metadata." +
                    "\nFile: " + metadataFile.getName() +
                    "\nFound: " + checksum +
                    "\nRequired: " + mChecksum.get(metadataFile.getName()));
        }
    }

    @Override
    public void close() {
        Log.d(TAG, "Close called");
        mCrypto.close();
        for (Path file : mDecryptedFiles) {
            Log.d(TAG, "Deleting %s", file);
            file.delete();
        }
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
        Path[] backupSourceFiles = getSourceFiles(mBackupPath);
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
        Path[] keyStoreFiles = getKeyStoreFiles(mBackupPath);
        if (keyStoreFiles.length == 0) {
            throw new BackupException("KeyStore files do not exist.");
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
            dataFiles = getDataFiles(mBackupPath, i);
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
            miscFile = mBackupFile.getMiscFile(mMetadata.crypto);
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
            rulesFile = mBackupFile.getRulesFile(mMetadata.crypto);
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

    @NonNull
    private Path[] getSourceFiles(@NonNull Path backupPath) {
        String mode = CryptoUtils.getExtension(mMetadata.crypto);
        return backupPath.listFiles((dir, name) -> name.startsWith(SOURCE_PREFIX) && name.endsWith(mode));
    }

    @NonNull
    private Path[] getKeyStoreFiles(@NonNull Path backupPath) {
        String mode = CryptoUtils.getExtension(mMetadata.crypto);
        return backupPath.listFiles((dir, name) -> name.startsWith(KEYSTORE_PREFIX) && name.endsWith(mode));
    }

    @NonNull
    private Path[] getDataFiles(@NonNull Path backupPath, int index) {
        String mode = CryptoUtils.getExtension(mMetadata.crypto);
        final String dataPrefix = DATA_PREFIX + index;
        return backupPath.listFiles((dir, name) -> name.startsWith(dataPrefix) && name.endsWith(mode));
    }
}