// SPDX-License-Identifier: BSD-2-Clause

package net.dongliu.apk.parser.parser;

import net.dongliu.apk.parser.bean.CertificateMeta;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.security.cert.CertificateEncodingException;
import javax.security.cert.X509Certificate;

import androidx.annotation.NonNull;

// Copyright 2018 hsiafan
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
                .signAlgorithm(certificate.getSigAlgName().toUpperCase(Locale.ROOT))
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
        MessageDigest digest = getMd5Digest();
        digest.update(input);
        return getHexString(digest.digest());
    }

    @NonNull
    private static String md5Digest(@NonNull String input) {
        MessageDigest digest = getMd5Digest();
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
            sb.append(sTemp.toUpperCase(Locale.ROOT));
        }
        return sb.toString();
    }

    @NonNull
    private static String getHexString(byte[] digest) {
        BigInteger bi = new BigInteger(1, digest);
        return String.format("%032x", bi);
    }

    @NonNull
    private static MessageDigest getMd5Digest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
