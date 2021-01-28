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

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SplitOutputStreamTest {
    private SplitOutputStream splitOutputStream;
    private InputStream inputStream;
    private final ClassLoader classLoader = getClass().getClassLoader();

    @Before
    public void setUp() throws Exception {
        splitOutputStream = new SplitOutputStream("/tmp/AppManager_v2.5.22.apks", 1024*1024);
        assert classLoader != null;
        File sampleFile = new File(classLoader.getResource("AppManager_v2.5.22.apks").getFile());
        inputStream = new FileInputStream(sampleFile);
    }

    @After
    public void tearDown() throws Exception {
        splitOutputStream.close();
        inputStream.close();
    }

    @Test
    public void write() throws IOException {
        IOUtils.copy(inputStream, splitOutputStream);
        List<String> expectedHashes = getExpectedHashes();
        List<String> actualHashes = getActualHashes();
        assertEquals(expectedHashes, actualHashes);
    }

    @NonNull
    private List<String> getExpectedHashes() {
        List<String> expectedHashes = new ArrayList<>();
        List<File> fileList = new ArrayList<>();
        assert classLoader != null;
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.0").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.1").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.2").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.3").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.4").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.5").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.6").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.7").getFile()));
        for (File file : fileList) {
            expectedHashes.add(DigestUtils.getHexDigest(DigestUtils.SHA_256, file));
        }
        return expectedHashes;
    }

    @NonNull
    private List<String> getActualHashes() throws FileNotFoundException {
        List<String> actualHashes = new ArrayList<>();
        List<File> fileList = new ArrayList<>();
        fileList.add(new File("/tmp/AppManager_v2.5.22.apks.0"));
        fileList.add(new File("/tmp/AppManager_v2.5.22.apks.1"));
        fileList.add(new File("/tmp/AppManager_v2.5.22.apks.2"));
        fileList.add(new File("/tmp/AppManager_v2.5.22.apks.3"));
        fileList.add(new File("/tmp/AppManager_v2.5.22.apks.4"));
        fileList.add(new File("/tmp/AppManager_v2.5.22.apks.5"));
        fileList.add(new File("/tmp/AppManager_v2.5.22.apks.6"));
        fileList.add(new File("/tmp/AppManager_v2.5.22.apks.7"));
        for (File file : fileList) {
            if (!file.exists()) {
                throw new FileNotFoundException(file + " does not exist.");
            }
            actualHashes.add(DigestUtils.getHexDigest(DigestUtils.SHA_256, file));
        }
        return actualHashes;
    }
}