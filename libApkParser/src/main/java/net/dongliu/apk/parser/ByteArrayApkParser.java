package net.dongliu.apk.parser;

/**
 * Parse apk file from byte array.
 * This class is not thread-safe.
 *
 * @author Liu Dong
 * @deprecated using {@link ByteArrayApkFile} instead
 */
@Deprecated
public class ByteArrayApkParser extends ByteArrayApkFile {

    public ByteArrayApkParser(byte[] apkData) {
        super(apkData);
    }
}
