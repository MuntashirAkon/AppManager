/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.apk;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.preference.PreferenceManager;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import aosp.libcore.util.HexEncoding;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import sun.security.pkcs.PKCS8Key;

public class SignUtils {
    @NonNull
    public static SignUtils getInstance(Context context) throws SignatureException {
        msgId = null;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        boolean isCustom = sp.getBoolean("custom_signature_file", false);
        if (isCustom) {
            int type = sp.getInt("key_type", 0);
            String keyPath = sp.getString("key_path", "");
            String cert_or_alias = sp.getString("cert_or_alias", "");
            String store_pass = sp.getString("store_pass", "");
            String key_pass = sp.getString("key_pass", "");
            try {
                if (type == 3) {
                    return loadKey(keyPath, cert_or_alias);
                } else {
                    return loadKey(context, keyPath, type, cert_or_alias, store_pass, key_pass);
                }
            } catch (Exception e) {
                throw new SignatureException("Unable to sign apk.", e);
            }
        }
        try {
            return loadKey(context.getAssets());
        } catch (Exception e) {
            throw new SignatureException("Unable to sign apk.", e);
        }
    }

    @NonNull
    private static SignUtils loadKey(AssetManager assets) throws IOException, InvalidKeyException, CertificateException {
        SignUtils signUtils = new SignUtils();
        signUtils.privateKey = getPrivateKey(assets);
        signUtils.certificate = getCert(assets);
        return signUtils;
    }

    @NonNull
    private static X509Certificate getCert(AssetManager assets) throws IOException, CertificateException {
        try (InputStream cert = assets.open("key/testkey.x509.pem")) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").
                    generateCertificate(cert);
        }
    }

    @NonNull
    private static PrivateKey getPrivateKey(AssetManager assets) throws IOException, InvalidKeyException {
        try (InputStream key = assets.open("key/testkey.pk8")) {
            PKCS8Key pkcs8 = new PKCS8Key();
            pkcs8.decode(key);
            return pkcs8;
        }
    }

    private static final String[] types = {"JKS", "PKCS12", "BKS"};

    @NonNull
    private static SignUtils loadKey(Context context, String keyPath, int type, String alias, String store_pass, String key_pass)
            throws KeyStoreException, UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, IOException {
        if (!exists(keyPath)) throw new FileNotFoundException(keyPath + " not found.");
        String keyType = types[type];
        final KeyStore ks = KeyStore.getInstance(keyType);
        if (store_pass.isEmpty()) {
            showPasswd(context, ks, keyPath, alias);
            throw new IOException("Not implemented yet");
        } else {
            char[] storePass = store_pass.toCharArray();
            char[] keyPass;
            if (key_pass.isEmpty()) keyPass = storePass;
            else keyPass = key_pass.toCharArray();
            return loadKey(ks, keyPath, alias, storePass, keyPass);
        }
    }

    private static void showPasswd(final Context context, final KeyStore ks, final String keyPath, final String alias) {
//        View view = LayoutInflater.from(context).inflate(R.layout.input_password, null);
//        final EditText storePass = view.findViewById(R.id.ks_pass);
//        final EditText keyPass = view.findViewById(R.id.alias_pass);
//        TextView msg = view.findViewById(R.id.msg);
//        msg.setText(keyPath);
//        new AlertDialog.Builder(context)
//                .setTitle(R.string.enter_password)
//                .setView(view)
//                .setPositiveButton(R.string.ok, (dialog, which) -> {
//                    String store_pass = storePass.getText().toString();
//                    String key_pass = keyPass.getText().toString();
//                    if (key_pass.isEmpty())
//                        key_pass = store_pass;
//                    loadKey(ks, keyPath, alias, store_pass.toCharArray(), key_pass.toCharArray());
//                })
//                .setNegativeButton(R.string.cancel, null)
//                .show();
    }

    @NonNull
    private static SignUtils loadKey(@NonNull KeyStore ks, String keyPath, @NonNull String alias, char[] storePass, char[] keyPass)
            throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException {
        InputStream i = new FileInputStream(keyPath);
        ks.load(i, storePass);
        if (alias.isEmpty()) alias = ks.aliases().nextElement();
        PrivateKey prk = (PrivateKey) ks.getKey(alias, keyPass);
        X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
        SignUtils st = new SignUtils();
        st.privateKey = prk;
        st.certificate = cert;
        return st;
    }

    @NonNull
    private static SignUtils loadKey(String keyPath, String certPath) throws Exception {
        if (!exists(keyPath)) throw new FileNotFoundException(keyPath + " not found.");
        if (!exists(certPath))  throw new FileNotFoundException(certPath + " not found.");
        InputStream pk = new FileInputStream(keyPath);
        byte[] data = IOUtils.readFully(pk, -1, true);
        pk.close();
        InputStream cer = new FileInputStream(certPath);
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509").
                generateCertificate(cer);
        cer.close();
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(data);
        PrivateKey prk = KeyFactory.getInstance(cert.getPublicKey().getAlgorithm()).generatePrivate(spec);
        SignUtils signUtils = new SignUtils();
        signUtils.privateKey = prk;
        signUtils.certificate = cert;
        return signUtils;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean exists(String path) {
        if (new File(path).exists())
            return true;
        msgId = "Signature file " + path + " not found, using default signature!";
        return false;
    }

    private static String msgId;
    private PrivateKey privateKey;
    private X509Certificate certificate;

    private SignUtils() {
    }

    public boolean sign(File in, File out, int minSdk) {
        if (msgId != null) Log.w("SignUtils::sign", msgId);
        ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder("CERT",
                privateKey, Collections.singletonList(certificate)).build();
        ApkSigner.Builder builder = new ApkSigner.Builder(Collections.singletonList(signerConfig));
        builder.setInputApk(in);
        builder.setOutputApk(out);
        builder.setCreatedBy("AppManager");
        if (minSdk != -1) builder.setMinSdkVersion(minSdk);
        builder.setV1SigningEnabled(true);
        builder.setV2SigningEnabled(true);
        ApkSigner signer = builder.build();
        Log.i("SignUtils::sign", String.format("SignApk: %s", in));
        try {
            signer.sign();
            Log.i("SignUtils::sign", "The signature is complete and the output file is " + out);
            return true;
        } catch (Exception e) {
            Log.w("SignUtils::sign", e);
            return false;
        }
    }

    public static boolean verify(File apk) {
        ApkVerifier.Builder builder = new ApkVerifier.Builder(apk);
        ApkVerifier verifier = builder.build();
        try {
            ApkVerifier.Result result = verifier.verify();
            Log.i("SignUtils::verify", apk.toString());
            boolean isVerify = result.isVerified();
            if (isVerify) {
                if (result.isVerifiedUsingV1Scheme())
                    Log.i("SignUtils::verify", "V1 signature verification succeeded.");
                else Log.w("SignUtils::verify", "V1 signature verification failed.");
                if (result.isVerifiedUsingV2Scheme())
                    Log.i("SignUtils::verify", "V2 signature verification succeeded.");
                else Log.w("SignUtils::verify", "V2 signature verification failed.");
                int i = 0;
                List<X509Certificate> signerCertificates = result.getSignerCertificates();
                Log.i("SignUtils::verify", "Number of signatures: " + signerCertificates.size());
                for (X509Certificate logCert : signerCertificates) {
                    i++;
                    logCert(logCert, "Signature" + i);
                }
            }
            for (ApkVerifier.IssueWithParams warn : result.getWarnings()) {
                Log.w("SignUtils::verify", warn.toString());
            }
            for (ApkVerifier.IssueWithParams err : result.getErrors()) {
                Log.e("SignUtils::verify", err.toString());
            }
            for (ApkVerifier.Result.V1SchemeSignerInfo signer : result.getV1SchemeIgnoredSigners()) {
                String name = signer.getName();
                for (ApkVerifier.IssueWithParams err : signer.getErrors()) {
                    Log.e("SignUtils::verify", "JAR signer" + name + ": " + err);
                }
                for (ApkVerifier.IssueWithParams err : signer.getWarnings()) {
                    Log.w("SignUtils::verify", "JAR signer " + name + ": " + err);
                }
            }
            return isVerify;
        } catch (Exception e) {
            Log.w("SignUtils::verify", "Verification failed.", e);
            return false;
        }
    }

    public static void logCert(@NonNull X509Certificate x509Certificate, CharSequence charSequence) throws CertificateEncodingException {
        int bitLength;
        Principal subjectDN = x509Certificate.getSubjectDN();
        Log.i("SignUtils::logCert", charSequence + " - Unique distinguished name: " + subjectDN);
        logEncoded(charSequence, x509Certificate.getEncoded());
        PublicKey publicKey = x509Certificate.getPublicKey();
        if (publicKey instanceof RSAKey) {
            bitLength = ((RSAKey) publicKey).getModulus().bitLength();
        } else if (publicKey instanceof ECKey) {
            bitLength = ((ECKey) publicKey).getParams().getOrder().bitLength();
        } else {
            if (publicKey instanceof DSAKey) {
                DSAParams params = ((DSAKey) publicKey).getParams();
                if (params != null) {
                    bitLength = params.getP().bitLength();
                }
            }
            bitLength = -1;
        }
        Log.i("SignUtils::logCert", charSequence + " - key size: " + (bitLength != -1 ? String.valueOf(bitLength) : "Unknown"));
        logKey(publicKey, charSequence);
    }

    private static void logEncoded(CharSequence charSequence, byte[] bArr) {
        log(charSequence + " - SHA-256: ", DigestUtils.getDigest(DigestUtils.SHA_256, bArr));
        log(charSequence + " - SHA-1: ", DigestUtils.getDigest(DigestUtils.SHA_1, bArr));
        log(charSequence + " - MD5: ", DigestUtils.getDigest(DigestUtils.MD5, bArr));
    }

    public static void logKey(@NonNull Key key, CharSequence charSequence) {
        String algorithm = key.getAlgorithm();
        Log.i("SignUtils::logKey", charSequence + " - key algorithm: " + algorithm);
        logEncoded(charSequence, key.getEncoded());
    }

    private static void log(String str, byte[] bArr) {
        Log.i("SignUtils:log", str);
        Log.w("SignUtils:log", HexEncoding.encodeToString(bArr));
    }
}
