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
    private final Signature[] mCurrentSignatures;
    @Nullable
    private final Signature[] mAllSignatures;

    public SignerInfo(@NonNull ApkVerifier.Result apkVerifierResult) throws CertificateEncodingException {
        List<X509Certificate> certificates = apkVerifierResult.getSignerCertificates();
        if (certificates == null) {
            mCurrentSignatures = null;
        } else {
            mCurrentSignatures = new Signature[certificates.size()];
            int i = 0;
            for (X509Certificate certificate : certificates) {
                mCurrentSignatures[i++] = new Signature(certificate.getEncoded());
            }
        }
        if (mCurrentSignatures == null || mCurrentSignatures.length > 1) {
            // Skip checking rotation because the app has multiple signers or no signer at all
            mAllSignatures = mCurrentSignatures;
            return;
        }
        SigningCertificateLineage lineage = apkVerifierResult.getSigningCertificateLineage();
        if (lineage == null) {
            // There is no SigningCertificateLineage block
            mAllSignatures = mCurrentSignatures;
            return;
        }
        List<X509Certificate> certificatesInLineage = lineage.getCertificatesInLineage();
        if (certificatesInLineage == null) {
            // There is no certificate in the SigningCertificateLineage block
            mAllSignatures = mCurrentSignatures;
            return;
        }
        // At this point, currentSignatures is a singleton array
        mAllSignatures = new Signature[mCurrentSignatures.length + certificatesInLineage.size()];
        int i = 0;
        // Add the current signature on top
        for (Signature signature : mCurrentSignatures) {
            mAllSignatures[i++] = signature;
        }
        for (X509Certificate certificate : certificatesInLineage) {
            mAllSignatures[i++] = new Signature(certificate.getEncoded());
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    public SignerInfo(@Nullable SigningInfo signingInfo) {
        if (signingInfo == null) {
            mCurrentSignatures = null;
            mAllSignatures = null;
            return;
        }
        mCurrentSignatures = signingInfo.getApkContentsSigners();
        if (signingInfo.hasMultipleSigners() || !signingInfo.hasPastSigningCertificates()) {
            mAllSignatures = signingInfo.getApkContentsSigners();
        } else {
            mAllSignatures = ArrayUtils.concatElements(
                    Signature.class,
                    signingInfo.getApkContentsSigners(),
                    signingInfo.getSigningCertificateHistory());
        }
    }

    public SignerInfo(@Nullable Signature[] signatures) {
        mCurrentSignatures = signatures;
        mAllSignatures = signatures;
    }

    public boolean hasMultipleSigners() {
        return mCurrentSignatures != null && mCurrentSignatures.length > 1;
    }

    public boolean hasProofOfRotation() {
        return !hasMultipleSigners() && mAllSignatures != null && mAllSignatures.length > 1;
    }

    @Nullable
    public Signature[] getCurrentSignatures() {
        return mCurrentSignatures;
    }

    /**
     * Retrieve all signatures, including the lineage ones. The current signature(s) are on top of the array.
     *
     * <p>If the APK has multiple signers, all signatures are the current signatures, and if the APK has only one
     * signer, the first signature is the current signature and rests are the lineage signature.
     */
    @Nullable
    public Signature[] getAllSignatures() {
        return mAllSignatures;
    }
}
