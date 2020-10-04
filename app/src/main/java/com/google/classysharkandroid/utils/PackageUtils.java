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

package com.google.classysharkandroid.utils;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Locale;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.utils.DigestUtils;

public class PackageUtils {
    public static String apkCert(@NonNull PackageInfo p) {
        Signature[] signatures = p.signatures;
        String s = "";
        X509Certificate c;
        byte[] certBytes;
        try {
            for (Signature signature : signatures) {
                certBytes = signature.toByteArray();
                c = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(certBytes));
                s = "\n\n<b>Issuer:</b> " + c.getIssuerX500Principal().getName() +
                        "\n\n<b>Algorithm:</b> " + c.getSigAlgName() +
                        "\n\n<b>Certificate fingerprints:</b>" +
                        "\n  <b>md5:</b> " + DigestUtils.getHexDigest(DigestUtils.MD5, certBytes) +
                        "\n  <b>sha1:</b> " + DigestUtils.getHexDigest(DigestUtils.SHA_1, certBytes) +
                        "\n  <b>sha256:</b> " + DigestUtils.getHexDigest(DigestUtils.SHA_256, certBytes);

            }
        } catch (CertificateException ignored) {
        }
        return s;
    }
}
