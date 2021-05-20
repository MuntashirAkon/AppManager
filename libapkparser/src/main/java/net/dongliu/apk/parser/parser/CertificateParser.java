// SPDX-License-Identifier: BSD-2-Clause AND BSD-3-Clause

package net.dongliu.apk.parser.parser;

import net.dongliu.apk.parser.bean.CertificateMeta;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import javax.security.cert.CertificateException;
import javax.security.cert.X509Certificate;

import androidx.annotation.NonNull;

// Copyright 2015 Jared Rummler
//           2014 Liu Dong
public class CertificateParser {
    private final byte[] data;

    public CertificateParser(byte[] data) {
        this.data = data;
    }

    public CertificateMeta parse() throws CertificateException {
        X509Certificate certificate = X509Certificate.getInstance(data);
        CertificateMeta.Builder builder = CertificateMeta.newCertificateMeta();
        byte[] bytes = certificate.getEncoded();
        String certMd5 = md5Digest(bytes);
        String publicKeyString = byteToHexString(bytes);
        String certBase64Md5 = md5Digest(publicKeyString);
        builder.data(bytes);
        builder.certBase64Md5(certBase64Md5);
        builder.certMd5(certMd5);
        builder.startDate(certificate.getNotBefore());
        builder.endDate(certificate.getNotAfter());
        builder.signAlgorithm(certificate.getSigAlgName());
        builder.signAlgorithmOID(certificate.getSigAlgOID());
        return builder.build();
    }

    @NonNull
    private String md5Digest(byte[] input) {
        MessageDigest digest = getMd5Digest();
        digest.update(input);
        return getHexString(digest.digest());
    }

    @NonNull
    private String md5Digest(@NonNull String input) {
        MessageDigest digest = getMd5Digest();
        digest.update(input.getBytes(StandardCharsets.UTF_8));
        return getHexString(digest.digest());
    }

    @NonNull
    private String byteToHexString(@NonNull byte[] bArray) {
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
    private String getHexString(byte[] digest) {
        BigInteger bi = new BigInteger(1, digest);
        return String.format("%032x", bi);
    }

    @NonNull
    private MessageDigest getMd5Digest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

}
