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

import io.github.muntashirakon.AppManager.backup.BackupException;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.io.FileSystemManager;
import io.github.muntashirakon.io.Path;

public final class ConvertUtils {
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
    static Crypto setupCrypto(MetadataManager.Metadata metadata) throws BackupException {
        try {
            // Setup crypto
            CryptoUtils.setupCrypto(metadata);
            return CryptoUtils.getCrypto(metadata);
        } catch (CryptoException e) {
            throw new BackupException("Failed to get crypto " + metadata.crypto, e);
        }
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
        if (certificates != null && certificates.size() > 0) {
            for (X509Certificate certificate : certificates) {
                checksums.add(DigestUtils.getHexDigest(algo, certificate.getEncoded()));
            }
        }
        return checksums.toArray(new String[0]);
    }
}
