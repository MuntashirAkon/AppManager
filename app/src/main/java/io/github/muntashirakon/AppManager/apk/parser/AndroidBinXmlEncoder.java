// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import androidx.annotation.NonNull;

import com.reandroid.apk.AndroidFrameworks;
import com.reandroid.apk.FrameworkApk;
import com.reandroid.apk.xmlencoder.EncodeMaterials;
import com.reandroid.apk.xmlencoder.XMLEncodeSource;
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.xml.source.XMLFileSource;
import com.reandroid.xml.source.XMLSource;
import com.reandroid.xml.source.XMLStringSource;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class AndroidBinXmlEncoder {
    @NonNull
    public static byte[] encodeFile(@NonNull File file) throws IOException {
        return encode(new XMLFileSource(file.getName(), file));
    }

    @NonNull
    public static byte[] encodeString(@NonNull String xml) throws IOException {
        return encode(new XMLStringSource("String.xml", xml));
    }

    @NonNull
    private static byte[] encode(@NonNull XMLSource xmlSource) throws IOException {
        XMLEncodeSource xmlEncodeSource = new XMLEncodeSource(getDefaultEncodeMaterials(), xmlSource);
        return xmlEncodeSource.getResXmlBlock().getBytes();
    }

    private static EncodeMaterials getDefaultEncodeMaterials() throws IOException {
        if (sDefaultEncodeMaterials != null) {
            return sDefaultEncodeMaterials;
        }
        FrameworkApk frameworkApk = AndroidFrameworks.getLatest();
        Collection<PackageBlock> packageBlocks = frameworkApk.getTableBlock().listPackages();
        if (packageBlocks.isEmpty()) {
            throw new IOException("Framework apk does not contain any packages!");
        }
        PackageBlock packageBlock = packageBlocks.iterator().next();
        sDefaultEncodeMaterials = new EncodeMaterials()
                .addFramework(frameworkApk)
                .setCurrentPackage(packageBlock);
        return sDefaultEncodeMaterials;
    }

    private static EncodeMaterials sDefaultEncodeMaterials;
}
