// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.IoUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class AndroidBinXmlDecoderTest {
    private final ClassLoader classLoader = getClass().getClassLoader();
    private File xmlBinary;
    private File xmlPlain;
    private File xmlPlainManifest;
    private File testPath;

    @Before
    public void setUp() {
        assert classLoader != null;
        xmlBinary = new File(classLoader.getResource("xml/HMS_Core_Android_Manifest.bin.xml").getFile());
        xmlPlain = new File(classLoader.getResource("xml/HMS_Core_Android_Manifest.plain.xml").getFile());
        xmlPlainManifest = new File(classLoader.getResource("xml/HMS_Core_Android_Manifest.man.xml").getFile());
        testPath = new File("/tmp/test");
        testPath.mkdirs();
    }

    @After
    public void tearDown() {
        FileUtils.deleteDir(testPath);
    }

    @Test
    public void testBinary() throws IOException {
        assertTrue(AndroidBinXmlDecoder.isBinaryXml(ByteBuffer.wrap(getFileContentAsBinary(xmlBinary))));
        assertFalse(AndroidBinXmlDecoder.isBinaryXml(ByteBuffer.wrap(getFileContentAsBinary(xmlPlain))));
        assertFalse(AndroidBinXmlDecoder.isBinaryXml(ByteBuffer.wrap(getFileContentAsBinary(xmlPlainManifest))));
    }

    @Test
    public void testDecode() throws IOException, AndroidBinXmlParser.XmlParserException {
        String xml = AndroidBinXmlDecoder.decode(getFileContentAsBinary(xmlBinary));
        assertEquals(FileUtils.getFileContent(xmlPlain), xml);
    }

    @Test
    public void testDecodeManifest() throws IOException, AndroidBinXmlParser.XmlParserException {
        String xml = AndroidBinXmlDecoder.decode(ByteBuffer.wrap(getFileContentAsBinary(xmlBinary)), true);
        assertEquals(FileUtils.getFileContent(xmlPlainManifest), xml);
    }

    private byte[] getFileContentAsBinary(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return IoUtils.readFully(fis, -1, true);
        }
    }
}