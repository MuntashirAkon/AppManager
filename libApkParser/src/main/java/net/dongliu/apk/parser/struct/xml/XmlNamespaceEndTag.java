package net.dongliu.apk.parser.struct.xml;

/**
 * @author dongliu
 */
public class XmlNamespaceEndTag {
    private String prefix;
    private String uri;

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    @Override
    public String toString() {
        return prefix + "=" + uri;
    }
}
