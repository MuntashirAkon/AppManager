// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import android.annotation.SuppressLint;
import android.os.RemoteException;

import androidx.annotation.NonNull;

import com.android.apksig.ApkVerifier;
import com.android.apksig.apk.ApkFormatException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.io.ProxyFile;
import io.github.muntashirakon.io.ProxyInputStream;

import static io.github.muntashirakon.AppManager.backup.MetadataManager.TAR_TYPES;

public final class ConvertUtils {
    @SuppressLint("SdCardPath")
    @NonNull
    public static String[] getDataDirs(String packageName, int userHandle, boolean hasInternal, boolean hasExternal, boolean hasObb) {
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

    @TarUtils.TarType
    @NonNull
    public static String getTarTypeFromPref() {
        String tarType = (String) AppPref.get(AppPref.PrefKey.PREF_BACKUP_COMPRESSION_METHOD_STR);
        // Verify tar type
        if (ArrayUtils.indexOf(TAR_TYPES, tarType) == -1) {
            // Unknown tar type, set default
            tarType = TarUtils.TAR_GZIP;
        }
        return tarType;
    }

    @NonNull
    public static Crypto setupCrypto(MetadataManager.Metadata metadata) throws BackupException {
        try {
            // Setup crypto
            CryptoUtils.setupCrypto(metadata);
            return CryptoUtils.getCrypto(metadata);
        } catch (CryptoException e) {
            throw new BackupException("Failed to get crypto " + metadata.crypto, e);
        }
    }

    @NonNull
    public static String[] getChecksumsFromApk(File apkFile, @DigestUtils.Algorithm String algo)
            throws IOException, RemoteException, ApkFormatException, NoSuchAlgorithmException,
            CertificateEncodingException {
        if (apkFile instanceof ProxyFile) {
            // Since we can't directly work with ProxyFile, we need to cache it and read the signature
            try (InputStream is = new ProxyInputStream(apkFile)) {
                apkFile = IOUtils.getCachedFile(is);
            }
        } // else Work with the apk file directly
        List<String> checksums = new ArrayList<>(1);
        ApkVerifier verifier = new ApkVerifier.Builder(apkFile).build();
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
