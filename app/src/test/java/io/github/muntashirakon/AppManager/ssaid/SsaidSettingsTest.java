// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ssaid;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;

import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class SsaidSettingsTest {
    private final ClassLoader classLoader = getClass().getClassLoader();
    private Path ssaidLocation;

    @Before
    public void setUp() {
        ssaidLocation = Paths.get(classLoader.getResource("xml/settings_ssaid.xml").getFile());
    }

    @Test
    public void testGetSsaid() throws IOException {
        SsaidSettings settings = new SsaidSettings(ssaidLocation, 0);
        assertEquals("A1F3C47E89D45B60C29E70A3F58D92E11B6AD02C78FE4D50937CA1B446E98F12", settings.getSsaid("android", 1000));
        assertEquals("3E9B72CDA48150F4", settings.getSsaid("com.google.android.gms", 10123));
        assertEquals("3E9B72CDA48150F4", settings.getSsaid("com.android.vending", 10124));
        assertEquals("3E9B72CDA48150F4", settings.getSsaid("com.android.chrome", 10125));
        assertEquals("9F4C3A7E21D86B52", settings.getSsaid("com.whatsapp", 10126));
    }

    @Test
    public void testSetSsaid() throws IOException {
        Path tmpSsaidLocation = Paths.get("/tmp/settings_ssaid.xml");
        try {
            IoUtils.copy(ssaidLocation, tmpSsaidLocation);
            SsaidSettings settings = new SsaidSettings(tmpSsaidLocation, 0);
            assertEquals("9F4C3A7E21D86B52", settings.getSsaid("com.whatsapp", 10126));
            settings.setSsaid("com.whatsapp", 10126, "4CAF91D267B8E3A5");
            assertEquals("4CAF91D267B8E3A5", settings.getSsaid("com.whatsapp", 10126));
            // Check if the changes have persisted
            SsaidSettings settings2 = new SsaidSettings(tmpSsaidLocation, 0);
            assertEquals("4CAF91D267B8E3A5", settings2.getSsaid("com.whatsapp", 10126));
        } finally {
            tmpSsaidLocation.delete();
        }
    }
}
