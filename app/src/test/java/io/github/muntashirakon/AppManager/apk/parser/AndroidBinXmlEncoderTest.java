// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.nio.ByteBuffer;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class AndroidBinXmlEncoderTest {
    private final ClassLoader classLoader = getClass().getClassLoader();
    private Path xmlPlain;
    private Path testPath;

    @Before
    public void setUp() throws Exception {
        assert classLoader != null;
        xmlPlain = Paths.get(classLoader.getResource("xml/HMS_Core_Android_Manifest.plain.xml").getFile());
        testPath = Paths.get("/tmp/test");
        testPath.mkdirs();
    }

    @After
    public void tearDown() {
        testPath.delete();
    }

    @Test
    public void testEncodeManifest() throws XmlPullParserException, IOException {
        // Encode
        String xml = xmlPlain.getContentAsString();
        byte[] bytes = AndroidBinXmlEncoder.encodeString(AppManager.getContext(), xml);
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        assertTrue(AndroidBinXmlDecoder.isBinaryXml(byteBuffer));
        // Decode
        String actualXml = AndroidBinXmlDecoder.decode(byteBuffer);
        assertEquals(xml, actualXml);
    }
}