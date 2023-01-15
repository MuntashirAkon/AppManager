// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.compat.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;
import static org.xmlpull.v1.XmlPullParser.TEXT;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@RunWith(RobolectricTestRunner.class)
public class XmlTest {
    private final ClassLoader classLoader = Objects.requireNonNull(getClass().getClassLoader());
    private final File ssaidAbxFile = new File(classLoader.getResource("settings_ssaid.abx.xml").getFile());
    private final File ssaidXmlFile = new File(classLoader.getResource("settings_ssaid.plain.xml").getFile());
    private final File uriGrantsAbxFile = new File(classLoader.getResource("urigrants.abx.xml").getFile());
    private final File uriGrantsXmlFile = new File(classLoader.getResource("urigrants.plain.xml").getFile());

    @Test
    public void isBinaryXml() throws IOException {
        try (InputStream is = new BufferedInputStream(new FileInputStream(ssaidAbxFile))) {
            assertTrue(Xml.isBinaryXml(is));
        }
        try (InputStream is = new BufferedInputStream(new FileInputStream(uriGrantsAbxFile))) {
            assertTrue(Xml.isBinaryXml(is));
        }
    }

    @Test
    public void newBinaryPullParserReadSsaid() throws IOException, XmlPullParserException {
        byte[] xmlActualBytes;
        try (InputStream is = new BufferedInputStream(new FileInputStream(ssaidAbxFile));
             ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            TypedXmlPullParser parser = Xml.newBinaryPullParser();
            parser.setInput(is, StandardCharsets.UTF_8.name());
            TypedXmlSerializer serializer = Xml.newFastSerializer();
            serializer.setOutput(os, StandardCharsets.UTF_8.name());
            copyXml(parser, serializer);
            xmlActualBytes = os.toByteArray();
        }

        byte[] xmlExpectedBytes = new byte[(int) ssaidXmlFile.length()];
        try (InputStream is = new BufferedInputStream(new FileInputStream(ssaidXmlFile))) {
            is.read(xmlExpectedBytes);
        }
        assertEquals(new String(xmlExpectedBytes), new String(xmlActualBytes));
    }

    @Test
    public void newBinaryPullParserReadUriGrants() throws IOException, XmlPullParserException {
        byte[] xmlActualBytes;
        try (InputStream is = new BufferedInputStream(new FileInputStream(uriGrantsAbxFile));
             ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            TypedXmlPullParser parser = Xml.newBinaryPullParser();
            parser.setInput(is, StandardCharsets.UTF_8.name());
            TypedXmlSerializer serializer = Xml.newFastSerializer();
            serializer.setOutput(os, StandardCharsets.UTF_8.name());
            copyXml(parser, serializer);
            xmlActualBytes = os.toByteArray();
        }

        byte[] xmlExpectedBytes = new byte[(int) uriGrantsXmlFile.length()];
        try (InputStream is = new BufferedInputStream(new FileInputStream(uriGrantsXmlFile))) {
            is.read(xmlExpectedBytes);
        }
        assertEquals(new String(xmlExpectedBytes), new String(xmlActualBytes));
    }

//    @Test
//    public void newBinarySerializerWriteSsaid() throws IOException, XmlPullParserException {
//        byte[] xmlActualBytes;
//        try (InputStream is = new BufferedInputStream(new FileInputStream(ssaidXmlFile));
//             ByteArrayOutputStream os = new ByteArrayOutputStream()) {
//            TypedXmlPullParser parser = Xml.newFastPullParser();
//            parser.setInput(is, StandardCharsets.UTF_8.name());
//            TypedXmlSerializer serializer = Xml.newBinarySerializer();
//            serializer.setOutput(os, StandardCharsets.UTF_8.name());
//            copyXml(parser, serializer);
//            xmlActualBytes = os.toByteArray();
//        }
//
//        byte[] xmlExpectedBytes = new byte[(int) ssaidAbxFile.length()];
//        try (InputStream is = new BufferedInputStream(new FileInputStream(ssaidAbxFile))) {
//            is.read(xmlExpectedBytes);
//        }
//        assertEquals(HexDump.toHexString(xmlExpectedBytes), HexDump.toHexString(xmlActualBytes));
//    }
//
//    @Test
//    public void newBinarySerializerWriteUriGrants() throws IOException, XmlPullParserException {
//        byte[] xmlActualBytes;
//        try (InputStream is = new BufferedInputStream(new FileInputStream(uriGrantsXmlFile));
//             ByteArrayOutputStream os = new ByteArrayOutputStream()) {
//            TypedXmlPullParser parser = Xml.newFastPullParser();
//            parser.setInput(is, StandardCharsets.UTF_8.name());
//            TypedXmlSerializer serializer = Xml.newBinarySerializer();
//            serializer.setOutput(os, StandardCharsets.UTF_8.name());
//            copyXml(parser, serializer);
//            xmlActualBytes = os.toByteArray();
//        }
//
//        byte[] xmlExpectedBytes = new byte[(int) uriGrantsAbxFile.length()];
//        try (InputStream is = new BufferedInputStream(new FileInputStream(uriGrantsAbxFile))) {
//            is.read(xmlExpectedBytes);
//        }
//        assertEquals(HexDump.toHexString(xmlExpectedBytes), HexDump.toHexString(xmlActualBytes));
//    }

    public static void copyXml(@NonNull TypedXmlPullParser parser, @NonNull TypedXmlSerializer serializer)
            throws IOException, XmlPullParserException {
        serializer.startDocument(null, null);
        int event;
        do {
            event = parser.next();
            switch (event) {
                case START_TAG:
                    serializer.startTag(null, parser.getName());
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        String attributeName = parser.getAttributeName(i);
                        serializer.attribute(null, attributeName, parser.getAttributeValue(i));
                    }
                    break;
                case END_TAG:
                    serializer.endTag(null, parser.getName());
                    break;
                case TEXT:
                    serializer.text(parser.getText());
                    break;
                case END_DOCUMENT:
                    serializer.endDocument();
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        } while (event != END_DOCUMENT);
    }
}