// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.pdf;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

final class PdfRendererBackend implements PdfRenderBackend {
    private static final int MAX_PAGE_COUNT = 10_000;
    private static final int MAX_BITMAP_DIMENSION = 4096;
    private static final long MAX_BITMAP_PIXELS = 16L * 1024L * 1024L;

    @Nullable
    private PdfRenderer mRenderer;

    @Override
    public void open(@NonNull ParcelFileDescriptor fileDescriptor) throws IOException {
        try {
            mRenderer = new PdfRenderer(fileDescriptor);
            if (getPageCount() > MAX_PAGE_COUNT) {
                close();
                throw new IOException("PDF contains too many pages");
            }
        } catch (RuntimeException e) {
            close();
            try {
                fileDescriptor.close();
            } catch (IOException ignored) {
            }
            throw e;
        }
    }

    @Override
    public int getPageCount() {
        if (mRenderer == null) {
            throw new IllegalStateException("PDF renderer is closed");
        }
        return mRenderer.getPageCount();
    }

    @NonNull
    @Override
    public int[] getPageDimensions(int pageIndex) {
        if (mRenderer == null) {
            throw new IllegalStateException("PDF renderer is closed");
        }
        try (PdfRenderer.Page page = mRenderer.openPage(pageIndex)) {
            return new int[]{Math.round(page.getWidth()), Math.round(page.getHeight())};
        }
    }

    @Override
    public int getMaxBitmapDimension() {
        return MAX_BITMAP_DIMENSION;
    }

    @Override
    public long getMaxBitmapPixels() {
        return MAX_BITMAP_PIXELS;
    }

    @NonNull
    @Override
    public Bitmap renderPage(int pageIndex, int targetWidth) {
        if (mRenderer == null) {
            throw new IllegalStateException("PDF renderer is closed");
        }
        try (PdfRenderer.Page page = mRenderer.openPage(pageIndex)) {
            int width = Math.max(1, Math.min(targetWidth, getMaxBitmapDimension()));
            int height = Math.max(1, Math.round(width * page.getHeight() / (float) page.getWidth()));
            if (height > getMaxBitmapDimension() || (long) width * height > getMaxBitmapPixels()) {
                float scale = Math.min(getMaxBitmapDimension() / (float) width,
                        (float) Math.sqrt(getMaxBitmapPixels() / (double) width / height));
                width = Math.max(1, Math.round(width * scale));
                height = Math.max(1, Math.round(height * scale));
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(0xffffffff);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bitmap;
        }
    }

    @Override
    public void close() {
        if (mRenderer != null) {
            mRenderer.close();
            mRenderer = null;
        }
    }
}
