// SPDX-License-Identifier: BSD-2-Clause AND GPL-3.0-or-later

package net.dongliu.apk.parser;

import net.dongliu.apk.parser.bean.ApkMeta;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Convenient utils method for parse apk file
 */
// Copyright 2016 Liu Dong
public class ApkParsers {
    /**
     * Get apk meta info for apk file
     *
     * @throws IOException
     */
    public static ApkMeta getMetaInfo(String apkFilePath) throws IOException {
        try (ApkParser apkParser = new ApkParser(apkFilePath)) {
            return apkParser.getApkMeta();
        }
    }

    /**
     * Get apk meta info for apk file
     *
     * @throws IOException
     */
    public static ApkMeta getMetaInfo(File file) throws IOException {
        try (ApkParser apkParser = new ApkParser(file)) {
            return apkParser.getApkMeta();
        }
    }

    /**
     * Get apk meta info for apk file
     *
     * @throws IOException
     */
    public static ApkMeta getMetaInfo(byte[] apkData) throws IOException {
        try (ByteArrayApkFile apkFile = new ByteArrayApkFile(apkData)) {
            return apkFile.getApkMeta();
        }
    }

    /**
     * Get apk meta info for apk file, with locale
     *
     * @throws IOException
     */
    public static ApkMeta getMetaInfo(String apkFilePath, Locale locale) throws IOException {
        try (ApkParser apkParser = new ApkParser(apkFilePath)) {
            apkParser.setPreferredLocale(locale);
            return apkParser.getApkMeta();
        }
    }

    /**
     * Get apk meta info for apk file
     *
     * @throws IOException
     */
    public static ApkMeta getMetaInfo(File file, Locale locale) throws IOException {
        try (ApkParser apkParser = new ApkParser(file)) {
            apkParser.setPreferredLocale(locale);
            return apkParser.getApkMeta();
        }
    }

    /**
     * Get apk meta info for apk file
     *
     * @throws IOException
     */
    public static ApkMeta getMetaInfo(byte[] apkData, Locale locale) throws IOException {
        try (ByteArrayApkFile apkFile = new ByteArrayApkFile(apkData)) {
            apkFile.setPreferredLocale(locale);
            return apkFile.getApkMeta();
        }
    }

    /**
     * Get apk manifest xml file as text
     *
     * @throws IOException
     */
    public static String getManifestXml(String apkFilePath) throws IOException {
        try (ApkParser apkParser = new ApkParser(apkFilePath)) {
            return apkParser.getManifestXml();
        }
    }

    /**
     * Get apk manifest xml file as text
     *
     * @throws IOException
     */
    public static String getManifestXml(File file) throws IOException {
        try (ApkParser apkParser = new ApkParser(file)) {
            return apkParser.getManifestXml();
        }
    }

    /**
     * Get apk manifest xml file as text
     *
     * @throws IOException
     */
    public static String getManifestXml(byte[] apkData) throws IOException {
        try (ByteArrayApkFile apkFile = new ByteArrayApkFile(apkData)) {
            return apkFile.getManifestXml();
        }
    }

    /**
     * Get apk manifest xml file as text
     *
     * @throws IOException
     */
    public static String getManifestXml(String apkFilePath, Locale locale) throws IOException {
        try (ApkParser apkParser = new ApkParser(apkFilePath)) {
            apkParser.setPreferredLocale(locale);
            return apkParser.getManifestXml();
        }
    }

    /**
     * Get apk manifest xml file as text
     *
     * @throws IOException
     */
    public static String getManifestXml(File file, Locale locale) throws IOException {
        try (ApkParser apkParser = new ApkParser(file)) {
            apkParser.setPreferredLocale(locale);
            return apkParser.getManifestXml();
        }
    }

    /**
     * Get apk manifest xml file as text
     *
     * @throws IOException
     */
    public static String getManifestXml(byte[] apkData, Locale locale) throws IOException {
        try (ByteArrayApkFile apkFile = new ByteArrayApkFile(apkData)) {
            apkFile.setPreferredLocale(locale);
            return apkFile.getManifestXml();
        }
    }
}
