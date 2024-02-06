// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing;

import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.apksig.ApkVerifier;
import com.android.apksig.SigningCertificateLineage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

public class SignerInfo {
    @Nullable
    private final X509Certificate[] mCurrentSignerCerts;
    @Nullable
    private final X509Certificate[] mSignerCertsInLineage;
    @Nullable
    private final X509Certificate[] mAllSignerCerts;
    @Nullable
    private final X509Certificate mSourceStampCert;

    public SignerInfo(@NonNull ApkVerifier.Result apkVerifierResult) {
        List<X509Certificate> certificates = apkVerifierResult.getSignerCertificates();
        if (certificates == null || certificates.isEmpty()) {
            mCurrentSignerCerts = null;
        } else {
            mCurrentSignerCerts = new X509Certificate[certificates.size()];
            int i = 0;
            for (X509Certificate certificate : certificates) {
                mCurrentSignerCerts[i++] = certificate;
            }
        }
        // Collect source stamp certificate
        ApkVerifier.Result.SourceStampInfo sourceStampInfo = apkVerifierResult.getSourceStampInfo();
        mSourceStampCert = sourceStampInfo != null ? sourceStampInfo.getCertificate() : null;
        if (mCurrentSignerCerts == null || mCurrentSignerCerts.length > 1) {
            // Skip checking rotation because the app has multiple signers or no signer at all
            mAllSignerCerts = mCurrentSignerCerts;
            mSignerCertsInLineage = null;
            return;
        }
        SigningCertificateLineage lineage = apkVerifierResult.getSigningCertificateLineage();
        if (lineage == null) {
            // There is no SigningCertificateLineage block
            mAllSignerCerts = mCurrentSignerCerts;
            mSignerCertsInLineage = null;
            return;
        }
        List<X509Certificate> certificatesInLineage = lineage.getCertificatesInLineage();
        if (certificatesInLineage == null || certificatesInLineage.isEmpty()) {
            // There is no certificate in the SigningCertificateLineage block
            mAllSignerCerts = mCurrentSignerCerts;
            mSignerCertsInLineage = null;
            return;
        }
        // At this point, currentSignatures is a singleton array
        mSignerCertsInLineage = certificatesInLineage.toArray(new X509Certificate[0]);
        mAllSignerCerts = new X509Certificate[mCurrentSignerCerts.length + certificatesInLineage.size()];
        int i = 0;
        // Add the current signature on top
        for (X509Certificate signature : mCurrentSignerCerts) {
            mAllSignerCerts[i++] = signature;
        }
        for (X509Certificate certificate : certificatesInLineage) {
            mAllSignerCerts[i++] = certificate;
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    public SignerInfo(@Nullable SigningInfo signingInfo) {
        mSourceStampCert = null;
        if (signingInfo == null) {
            mCurrentSignerCerts = null;
            mSignerCertsInLineage = null;
            mAllSignerCerts = null;
            return;
        }
        Signature[] currentSignatures = signingInfo.getApkContentsSigners();
        Signature[] lineageSignatures = signingInfo.getSigningCertificateHistory();
        boolean isLineage = !signingInfo.hasMultipleSigners() && signingInfo.hasPastSigningCertificates();
        // Validation
        if (currentSignatures == null || currentSignatures.length == 0) {
            // Invalid signatures
            mCurrentSignerCerts = null;
            mSignerCertsInLineage = null;
            mAllSignerCerts = null;
            return;
        }
        if (isLineage && (lineageSignatures == null || lineageSignatures.length == 0)) {
            // Invalid lineage signatures
            mCurrentSignerCerts = null;
            mSignerCertsInLineage = null;
            mAllSignerCerts = null;
            return;
        }
        int totalSigner = currentSignatures.length + (isLineage ? lineageSignatures.length : 0);
        mCurrentSignerCerts = new X509Certificate[currentSignatures.length];
        mAllSignerCerts = new X509Certificate[totalSigner];
        for (int i = 0; i < currentSignatures.length; ++i) {
            X509Certificate cert = generateCertificateOrFail(currentSignatures[i]);
            mCurrentSignerCerts[i] = cert;
            mAllSignerCerts[i] = cert;
        }
        if (isLineage) {
            mSignerCertsInLineage = new X509Certificate[lineageSignatures.length];
            for (int i = currentSignatures.length, j = 0; i < totalSigner; ++i, ++j) {
                X509Certificate cert = generateCertificateOrFail(lineageSignatures[j]);
                mSignerCertsInLineage[j] = cert;
                mAllSignerCerts[i] = cert;
            }
        } else mSignerCertsInLineage = null;
    }

    public SignerInfo(@Nullable Signature[] signatures) {
        mSourceStampCert = null;
        mSignerCertsInLineage = null;
        if (signatures != null && signatures.length > 0) {
            mAllSignerCerts = new X509Certificate[signatures.length];
            mCurrentSignerCerts = new X509Certificate[signatures.length];
            for (int i = 0; i < signatures.length; ++i) {
                X509Certificate cert = generateCertificateOrFail(signatures[i]);
                mAllSignerCerts[i] = cert;
                mCurrentSignerCerts[i] = cert;
            }
        } else {
            mCurrentSignerCerts = null;
            mAllSignerCerts = null;
        }
    }

    public boolean hasMultipleSigners() {
        return mCurrentSignerCerts != null && mCurrentSignerCerts.length > 1;
    }

    public boolean hasProofOfRotation() {
        return !hasMultipleSigners() && mSignerCertsInLineage != null;
    }

    @Nullable
    public X509Certificate[] getCurrentSignerCerts() {
        return mCurrentSignerCerts;
    }

    @Nullable
    public X509Certificate getSourceStampCert() {
        return mSourceStampCert;
    }

    @Nullable
    public X509Certificate[] getSignerCertsInLineage() {
        return mSignerCertsInLineage;
    }

    /**
     * Retrieve all signatures, including the lineage ones. The current signature(s) are on top of the array.
     *
     * <p>If the APK has multiple signers, all signatures are the current signatures, and if the APK has only one
     * signer, the first signature is the current signature and rests are the lineage signature.
     */
    @Nullable
    public X509Certificate[] getAllSignerCerts() {
        return mAllSignerCerts;
    }

    private static X509Certificate generateCertificateOrFail(Signature signature) {
        try (InputStream is = new ByteArrayInputStream(signature.toByteArray())) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(is);
        } catch (IOException | CertificateException e) {
            throw new RuntimeException("Invalid signature", e);
        }
    }
}
