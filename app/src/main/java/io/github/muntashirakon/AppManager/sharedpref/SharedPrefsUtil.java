// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sharedpref;

import android.text.TextUtils;
import android.util.Xml;

import androidx.annotation.NonNull;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


public final class SharedPrefsUtil {
    public static final String TAG_ROOT = "map";  // <map></map>
    public static final String TAG_BOOLEAN = "boolean";  // <boolean name="bool" value="true" />
    public static final String TAG_FLOAT = "float";  // <float name="float" value="12.3" />
    public static final String TAG_INTEGER = "int";  // <int name="integer" value="123" />
    public static final String TAG_LONG = "long";  // <long name="long" value="123456789" />
    public static final String TAG_STRING = "string";  // <string name="string"></string>
    public static final String TAG_SET = "set";  // <set name="string_set"><string></string></set>

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
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String tagName = parser.getName();
                String attrName = parser.getAttributeValue(null, "name");
                if (attrName == null) attrName = "";
                String attrValue = parser.getAttributeValue(null, "value");
                switch (tagName) {
                    case TAG_BOOLEAN:
                        prefs.put(attrName, Objects.equals(attrValue, "true"));
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
                        break;
                    case TAG_SET:
                        Set<String> stringSet = new HashSet<>();
                        prefs.put(attrName, stringSet);
                        // Grab all strings
                        event = parser.next();
                        tagName = parser.getName();
                        while (event != XmlPullParser.END_TAG || !Objects.equals(tagName, TAG_SET)) {
                            if (event == XmlPullParser.START_TAG) {
                                if (!Objects.equals(tagName, TAG_STRING)) {
                                    throw new XmlPullParserException("Invalid tag inside <set>: " + tagName);
                                }
                                stringSet.add(parser.nextText());
                            }
                            event = parser.next();
                            tagName = parser.getName();
                        }
                        break;
                    default:
                        throw new XmlPullParserException("Invalid tag: " + tagName);
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
            } else if (value instanceof Set) {
                xmlSerializer.startTag("", TAG_SET);
                xmlSerializer.attribute("", "name", name);
                //noinspection unchecked
                for (String v : (Set<String>) value) {
                    xmlSerializer.startTag("", TAG_STRING);
                    xmlSerializer.text(v);
                    xmlSerializer.endTag("", TAG_STRING);
                }
                xmlSerializer.endTag("", TAG_SET);
            } else {
                throw new IOException("Invalid value for key: " + name + " (value: " + value + ")");
            }
        }
        xmlSerializer.endTag("", TAG_ROOT);
        xmlSerializer.endDocument();
        xmlSerializer.flush();
        os.write(stringWriter.toString().getBytes());
    }

    @NonNull
    public static String flattenToString(@NonNull Set<String> stringSet) {
        List<String> stringList = new ArrayList<>(stringSet.size());
        for (String string : stringSet) {
            stringList.add(string.replace(",", "\\,"));
        }
        return TextUtils.join(",", stringList);
    }

    @NonNull
    public static Set<String> unflattenToSet(@NonNull String rawValue) {
        // Split on commas unless they are preceded by an escape.
        // The escape character must be escaped for the string and
        // again for the regex, thus four escape characters become one.
        String[] strings = rawValue.split("(?<!\\\\),");
        Set<String> stringSet = new HashSet<>(strings.length);
        Collections.addAll(stringSet, strings);
        return stringSet;
    }
}
