// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.RoboUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.AppManager.utils.TarUtilsTest;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class AndroidBackupExtractorTest {
    private final ClassLoader classLoader = getClass().getClassLoader();
    private Path testDir;
    private Path abPath;
    private Path tarPath;

    @Before
    public void setUp() throws Exception {
        assert classLoader != null;
        testDir = Paths.get(RoboUtils.getTestBaseDir()).createNewDirectory("test-dir");
        abPath = Paths.get(classLoader.getResource("backups/adb/all_data.ab").getFile());
        tarPath = Paths.get(classLoader.getResource("backups/adb/all_data.tar").getFile());
    }

    @After
    public void tearDown() throws Exception {
        testDir.delete();
    }

    @Test
    public void testAbToTar() throws IOException {
        Path tmpTarPath = testDir.createNewFile("output.tar", null);
        AndroidBackupExtractor.toTar(abPath, tmpTarPath, null);
        assertEquals(DigestUtils.getHexDigest(DigestUtils.SHA_256, tarPath),
                DigestUtils.getHexDigest(DigestUtils.SHA_256, tmpTarPath));
    }

    @Test
    public void testAbToAmBackup() throws IOException {
        List<String> internalFiles = new ArrayList<String>() {{
            add("root_data.txt");
            add("files/internal_data.txt");
            add("databases/mixed_test_db-journal");
            add("databases/mixed_test_db");
            add("shared_prefs/mixed_prefs.xml");
        }};
        List<String> externalFiles = new ArrayList<String>() {{
            add("Images/");
            add("Images/test_image.txt");
            add("Documents/");
            add("Documents/document.txt");
            add("external_data.txt");
        }};
        try (AndroidBackupExtractor extractor = new AndroidBackupExtractor(abPath, testDir,
                "io.github.muntashirakon.androidbackuptestapps.all_data")) {
            // CAT_SRC
            Path[] sourceFiles = extractor.getSourceFiles(".tar.gz", TarUtils.TAR_GZIP);
            assertNotNull(sourceFiles);
            assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(Arrays.asList(sourceFiles)));
            sourceFiles[0].copyTo(Paths.get("/tmp"));
            // CAT_INT_CE
            Path[] intCeFiles = extractor.getInternalCeDataFiles(0, ".tar.gz", TarUtils.TAR_GZIP);
            assertNotNull(intCeFiles);
            assertEquals(internalFiles, TarUtilsTest.getFileNamesGZip(Arrays.asList(intCeFiles)));
            intCeFiles[0].copyTo(Paths.get("/tmp"));
            // CAT_INT_DE
            Path[] intDeFiles = extractor.getInternalDeDataFiles(1, ".tar.gz", TarUtils.TAR_GZIP);
            assertNotNull(intDeFiles);
            assertEquals(internalFiles, TarUtilsTest.getFileNamesGZip(Arrays.asList(intDeFiles)));
            intDeFiles[0].copyTo(Paths.get("/tmp"));
            // CAT_EXT
            Path[] extFiles = extractor.getExternalDataFiles(2, ".tar.gz", TarUtils.TAR_GZIP);
            assertNotNull(extFiles);
            assertEquals(externalFiles, TarUtilsTest.getFileNamesGZip(Arrays.asList(extFiles)));
            extFiles[0].copyTo(Paths.get("/tmp"));
            // CAT_OBB
            assertNull(extractor.getObbFiles(3, ".tar.gz", TarUtils.TAR_GZIP));
        }
    }

    @Test
    public void testKeyValue() {
        assertThrows("Unknown/unsupported entries detected.", IOException.class, () -> {
            assert classLoader != null;
            Path abPath = Paths.get(classLoader.getResource("backups/adb/key_value.ab").getFile());
            try (AndroidBackupExtractor ignored = new AndroidBackupExtractor(abPath, testDir,
                    "io.github.muntashirakon.androidbackuptestapps.key_value")) {
            }
        });
    }
}
