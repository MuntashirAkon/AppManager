// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.utils.DigestUtils;

@RunWith(RobolectricTestRunner.class)
public class SplitInputStreamTest {
    private final List<Path> fileList = new ArrayList<>();
    private final List<File> junkFiles = new ArrayList<>();
    private final ClassLoader classLoader = getClass().getClassLoader();

    @Before
    public void setUp() {
        assert classLoader != null;
        fileList.add(Paths.get(classLoader.getResource("AppManager_v2.5.22.apks.0").getFile()));
        fileList.add(Paths.get(classLoader.getResource("AppManager_v2.5.22.apks.1").getFile()));
        fileList.add(Paths.get(classLoader.getResource("AppManager_v2.5.22.apks.2").getFile()));
        fileList.add(Paths.get(classLoader.getResource("AppManager_v2.5.22.apks.3").getFile()));
        fileList.add(Paths.get(classLoader.getResource("AppManager_v2.5.22.apks.4").getFile()));
        fileList.add(Paths.get(classLoader.getResource("AppManager_v2.5.22.apks.5").getFile()));
        fileList.add(Paths.get(classLoader.getResource("AppManager_v2.5.22.apks.6").getFile()));
        fileList.add(Paths.get(classLoader.getResource("AppManager_v2.5.22.apks.7").getFile()));
    }

    @After
    public void tearDown() {
        for (File file : junkFiles) {
            file.delete();
        }
    }

    @Test
    public void read() throws IOException {
        File file = new File("/tmp/AppManager_v2.5.22.apks");
        junkFiles.add(file);
        try (SplitInputStream splitInputStream = new SplitInputStream(fileList);
             OutputStream outputStream = new FileOutputStream(file)) {
            IoUtils.copy(splitInputStream, outputStream);
        }
        assert classLoader != null;
        String expectedHash = DigestUtils.getHexDigest(DigestUtils.SHA_256, new File(classLoader.getResource("AppManager_v2.5.22.apks").getFile()));
        String actualHash = DigestUtils.getHexDigest(DigestUtils.SHA_256, file);
        assertEquals(expectedHash, actualHash);
    }

    @Test
    public void skip() throws IOException {
        try (SplitInputStream splitInputStream = new SplitInputStream(fileList)) {
            // For 1 KB
            long expectedSkipBytes = 10024;
            long actualSkipBytes = splitInputStream.skip(expectedSkipBytes);
            assertEquals(expectedSkipBytes, actualSkipBytes);
            // For 1 MB
            expectedSkipBytes = 1024 * 1024;
            actualSkipBytes = splitInputStream.skip(expectedSkipBytes);
            assertEquals(expectedSkipBytes, actualSkipBytes);
            // For 2 MB
            expectedSkipBytes = 1024 * 1024 * 2;
            actualSkipBytes = splitInputStream.skip(expectedSkipBytes);
            assertEquals(expectedSkipBytes, actualSkipBytes);
        }
    }
}