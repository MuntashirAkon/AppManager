// SPDX-License-Identifier: GPL-3.0-or-later

package androidx.documentfile.provider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.backup.convert.OABConverter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ZipDocumentFileTest {
    private final ClassLoader classLoader = getClass().getClassLoader();
    private File apkFile;

    @Before
    public void setUp() throws Exception {
        assert classLoader != null;
        apkFile = new File(new File(classLoader.getResource(OABConverter.PATH_SUFFIX).getFile(), "ademar.textlauncher"), "base.apk");
    }

    @Test
    public void testZipDocument() throws IOException {
        List<String> level1 = Arrays.asList("AndroidManifest.xml", "META-INF", "classes.dex", "res", "resources.arsc");
        ZipFile zipFile = new ZipFile(apkFile);
        ZipDocumentFile doc = new ZipDocumentFile(zipFile, null);
        assertTrue(doc.isDirectory());
        assertFalse(doc.isFile());
        assertTrue(doc.exists());
        assertEquals(doc.length(), 0);
        assertEquals(doc.getName(), File.separator);
        // Children checks
        List<String> tmpList = getChildNames(doc);
        Collections.sort(tmpList);
        assertEquals(level1, tmpList);
        // Arbitrary Directory level check
        ZipDocumentFile activityXml = doc.findFile("res/layout/activity.xml");
        assertNotNull(activityXml);
        assertTrue(activityXml.exists());
        assertTrue(activityXml.canRead());
        assertTrue(activityXml.isFile());
        assertFalse(activityXml.isDirectory());
        assertNotEquals(activityXml.length(), 0);
        assertEquals("/res/layout/activity.xml", activityXml.getFullPath());
    }

    private static List<String> getChildNames(ZipDocumentFile doc) {
        List<String> list = new ArrayList<>();
        ZipDocumentFile[] files = doc.listFiles();
        for (ZipDocumentFile file : files) {
            list.add(file.getName());
        }
        return list;
    }
}