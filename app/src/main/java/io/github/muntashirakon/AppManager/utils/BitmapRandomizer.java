// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.crypto.ks.CompatUtil;

public class BitmapRandomizer {
    public static void randomizePixel(@NonNull Bitmap bitmap) {
        if (!bitmap.isMutable()) {
            throw new IllegalArgumentException("Bitmap must be mutable");
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Randomly select a pixel location
        int x = CompatUtil.getPrng().nextInt(width);
        int y = CompatUtil.getPrng().nextInt(height);

        // Get the original pixel color
        int originalColor = bitmap.getPixel(x, y);

        // Extract ARGB components
        int alpha = Color.alpha(originalColor);
        int red = Color.red(originalColor);
        int green = Color.green(originalColor);
        int blue = Color.blue(originalColor);

        // Get neighboring pixels for blending
        int[] neighborColors = getNeighborColors(bitmap, x, y);

        // Calculate average of neighbors for blending
        int avgRed = 0, avgGreen = 0, avgBlue = 0;
        for (int neighborColor : neighborColors) {
            avgRed += Color.red(neighborColor);
            avgGreen += Color.green(neighborColor);
            avgBlue += Color.blue(neighborColor);
        }
        avgRed /= neighborColors.length;
        avgGreen /= neighborColors.length;
        avgBlue /= neighborColors.length;

        // Modify at least one MSB (Most Significant Bit) while blending
        int newRed = modifyMsbWithBlending(red, avgRed);
        int newGreen = modifyMsbWithBlending(green, avgGreen);
        int newBlue = modifyMsbWithBlending(blue, avgBlue);

        // Ensure the new color is different from original
        int newColor = Color.argb(alpha, newRed, newGreen, newBlue);
        if (newColor == originalColor) {
            // Force a change by flipping the MSB of red channel
            newRed = red ^ 0x80; // Flip bit 7 (MSB)
            newColor = Color.argb(alpha, newRed, newGreen, newBlue);
        }

        // Set the modified pixel
        bitmap.setPixel(x, y, newColor);
    }

    private static int[] getNeighborColors(Bitmap bitmap, int x, int y) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Get up to 8 neighboring pixels
        int[] neighbors = new int[8];
        int count = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue; // Skip center pixel

                int nx = x + dx;
                int ny = y + dy;

                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    neighbors[count++] = bitmap.getPixel(nx, ny);
                }
            }
        }

        // Return only valid neighbors
        int[] result = new int[count];
        System.arraycopy(neighbors, 0, result, 0, count);
        return result;
    }

    private static int modifyMsbWithBlending(int originalValue, int avgNeighborValue) {
        // Blend original with neighbor average (50% blend)
        int blended = (originalValue + avgNeighborValue) / 2;

        // Ensure MSB modification by flipping a random bit in upper half (bits 4-7)
        int bitToFlip = 4 + CompatUtil.getPrng().nextInt(4); // Random bit from 4 to 7
        int modified = blended ^ (1 << bitToFlip);

        // Clamp to valid range [0, 255]
        return Math.max(0, Math.min(255, modified));
    }
}
