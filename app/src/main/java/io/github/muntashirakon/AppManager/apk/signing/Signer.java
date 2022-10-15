// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.security.KeyStoreException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.util.Collections;
import java.util.List;

import aosp.libcore.util.HexEncoding;
import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.DigestUtils;

public class Signer {
    public static final String TAG = "Signer";
    public static final String SIGNING_KEY_ALIAS = "signing_key";

    public static boolean canSign() {
        try {
            // In order to sign an APK, a signing key must be inserted
            return KeyStoreManager.getInstance().containsKey(Signer.SIGNING_KEY_ALIAS);
        } catch (Exception e) {
            // Signing not configured
            return false;
        }
    }

    @NonNull
    public static Signer getInstance(SigSchemes sigSchemes) throws SignatureException {
        try {
            KeyStoreManager manager = KeyStoreManager.getInstance();
            KeyPair signingKey = manager.getKeyPair(SIGNING_KEY_ALIAS);
            if (signingKey == null) {
                throw new KeyStoreException("Alias " + SIGNING_KEY_ALIAS + " does not exist in KeyStore.");
            }
            return new Signer(sigSchemes, signingKey.getPrivateKey(), (X509Certificate) signingKey.getCertificate());
        } catch (Exception e) {
            throw new SignatureException(e);
        }
    }

    @NonNull
    private final PrivateKey privateKey;
    @NonNull
    private final X509Certificate certificate;
    @NonNull
    private final SigSchemes sigSchemes;
    @Nullable
    private File idsigFile;

    private Signer(@NonNull SigSchemes sigSchemes, @NonNull PrivateKey privateKey, @NonNull X509Certificate certificate) {
        this.sigSchemes = sigSchemes;
        this.privateKey = privateKey;
        this.certificate = certificate;
    }

    public boolean isV4SchemeEnabled() {
        return sigSchemes.v4SchemeEnabled();
    }

    public void setIdsigFile(@Nullable File idsigFile) {
        this.idsigFile = idsigFile;
    }

    public boolean sign(File in, File out) {
        return sign(in, out, -1);
    }

    public boolean sign(File in, File out, int minSdk) {
        return sign(in, out, minSdk, false);
    }

    public boolean sign(File in, File out, int minSdk, boolean alignFileSize) {
        ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder("CERT",
                privateKey, Collections.singletonList(certificate)).build();
        ApkSigner.Builder builder = new ApkSigner.Builder(Collections.singletonList(signerConfig));
        builder.setInputApk(in);
        builder.setOutputApk(out);
        builder.setCreatedBy("AppManager");
        builder.setAlignFileSize(alignFileSize);
        if (minSdk != -1) builder.setMinSdkVersion(minSdk);
        if (sigSchemes.v1SchemeEnabled()) {
            builder.setV1SigningEnabled(true);
        }
        if (sigSchemes.v2SchemeEnabled()) {
            builder.setV2SigningEnabled(true);
        }
        if (sigSchemes.v3SchemeEnabled()) {
            builder.setV3SigningEnabled(true);
        }
        if (sigSchemes.v4SchemeEnabled()) {
            if (idsigFile == null) {
                throw new RuntimeException("idsig file is mandatory for v4 signature scheme.");
            }
            builder.setV4SigningEnabled(true);
            builder.setV4SignatureOutputFile(idsigFile);
        }
        ApkSigner signer = builder.build();
        Log.i(TAG, String.format("SignApk: %s", in));
        try {
            signer.sign();
            Log.i(TAG, "The signature is complete and the output file is " + out);
            return true;
        } catch (Exception e) {
            Log.w(TAG, e);
            return false;
        }
    }

    public static boolean verify(@NonNull SigSchemes sigSchemes, @NonNull File apk, @Nullable File idsig) {
        ApkVerifier.Builder builder = new ApkVerifier.Builder(apk)
                .setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT);
        if (sigSchemes.v4SchemeEnabled()) {
            if (idsig == null) {
                throw new RuntimeException("idsig file is mandatory for v4 signature scheme.");
            }
            builder.setV4SignatureFile(idsig);
        }
        ApkVerifier verifier = builder.build();
        try {
            ApkVerifier.Result result = verifier.verify();
            Log.i(TAG, apk.toString());
            boolean isVerify = result.isVerified();
            if (isVerify) {
                if (sigSchemes.v1SchemeEnabled() && result.isVerifiedUsingV1Scheme())
                    Log.i(TAG, "V1 signature verification succeeded.");
                else Log.w(TAG, "V1 signature verification failed/disabled.");
                if (sigSchemes.v2SchemeEnabled() && result.isVerifiedUsingV2Scheme())
                    Log.i(TAG, "V2 signature verification succeeded.");
                else Log.w(TAG, "V2 signature verification failed/disabled.");
                if (sigSchemes.v3SchemeEnabled() && result.isVerifiedUsingV3Scheme())
                    Log.i(TAG, "V3 signature verification succeeded.");
                else Log.w(TAG, "V3 signature verification failed/disabled.");
                if (sigSchemes.v4SchemeEnabled() && result.isVerifiedUsingV4Scheme())
                    Log.i(TAG, "V4 signature verification succeeded.");
                else Log.w(TAG, "V4 signature verification failed/disabled.");
                int i = 0;
                List<X509Certificate> signerCertificates = result.getSignerCertificates();
                Log.i(TAG, "Number of signatures: " + signerCertificates.size());
                for (X509Certificate logCert : signerCertificates) {
                    i++;
                    logCert(logCert, "Signature" + i);
                }
            }
            for (ApkVerifier.IssueWithParams warn : result.getWarnings()) {
                Log.w(TAG, warn.toString());
            }
            for (ApkVerifier.IssueWithParams err : result.getErrors()) {
                Log.e(TAG, err.toString());
            }
            if (sigSchemes.v1SchemeEnabled()) {
                for (ApkVerifier.Result.V1SchemeSignerInfo signer : result.getV1SchemeIgnoredSigners()) {
                    String name = signer.getName();
                    for (ApkVerifier.IssueWithParams err : signer.getErrors()) {
                        Log.e(TAG, name + ": " + err);
                    }
                    for (ApkVerifier.IssueWithParams err : signer.getWarnings()) {
                        Log.w(TAG, name + ": " + err);
                    }
                }
            }
            return isVerify;
        } catch (Exception e) {
            Log.w(TAG, "Verification failed.", e);
            return false;
        }
    }

    public static void logCert(@NonNull X509Certificate x509Certificate, CharSequence charSequence) throws CertificateEncodingException {
        int bitLength;
        Principal subjectDN = x509Certificate.getSubjectDN();
        Log.i(TAG, charSequence + " - Unique distinguished name: " + subjectDN);
        logEncoded(charSequence, x509Certificate.getEncoded());
        PublicKey publicKey = x509Certificate.getPublicKey();
        if (publicKey instanceof RSAKey) {
            bitLength = ((RSAKey) publicKey).getModulus().bitLength();
        } else if (publicKey instanceof ECKey) {
            bitLength = ((ECKey) publicKey).getParams().getOrder().bitLength();
        } else if (publicKey instanceof DSAKey) {
            DSAParams params = ((DSAKey) publicKey).getParams();
            if (params != null) {
                bitLength = params.getP().bitLength();
            } else bitLength = -1;
        } else {
            bitLength = -1;
        }
        Log.i(TAG, charSequence + " - key size: " + (bitLength != -1 ? String.valueOf(bitLength) : "Unknown"));
        String algorithm = publicKey.getAlgorithm();
        Log.i(TAG, charSequence + " - key algorithm: " + algorithm);
        logEncoded(charSequence, publicKey.getEncoded());
    }

    private static void logEncoded(CharSequence charSequence, byte[] bArr) {
        log(charSequence + " - SHA-256: ", DigestUtils.getDigest(DigestUtils.SHA_256, bArr));
        log(charSequence + " - SHA-1: ", DigestUtils.getDigest(DigestUtils.SHA_1, bArr));
        log(charSequence + " - MD5: ", DigestUtils.getDigest(DigestUtils.MD5, bArr));
    }

    private static void log(String str, byte[] bArr) {
        Log.i(TAG, str);
        Log.w(TAG, HexEncoding.encodeToString(bArr));
    }
}
