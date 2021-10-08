package org.xmlpull.v1;

import java.io.IOException;

public interface XmlSerializer {
    XmlSerializer attribute(String namespace, String name, String value)
            throws IOException, IllegalArgumentException, IllegalStateException;
}