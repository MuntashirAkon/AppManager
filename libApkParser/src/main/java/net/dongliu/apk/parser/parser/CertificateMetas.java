package net.dongliu.apk.parser.parser;

import net.dongliu.apk.parser.bean.CertificateMeta;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.security.cert.CertificateEncodingException;
import javax.security.cert.X509Certificate;

import androidx.annotation.NonNull;

public class CertificateMetas {

    @NonNull
    public static List<CertificateMeta> from(@NonNull List<X509Certificate> certificates) throws CertificateEncodingException {
        List<CertificateMeta> certificateMetas = new ArrayList<>(certificates.size());
        for (X509Certificate certificate : certificates) {
            CertificateMeta certificateMeta = CertificateMetas.from(certificate);
            certificateMetas.add(certificateMeta);
        }
        return certificateMetas;
    }

    @NonNull
    public static CertificateMeta from(@NonNull X509Certificate certificate) throws CertificateEncodingException {
        byte[] bytes = certificate.getEncoded();
        String certMd5 = md5Digest(bytes);
        String publicKeyString = byteToHexString(bytes);
        String certBase64Md5 = md5Digest(publicKeyString);
        return new CertificateMeta.Builder()
                .signAlgorithm(certificate.getSigAlgName().toUpperCase())
                .signAlgorithmOID(certificate.getSigAlgOID())
                .startDate(certificate.getNotBefore())
                .endDate(certificate.getNotAfter())
                .data(bytes)
                .certBase64Md5(certBase64Md5)
                .certMd5(certMd5)
                .build();
    }

    @NonNull
    private static String md5Digest(byte[] input) {
        MessageDigest digest = getDigest("md5");
        digest.update(input);
        return getHexString(digest.digest());
    }

    @NonNull
    private static String md5Digest(@NonNull String input) {
        MessageDigest digest = getDigest("md5");
        digest.update(input.getBytes(StandardCharsets.UTF_8));
        return getHexString(digest.digest());
    }

    @NonNull
    private static String byteToHexString(@NonNull byte[] bArray) {
        StringBuilder sb = new StringBuilder(bArray.length);
        String sTemp;
        for (byte aBArray : bArray) {
            sTemp = Integer.toHexString(0xFF & (char) aBArray);
            if (sTemp.length() < 2) {
                sb.append(0);
            }
            sb.append(sTemp.toUpperCase());
        }
        return sb.toString();
    }

    @NonNull
    private static String getHexString(byte[] digest) {
        BigInteger bi = new BigInteger(1, digest);
        return String.format("%032x", bi);
    }

    @NonNull
    private static MessageDigest getDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
