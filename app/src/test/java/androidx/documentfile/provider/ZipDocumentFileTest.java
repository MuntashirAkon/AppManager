// SPDX-License-Identifier: GPL-3.0-or-later

package androidx.documentfile.provider;

import android.net.Uri;

import com.j256.simplemagic.ContentType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.backup.convert.OABConverter;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.fs.VirtualFileSystem;

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
    public void testZipDocument() throws Throwable {
        List<String> level1 = Arrays.asList("AndroidManifest.xml", "META-INF", "classes.dex", "res", "resources.arsc");
        int fsId = VirtualFileSystem.mount(Uri.fromFile(new File("/tmp/zip1")), Paths.get(apkFile), ContentType.ZIP.getMimeType());
        VirtualDocumentFile doc = new VirtualDocumentFile(null, Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId)));
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
        VirtualDocumentFile resDir = doc.findFile("res");
        assertNotNull(resDir);
        VirtualDocumentFile layoutDir = resDir.findFile("layout");
        assertNotNull(layoutDir);
        VirtualDocumentFile activityXml = layoutDir.findFile("activity.xml");
        assertNotNull(activityXml);
        assertTrue(activityXml.exists());
        assertTrue(activityXml.canRead());
        assertTrue(activityXml.isFile());
        assertFalse(activityXml.isDirectory());
        assertNotEquals(activityXml.length(), 0);
        assertEquals("/res/layout/activity.xml", activityXml.getFullPath());
    }

    public static List<String> getChildNames(DocumentFile doc) {
        List<String> list = new ArrayList<>();
        DocumentFile[] files = doc.listFiles();
        for (DocumentFile file : files) {
            list.add(file.getName());
        }
        return list;
    }
}