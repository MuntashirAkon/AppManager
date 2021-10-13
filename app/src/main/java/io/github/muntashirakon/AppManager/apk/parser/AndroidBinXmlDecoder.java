// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.github.muntashirakon.AppManager.utils.IntegerUtils;
import io.github.muntashirakon.io.IoUtils;

public class AndroidBinXmlDecoder {
    public static boolean isBinaryXml(@NonNull ByteBuffer buffer) {
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.mark();
        int version = IntegerUtils.getUInt16(buffer);
        int header = IntegerUtils.getUInt16(buffer);
        buffer.reset();
        return version == 0x0003 && header == 0x0008;
    }

    @NonNull
    public static String decode(@NonNull byte[] data) throws AndroidBinXmlParser.XmlParserException {
        return decode(ByteBuffer.wrap(data));
    }

    @NonNull
    public static String decode(@NonNull InputStream is) throws IOException, AndroidBinXmlParser.XmlParserException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] buf = new byte[IoUtils.DEFAULT_BUFFER_SIZE];
        int n;
        while (-1 != (n = is.read(buf))) {
            buffer.write(buf, 0, n);
        }
        return decode(buffer.toByteArray());
    }

    @NonNull
    public static String decode(@NonNull File file) throws IOException, AndroidBinXmlParser.XmlParserException {
        try (FileInputStream is = new FileInputStream(file)) {
            return decode(is);
        }
    }

    @NonNull
    public static String decode(@NonNull ByteBuffer byteBuffer)
            throws AndroidBinXmlParser.XmlParserException {
        return decode(byteBuffer, false);
    }

    @NonNull
    public static String decode(@NonNull ByteBuffer byteBuffer, boolean manifest)
            throws AndroidBinXmlParser.XmlParserException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(bos)) {
            decode(byteBuffer, ps, manifest);
            byte[] bs = bos.toByteArray();
            return new String(bs, StandardCharsets.UTF_8);
        }
    }

    public static void decode(ByteBuffer byteBuffer, PrintStream out) throws AndroidBinXmlParser.XmlParserException {
        decode(byteBuffer, out, false);
    }

    public static void decode(ByteBuffer byteBuffer, PrintStream out, boolean manifest)
            throws AndroidBinXmlParser.XmlParserException {
        byteBuffer.position(0);
        AndroidBinXmlParser parser = new AndroidBinXmlParser(byteBuffer);
        StringBuilder indent = new StringBuilder(10);
        final String indentStep = "  ";
        out.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        XML_BUILDER:
        while (true) {
            int type = parser.next();
            switch (type) {
                case AndroidBinXmlParser.EVENT_START_ELEMENT: {
                    out.printf("%s<%s%s", indent, getNamespacePrefix(parser.getPrefix()), parser.getName());
                    indent.append(indentStep);

                    long namespaceCountBefore = parser.getNamespaceCount(parser.getDepth() - 1);
                    long namespaceCount = parser.getNamespaceCount(parser.getDepth());
                    for (long i = namespaceCountBefore; i != namespaceCount; ++i) {
                        out.println();
                        out.printf("%sxmlns:%s=\"%s\"", indent, parser.getNamespacePrefix(i), parser.getNamespaceUri(i));
                    }

                    for (int i = 0; i != parser.getAttributeCount(); ++i) {
                        out.println();
                        out.printf("%s%s%s=\"%s\"", indent, getNamespacePrefix(parser.getAttributePrefix(i)),
                                parser.getAttributeName(i), manifest ? getManifestAttributeValue(parser, i)
                                        : parser.getAttributeStringValue(i));
                    }
                    out.println(">");
                    break;
                }
                case AndroidBinXmlParser.EVENT_END_ELEMENT: {
                    indent.setLength(indent.length() - indentStep.length());
                    out.printf("%s</%s%s>%n", indent, getNamespacePrefix(parser.getPrefix()), parser.getName());
                    break;
                }
                case AndroidBinXmlParser.EVENT_END_DOCUMENT:
                    break XML_BUILDER;
                case AndroidBinXmlParser.EVENT_START_DOCUMENT:
                    // Unreachable statement
                    break;
            }
        }
    }

    @NonNull
    private static String getNamespacePrefix(String prefix) {
        if (prefix == null || prefix.length() == 0) {
            return "";
        }
        return prefix + ":";
    }

    private static final Set<String> intAttributes = new HashSet<>(
            Arrays.asList("appCategory", "autoRevokePermissions", "colorMode", "configChanges", "documentLaunchMode",
                    "foregroundServiceType", "gwpAsanMode", "installLocation", "launchMode", "lockTaskMode",
                    "memtagMode", "persistableMode", "protectionLevel", "reqKeyboardType", "reqNavigation",
                    "reqTouchScreen", "rollbackDataPolicy", "screenDensity", "screenOrientation", "screenSize",
                    "uiOptions", "usesPermissionFlags", "windowSoftInputMode"));

    private static String getManifestAttributeValue(@NonNull AndroidBinXmlParser parser, int index)
            throws AndroidBinXmlParser.XmlParserException {
        AndroidBinXmlParser.Attribute attribute = parser.getAttribute(index);
        String name = attribute.getName();
        String value = attribute.getStringValue();
        if (intAttributes.contains(name) && (value.startsWith("0x") || TextUtils.isDigitsOnly(value))) {
            int intValue = Integer.decode(value);
            switch (name) {
                case "appCategory":
                    return ManifestAttributes.getAppCategory(intValue);
                case "autoRevokePermissions":
                    return ManifestAttributes.getAutoRevokePermissions(intValue);
                case "colorMode":
                    return ManifestAttributes.getColorMode(intValue);
                case "configChanges":
                    return ManifestAttributes.getConfigChanges(intValue);
                case "documentLaunchMode":
                    return ManifestAttributes.getDocumentLaunchMode(intValue);
                case "foregroundServiceType":
                    return ManifestAttributes.getForegroundServiceType(intValue);
                case "gwpAsanMode":
                    return ManifestAttributes.getGwpAsanMode(intValue);
                case "installLocation":
                    return ManifestAttributes.getInstallLocation(intValue);
                case "launchMode":
                    return ManifestAttributes.getLaunchMode(intValue);
                case "lockTaskMode":
                    return ManifestAttributes.getLockTaskMode(intValue);
                case "memtagMode":
                    return ManifestAttributes.getMemtagMode(intValue);
                case "persistableMode":
                    return ManifestAttributes.getPersistableMode(intValue);
                case "protectionLevel":
                    return ManifestAttributes.getProtectionLevel(intValue);
                case "reqKeyboardType":
                    return ManifestAttributes.getReqKeyboardType(intValue);
                case "reqNavigation":
                    return ManifestAttributes.getReqNavigation(intValue);
                case "reqTouchScreen":
                    return ManifestAttributes.getReqTouchScreen(intValue);
                case "rollbackDataPolicy":
                    return ManifestAttributes.getRollbackDataPolicy(intValue);
                case "screenDensity":
                    return ManifestAttributes.getScreenDensity(intValue);
                case "screenOrientation":
                    return ManifestAttributes.getScreenOrientation(intValue);
                case "screenSize":
                    return ManifestAttributes.getScreenSize(intValue);
                case "uiOptions":
                    return ManifestAttributes.getUiOptions(intValue);
                case "usesPermissionFlags":
                    return ManifestAttributes.getUsesPermissionFlags(intValue);
                case "windowSoftInputMode":
                    return ManifestAttributes.getWindowSoftInputMode(intValue);
            }
        }
        return value;
    }
}