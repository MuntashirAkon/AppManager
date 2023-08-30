// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import androidx.annotation.NonNull;

import com.reandroid.apk.xmlencoder.XMLEncodeSource;
import com.reandroid.xml.source.XMLFileParserSource;
import com.reandroid.xml.source.XMLParserSource;
import com.reandroid.xml.source.XMLStringParserSource;

import java.io.File;
import java.io.IOException;

public class AndroidBinXmlEncoder {
    @NonNull
    public static byte[] encodeFile(@NonNull File file) throws IOException {
        return encode(new XMLFileParserSource(file.getName(), file));
    }

    @NonNull
    public static byte[] encodeString(@NonNull String xml) throws IOException {
        return encode(new XMLStringParserSource("String.xml", xml));
    }

    @NonNull
    private static byte[] encode(@NonNull XMLParserSource xmlSource) throws IOException {
        XMLEncodeSource xmlEncodeSource = new XMLEncodeSource(AndroidBinXmlDecoder.getFrameworkPackageBlock(), xmlSource);
        return xmlEncodeSource.getBytes();
    }
}
