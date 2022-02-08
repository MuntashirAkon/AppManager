// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sharedpref;

import android.util.Xml;

import androidx.annotation.NonNull;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;


public final class SharedPrefsUtil {
    public static final String TAG_ROOT = "map";  // <map></map>
    public static final String TAG_BOOLEAN = "boolean";  // <boolean name="bool" value="true" />
    public static final String TAG_FLOAT = "float";  // <float name="float" value="12.3" />
    public static final String TAG_INTEGER = "int";  // <int name="integer" value="123" />
    public static final String TAG_LONG = "long";  // <long name="long" value="123456789" />
    public static final String TAG_STRING = "string";  // <string name="string"></string> | <string name="string"><string></string></string>

    @NonNull
    public static HashMap<String, Object> readSharedPref(@NonNull InputStream is)
            throws XmlPullParserException, IOException {
        HashMap<String, Object> prefs = new HashMap<>();
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(is, null);
        parser.nextTag();
        parser.require(XmlPullParser.START_TAG, null, TAG_ROOT);
        int event = parser.next();
        String tagName, attrName, attrValue;
        while (event != XmlPullParser.END_DOCUMENT) {
            tagName = parser.getName();
            if (event == XmlPullParser.START_TAG) {
                attrName = parser.getAttributeValue(null, "name");
                if (attrName == null) attrName = "";
                attrValue = parser.getAttributeValue(null, "value");
                switch (tagName) {
                    case TAG_BOOLEAN:
                        if (attrValue != null) {
                            prefs.put(attrName, attrValue.equals("true"));
                        }
                        break;
                    case TAG_FLOAT:
                        if (attrValue != null) {
                            prefs.put(attrName, Float.valueOf(attrValue));
                        }
                        break;
                    case TAG_INTEGER:
                        if (attrValue != null) {
                            prefs.put(attrName, Integer.valueOf(attrValue));
                        }
                        break;
                    case TAG_LONG:
                        if (attrValue != null) {
                            prefs.put(attrName, Long.valueOf(attrValue));
                        }
                        break;
                    case TAG_STRING:
                        prefs.put(attrName, parser.nextText());
                }
            }
            event = parser.next();
        }
        return prefs;
    }

    public static void writeSharedPref(@NonNull OutputStream os, @NonNull Map<String, Object> hashMap)
            throws IOException {
        XmlSerializer xmlSerializer = Xml.newSerializer();
        StringWriter stringWriter = new StringWriter();
        xmlSerializer.setOutput(stringWriter);
        xmlSerializer.startDocument("UTF-8", true);
        xmlSerializer.startTag("", TAG_ROOT);
        // Add values
        for (String name : hashMap.keySet()) {
            Object value = hashMap.get(name);
            if (value instanceof Boolean) {
                xmlSerializer.startTag("", TAG_BOOLEAN);
                xmlSerializer.attribute("", "name", name);
                xmlSerializer.attribute("", "value", value.toString());
                xmlSerializer.endTag("", TAG_BOOLEAN);
            } else if (value instanceof Float) {
                xmlSerializer.startTag("", TAG_FLOAT);
                xmlSerializer.attribute("", "name", name);
                xmlSerializer.attribute("", "value", value.toString());
                xmlSerializer.endTag("", TAG_FLOAT);
            } else if (value instanceof Integer) {
                xmlSerializer.startTag("", TAG_INTEGER);
                xmlSerializer.attribute("", "name", name);
                xmlSerializer.attribute("", "value", value.toString());
                xmlSerializer.endTag("", TAG_INTEGER);
            } else if (value instanceof Long) {
                xmlSerializer.startTag("", TAG_LONG);
                xmlSerializer.attribute("", "name", name);
                xmlSerializer.attribute("", "value", value.toString());
                xmlSerializer.endTag("", TAG_LONG);
            } else if (value instanceof String) {
                xmlSerializer.startTag("", TAG_STRING);
                xmlSerializer.attribute("", "name", name);
                xmlSerializer.text(value.toString());
                xmlSerializer.endTag("", TAG_STRING);
            }
        }
        xmlSerializer.endTag("", TAG_ROOT);
        xmlSerializer.endDocument();
        xmlSerializer.flush();
        os.write(stringWriter.toString().getBytes());
    }
}
