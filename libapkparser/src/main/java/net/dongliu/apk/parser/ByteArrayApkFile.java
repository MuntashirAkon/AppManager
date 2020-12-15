package net.dongliu.apk.parser;

import net.dongliu.apk.parser.bean.ApkSignStatus;
import net.dongliu.apk.parser.utils.Inputs;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Parse apk file from byte array.
 * This class is not thread-safe
 *
 * @author Liu Dong
 */
public class ByteArrayApkFile extends AbstractApkFile implements Closeable {

    private byte[] apkData;

    public ByteArrayApkFile(byte[] apkData) {
        this.apkData = apkData;
    }

    @Override
    protected List<CertificateFile> getAllCertificateData() throws IOException {
        List<CertificateFile> list = new ArrayList<>();
        try (InputStream in = new ByteArrayInputStream(apkData);
             ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.toUpperCase().endsWith(".RSA") || name.toUpperCase().endsWith(".DSA")) {
                    list.add(new CertificateFile(name, Inputs.readAll(zis)));
                }
            }
        }
        return list;
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

    @Deprecated
    @Override
    public ApkSignStatus verifyApk() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
        super.close();
        this.apkData = null;
    }
}
