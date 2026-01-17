// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.InputStream;

import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class BitmapRandomizerTest {
    private final ClassLoader classLoader = getClass().getClassLoader();
    private Bitmap bitmap;

    @Before
    public void setUp() throws Exception {
        assert classLoader != null;
        // Load test_icon.png as mutable Bitmap
        try (InputStream is = Paths.get(classLoader.getResource("images/test_icon.png").getFile()).openInputStream()) {
            Bitmap original = BitmapFactory.decodeStream(is);
            bitmap = original.copy(Bitmap.Config.ARGB_8888, true);
        }

        assertNotNull(bitmap);
        assertTrue(bitmap.isMutable());
    }

    @Test
    public void testRandomizePixelChangesPixelColor() {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Store original bitmap copy for pixel color comparison
        Bitmap originalCopy = bitmap.copy(Bitmap.Config.ARGB_8888, false);

        boolean pixelChanged = false;
        boolean msbFlipped = false;

        // Run multiple times to test different random pixels
        for (int i = 0; i < 10; i++) {
            BitmapRandomizer.randomizePixel(bitmap);

            // Find pixel locations changed by comparing with originalCopy
            for (int x = 0; x < width && !pixelChanged; x++) {
                for (int y = 0; y < height && !pixelChanged; y++) {
                    int origColor = originalCopy.getPixel(x, y);
                    int modColor = bitmap.getPixel(x, y);
                    if (origColor != modColor) {
                        pixelChanged = true;

                        // Extract RGB channels
                        int origRed = Color.red(origColor);
                        int origGreen = Color.green(origColor);
                        int origBlue = Color.blue(origColor);

                        int modRed = Color.red(modColor);
                        int modGreen = Color.green(modColor);
                        int modBlue = Color.blue(modColor);

                        // Check for flipped bits in upper half (bits 4 to 7) for any channel
                        if (checkUpperHalfBitsFlipped(origRed, modRed) ||
                            checkUpperHalfBitsFlipped(origGreen, modGreen) ||
                            checkUpperHalfBitsFlipped(origBlue, modBlue)) {
                            msbFlipped = true;
                        }
                    }
                }
            }
        }

        assertTrue("At least one pixel color should be changed", pixelChanged);
        assertTrue("At least one upper half bit (bits 4-7) should be flipped", msbFlipped);
    }

    private boolean checkUpperHalfBitsFlipped(int original, int modified) {
        // Focus on bits 4-7 mask: 0b11110000 = 0xF0
        int originalMasked = original & 0xF0;
        int modifiedMasked = modified & 0xF0;
        return (originalMasked ^ modifiedMasked) != 0;
    }
}
