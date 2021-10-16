// SPDX-License-Identifier: GPL-3.0-or-later

package androidx.documentfile.provider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.backup.convert.OABConverter;
import io.github.muntashirakon.AppManager.scanner.DexClasses;

import static androidx.documentfile.provider.ZipDocumentFileTest.getChildNames;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DexDocumentFileTest {
    private final ClassLoader classLoader = getClass().getClassLoader();
    private File dexFile;

    @Before
    public void setUp() throws Exception {
        assert classLoader != null;
        dexFile = new File(new File(classLoader.getResource(OABConverter.PATH_SUFFIX).getFile(), "ademar.textlauncher"), "classes.dex");
    }

    @Test
    public void testDexFile() throws IOException {
        List<String> level1 = Arrays.asList("a", "ademar");
        DexClasses dexClasses = new DexClasses(dexFile);
        DexDocumentFile doc = new DexDocumentFile(11, dexClasses, null);
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
        DexDocumentFile activityXml = doc.findFile("ademar/textlauncher/Activity.smali");
        assertNotNull(activityXml);
        assertTrue(activityXml.exists());
        assertTrue(activityXml.canRead());
        assertTrue(activityXml.isFile());
        assertFalse(activityXml.isDirectory());
        assertNotEquals(activityXml.length(), 0);
        assertEquals("/ademar/textlauncher/Activity.smali", activityXml.getFullPath());
    }
}