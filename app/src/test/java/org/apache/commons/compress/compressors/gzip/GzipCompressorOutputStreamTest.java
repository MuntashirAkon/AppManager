// SPDX-License-Identifier: GPL-3.0-or-later

package org.apache.commons.compress.compressors.gzip;

import android.content.Context;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
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

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.ProxyInputStream;
import io.github.muntashirakon.io.SplitInputStream;
import io.github.muntashirakon.io.SplitOutputStream;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class GzipCompressorOutputStreamTest {
    private final ClassLoader classLoader = getClass().getClassLoader();
    private final Context context = AppManager.getContext();

    @Test
    public void testTarGzip() throws IOException {
        List<String> fileNames = Arrays.asList("AppManager_v2.5.22.apks.0", "AppManager_v2.5.22.apks.1");
        List<File> fileList = new ArrayList<>();
        assert classLoader != null;
        for (String fileName : fileNames) {
            fileList.add(new File(classLoader.getResource(fileName).getFile()));
        }

        try (FileOutputStream fos = new FileOutputStream("/tmp/AppManager_v2.5.22.apks.tar.gz");
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             GzipCompressorOutputStream gos = new GzipCompressorOutputStream(bos);
             TarArchiveOutputStream tos = new TarArchiveOutputStream(gos)) {
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
        List<String> actualFileNames = new ArrayList<>();
        try (FileInputStream sis = new FileInputStream("/tmp/AppManager_v2.5.22.apks.tar.gz");
             BufferedInputStream bis = new BufferedInputStream(sis);
             GzipCompressorInputStream gcis = new GzipCompressorInputStream(bis);
             TarArchiveInputStream tis = new TarArchiveInputStream(gcis)) {
            ArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                // create a new path, remember check zip slip attack
                actualFileNames.add(entry.getName());
            }
        }

        Collections.sort(fileNames);
        Collections.sort(actualFileNames);
        assertEquals(fileNames, actualFileNames);
    }

    @Test
    public void testSplitTarGzip() throws IOException {
        List<String> fileNames = Arrays.asList("AppManager_v2.5.22.apks.0", "AppManager_v2.5.22.apks.1");
        List<File> fileList = new ArrayList<>();
        assert classLoader != null;
        for (String fileName : fileNames) {
            fileList.add(new File(classLoader.getResource(fileName).getFile()));
        }

        Path tmpPath = new Path(context, new File("/tmp"));
        try (SplitOutputStream sos = new SplitOutputStream(tmpPath, "AppManager_v2.5.22.apks.tar.gz", 1041921);
             BufferedOutputStream bos = new BufferedOutputStream(sos);
             GzipCompressorOutputStream gos = new GzipCompressorOutputStream(bos);
             TarArchiveOutputStream tos = new TarArchiveOutputStream(gos)) {
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
        List<String> actualFileNames = new ArrayList<>();
        List<Path> pathList = new ArrayList<>();
        pathList.add(tmpPath.findFile("AppManager_v2.5.22.apks.tar.gz.0"));
        pathList.add(tmpPath.findFile("AppManager_v2.5.22.apks.tar.gz.1"));
        try (SplitInputStream sis = new SplitInputStream(pathList);
             BufferedInputStream bis = new BufferedInputStream(sis);
             GzipCompressorInputStream gcis = new GzipCompressorInputStream(bis);
             TarArchiveInputStream tis = new TarArchiveInputStream(gcis)) {
            ArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                // create a new path, remember check zip slip attack
                actualFileNames.add(entry.getName());
            }
        }
        Collections.sort(fileNames);
        Collections.sort(actualFileNames);
        assertEquals(fileNames, actualFileNames);
    }
}
