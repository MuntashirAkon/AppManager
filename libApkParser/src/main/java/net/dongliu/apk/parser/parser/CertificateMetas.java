package net.dongliu.apk.parser.parser;

import net.dongliu.apk.parser.bean.CertificateMeta;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class CertificateMetas {

    public static List<CertificateMeta> from(List<X509Certificate> certificates) throws CertificateEncodingException {
        List<CertificateMeta> certificateMetas = new ArrayList<>(certificates.size());
        for (X509Certificate certificate : certificates) {
            CertificateMeta certificateMeta = CertificateMetas.from(certificate);
            certificateMetas.add(certificateMeta);
        }
        return certificateMetas;
    }

    public static CertificateMeta from(X509Certificate certificate) throws CertificateEncodingException {
        byte[] bytes = certificate.getEncoded();
        String certMd5 = md5Digest(bytes);
        String publicKeyString = byteToHexString(bytes);
        String certBase64Md5 = md5Digest(publicKeyString);
        return new CertificateMeta(
                certificate.getSigAlgName().toUpperCase(),
                certificate.getSigAlgOID(),
                certificate.getNotBefore(),
                certificate.getNotAfter(),
                bytes, certBase64Md5, certMd5);
    }

    private static String md5Digest(byte[] input) {
        MessageDigest digest = getDigest("md5");
        digest.update(input);
        return getHexString(digest.digest());
    }

    private static String md5Digest(String input) {
        MessageDigest digest = getDigest("md5");
        digest.update(input.getBytes(StandardCharsets.UTF_8));
        return getHexString(digest.digest());
    }

    private static String byteToHexString(byte[] bArray) {
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

    private static String getHexString(byte[] digest) {
        BigInteger bi = new BigInteger(1, digest);
        return String.format("%032x", bi);
    }

    private static MessageDigest getDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
