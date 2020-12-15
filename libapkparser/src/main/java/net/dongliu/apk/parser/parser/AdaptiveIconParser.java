package net.dongliu.apk.parser.parser;

import net.dongliu.apk.parser.struct.xml.*;

/**
 * Parse adaptive icon xml file.
 *
 * @author Liu Dong dongliu@live.cn
 */
public class AdaptiveIconParser implements XmlStreamer {

    private String foreground;
    private String background;

    public String getForeground() {
        return foreground;
    }

    public String getBackground() {
        return background;
    }

    @Override
    public void onStartTag(XmlNodeStartTag xmlNodeStartTag) {
        if (xmlNodeStartTag.getName().equals("background")) {
            background = getDrawable(xmlNodeStartTag);
        } else if (xmlNodeStartTag.getName().equals("foreground")) {
            foreground = getDrawable(xmlNodeStartTag);
        }
    }

    private String getDrawable(XmlNodeStartTag xmlNodeStartTag) {
        Attributes attributes = xmlNodeStartTag.getAttributes();
        for (Attribute attribute : attributes.values()) {
            if (attribute.getName().equals("drawable")) {
                return attribute.getValue();
            }
        }
        return null;
    }

    @Override
    public void onEndTag(XmlNodeEndTag xmlNodeEndTag) {

    }

    @Override
    public void onCData(XmlCData xmlCData) {

    }

    @Override
    public void onNamespaceStart(XmlNamespaceStartTag tag) {

    }

    @Override
    public void onNamespaceEnd(XmlNamespaceEndTag tag) {

    }
}
