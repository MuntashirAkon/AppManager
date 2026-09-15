// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.pdf;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import android.os.ParcelFileDescriptor;

import java.io.IOException;

abstract class AbstractPdfRendererBackend implements PdfRenderBackend {
    static final int MAX_PAGE_COUNT = 10_000;
    static final int MAX_BITMAP_DIMENSION = 4096;
    static final long MAX_BITMAP_PIXELS = 16L * 1024L * 1024L;

    @NonNull
    @Override
    public String getBackendName() {
        return getClass().getSimpleName();
    }

    @Override
    public int getCapabilities() {
        return 0;
    }

    @Override
    public int getDocumentLinearizationType() {
        return -1;
    }

    @Override
    public int getPdfFormType() {
        return -1;
    }

    @Override
    public boolean shouldScaleForPrinting() {
        return false;
    }

    @Override
    public void writeDocument(@NonNull ParcelFileDescriptor destination, boolean removePasswordProtection)
            throws IOException {
        throw new UnsupportedOperationException("PDF writing is unavailable on this API");
    }

    @Override
    public int getMaxBitmapDimension() {
        return MAX_BITMAP_DIMENSION;
    }

    @Override
    public long getMaxBitmapPixels() {
        return MAX_BITMAP_PIXELS;
    }

    Bitmap createBitmap(int pageWidth, int pageHeight, int targetWidth) {
        int width = Math.max(1, Math.min(targetWidth, getMaxBitmapDimension()));
        int height = Math.max(1, Math.round(width * pageHeight / (float) pageWidth));
        if (height > getMaxBitmapDimension() || (long) width * height > getMaxBitmapPixels()) {
            float scale = Math.min(getMaxBitmapDimension() / (float) width,
                    (float) Math.sqrt(getMaxBitmapPixels() / (double) width / height));
            width = Math.max(1, Math.round(width * scale));
            height = Math.max(1, Math.round(height * scale));
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xffffffff);
        return bitmap;
    }
}
