// SPDX-License-Identifier: GPL-3.0-or-later

package org.apache.commons.compress.compressors.bzip2;

import android.os.RemoteException;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.Test;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.io.ProxyInputStream;
import io.github.muntashirakon.io.SplitOutputStream;

import static org.junit.Assert.assertEquals;

public class BZip2CompressorOutputStreamTest {
    private final ClassLoader classLoader = getClass().getClassLoader();

    @Test
    public void testTarGzip() throws IOException, RemoteException {
        List<File> fileList = new ArrayList<>();
        assert classLoader != null;
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.0").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.1").getFile()));

        try (FileOutputStream fos = new FileOutputStream("/tmp/AppManager_v2.5.22.apks.tar.bz2");
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             BZip2CompressorOutputStream bZos = new BZip2CompressorOutputStream(bos);
             TarArchiveOutputStream tos = new TarArchiveOutputStream(bZos)) {
            for (File file : fileList) {
                TarArchiveEntry tarEntry = new TarArchiveEntry(file, file.getName());
                tos.putArchiveEntry(tarEntry);
                try (InputStream is = new ProxyInputStream(file)) {
                    IOUtils.copy(is, tos);
                }
                tos.closeArchiveEntry();
            }
            tos.finish();
        }

        // Check integrity
        String expectedHash = DigestUtils.getHexDigest(DigestUtils.SHA_256, new File(classLoader.getResource("AppManager_v2.5.22.apks.tar.bz2").getFile()));
        String actualHash = DigestUtils.getHexDigest(DigestUtils.SHA_256, new File("/tmp/AppManager_v2.5.22.apks.tar.bz2"));
        assertEquals(expectedHash, actualHash);
    }

    @Test
    public void testSplitTarBZip2() throws IOException, RemoteException {
        List<File> fileList = new ArrayList<>();
        assert classLoader != null;
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.0").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.1").getFile()));

        try (SplitOutputStream sos = new SplitOutputStream("/tmp/AppManager_v2.5.22.apks.tar.bz2", 1024 * 1024);
             BufferedOutputStream bos = new BufferedOutputStream(sos);
             BZip2CompressorOutputStream bZos = new BZip2CompressorOutputStream(bos);
             TarArchiveOutputStream tos = new TarArchiveOutputStream(bZos)) {
            for (File file : fileList) {
                TarArchiveEntry tarEntry = new TarArchiveEntry(file, file.getName());
                tos.putArchiveEntry(tarEntry);
                try (InputStream is = new ProxyInputStream(file)) {
                    IOUtils.copy(is, tos);
                }
                tos.closeArchiveEntry();
            }
            tos.finish();
        }

        // Check integrity
        List<String> expectedHashes = new ArrayList<>();
        fileList.clear();
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.tar.bz2.0").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.tar.bz2.1").getFile()));
        for (File file : fileList) {
            expectedHashes.add(DigestUtils.getHexDigest(DigestUtils.SHA_256, file));
        }
        List<String> actualHashes = new ArrayList<>();
        fileList.clear();
        fileList.add(new File("/tmp/AppManager_v2.5.22.apks.tar.bz2.0"));
        fileList.add(new File("/tmp/AppManager_v2.5.22.apks.tar.bz2.1"));
        for (File file : fileList) {
            if (!file.exists()) {
                throw new FileNotFoundException(file + " does not exist.");
            }
            actualHashes.add(DigestUtils.getHexDigest(DigestUtils.SHA_256, file));
        }
        assertEquals(expectedHashes, actualHashes);
    }
}