package com.google.classysharkandroid.utils;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Locale;

import androidx.annotation.NonNull;

public class PackageUtils {
    public static String apkCert(@NonNull PackageInfo p){
        Signature[] signatures = p.signatures;
        String s = "";
        X509Certificate c;
        try {
            for (Signature sg: signatures) {
                c = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(sg.toByteArray()));
                s = "\n\n<b>Issuer:</b> " + c.getIssuerX500Principal().getName()
                    + "\n\n<b>Algorithm:</b> " + c.getSigAlgName();
                try{
                    s += "\n\n<b>Certificate fingerprints:</b>"
                        + "\n  <b>md5:</b> " + convertS(MessageDigest.getInstance("md5").digest(sg.toByteArray()))
                        + "\n  <b>sha1:</b> " + convertS(MessageDigest.getInstance("sha1").digest(sg.toByteArray()))
                        + "\n  <b>sha256:</b> " + convertS(MessageDigest.getInstance("sha256").digest(sg.toByteArray()));

                } catch (NoSuchAlgorithmException ignored) {}
            }
        } catch (CertificateException ignored) {}
        return s;
    }

    @NonNull
    public static String convertS(@NonNull byte[] digest) {
        StringBuilder s= new StringBuilder();
        for (byte b:digest){
            s.append(String.format("%02X", b).toLowerCase(Locale.ROOT));
        }
        return s.toString();
    }

}
