// SPDX-License-Identifier: GPL-3.0-or-later

package org.apache.commons.compress.compressors.bzip2;

import static org.junit.Assert.assertEquals;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
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
public class BZip2CompressorOutputStreamTest {
    private final ClassLoader classLoader = getClass().getClassLoader();
    private final List<File> junkFiles = new ArrayList<>();

    @After
    public void tearDown() {
        for (File file : junkFiles) {
            file.delete();
        }
    }

    @Test
    public void testTarGzip() throws IOException {
        File base = new File("/tmp/AppManager_v2.5.22.apks.tar.bz2");
        List<String> fileNames = Arrays.asList("AppManager_v2.5.22.apks.0", "AppManager_v2.5.22.apks.1");
        List<Path> fileList = new ArrayList<>();
        assert classLoader != null;
        for (String fileName : fileNames) {
            fileList.add(Paths.get(classLoader.getResource(fileName).getFile()));
        }

        try (FileOutputStream fos = new FileOutputStream(base);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             BZip2CompressorOutputStream bZos = new BZip2CompressorOutputStream(bos);
             TarArchiveOutputStream tos = new TarArchiveOutputStream(bZos)) {
            for (Path file : fileList) {
                TarArchiveEntry tarEntry = new TarArchiveEntry(file, file.getName());
                tos.putArchiveEntry(tarEntry);
                try (InputStream is = file.openInputStream()) {
                    IoUtils.copy(is, tos);
                }
                tos.closeArchiveEntry();
            }
            tos.finish();
        }

        // Check integrity
        List<String> actualFileNames = new ArrayList<>();
        try (FileInputStream sis = new FileInputStream(base);
             BufferedInputStream bis = new BufferedInputStream(sis);
             BZip2CompressorInputStream bcis = new BZip2CompressorInputStream(bis);
             TarArchiveInputStream tis = new TarArchiveInputStream(bcis)) {
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

    @Test
    public void testSplitTarBZip2() throws IOException {
        List<String> fileNames = Arrays.asList("AppManager_v2.5.22.apks.0", "AppManager_v2.5.22.apks.1");
        List<Path> fileList = new ArrayList<>();
        assert classLoader != null;
        for (String fileName : fileNames) {
            fileList.add(Paths.get(classLoader.getResource(fileName).getFile()));
        }

        Path tmpPath = Paths.get("/tmp");
        try (SplitOutputStream sos = new SplitOutputStream(tmpPath, "AppManager_v2.5.22.apks.tar.bz2", 1024 * 1024);
             BufferedOutputStream bos = new BufferedOutputStream(sos);
             BZip2CompressorOutputStream bZos = new BZip2CompressorOutputStream(bos);
             TarArchiveOutputStream tos = new TarArchiveOutputStream(bZos)) {
            for (Path file : fileList) {
                TarArchiveEntry tarEntry = new TarArchiveEntry(file, file.getName());
                tos.putArchiveEntry(tarEntry);
                try (InputStream is = file.openInputStream()) {
                    IoUtils.copy(is, tos);
                }
                tos.closeArchiveEntry();
            }
            tos.finish();
        }

        // Check integrity
        List<String> actualFileNames = new ArrayList<>();
        List<Path> pathList = new ArrayList<>();
        pathList.add(tmpPath.findFile("AppManager_v2.5.22.apks.tar.bz2.0"));
        pathList.add(tmpPath.findFile("AppManager_v2.5.22.apks.tar.bz2.1"));
        try (SplitInputStream sis = new SplitInputStream(pathList);
             BufferedInputStream bis = new BufferedInputStream(sis);
             BZip2CompressorInputStream bcis = new BZip2CompressorInputStream(bis);
             TarArchiveInputStream tis = new TarArchiveInputStream(bcis)) {
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
}