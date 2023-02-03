// SPDX-License-Identifier: GPL-3.0-or-later

package androidx.documentfile.provider;

import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.backup.convert.OABConverter;
import io.github.muntashirakon.AppManager.fm.ContentType2;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.fs.VirtualFileSystem;

import static androidx.documentfile.provider.ZipDocumentFileTest.getChildNames;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
    public void testDexFile() throws Throwable {
        List<String> level1 = Arrays.asList("a", "ademar");
        int fsId = VirtualFileSystem.mount(Uri.fromFile(new File("/tmp/dex1")), Paths.get(dexFile), ContentType2.DEX.getMimeType());
        VirtualDocumentFile doc = new VirtualDocumentFile(null, Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId)));
        assertTrue(doc.isDirectory());
        assertFalse(doc.isFile());
        assertTrue(doc.exists());
        assertEquals(doc.length(), 0);
        assertEquals(File.separator, doc.getName());
        // Children checks
        List<String> tmpList = getChildNames(doc);
        Collections.sort(tmpList);
        assertEquals(level1, tmpList);
        // Arbitrary Directory level check
        VirtualDocumentFile ademarDir = doc.findFile("ademar");
        assertNotNull(ademarDir);
        VirtualDocumentFile textLauncherDir = ademarDir.findFile("textlauncher");
        assertNotNull(textLauncherDir);
        VirtualDocumentFile activitySmali = textLauncherDir.findFile("Activity.smali");
        assertNotNull(activitySmali);
        assertTrue(activitySmali.exists());
        assertTrue(activitySmali.canRead());
        assertTrue(activitySmali.isFile());
        assertFalse(activitySmali.isDirectory());
        assertNotEquals(activitySmali.length(), 0);
        assertEquals("/ademar/textlauncher/Activity.smali", activitySmali.getFullPath());
        // Parent check
        DocumentFile parent = activitySmali.getParentFile();
        assertNotNull(parent);
        assertTrue(parent.exists());
        assertTrue(parent.canRead());
        assertFalse(parent.isFile());
        assertTrue(parent.isDirectory());
        assertEquals(parent.length(), 0);
        assertEquals("/ademar/textlauncher", ((VirtualDocumentFile) parent).getFullPath());
        assertEquals(textLauncherDir.getUri(), parent.getUri());
        VirtualDocumentFile invalidFile = textLauncherDir.findFile("Invalid.smali");
        assertNull(invalidFile);
    }
}