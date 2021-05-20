// SPDX-License-Identifier: BSD-2-Clause

package net.dongliu.apk.parser;

/**
 * Parse apk file from byte array.
 * This class is not thread-safe.
 *
 * @deprecated use {@link ByteArrayApkFile} instead
 */
// Copyright 2016 董刘
@Deprecated
public class ByteArrayApkParser extends ByteArrayApkFile {

    public ByteArrayApkParser(byte[] apkData) {
        super(apkData);
    }
}
