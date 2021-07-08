// SPDX-License-Identifier: GPL-3.0-or-later

package org.apache.commons.compress.compressors.bzip2;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.io.ProxyInputStream;
import io.github.muntashirakon.io.ProxyOutputStream;
import io.github.muntashirakon.io.SplitInputStream;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class BZip2CompressorInputStreamTest {
    private final ClassLoader classLoader = getClass().getClassLoader();

    @Test
    public void testUnTarBZip2() throws IOException {
        assert classLoader != null;
        try (ProxyInputStream pis = new ProxyInputStream(new File(classLoader.getResource("AppManager_v2.5.22.apks.tar.bz2").getFile()));
             BufferedInputStream bis = new BufferedInputStream(pis);
             BZip2CompressorInputStream bZis = new BZip2CompressorInputStream(bis);
             TarArchiveInputStream tis = new TarArchiveInputStream(bZis)) {
            ArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                // create a new path, remember check zip slip attack
                File file = new File("/tmp", entry.getName());
                // copy TarArchiveInputStream to newPath
                try (OutputStream os = new ProxyOutputStream(file)) {
                    IOUtils.copy(tis, os);
                }
            }
        }

        // Check integrity
        List<File> fileList = new ArrayList<>();
        List<String> expectedHashes = new ArrayList<>();
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.0").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.1").getFile()));
        for (File file : fileList) {
            expectedHashes.add(DigestUtils.getHexDigest(DigestUtils.SHA_256, file));
        }
        List<String> actualHashes = new ArrayList<>();
        fileList.clear();
        fileList.add(new File("/tmp/AppManager_v2.5.22.apks.0"));
        fileList.add(new File("/tmp/AppManager_v2.5.22.apks.1"));
        for (File file : fileList) {
            if (!file.exists()) {
                throw new FileNotFoundException(file + " does not exist.");
            }
            actualHashes.add(DigestUtils.getHexDigest(DigestUtils.SHA_256, file));
        }
        assertEquals(expectedHashes, actualHashes);
    }

    @Test
    public void testSplitUnTarBZip2() throws IOException {
        List<File> fileList = new ArrayList<>();
        assert classLoader != null;
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.tar.bz2.0").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.tar.bz2.1").getFile()));

        try (SplitInputStream sis = new SplitInputStream(fileList);
             BufferedInputStream bis = new BufferedInputStream(sis);
             BZip2CompressorInputStream bZis = new BZip2CompressorInputStream(bis);
             TarArchiveInputStream tis = new TarArchiveInputStream(bZis)) {
            ArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                // create a new path, remember check zip slip attack
                File file = new File("/tmp", entry.getName());
                // copy TarArchiveInputStream to newPath
                try (OutputStream os = new ProxyOutputStream(file)) {
                    IOUtils.copy(tis, os);
                }
            }
        }

        // Check integrity
        List<String> expectedHashes = new ArrayList<>();
        fileList.clear();
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.0").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.1").getFile()));
        for (File file : fileList) {
            expectedHashes.add(DigestUtils.getHexDigest(DigestUtils.SHA_256, file));
        }
        List<String> actualHashes = new ArrayList<>();
        fileList.clear();
        fileList.add(new File("/tmp/AppManager_v2.5.22.apks.0"));
        fileList.add(new File("/tmp/AppManager_v2.5.22.apks.1"));
        for (File file : fileList) {
            if (!file.exists()) {
                throw new FileNotFoundException(file + " does not exist.");
            }
            actualHashes.add(DigestUtils.getHexDigest(DigestUtils.SHA_256, file));
        }
        assertEquals(expectedHashes, actualHashes);
    }
}