package org.xmlpull.v1;

public interface XmlPullParser {
    int getAttributeCount();

    String getAttributeNamespace(int index);

    String getAttributeName(int index);

    String getAttributeValue(int index);
}
