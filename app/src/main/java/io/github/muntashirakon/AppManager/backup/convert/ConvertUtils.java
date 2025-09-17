// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.NonNull;

import com.android.apksig.ApkVerifier;
import com.android.apksig.apk.ApkFormatException;
import com.android.apksig.util.DataSource;
import com.android.apksig.util.DataSources;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.backup.BackupCryptSetupHelper;
import io.github.muntashirakon.AppManager.backup.BackupItems;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV2;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.crypto.DummyCrypto;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.io.FileSystemManager;
import io.github.muntashirakon.io.Path;

public final class ConvertUtils {
    public static final String TAG = ConvertUtils.class.getSimpleName();

    @NonNull
    public static BackupMetadataV5 getV5Metadata(@NonNull BackupMetadataV2 metadataV2,
                                                 @NonNull BackupItems.BackupItem backupItem)
            throws CryptoException {
        // Here we don't care about the crypto we had for metdataV2, because the crypto that the
        // imported backups use may be different from the one configured for this app
        String compressionMethod = Prefs.BackupRestore.getCompressionMethod();
        String crypto = CryptoUtils.getMode();
        BackupCryptSetupHelper cryptoHelper = new BackupCryptSetupHelper(crypto, MetadataManager.getCurrentBackupMetaVersion());
        BackupMetadataV5.Info info = new BackupMetadataV5.Info(metadataV2.backupTime,
                metadataV2.flags, metadataV2.userId, compressionMethod, DigestUtils.SHA_256, crypto,
                cryptoHelper.getIv(), cryptoHelper.getAes(), cryptoHelper.getKeyIds());
        info.setBackupItem(backupItem);
        BackupMetadataV5.Metadata metadata = new BackupMetadataV5.Metadata(backupItem.getBackupName());
        metadata.hasRules = metadataV2.hasRules;
        metadata.label = metadataV2.label;
        metadata.packageName = metadataV2.packageName;
        metadata.versionName = metadataV2.versionName;
        metadata.versionCode = metadataV2.versionCode;
        if (metadataV2.dataDirs != null) {
            metadata.dataDirs = metadataV2.dataDirs.clone();
        }
        metadata.isSystem = metadataV2.isSystem;
        metadata.isSplitApk = metadataV2.isSplitApk;
        if (metadataV2.splitConfigs != null) {
            metadata.splitConfigs = metadataV2.splitConfigs.clone();
        }
        metadata.apkName = metadataV2.apkName;
        metadata.instructionSet = metadataV2.instructionSet;
        metadata.keyStore = metadataV2.keyStore;
        metadata.installer = metadataV2.installer;
        return new BackupMetadataV5(info, metadata);
    }

    @NonNull
    public static Path[] decryptSourceFiles(@NonNull Path[] files,
                                            @NonNull Crypto crypto,
                                            @NonNull String cryptoMode,
                                            @NonNull BackupItems.BackupItem backupItem)
            throws IOException {
        if (crypto instanceof DummyCrypto) {
            return files;
        }
        List<Path> newFileList = new ArrayList<>();
        // Get desired extension
        String ext = CryptoUtils.getExtension(cryptoMode);
        // Create necessary files (1-1 correspondence)
        for (Path inputFile : files) {
            Path parent = backupItem.requireUnencryptedBackupPath();
            String filename = inputFile.getName();
            String outputFilename = filename.substring(0, filename.lastIndexOf(ext));
            Path outputPath = parent.createNewFile(outputFilename, null);
            newFileList.add(outputPath);
            Log.i(TAG, "Input: %s\nOutput: %s", inputFile, outputPath);
        }
        Path[] newFiles = newFileList.toArray(new Path[0]);
        // Perform actual decryption
        crypto.decrypt(files, newFiles);
        return newFiles;
    }

    @NonNull
    public static Converter getConversionUtil(@ImportType int backupType, Path file) {
        switch (backupType) {
            case ImportType.OAndBackup:
                return new OABConverter(file);
            case ImportType.TitaniumBackup:
                return new TBConverter(file);
            case ImportType.SwiftBackup:
                return new SBConverter(file);
            default:
                throw new IllegalArgumentException("Unsupported import type " + backupType);
        }
    }

    @NonNull
    public static Path[] getRelevantImportFiles(@NonNull Path baseLocation, @ImportType int backupType) {
        switch (backupType) {
            case ImportType.OAndBackup:
                // Package directories
                return baseLocation.listFiles(Path::isDirectory);
            case ImportType.TitaniumBackup:
                // Properties files
                return baseLocation.listFiles((dir, name) -> name.endsWith(".properties"));
            case ImportType.SwiftBackup:
                // XML files
                return baseLocation.listFiles((dir, name) -> name.endsWith(".xml"));
            default:
                throw new IllegalArgumentException("Unsupported import type " + backupType);
        }
    }

    @SuppressLint("SdCardPath")
    @NonNull
    static String[] getDataDirs(String packageName, int userHandle, boolean hasInternal, boolean hasExternal, boolean hasObb) {
        List<String> dataDirs = new ArrayList<>(2);
        if (hasInternal) {
            dataDirs.add("/data/user/" + userHandle + "/" + packageName);
        }
        if (hasExternal) {
            dataDirs.add("/storage/emulated/" + userHandle + "/Android/data/" + packageName);
        }
        if (hasObb) {
            dataDirs.add("/storage/emulated/" + userHandle + "/Android/obb/" + packageName);
        }
        return dataDirs.toArray(new String[0]);
    }

    @NonNull
    static String[] getChecksumsFromApk(@NonNull Path apkFile, @DigestUtils.Algorithm String algo)
            throws IOException, ApkFormatException, NoSuchAlgorithmException, CertificateEncodingException {
        // Since we can't directly work with ProxyFile, we need to cache it and read the signature
        FileChannel fileChannel;
        try {
            fileChannel = apkFile.openFileChannel(FileSystemManager.MODE_READ_ONLY);
        } catch (IOException e) {
            File cachedFile = FileCache.getGlobalFileCache().getCachedFile(apkFile);
            fileChannel = new RandomAccessFile(cachedFile, "r").getChannel();
        }
        DataSource dataSource = DataSources.asDataSource(fileChannel);
        List<String> checksums = new ArrayList<>(1);
        ApkVerifier verifier = new ApkVerifier.Builder(dataSource)
                .setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT)
                .build();
        ApkVerifier.Result apkVerifierResult = verifier.verify();
        // Get signer certificates
        List<X509Certificate> certificates = apkVerifierResult.getSignerCertificates();
        if (certificates != null && !certificates.isEmpty()) {
            for (X509Certificate certificate : certificates) {
                checksums.add(DigestUtils.getHexDigest(algo, certificate.getEncoded()));
            }
        }
        return checksums.toArray(new String[0]);
    }
}
