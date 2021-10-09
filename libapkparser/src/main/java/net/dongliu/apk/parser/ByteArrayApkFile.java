// SPDX-License-Identifier: BSD-2-Clause

package net.dongliu.apk.parser;

import net.dongliu.apk.parser.utils.Inputs;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Parse apk file from byte array.
 * This class is not thread-safe
 */
// Copyright 2016 Liu Dong
public class ByteArrayApkFile extends AbstractApkFile implements Closeable {

    private byte[] apkData;

    public ByteArrayApkFile(byte[] apkData) {
        this.apkData = apkData;
    }

    @Override
    public byte[] getFileData(String path) throws IOException {
        try (InputStream in = new ByteArrayInputStream(apkData);
             ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (path.equals(entry.getName())) {
                    return Inputs.readAll(zis);
                }
            }
        }
        return null;
    }

    @Override
    protected ByteBuffer fileData() {
        return ByteBuffer.wrap(apkData).asReadOnlyBuffer();
    }

    @Override
    public void close() throws IOException {
        super.close();
        this.apkData = null;
    }
}
