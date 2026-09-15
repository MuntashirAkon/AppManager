// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.pdf;

import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

import java.io.IOException;

interface PdfRenderBackend extends AutoCloseable {
    void open(@NonNull ParcelFileDescriptor fileDescriptor) throws IOException;

    int getPageCount();

    /**
     * Returns the page size in points as {width, height}.
     */
    @NonNull
    int[] getPageDimensions(int pageIndex);

    int getMaxBitmapDimension();

    long getMaxBitmapPixels();

    @NonNull
    Bitmap renderPage(int pageIndex, int targetWidth);

    @Override
    void close();
}
