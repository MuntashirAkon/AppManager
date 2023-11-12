// SPDX-License-Identifier: GPL-3.0-or-later

package org.apache.commons.compress.compressors.bzip2;

import static org.junit.Assert.assertEquals;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.SplitInputStream;

@RunWith(RobolectricTestRunner.class)
public class BZip2CompressorInputStreamTest {
    private final ClassLoader classLoader = Objects.requireNonNull(getClass().getClassLoader());
    private final List<File> junkFiles = new ArrayList<>();

    @After
    public void tearDown() {
        for (File file : junkFiles) {
            file.delete();
        }
    }

    @Test
    public void testUnTarBZip2() throws IOException {
        try (InputStream is = Paths.get(classLoader.getResource("AppManager_v2.5.22.apks.tar.bz2").getFile()).openInputStream();
             BufferedInputStream bis = new BufferedInputStream(is);
             BZip2CompressorInputStream bZis = new BZip2CompressorInputStream(bis);
             TarArchiveInputStream tis = new TarArchiveInputStream(bZis)) {
            ArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                // create a new path, remember check zip slip attack
                File file = new File("/tmp", entry.getName());
                // copy TarArchiveInputStream to newPath
                try (OutputStream os = Paths.get(file).openOutputStream()) {
                    IoUtils.copy(tis, os);
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
            junkFiles.add(file);
        }
        assertEquals(expectedHashes, actualHashes);
    }

    @Test
    public void testSplitUnTarBZip2() throws IOException {
        List<Path> pathList = new ArrayList<>();
        pathList.add(Paths.get(classLoader.getResource("AppManager_v2.5.22.apks.tar.bz2.0").getFile()));
        pathList.add(Paths.get(classLoader.getResource("AppManager_v2.5.22.apks.tar.bz2.1").getFile()));

        try (SplitInputStream sis = new SplitInputStream(pathList);
             BufferedInputStream bis = new BufferedInputStream(sis);
             BZip2CompressorInputStream bZis = new BZip2CompressorInputStream(bis);
             TarArchiveInputStream tis = new TarArchiveInputStream(bZis)) {
            ArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                // create a new path, remember check zip slip attack
                File file = new File("/tmp", entry.getName());
                // copy TarArchiveInputStream to newPath
                try (OutputStream os = Paths.get(file).openOutputStream()) {
                    IoUtils.copy(tis, os);
                }
            }
        }

        // Check integrity
        List<String> expectedHashes = new ArrayList<>();
        List<File> fileList = new ArrayList<>();
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
            junkFiles.add(file);
        }
        assertEquals(expectedHashes, actualHashes);
    }
}