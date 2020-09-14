package net.dongliu.apk.parser;

import java.io.File;
import java.io.IOException;


/**
 * ApkParse and result holder.
 * This class is not thread-safe.
 *
 * @author dongliu
 * @deprecated use {@link net.dongliu.apk.parser.ApkFile} instead
 */
@Deprecated
public class ApkParser extends ApkFile {

    public ApkParser(File apkFile) throws IOException {
        super(apkFile);
    }

    public ApkParser(String filePath) throws IOException {
        super(filePath);
    }
}
