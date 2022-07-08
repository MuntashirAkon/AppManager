// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.sun.misc.BASE64Decoder;
import android.sun.misc.BASE64Encoder;
import android.sun.security.pkcs.PKCS8Key;
import android.sun.security.provider.JavaKeyStoreProvider;
import android.sun.security.provider.X509Factory;
import android.sun.security.x509.AlgorithmId;
import android.sun.security.x509.CertificateAlgorithmId;
import android.sun.security.x509.CertificateExtensions;
import android.sun.security.x509.CertificateIssuerName;
import android.sun.security.x509.CertificateSerialNumber;
import android.sun.security.x509.CertificateSubjectName;
import android.sun.security.x509.CertificateValidity;
import android.sun.security.x509.CertificateVersion;
import android.sun.security.x509.CertificateX509Key;
import android.sun.security.x509.KeyIdentifier;
import android.sun.security.x509.PrivateKeyUsageExtension;
import android.sun.security.x509.SubjectKeyIdentifierExtension;
import android.sun.security.x509.X500Name;
import android.sun.security.x509.X509CertImpl;
import android.sun.security.x509.X509CertInfo;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.DSAPrivateKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.io.IoUtils;

public class KeyStoreUtils {
    public static final String TAG = KeyStoreUtils.class.getSimpleName();

    @NonNull
    public static KeyPair loadDefaultKey(@NonNull Context context)
            throws IOException, InvalidKeyException, CertificateException {
        AssetManager assets = context.getAssets();
        PrivateKey privateKey;
        X509Certificate certificate;
        try (InputStream key = assets.open("key/testkey.pk8")) {
            PKCS8Key pkcs8Key = new PKCS8Key();
            pkcs8Key.decode(key);
            privateKey = pkcs8Key;
        }
        try (InputStream cert = assets.open("key/testkey.x509.pem")) {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(cert);
        }
        return new KeyPair(privateKey, certificate);
    }

    public static final String KEY_STORE_TYPE_JKS = "JKS";
    public static final String KEY_STORE_TYPE_PKCS12 = "PKCS12";
    public static final String KEY_STORE_TYPE_BKS = "BKS";

    private static final String[] TYPES = {KEY_STORE_TYPE_JKS, KEY_STORE_TYPE_PKCS12, KEY_STORE_TYPE_BKS};

    @NonNull
    public static ArrayList<String> listAliases(@NonNull Context context,
                                                @NonNull Uri ksUri,
                                                int ksType,
                                                @Nullable char[] ksPass)
            throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException {
        String keyType = TYPES[ksType];
        Log.d(TAG, "Loading keystore " + keyType);
        final KeyStore ks = KeyStore.getInstance(keyType, getKeyStoreProvider(keyType));
        try (InputStream is = context.getContentResolver().openInputStream(ksUri)) {
            if (is == null) throw new FileNotFoundException(ksUri + " does not exist.");
            ks.load(is, ksPass);
        }
        return Collections.list(ks.aliases());
    }

    @NonNull
    public static KeyPair getKeyPair(@NonNull Context context, @NonNull Uri ksUri, int ksType,
                                     @Nullable String ksAlias, @Nullable char[] ksPass,
                                     @Nullable char[] aliasPass)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException,
            UnrecoverableKeyException {
        String keyType = TYPES[ksType];
        Log.d(TAG, "Loading keystore " + keyType);
        final KeyStore ks = KeyStore.getInstance(keyType, getKeyStoreProvider(keyType));
        try (InputStream is = context.getContentResolver().openInputStream(ksUri)) {
            if (is == null) throw new FileNotFoundException(ksUri + " does not exist.");
            ks.load(is, ksPass);
        }
        if (TextUtils.isEmpty(ksAlias)) {
            ksAlias = ks.aliases().nextElement();
        }
        Key key = ks.getKey(ksAlias, aliasPass);
        if (key instanceof PrivateKey) {
            X509Certificate cert = (X509Certificate) ks.getCertificate(ksAlias);
            return new KeyPair((PrivateKey) key, cert);
        }
        throw new KeyStoreException("The provided alias " + ksAlias + " does not exist.");
    }

    @NonNull
    public static KeyPair getKeyPair(@NonNull Context context, @NonNull Uri keyPath, @NonNull Uri certPath)
            throws Exception {
        ContentResolver cr = context.getContentResolver();
        PKCS8EncodedKeySpec spec;
        PrivateKey privateKey;
        X509Certificate cert;
        try (InputStream pk = cr.openInputStream(keyPath)) {
            byte[] data = IoUtils.readFully(pk, -1, true);
            spec = new PKCS8EncodedKeySpec(data);
        }
        try (InputStream cer = cr.openInputStream(certPath)) {
            cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(cer);
            privateKey = KeyFactory.getInstance(cert.getPublicKey().getAlgorithm()).generatePrivate(spec);
        }
        return new KeyPair(privateKey, cert);
    }

    @NonNull
    public static KeyPair generateRSAKeyPair(@NonNull String formattedSubject, int keySize, long expiryDate)
            throws NoSuchAlgorithmException, CertificateException, NoSuchProviderException,
            InvalidKeyException, SignatureException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(keySize, SecureRandom.getInstance("SHA1PRNG"));
        java.security.KeyPair generateKeyPair = keyPairGenerator.generateKeyPair();
        PublicKey publicKey = generateKeyPair.getPublic();
        PrivateKey privateKey = generateKeyPair.getPrivate();
        try {
            return new KeyPair(privateKey, generateCert(privateKey, publicKey, formattedSubject, expiryDate));
        } catch (IOException e) {
            throw new CertificateEncodingException(e);
        }
    }

    @NonNull
    private static Provider getKeyStoreProvider(String keyStoreType) {
        switch (keyStoreType) {
            default:
            case KEY_STORE_TYPE_JKS:
                return new JavaKeyStoreProvider();
            case KEY_STORE_TYPE_PKCS12:
            case KEY_STORE_TYPE_BKS:
                return new BouncyCastleProvider();
        }
    }

    /**
     * Read a PKCS #8, Base64-encrypted file as a Key instance. This is similar to
     * {@link CertificateFactory#generateCertificate(InputStream)} except that it generates a private key.
     */
    public static PrivateKey generatePrivateKey(InputStream inputStream)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, KeyException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            boolean readingKey = false;
            boolean pkcs8Format = false;
            boolean rsaFormat = false;
            boolean dsaFormat = false;
            // FIXME: Read securely using char[]
            StringBuilder base64EncodedKey = new StringBuilder();
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (readingKey) {
                    switch (line) {
                        case RSA_END_HEADER:
                        case DSA_END_HEADER:
                        case PKCS8_END_HEADER:
                            readingKey = false;
                            break;
                        default:
                            base64EncodedKey.append(line);
                            break;
                    }
                } else {
                    switch (line) {
                        case RSA_BEGIN_HEADER:
                            readingKey = true;
                            rsaFormat = true;
                            break;
                        case DSA_BEGIN_HEADER:
                            readingKey = true;
                            dsaFormat = true;
                            break;
                        case PKCS8_BEGIN_HEADER:
                            readingKey = true;
                            pkcs8Format = true;
                            break;
                    }
                }
            }
            if (base64EncodedKey.length() == 0) {
                throw new IOException("Stream does not contain an unencrypted private key.");
            }

            BASE64Decoder decoder = new BASE64Decoder();
            byte[] bytes = decoder.decodeBuffer(base64EncodedKey.toString());

            KeyFactory kf;
            KeySpec spec;
            if (pkcs8Format) {
                kf = KeyFactory.getInstance("RSA");
                spec = new PKCS8EncodedKeySpec(bytes);
            } else if (rsaFormat) {
                // PKCS#1 format
                kf = KeyFactory.getInstance("RSA");
                List<BigInteger> rsaIntegers = new ArrayList<>();
                ASN1Parse(bytes, rsaIntegers);
                if (rsaIntegers.size() < 8) {
                    throw new InvalidKeyException("Stream does not appear to be a properly formatted RSA key.");
                }
                BigInteger publicExponent = rsaIntegers.get(2);
                BigInteger privateExponent = rsaIntegers.get(3);
                BigInteger modulus = rsaIntegers.get(1);
                BigInteger primeP = rsaIntegers.get(4);
                BigInteger primeQ = rsaIntegers.get(5);
                BigInteger primeExponentP = rsaIntegers.get(6);
                BigInteger primeExponentQ = rsaIntegers.get(7);
                BigInteger crtCoefficient = rsaIntegers.get(8);
                //spec = new RSAPrivateKeySpec(modulus, privateExponent);
                spec = new RSAPrivateCrtKeySpec(modulus, publicExponent, privateExponent,
                        primeP, primeQ, primeExponentP, primeExponentQ, crtCoefficient);
            } else if (dsaFormat) {
                kf = KeyFactory.getInstance("DSA");
                List<BigInteger> dsaIntegers = new ArrayList<>();
                ASN1Parse(bytes, dsaIntegers);
                if (dsaIntegers.size() < 5) {
                    throw new InvalidKeyException("Stream does not appear to be a properly formatted DSA key");
                }
                BigInteger privateExponent = dsaIntegers.get(1);
                BigInteger publicExponent = dsaIntegers.get(2);
                BigInteger P = dsaIntegers.get(3);
                BigInteger Q = dsaIntegers.get(4);
                BigInteger G = dsaIntegers.get(5);
                spec = new DSAPrivateKeySpec(privateExponent, P, Q, G);
            } else {
                throw new NoSuchAlgorithmException("Couldn't find any suitable algorithm");
            }
            return kf.generatePrivate(spec);
        }
    }

    public static byte[] getPemCertificate(@NonNull Certificate certificate)
            throws CertificateEncodingException, IOException {
        BASE64Encoder encoder = new BASE64Encoder();
        try (ByteArrayOutputStream os = new ByteArrayOutputStream(X509Factory.BEGIN_CERT.length() +
                X509Factory.BEGIN_CERT.length() + certificate.getEncoded().length + 2)) {
            os.write(X509Factory.BEGIN_CERT.getBytes(StandardCharsets.UTF_8));
            os.write('\n');
            encoder.encode(certificate.getEncoded(), os);
            os.write('\n');
            os.write(X509Factory.END_CERT.getBytes(StandardCharsets.UTF_8));
            return os.toByteArray();
        }
    }

    private static final String RSA_BEGIN_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String RSA_END_HEADER = "-----END RSA PRIVATE KEY-----";
    private static final String PKCS8_BEGIN_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PKCS8_END_HEADER = "-----END PRIVATE KEY-----";
    private static final String DSA_BEGIN_HEADER = "-----BEGIN DSA PRIVATE KEY-----";
    private static final String DSA_END_HEADER = "-----END DSA PRIVATE KEY-----";

    /**
     * Bare-bones ASN.1 parser that can only deal with a structure that contains integers
     * (as I expect for the RSA private key format given in PKCS #1 and RFC 3447).
     *
     * @param b        the bytes to be parsed as ASN.1 DER
     * @param integers an output array to which all integers encountered during the parse
     *                 will be appended in the order they're encountered.  It's up to the caller to determine
     *                 which is which.
     */
    private static void ASN1Parse(@NonNull byte[] b, List<BigInteger> integers) throws KeyException {
        int pos = 0;
        while (pos < b.length) {
            byte tag = b[pos++];
            int length = b[pos++];
            if ((length & 0x80) != 0) {
                int extLen = 0;
                for (int i = 0; i < (length & 0x7F); i++) {
                    extLen = (extLen << 8) | (b[pos++] & 0xFF);
                }
                length = extLen;
            }
            byte[] contents = new byte[length];
            System.arraycopy(b, pos, contents, 0, length);
            pos += length;

            if (tag == 0x30) {  // sequence
                ASN1Parse(contents, integers);
            } else if (tag == 0x02) {  // Integer
                BigInteger i = new BigInteger(contents);
                integers.add(i);
            } else {
                throw new KeyException("Unsupported ASN.1 tag " + tag + " encountered.  Is this a " + "valid RSA key?");
            }
        }
    }

    @NonNull
    private static X509Certificate generateCert(PrivateKey privateKey, PublicKey publicKey,
                                                @NonNull String formattedSubject, long expiryDate)
            throws CertificateException, NoSuchAlgorithmException, NoSuchProviderException, SignatureException,
            InvalidKeyException, IOException {
        String algorithmName = "SHA512withRSA";
        CertificateExtensions certificateExtensions = new CertificateExtensions();
        certificateExtensions.set("SubjectKeyIdentifier", new SubjectKeyIdentifierExtension(
                new KeyIdentifier(publicKey).getIdentifier()));
        X500Name x500Name = new X500Name(formattedSubject);
        Date notBefore = new Date();
        Date notAfter = new Date(expiryDate);
        certificateExtensions.set("PrivateKeyUsage", new PrivateKeyUsageExtension(notBefore, notAfter));
        CertificateValidity certificateValidity = new CertificateValidity(notBefore, notAfter);
        X509CertInfo x509CertInfo = new X509CertInfo();
        x509CertInfo.set("version", new CertificateVersion(2));
        x509CertInfo.set("serialNumber", new CertificateSerialNumber(new Random().nextInt() & Integer.MAX_VALUE));
        x509CertInfo.set("algorithmID", new CertificateAlgorithmId(AlgorithmId.get(algorithmName)));
        x509CertInfo.set("subject", new CertificateSubjectName(x500Name));
        x509CertInfo.set("key", new CertificateX509Key(publicKey));
        x509CertInfo.set("validity", certificateValidity);
        x509CertInfo.set("issuer", new CertificateIssuerName(x500Name));
        x509CertInfo.set("extensions", certificateExtensions);
        X509CertImpl x509CertImpl = new X509CertImpl(x509CertInfo);
        x509CertImpl.sign(privateKey, algorithmName);
        return x509CertImpl;
    }
}
