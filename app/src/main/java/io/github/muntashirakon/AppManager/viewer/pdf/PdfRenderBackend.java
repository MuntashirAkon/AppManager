// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.pdf;

import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

import java.io.IOException;

interface PdfRenderBackend extends AutoCloseable {
    void open(@NonNull ParcelFileDescriptor fileDescriptor, String password) throws IOException;

    @NonNull
    String getBackendName();

    int getCapabilities();

    int getPageCount();

    /**
     * Returns the page size in points as {width, height}.
     */
    @NonNull
    int[] getPageDimensions(int pageIndex);

    int getMaxBitmapDimension();

    long getMaxBitmapPixels();

    int getDocumentLinearizationType();

    int getPdfFormType();

    boolean shouldScaleForPrinting();

    @NonNull
    Bitmap renderPage(int pageIndex, int targetWidth, int renderMode, int renderFlags);

    void writeDocument(@NonNull ParcelFileDescriptor destination, boolean removePasswordProtection)
            throws IOException;

    @Override
    void close();
}
