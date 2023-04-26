// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.ByteBuffer;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class AndroidBinXmlDecoderTest {
    private final ClassLoader classLoader = getClass().getClassLoader();
    private Path xmlBinary;
    private Path xmlPlainManifest;
    private Path testPath;

    @Before
    public void setUp() {
        assert classLoader != null;
        xmlBinary = Paths.get(classLoader.getResource("xml/HMS_Core_Android_Manifest.bin.xml").getFile());
        xmlPlainManifest = Paths.get(classLoader.getResource("xml/HMS_Core_Android_Manifest.man.xml").getFile());
        testPath = Paths.get("/tmp/test");
        testPath.mkdirs();
    }

    @After
    public void tearDown() {
        testPath.delete();
    }

    @Test
    public void testBinary() {
        assertTrue(AndroidBinXmlDecoder.isBinaryXml(ByteBuffer.wrap(xmlBinary.getContentAsBinary())));
        assertFalse(AndroidBinXmlDecoder.isBinaryXml(ByteBuffer.wrap(xmlPlainManifest.getContentAsBinary())));
    }

    @Test
    public void testDecode() throws IOException {
        String xml = AndroidBinXmlDecoder.decode(xmlBinary.getContentAsBinary());
        assertEquals(xmlPlainManifest.getContentAsString(), xml);
    }
}