// SPDX-License-Identifier: GPL-3.0-or-later

package org.apache.commons.compress.archivers.tar;

import static org.junit.Assert.assertEquals;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.SplitInputStream;
import io.github.muntashirakon.io.SplitOutputStream;

@RunWith(RobolectricTestRunner.class)
public class TarArchiveOutputStreamTest {
    private final ClassLoader classLoader = getClass().getClassLoader();
    private final List<File> junkFiles = new ArrayList<>();

    @After
    public void tearDown() {
        for (File file : junkFiles) {
            file.delete();
        }
    }

    @Test
    public void testTarSplit() throws IOException {
        List<String> fileNames = Arrays.asList("AppManager_v2.5.22.apks.0", "AppManager_v2.5.22.apks.1");
        List<Path> fileList = new ArrayList<>();
        assert classLoader != null;
        for (String fileName : fileNames) {
            fileList.add(Paths.get(classLoader.getResource(fileName).getFile()));
        }

        Path tmpPath = Paths.get("/tmp");
        // Always run tests using SplitOutputStream
        try (SplitOutputStream sot = new SplitOutputStream(tmpPath, "AppManager_v2.5.22.apks.tar", 1024 * 1024);
             BufferedOutputStream bot = new BufferedOutputStream(sot);
             TarArchiveOutputStream tot = new TarArchiveOutputStream(bot)) {
            for (Path file : fileList) {
                TarArchiveEntry tarEntry = new TarArchiveEntry(file, file.getName());
                tot.putArchiveEntry(tarEntry);
                try (InputStream is = file.openInputStream()) {
                    IoUtils.copy(is, tot);
                }
                tot.closeArchiveEntry();
            }
            tot.finish();
        }

        // Check integrity
        List<String> actualFileNames = new ArrayList<>();
        List<Path> pathList = new ArrayList<>();
        pathList.add(tmpPath.findFile("AppManager_v2.5.22.apks.tar.0"));
        pathList.add(tmpPath.findFile("AppManager_v2.5.22.apks.tar.1"));
        try (SplitInputStream sis = new SplitInputStream(pathList);
             BufferedInputStream bis = new BufferedInputStream(sis);
             TarArchiveInputStream tis = new TarArchiveInputStream(bis)) {
            ArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                // create a new path, remember check zip slip attack
                actualFileNames.add(entry.getName());
            }
        }
        Collections.sort(fileNames);
        Collections.sort(actualFileNames);
        assertEquals(fileNames, actualFileNames);
        for (Path path : pathList) {
            junkFiles.add(path.getFile());
        }
    }

    @Test
    public void testTar() throws IOException {
        File base = new File("/tmp/AppManager_v2.5.22.apks.tar");
        List<String> fileNames = Arrays.asList("AppManager_v2.5.22.apks.0", "AppManager_v2.5.22.apks.1");
        List<Path> fileList = new ArrayList<>();
        assert classLoader != null;
        for (String fileName : fileNames) {
            fileList.add(Paths.get(classLoader.getResource(fileName).getFile()));
        }

        // Always run tests using SplitOutputStream
        try (FileOutputStream sot = new FileOutputStream(base);
             BufferedOutputStream bot = new BufferedOutputStream(sot);
             TarArchiveOutputStream tot = new TarArchiveOutputStream(bot)) {
            for (Path file : fileList) {
                TarArchiveEntry tarEntry = new TarArchiveEntry(file, file.getName());
                tot.putArchiveEntry(tarEntry);
                try (InputStream is = file.openInputStream()) {
                    IoUtils.copy(is, tot);
                }
                tot.closeArchiveEntry();
            }
            tot.finish();
        }

        // Check integrity
        List<String> actualFileNames = new ArrayList<>();
        try (FileInputStream sis = new FileInputStream(base);
             BufferedInputStream bis = new BufferedInputStream(sis);
             TarArchiveInputStream tis = new TarArchiveInputStream(bis)) {
            ArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                // create a new path, remember check zip slip attack
                actualFileNames.add(entry.getName());
            }
        }
        Collections.sort(fileNames);
        Collections.sort(actualFileNames);
        assertEquals(fileNames, actualFileNames);
        junkFiles.add(base);
    }
}