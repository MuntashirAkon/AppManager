package net.dongliu.apk.parser.parser;

import net.dongliu.apk.parser.struct.xml.XmlNamespaceEndTag;
import net.dongliu.apk.parser.struct.xml.XmlNamespaceStartTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * the xml file's namespaces.
 *
 * @author dongliu
 */
class XmlNamespaces {

    private List<XmlNamespace> namespaces;

    private List<XmlNamespace> newNamespaces;

    public XmlNamespaces() {
        this.namespaces = new ArrayList<>();
        this.newNamespaces = new ArrayList<>();
    }

    public void addNamespace(XmlNamespaceStartTag tag) {
        XmlNamespace namespace = new XmlNamespace(tag.getPrefix(), tag.getUri());
        namespaces.add(namespace);
        newNamespaces.add(namespace);
    }

    public void removeNamespace(XmlNamespaceEndTag tag) {
        XmlNamespace namespace = new XmlNamespace(tag.getPrefix(), tag.getUri());
        namespaces.remove(namespace);
        newNamespaces.remove(namespace);
    }

    public String getPrefixViaUri(String uri) {
        if (uri == null) {
            return null;
        }
        for (XmlNamespace namespace : namespaces) {
            if (namespace.uri.equals(uri)) {
                return namespace.prefix;
            }
        }
        return null;
    }

    public List<XmlNamespace> consumeNameSpaces() {
        if (!newNamespaces.isEmpty()) {
            List<XmlNamespace> xmlNamespaces = new ArrayList<>();
            xmlNamespaces.addAll(newNamespaces);
            newNamespaces.clear();
            return xmlNamespaces;
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * one namespace
     */
    public static class XmlNamespace {
        private String prefix;
        private String uri;

        private XmlNamespace(String prefix, String uri) {
            this.prefix = prefix;
            this.uri = uri;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getUri() {
            return uri;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            XmlNamespace namespace = (XmlNamespace) o;

            if (prefix == null && namespace.prefix != null) return false;
            if (uri == null && namespace.uri != null) return false;
            if (prefix != null && !prefix.equals(namespace.prefix)) return false;
            if (uri != null && !uri.equals(namespace.uri)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = prefix.hashCode();
            result = 31 * result + uri.hashCode();
            return result;
        }
    }
}
