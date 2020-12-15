package net.dongliu.apk.parser.parser;

import net.dongliu.apk.parser.struct.xml.*;
import net.dongliu.apk.parser.utils.xml.XmlEscaper;

import java.util.List;

/**
 * trans to xml text when parse binary xml file.
 *
 * @author dongliu
 */
public class XmlTranslator implements XmlStreamer {
    private StringBuilder sb;
    private int shift = 0;
    private XmlNamespaces namespaces;
    private boolean isLastStartTag;

    public XmlTranslator() {
        sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        this.namespaces = new XmlNamespaces();
    }

    @Override
    public void onStartTag(XmlNodeStartTag xmlNodeStartTag) {
        if (isLastStartTag) {
            sb.append(">\n");
        }
        appendShift(shift++);
        sb.append('<');
        if (xmlNodeStartTag.getNamespace() != null) {
            String prefix = namespaces.getPrefixViaUri(xmlNodeStartTag.getNamespace());
            if (prefix != null) {
                sb.append(prefix).append(":");
            } else {
                sb.append(xmlNodeStartTag.getNamespace()).append(":");
            }
        }
        sb.append(xmlNodeStartTag.getName());

        List<XmlNamespaces.XmlNamespace> nps = namespaces.consumeNameSpaces();
        if (!nps.isEmpty()) {
            for (XmlNamespaces.XmlNamespace np : nps) {
                sb.append(" xmlns:").append(np.getPrefix()).append("=\"")
                        .append(np.getUri())
                        .append("\"");
            }
        }
        isLastStartTag = true;

        for (Attribute attribute : xmlNodeStartTag.getAttributes().values()) {
            onAttribute(attribute);
        }
    }

    private void onAttribute(Attribute attribute) {
        sb.append(" ");
        String namespace = this.namespaces.getPrefixViaUri(attribute.getNamespace());
        if (namespace == null) {
            namespace = attribute.getNamespace();
        }
        if (namespace != null && !namespace.isEmpty()) {
            sb.append(namespace).append(':');
        }
        String escapedFinalValue = XmlEscaper.escapeXml10(attribute.getValue());
        sb.append(attribute.getName()).append('=').append('"')
                .append(escapedFinalValue).append('"');
    }

    @Override
    public void onEndTag(XmlNodeEndTag xmlNodeEndTag) {
        --shift;
        if (isLastStartTag) {
            sb.append(" />\n");
        } else {
            appendShift(shift);
            sb.append("</");
            if (xmlNodeEndTag.getNamespace() != null) {
                String namespace = this.namespaces.getPrefixViaUri(xmlNodeEndTag.getNamespace());
                if (namespace == null) {
                    namespace = xmlNodeEndTag.getNamespace();
                }
                sb.append(namespace).append(":");
            }
            sb.append(xmlNodeEndTag.getName());
            sb.append(">\n");
        }
        isLastStartTag = false;
    }


    @Override
    public void onCData(XmlCData xmlCData) {
        appendShift(shift);
        sb.append(xmlCData.getValue()).append('\n');
        isLastStartTag = false;
    }

    @Override
    public void onNamespaceStart(XmlNamespaceStartTag tag) {
        this.namespaces.addNamespace(tag);
    }

    @Override
    public void onNamespaceEnd(XmlNamespaceEndTag tag) {
        this.namespaces.removeNamespace(tag);
    }

    private void appendShift(int shift) {
        for (int i = 0; i < shift; i++) {
            sb.append("\t");
        }
    }

    public String getXml() {
        return sb.toString();
    }
}
