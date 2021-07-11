// SPDX-License-Identifier: BSD-2-Clause

package net.dongliu.apk.parser.parser;

import net.dongliu.apk.parser.struct.xml.*;

/**
 * callback interface for parse binary xml file.
 */
// Copyright 2014 Liu Dong
public interface XmlStreamer {

    void onStartTag(XmlNodeStartTag xmlNodeStartTag);

    void onEndTag(XmlNodeEndTag xmlNodeEndTag);

    void onCData(XmlCData xmlCData);

    void onNamespaceStart(XmlNamespaceStartTag tag);

    void onNamespaceEnd(XmlNamespaceEndTag tag);
}
