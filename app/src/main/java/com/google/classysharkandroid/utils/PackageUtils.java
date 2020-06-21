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
    public static String apkPro(PackageInfo p, PackageManager mPackageManager) {
        String[] aPermissionsUse;
        StringBuilder s = new StringBuilder(apkCert(p));
        String tmp;
        PermissionInfo pI;

        if (p.requestedPermissions != null) {
            aPermissionsUse= new String[p.requestedPermissions.length];
            for (int i=0; i < p.requestedPermissions.length; i++){
                if (p.requestedPermissions[i].startsWith("android.permission"))
                    aPermissionsUse[i] = p.requestedPermissions[i].substring(18) + " ";
                else aPermissionsUse[i] = p.requestedPermissions[i] + " ";
                try {
                    pI = mPackageManager.getPermissionInfo(p.requestedPermissions[i], PackageManager.GET_META_DATA);
                    tmp = getProtectionLevelString(pI);
                    if (tmp.contains("dangerous")) aPermissionsUse[i] = "*\u2638"+aPermissionsUse[i];
                    aPermissionsUse[i] += tmp+"\n-->"+pI.group;
                } catch (PackageManager.NameNotFoundException ignored) {}
                if ((p.requestedPermissionsFlags[i]
                        & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0)
                    aPermissionsUse[i] +=" ^\u2714";
            }

            try {
                Arrays.sort(aPermissionsUse);
            } catch (NullPointerException ignored) {}

            s.append("\n");

            for (String value : aPermissionsUse) s.append("\n\n").append(value);
        }
        if (p.permissions != null) {
            s.append("\n\n#######################\n### Declared Permissions ###");
            Arrays.sort(p.permissions, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
            for (int i=0;i < p.permissions.length;i++) {
                s.append("\n\n\u25a0").append(p.permissions[i].name).append("\n").
                        append(p.permissions[i].loadLabel(mPackageManager)).append("\n").
                        append(p.permissions[i].loadDescription(mPackageManager)).
                        append("\n").append(p.permissions[i].group);
            }


        }
        return s.toString();
    }

    @NonNull
    public static String convertS(@NonNull byte[] digest) {
        StringBuilder s= new StringBuilder();
        for (byte b:digest){
            s.append(String.format("%02X", b).toLowerCase());
        }
        return s.toString();
    }

    @NonNull
    public static String getProtectionLevelString(PermissionInfo permissionInfo) {
        int basePermissionType;
        int permissionFlags;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissionFlags = permissionInfo.getProtectionFlags();
            basePermissionType = permissionInfo.getProtection();
        } else {
            permissionFlags = permissionInfo.protectionLevel;
            basePermissionType = permissionFlags & PermissionInfo.PROTECTION_MASK_BASE;
        }
        String protLevel = "????";
        switch (basePermissionType) {
            case PermissionInfo.PROTECTION_DANGEROUS:
                protLevel = "dangerous";
                break;
            case PermissionInfo.PROTECTION_NORMAL:
                protLevel = "normal";
                break;
            case PermissionInfo.PROTECTION_SIGNATURE:
                protLevel = "signature";
                break;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            if (basePermissionType == (PermissionInfo.PROTECTION_SIGNATURE
                    | PermissionInfo.PROTECTION_FLAG_PRIVILEGED)) {
                protLevel = "signatureOrPrivileged";
            }
            if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_PRIVILEGED) != 0)
                protLevel += "|privileged";
            if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_PRE23) != 0)
                protLevel += "|pre23";  // pre marshmallow
            if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_INSTALLER) != 0)
                protLevel += "|installer";
            if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_VERIFIER) != 0)
                protLevel += "|verifier";
            if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_PREINSTALLED) != 0)
                protLevel += "|preinstalled";
            if (Build.VERSION.SDK_INT >= 24) {
                if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_SETUP) != 0)
                    protLevel += "|setup";
            }
            if (Build.VERSION.SDK_INT >= 26) {
                if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_RUNTIME_ONLY) != 0)
                    protLevel += "|runtime";
            }
            if (Build.VERSION.SDK_INT >= 27) {
                if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_INSTANT) != 0)
                    protLevel += "|instant";
            }
        } else {
            if (basePermissionType == PermissionInfo.PROTECTION_SIGNATURE_OR_SYSTEM) {
                protLevel = "signatureOrSystem";
            }
            if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_SYSTEM) != 0) {
                protLevel += "|system";
            }
        }
        if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0) {
            protLevel += "|development";
        }
        if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_APPOP) != 0) {
            protLevel += "|appop";
        }
        return protLevel;
    }
}
