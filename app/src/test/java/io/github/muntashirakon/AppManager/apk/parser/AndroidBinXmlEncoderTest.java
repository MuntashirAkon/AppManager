// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import static org.junit.Assert.assertEquals;
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
public class AndroidBinXmlEncoderTest {
    private final ClassLoader classLoader = Objects.requireNonNull(getClass().getClassLoader());
    private Path testPath;

    @Before
    public void setUp() throws Exception {
        testPath = Paths.get("/tmp/test");
        testPath.mkdirs();
    }

    @After
    public void tearDown() {
        testPath.delete();
    }

    @Test
    public void testEncodeManifest() throws IOException {
        Path xmlBinary = Paths.get(classLoader.getResource("xml/HMS_Core_Android_Manifest.bin.xml").getFile());
        Path xmlPlain = Paths.get(classLoader.getResource("xml/HMS_Core_Android_Manifest.man.xml").getFile());
        // Encode
        String xml = xmlPlain.getContentAsString();
        byte[] bytes = AndroidBinXmlEncoder.encodeString(xml);
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        assertTrue(AndroidBinXmlDecoder.isBinaryXml(byteBuffer));
        // Decode
        String actualXml = AndroidBinXmlDecoder.decode(byteBuffer);
//        byte[] manifestContent = xmlBinary.getContentAsBinary();
//        assertArrayEquals(manifestContent, bytes);
        assertEquals(xml, actualXml);
    }

    @Test
    public void testEncodeLayout() throws IOException {
        Path xmlBinary = Paths.get(classLoader.getResource("xml/test_layout.bin.xml").getFile());
        Path xmlPlain = Paths.get(classLoader.getResource("xml/test_layout.plain.xml").getFile());
        // Encode
        String xml = xmlPlain.getContentAsString();
        byte[] bytes = AndroidBinXmlEncoder.encodeString(xml);
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        assertTrue(AndroidBinXmlDecoder.isBinaryXml(byteBuffer));
        // Decode
        String actualXml = AndroidBinXmlDecoder.decode(byteBuffer);
//        byte[] manifestContent = xmlBinary.getContentAsBinary();
//        assertArrayEquals(manifestContent, bytes);
        assertEquals(xml, actualXml);
    }
}