// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package org.openintents.openpgp.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Copyright 2014-2015 Dominik Schürmann
public class OpenPgpUtils {

    public static final Pattern PGP_MESSAGE = Pattern.compile(
            ".*?(-----BEGIN PGP MESSAGE-----.*?-----END PGP MESSAGE-----).*",
            Pattern.DOTALL);

    public static final Pattern PGP_SIGNED_MESSAGE = Pattern.compile(
            ".*?(-----BEGIN PGP SIGNED MESSAGE-----.*?-----BEGIN PGP SIGNATURE-----.*?-----END PGP SIGNATURE-----).*",
            Pattern.DOTALL);

    public static final int PARSE_RESULT_NO_PGP = -1;
    public static final int PARSE_RESULT_MESSAGE = 0;
    public static final int PARSE_RESULT_SIGNED_MESSAGE = 1;

    public static int parseMessage(String message) {
        Matcher matcherSigned = PGP_SIGNED_MESSAGE.matcher(message);
        Matcher matcherMessage = PGP_MESSAGE.matcher(message);

        if (matcherMessage.matches()) {
            return PARSE_RESULT_MESSAGE;
        } else if (matcherSigned.matches()) {
            return PARSE_RESULT_SIGNED_MESSAGE;
        } else {
            return PARSE_RESULT_NO_PGP;
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    public static boolean isAvailable(Context context) {
        Intent intent = new Intent(OpenPgpApi.SERVICE_INTENT_2);
        List<ResolveInfo> resInfo = context.getPackageManager().queryIntentServices(intent, 0);
        return !resInfo.isEmpty();
    }

    public static String convertKeyIdToHex(long keyId) {
        return "0x" + convertKeyIdToHex32bit(keyId >> 32) + convertKeyIdToHex32bit(keyId);
    }

    private static String convertKeyIdToHex32bit(long keyId) {
        StringBuilder hexString = new StringBuilder(Long.toHexString(keyId & 0xffffffffL).toLowerCase(Locale.ENGLISH));
        while (hexString.length() < 8) {
            hexString.insert(0, "0");
        }
        return hexString.toString();
    }

    @SuppressLint("QueryPermissionsNeeded")
    @NonNull
    public static List<ServiceInfo> getPgpClientServices(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        final List<ResolveInfo> resolveInfoList = new ArrayList<>(pm.queryIntentServices(
                new Intent(OpenPgpApi.SERVICE_INTENT_2), 0));
        Intent intent = new Intent("org.openintents.openpgp.IOpenPgpService");
        intent.setPackage("org.thialfihar.android.apg");
        try {
            resolveInfoList.addAll(pm.queryIntentServices(intent, 0));
        } catch (NullPointerException ignore) {
        }
        List<ServiceInfo> serviceInfoList = new ArrayList<>(resolveInfoList.size());
        for (ResolveInfo resolveInfo : resolveInfoList) {
            if (resolveInfo.serviceInfo == null) {
                continue;
            }
            serviceInfoList.add(resolveInfo.serviceInfo);
        }
        return serviceInfoList;
    }


    private static final Pattern USER_ID_PATTERN = Pattern.compile("^(.*?)(?: \\((.*)\\))?(?: <(.*)>)?$");

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^<?\"?([^<>\"]*@[^<>\"]*\\.[^<>\"]*)\"?>?$");

    /**
     * Splits userId string into naming part, email part, and comment part.
     * See SplitUserIdTest for examples.
     */
    public static UserId splitUserId(final String userId) {
        if (!TextUtils.isEmpty(userId)) {
            final Matcher matcher = USER_ID_PATTERN.matcher(userId);
            if (matcher.matches()) {
                String name = matcher.group(1).isEmpty() ? null : matcher.group(1);
                String comment = matcher.group(2);
                String email = matcher.group(3);
                if (email != null && name != null) {
                    final Matcher emailMatcher = EMAIL_PATTERN.matcher(name);
                    if (emailMatcher.matches() && email.equals(emailMatcher.group(1))) {
                        email = emailMatcher.group(1);
                        name = null;
                    }
                }
                if (email == null && name != null) {
                    final Matcher emailMatcher = EMAIL_PATTERN.matcher(name);
                    if (emailMatcher.matches()) {
                        email = emailMatcher.group(1);
                        name = null;
                    }
                }
                return new UserId(name, email, comment);
            }
        }
        return new UserId(null, null, null);
    }

    /**
     * Returns a composed user id. Returns null if name, email and comment are empty.
     */
    public static String createUserId(UserId userId) {
        StringBuilder userIdBuilder = new StringBuilder();
        if (!TextUtils.isEmpty(userId.name)) {
            userIdBuilder.append(userId.name);
        }
        if (!TextUtils.isEmpty(userId.comment)) {
            userIdBuilder.append(" (");
            userIdBuilder.append(userId.comment);
            userIdBuilder.append(")");
        }
        if (!TextUtils.isEmpty(userId.email)) {
            userIdBuilder.append(" <");
            userIdBuilder.append(userId.email);
            userIdBuilder.append(">");
        }
        return userIdBuilder.length() == 0 ? null : userIdBuilder.toString();
    }

    public static class UserId implements Serializable {
        public final String name;
        public final String email;
        public final String comment;

        public UserId(String name, String email, String comment) {
            this.name = name;
            this.email = email;
            this.comment = comment;
        }
    }
}
