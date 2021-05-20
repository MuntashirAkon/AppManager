// SPDX-License-Identifier: BSD-2-Clause

package net.dongliu.apk.parser.struct.xml;

// Copyright 2014 Liu Dong
public class XmlNodeEndTag {
    private String namespace;
    private String name;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("</");
        if (namespace != null) {
            sb.append(namespace).append(":");
        }
        sb.append(name).append('>');
        return sb.toString();
    }
}
