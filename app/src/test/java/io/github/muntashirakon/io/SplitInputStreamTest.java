/*
 * Copyright (c) 2021 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.io;

import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SplitInputStreamTest {
    private SplitInputStream splitInputStream;
    private final ClassLoader classLoader = getClass().getClassLoader();

    @Before
    public void setUp() {
        assert classLoader != null;
        List<File> fileList = new ArrayList<>();
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.0").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.1").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.2").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.3").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.4").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.5").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.6").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.7").getFile()));
        splitInputStream = new SplitInputStream(fileList);

    }

    @After
    public void tearDown() throws Exception {
        splitInputStream.close();
    }

    @Test
    public void read() throws IOException {
        File file = new File("/tmp/AppManager_v2.5.22.apks");
        try (OutputStream outputStream = new FileOutputStream(file)) {
            IOUtils.copy(splitInputStream, outputStream);
        }
        assert classLoader != null;
        String expectedHash = DigestUtils.getHexDigest(DigestUtils.SHA_256, new File(classLoader.getResource("AppManager_v2.5.22.apks").getFile()));
        String actualHash = DigestUtils.getHexDigest(DigestUtils.SHA_256, file);
        assertEquals(expectedHash, actualHash);
    }

    @Test
    public void skip() throws IOException {
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