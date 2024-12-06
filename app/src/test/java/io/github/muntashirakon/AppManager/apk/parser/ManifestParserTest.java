// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class ManifestParserTest {
    private final ClassLoader classLoader = Objects.requireNonNull(getClass().getClassLoader());

    @Test
    public void testManifestIntentFilterParsing() throws IOException {
        Path xmlBinary = Paths.get(classLoader.getResource("xml/HMS_Core_Android_Manifest.bin.xml").getFile());
        ManifestParser parser = new ManifestParser(xmlBinary.getContentAsBinary());
        List<ManifestComponent> manifestComponents = parser.parseComponents();
        assertEquals(1598, manifestComponents.size());
        int intentFilterCount = 0;
        for (ManifestComponent component : manifestComponents) {
            intentFilterCount += component.intentFilters.size();
        }
        assertEquals(156, intentFilterCount);
    }
}
