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

import java.nio.ByteBuffer;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class AndroidBinXmlDecoderTest {
    private final ClassLoader classLoader = getClass().getClassLoader();
    private Path xmlBinary;
    private Path xmlPlain;
    private Path xmlPlainManifest;
    private Path testPath;

    @Before
    public void setUp() {
        assert classLoader != null;
        xmlBinary = Paths.get(classLoader.getResource("xml/HMS_Core_Android_Manifest.bin.xml").getFile());
        xmlPlain = Paths.get(classLoader.getResource("xml/HMS_Core_Android_Manifest.plain.xml").getFile());
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
        assertFalse(AndroidBinXmlDecoder.isBinaryXml(ByteBuffer.wrap(xmlPlain.getContentAsBinary())));
        assertFalse(AndroidBinXmlDecoder.isBinaryXml(ByteBuffer.wrap(xmlPlainManifest.getContentAsBinary())));
    }

    @Test
    public void testDecode() throws AndroidBinXmlParser.XmlParserException {
        String xml = AndroidBinXmlDecoder.decode(xmlBinary.getContentAsBinary());
        assertEquals(xmlPlain.getContentAsString(), xml);
    }

    @Test
    public void testDecodeManifest() throws AndroidBinXmlParser.XmlParserException {
        String xml = AndroidBinXmlDecoder.decode(ByteBuffer.wrap(xmlBinary.getContentAsBinary()), true);
        assertEquals(xmlPlainManifest.getContentAsString(), xml);
    }
}