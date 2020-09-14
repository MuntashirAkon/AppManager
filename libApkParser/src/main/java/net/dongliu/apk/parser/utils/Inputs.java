package net.dongliu.apk.parser.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Inputs {

    public static byte[] readAll(InputStream in) throws IOException {
        byte[] buf = new byte[1024 * 8];
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            int len;
            while ((len = in.read(buf)) != -1) {
                bos.write(buf, 0, len);
            }
            return bos.toByteArray();
        }
    }

    public static byte[] readAllAndClose(InputStream in) throws IOException {
        try {
            return readAll(in);
        } finally {
            in.close();
        }
    }
}
