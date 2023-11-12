// SPDX-License-Identifier: GPL-3.0-or-later

package org.apache.commons.compress.archivers.tar;

import static org.junit.Assert.assertEquals;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.junit.After;
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
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.SplitInputStream;

@RunWith(RobolectricTestRunner.class)
public class TarArchiveInputStreamTest {
    private final ClassLoader classLoader = getClass().getClassLoader();
    private final List<File> junkFiles = new ArrayList<>();

    @After
    public void tearDown() {
        for (File file : junkFiles) {
            file.delete();
        }
    }

    @Test
    public void TestUnTar() throws IOException {
        List<Path> pathList = new ArrayList<>();
        assert classLoader != null;
        pathList.add(Paths.get(classLoader.getResource("AppManager_v2.5.22.apks.tar.0").getFile()));
        pathList.add(Paths.get(classLoader.getResource("AppManager_v2.5.22.apks.tar.1").getFile()));

        // Always run tests using SplitInputStream
        try (SplitInputStream sis = new SplitInputStream(pathList);
             BufferedInputStream bis = new BufferedInputStream(sis);
             TarArchiveInputStream tis = new TarArchiveInputStream(bis)) {
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