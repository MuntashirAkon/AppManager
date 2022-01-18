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

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;

import io.github.muntashirakon.AppManager.utils.ArrayUtils;

public class SignerInfo {
    @Nullable
    private final Signature[] currentSignatures;
    @Nullable
    private final Signature[] allSignatures;

    public SignerInfo(@NonNull ApkVerifier.Result apkVerifierResult) throws CertificateEncodingException {
        List<X509Certificate> certificates = apkVerifierResult.getSignerCertificates();
        if (certificates == null) {
            currentSignatures = null;
        } else {
            currentSignatures = new Signature[certificates.size()];
            int i = 0;
            for (X509Certificate certificate : certificates) {
                currentSignatures[i++] = new Signature(certificate.getEncoded());
            }
        }
        if (currentSignatures == null || currentSignatures.length > 1) {
            // Skip checking rotation because the app has multiple signers or no signer at all
            allSignatures = currentSignatures;
            return;
        }
        SigningCertificateLineage lineage = apkVerifierResult.getSigningCertificateLineage();
        if (lineage == null) {
            // There is no SigningCertificateLineage block
            allSignatures = currentSignatures;
            return;
        }
        List<X509Certificate> certificatesInLineage = lineage.getCertificatesInLineage();
        if (certificatesInLineage == null) {
            // There is no certificate in the SigningCertificateLineage block
            allSignatures = currentSignatures;
            return;
        }
        // At this point, currentSignatures is a singleton array
        allSignatures = new Signature[currentSignatures.length + certificatesInLineage.size()];
        int i = 0;
        // Add the current signature on top
        for (Signature signature : currentSignatures) {
            allSignatures[i++] = signature;
        }
        for (X509Certificate certificate : certificatesInLineage) {
            allSignatures[i++] = new Signature(certificate.getEncoded());
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    public SignerInfo(@Nullable SigningInfo signingInfo) {
        if (signingInfo == null) {
            currentSignatures = null;
            allSignatures = null;
            return;
        }
        currentSignatures = signingInfo.getApkContentsSigners();
        if (signingInfo.hasMultipleSigners() || !signingInfo.hasPastSigningCertificates()) {
            allSignatures = signingInfo.getApkContentsSigners();
        } else {
            allSignatures = ArrayUtils.concatElements(
                    Signature.class,
                    signingInfo.getApkContentsSigners(),
                    signingInfo.getSigningCertificateHistory());
        }
    }

    public SignerInfo(@Nullable Signature[] signatures) {
        currentSignatures = signatures;
        allSignatures = signatures;
    }

    public boolean hasMultipleSigners() {
        return currentSignatures != null && currentSignatures.length > 1;
    }

    public boolean hasProofOfRotation() {
        return !hasMultipleSigners() && allSignatures != null && allSignatures.length > 1;
    }

    @Nullable
    public Signature[] getCurrentSignatures() {
        return currentSignatures;
    }

    /**
     * Retrieve all signatures, including the lineage ones. The current signature(s) are on top of the array.
     *
     * <p>If the APK has multiple signers, all signatures are the current signatures, and if the APK has only one
     * signer, the first signature is the current signature and rests are the lineage signature.
     */
    @Nullable
    public Signature[] getAllSignatures() {
        return allSignatures;
    }
}
