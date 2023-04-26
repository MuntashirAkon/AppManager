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
import java.util.Objects;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class AndroidBinXmlDecoderTest {
    private final ClassLoader classLoader = Objects.requireNonNull(getClass().getClassLoader());
    private Path testPath;

    @Before
    public void setUp() {
        testPath = Paths.get("/tmp/test");
        testPath.mkdirs();
    }

    @After
    public void tearDown() {
        testPath.delete();
    }

    @Test
    public void testBinary() {
        Path xmlBinary = Paths.get(classLoader.getResource("xml/HMS_Core_Android_Manifest.bin.xml").getFile());
        Path xmlPlainManifest = Paths.get(classLoader.getResource("xml/HMS_Core_Android_Manifest.man.xml").getFile());
        assertTrue(AndroidBinXmlDecoder.isBinaryXml(ByteBuffer.wrap(xmlBinary.getContentAsBinary())));
        assertFalse(AndroidBinXmlDecoder.isBinaryXml(ByteBuffer.wrap(xmlPlainManifest.getContentAsBinary())));
    }

    @Test
    public void testDecodeManifest() throws IOException {
        Path xmlBinary = Paths.get(classLoader.getResource("xml/HMS_Core_Android_Manifest.bin.xml").getFile());
        Path xmlPlainManifest = Paths.get(classLoader.getResource("xml/HMS_Core_Android_Manifest.man.xml").getFile());
        String xml = AndroidBinXmlDecoder.decode(xmlBinary.getContentAsBinary());
        assertEquals(xmlPlainManifest.getContentAsString(), xml);
    }

    @Test
    public void testDecodeLayout() throws IOException {
        Path xmlBinary = Paths.get(classLoader.getResource("xml/test_layout.bin.xml").getFile());
        Path xmlPlainManifest = Paths.get(classLoader.getResource("xml/test_layout.plain.xml").getFile());
        String xml = AndroidBinXmlDecoder.decode(xmlBinary.getContentAsBinary());
        assertEquals(xmlPlainManifest.getContentAsString(), xml);
    }
}