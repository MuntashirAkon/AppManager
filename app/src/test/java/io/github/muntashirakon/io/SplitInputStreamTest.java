// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.content.Context;

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

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class SplitInputStreamTest {
    private final List<Path> fileList = new ArrayList<>();
    private final ClassLoader classLoader = getClass().getClassLoader();
    private final Context context = AppManager.getContext();

    @Before
    public void setUp() {
        assert classLoader != null;
        fileList.add(new Path(context, new File(classLoader.getResource("AppManager_v2.5.22.apks.0").getFile())));
        fileList.add(new Path(context, new File(classLoader.getResource("AppManager_v2.5.22.apks.1").getFile())));
        fileList.add(new Path(context, new File(classLoader.getResource("AppManager_v2.5.22.apks.2").getFile())));
        fileList.add(new Path(context, new File(classLoader.getResource("AppManager_v2.5.22.apks.3").getFile())));
        fileList.add(new Path(context, new File(classLoader.getResource("AppManager_v2.5.22.apks.4").getFile())));
        fileList.add(new Path(context, new File(classLoader.getResource("AppManager_v2.5.22.apks.5").getFile())));
        fileList.add(new Path(context, new File(classLoader.getResource("AppManager_v2.5.22.apks.6").getFile())));
        fileList.add(new Path(context, new File(classLoader.getResource("AppManager_v2.5.22.apks.7").getFile())));
    }

    @Test
    public void read() throws IOException {
        File file = new File("/tmp/AppManager_v2.5.22.apks");
        try (SplitInputStream splitInputStream = new SplitInputStream(fileList);
             OutputStream outputStream = new FileOutputStream(file)) {
            IOUtils.copy(splitInputStream, outputStream);
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