// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.compat.xml;

import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.NonNull;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;

import io.github.muntashirakon.compat.HexDump;

public class XmlUtils {
    private static class ForcedTypedXmlSerializer extends XmlSerializerWrapper
            implements TypedXmlSerializer {
        public ForcedTypedXmlSerializer(XmlSerializer wrapped) {
            super(wrapped);
        }

        @Override
        public XmlSerializer attributeInterned(String namespace, String name, String value)
                throws IOException {
            return attribute(namespace, name, value);
        }

        @Override
        public XmlSerializer attributeBytesHex(String namespace, String name, byte[] value)
                throws IOException {
            return attribute(namespace, name, HexDump.toHexString(value));
        }

        @Override
        public XmlSerializer attributeBytesBase64(String namespace, String name, byte[] value)
                throws IOException {
            return attribute(namespace, name, Base64.encodeToString(value, Base64.NO_WRAP));
        }

        @Override
        public XmlSerializer attributeInt(String namespace, String name, int value)
                throws IOException {
            return attribute(namespace, name, Integer.toString(value));
        }

        @Override
        public XmlSerializer attributeIntHex(String namespace, String name, int value)
                throws IOException {
            return attribute(namespace, name, Integer.toString(value, 16));
        }

        @Override
        public XmlSerializer attributeLong(String namespace, String name, long value)
                throws IOException {
            return attribute(namespace, name, Long.toString(value));
        }

        @Override
        public XmlSerializer attributeLongHex(String namespace, String name, long value)
                throws IOException {
            return attribute(namespace, name, Long.toString(value, 16));
        }

        @Override
        public XmlSerializer attributeFloat(String namespace, String name, float value)
                throws IOException {
            return attribute(namespace, name, Float.toString(value));
        }

        @Override
        public XmlSerializer attributeDouble(String namespace, String name, double value)
                throws IOException {
            return attribute(namespace, name, Double.toString(value));
        }

        @Override
        public XmlSerializer attributeBoolean(String namespace, String name, boolean value)
                throws IOException {
            return attribute(namespace, name, Boolean.toString(value));
        }
    }

    /**
     * Return a specialization of the given {@link XmlSerializer} which has
     * explicit methods to support consistent and efficient conversion of
     * primitive data types.
     */
    public static @NonNull TypedXmlSerializer makeTyped(@NonNull XmlSerializer xml) {
        if (xml instanceof TypedXmlSerializer) {
            return (TypedXmlSerializer) xml;
        } else {
            return new ForcedTypedXmlSerializer(xml);
        }
    }

    private static class ForcedTypedXmlPullParser extends XmlPullParserWrapper
            implements TypedXmlPullParser {
        public ForcedTypedXmlPullParser(XmlPullParser wrapped) {
            super(wrapped);
        }

        @Override
        public byte[] getAttributeBytesHex(int index)
                throws XmlPullParserException {
            try {
                return HexDump.hexStringToByteArray(getAttributeValue(index));
            } catch (Exception e) {
                throw new XmlPullParserException(
                        "Invalid attribute " + getAttributeName(index) + ": " + e);
            }
        }

        @Override
        public byte[] getAttributeBytesBase64(int index)
                throws XmlPullParserException {
            try {
                return Base64.decode(getAttributeValue(index), Base64.NO_WRAP);
            } catch (Exception e) {
                throw new XmlPullParserException(
                        "Invalid attribute " + getAttributeName(index) + ": " + e);
            }
        }

        @Override
        public int getAttributeInt(int index)
                throws XmlPullParserException {
            try {
                return Integer.parseInt(getAttributeValue(index));
            } catch (Exception e) {
                throw new XmlPullParserException(
                        "Invalid attribute " + getAttributeName(index) + ": " + e);
            }
        }

        @Override
        public int getAttributeIntHex(int index)
                throws XmlPullParserException {
            try {
                return Integer.parseInt(getAttributeValue(index), 16);
            } catch (Exception e) {
                throw new XmlPullParserException(
                        "Invalid attribute " + getAttributeName(index) + ": " + e);
            }
        }

        @Override
        public long getAttributeLong(int index)
                throws XmlPullParserException {
            try {
                return Long.parseLong(getAttributeValue(index));
            } catch (Exception e) {
                throw new XmlPullParserException(
                        "Invalid attribute " + getAttributeName(index) + ": " + e);
            }
        }

        @Override
        public long getAttributeLongHex(int index)
                throws XmlPullParserException {
            try {
                return Long.parseLong(getAttributeValue(index), 16);
            } catch (Exception e) {
                throw new XmlPullParserException(
                        "Invalid attribute " + getAttributeName(index) + ": " + e);
            }
        }

        @Override
        public float getAttributeFloat(int index)
                throws XmlPullParserException {
            try {
                return Float.parseFloat(getAttributeValue(index));
            } catch (Exception e) {
                throw new XmlPullParserException(
                        "Invalid attribute " + getAttributeName(index) + ": " + e);
            }
        }

        @Override
        public double getAttributeDouble(int index)
                throws XmlPullParserException {
            try {
                return Double.parseDouble(getAttributeValue(index));
            } catch (Exception e) {
                throw new XmlPullParserException(
                        "Invalid attribute " + getAttributeName(index) + ": " + e);
            }
        }

        @Override
        public boolean getAttributeBoolean(int index)
                throws XmlPullParserException {
            final String value = getAttributeValue(index);
            if ("true".equalsIgnoreCase(value)) {
                return true;
            } else if ("false".equalsIgnoreCase(value)) {
                return false;
            } else {
                throw new XmlPullParserException(
                        "Invalid attribute " + getAttributeName(index) + ": " + value);
            }
        }
    }

    /**
     * Return a specialization of the given {@link XmlPullParser} which has
     * explicit methods to support consistent and efficient conversion of
     * primitive data types.
     */
    public static @NonNull TypedXmlPullParser makeTyped(@NonNull XmlPullParser xml) {
        if (xml instanceof TypedXmlPullParser) {
            return (TypedXmlPullParser) xml;
        } else {
            return new ForcedTypedXmlPullParser(xml);
        }
    }

    public static void skipCurrentTag(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG
                || parser.getDepth() > outerDepth)) {
        }
    }

    public static final void nextElement(XmlPullParser parser) throws XmlPullParserException, IOException {
        int type;
        while ((type = parser.next()) != parser.START_TAG
                && type != parser.END_DOCUMENT) {
        }
    }

    public static boolean nextElementWithin(XmlPullParser parser, int outerDepth)
            throws IOException, XmlPullParserException {
        for (;;) {
            int type = parser.next();
            if (type == XmlPullParser.END_DOCUMENT
                    || (type == XmlPullParser.END_TAG && parser.getDepth() == outerDepth)) {
                return false;
            }
            if (type == XmlPullParser.START_TAG
                    && parser.getDepth() == outerDepth + 1) {
                return true;
            }
        }
    }

    public static int readIntAttribute(XmlPullParser in, String name, int defaultValue) {
        final String value = in.getAttributeValue(null, name);
        if (TextUtils.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean readBooleanAttribute(XmlPullParser in, String name,
                                               boolean defaultValue) {
        final String value = in.getAttributeValue(null, name);
        if (TextUtils.isEmpty(value)) {
            return defaultValue;
        } else {
            return Boolean.parseBoolean(value);
        }
    }
}
