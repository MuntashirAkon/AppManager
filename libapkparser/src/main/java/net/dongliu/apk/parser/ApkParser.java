// SPDX-License-Identifier: BSD-2-Clause

package net.dongliu.apk.parser;

import androidx.annotation.Nullable;

import net.dongliu.apk.parser.utils.Inputs;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


/**
 * ApkParser, for parsing apk file info.
 * This class is not thread-safe.
 */
// Copyright 2016 Liu Dong
public class ApkParser extends AbstractApkFile implements Closeable {

    private final ZipFile zf;
    private final File apkFile;
    @Nullable
    private FileChannel fileChannel;

    public ApkParser(File apkFile) throws IOException {
        this.apkFile = apkFile;
        // create zip file cost time, use one zip file for apk parser life cycle
        this.zf = new ZipFile(apkFile);
    }

    public ApkParser(String filePath) throws IOException {
        this(new File(filePath));
    }

    @Override
    public byte[] getFileData(String path) throws IOException {
        ZipEntry entry = zf.getEntry(path);
        if (entry == null) {
            return null;
        }

        InputStream inputStream = zf.getInputStream(entry);
        return Inputs.readAllAndClose(inputStream);
    }

    @Override
    protected ByteBuffer fileData() throws IOException {
        fileChannel = new FileInputStream(apkFile).getChannel();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
    }


    @Override
    public void close() throws IOException {
        //noinspection EmptyTryBlock
        try (Closeable ignore = ApkParser.super::close;
             Closeable ignored1 = zf;
             Closeable ignored2 = fileChannel) {
        }
    }
}
