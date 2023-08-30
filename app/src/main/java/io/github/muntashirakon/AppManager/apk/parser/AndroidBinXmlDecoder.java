// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.reandroid.apk.AndroidFrameworks;
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.xml.ResXmlDocument;
import com.reandroid.arsc.chunk.xml.ResXmlPullParser;
import com.reandroid.arsc.io.BlockReader;
import com.reandroid.xml.XMLDocument;

import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import io.github.muntashirakon.AppManager.utils.IntegerUtils;
import io.github.muntashirakon.io.IoUtils;

public class AndroidBinXmlDecoder {
    public static boolean isBinaryXml(@NonNull ByteBuffer buffer) {
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.mark();
        int version = IntegerUtils.getUInt16(buffer);
        int header = IntegerUtils.getUInt16(buffer);
        buffer.reset();
        // 0x0000 is NULL header. The only example of application using a NULL header is NP Manager
        return (version == 0x0003 || version == 0x0000) && header == 0x0008;
    }

    @NonNull
    public static String decode(@NonNull byte[] data) throws IOException {
        return decode(ByteBuffer.wrap(data));
    }

    @NonNull
    public static String decode(@NonNull InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] buf = new byte[IoUtils.DEFAULT_BUFFER_SIZE];
        int n;
        while (-1 != (n = is.read(buf))) {
            buffer.write(buf, 0, n);
        }
        return decode(buffer.toByteArray());
    }

    @NonNull
    public static String decode(@NonNull ByteBuffer byteBuffer) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            decode(byteBuffer, bos);
            byte[] bs = bos.toByteArray();
            return new String(bs, StandardCharsets.UTF_8);
        }
    }

    public static void decode(@NonNull ByteBuffer byteBuffer, @NonNull OutputStream os) throws IOException {
        try (BlockReader reader = new BlockReader(byteBuffer.array());
             PrintStream out = new PrintStream(os)) {
            ResXmlDocument resXmlDocument = new ResXmlDocument();
            resXmlDocument.readBytes(reader);
            try (ResXmlPullParser parser = new ResXmlPullParser()) {
                parser.setCurrentPackage(getFrameworkPackageBlock());
                parser.setResXmlDocument(resXmlDocument);
                StringBuilder indent = new StringBuilder(10);
                final String indentStep = "  ";
                out.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
                XML_BUILDER:
                while (true) {
                    int type = parser.next();
                    switch (type) {
                        case START_TAG: {
                            out.printf("%s<%s%s", indent, getNamespacePrefix(parser.getPrefix()), parser.getName());
                            indent.append(indentStep);

                            int nsStart = parser.getNamespaceCount(parser.getDepth() - 1);
                            int nsEnd = parser.getNamespaceCount(parser.getDepth());
                            for (int i = nsStart; i < nsEnd; ++i) {
                                out.printf("\n%sxmlns:%s=\"%s\"", indent,
                                        parser.getNamespacePrefix(i),
                                        parser.getNamespaceUri(i));
                            }

                            for (int i = 0; i != parser.getAttributeCount(); ++i) {
                                out.printf("\n%s%s%s=\"%s\"",
                                        indent,
                                        getNamespacePrefix(parser.getAttributePrefix(i)),
                                        parser.getAttributeName(i),
                                        parser.getAttributeValue(i));
                            }
                            out.println(">");
                            break;
                        }
                        case END_TAG: {
                            indent.setLength(indent.length() - indentStep.length());
                            out.printf("%s</%s%s>%n", indent, getNamespacePrefix(parser.getPrefix()), parser.getName());
                            break;
                        }
                        case END_DOCUMENT:
                            break XML_BUILDER;
                        case START_DOCUMENT:
                            // Unreachable statement
                            break;
                    }
                }
            }
        } catch (XmlPullParserException e) {
            throw new IOException(e);
        }
    }

    public static XMLDocument decodeToXml(@NonNull ByteBuffer byteBuffer) throws IOException {
        ResXmlDocument xmlBlock = new ResXmlDocument();
        try (BlockReader reader = new BlockReader(byteBuffer.array())) {
            xmlBlock.readBytes(reader);
            xmlBlock.setPackageBlock(getFrameworkPackageBlock());
            return xmlBlock.decodeToXml();
        }
    }

    @NonNull
    private static String getNamespacePrefix(String prefix) {
        if (TextUtils.isEmpty(prefix)) {
            return "";
        }
        return prefix + ":";
    }

    @NonNull
    static PackageBlock getFrameworkPackageBlock() throws IOException {
        if (sFrameworkPackageBlock != null) {
            return sFrameworkPackageBlock;
        }
        sFrameworkPackageBlock = AndroidFrameworks.getLatest().getTableBlock().getAllPackages().next();
        return sFrameworkPackageBlock;
    }

    private static PackageBlock sFrameworkPackageBlock;
}