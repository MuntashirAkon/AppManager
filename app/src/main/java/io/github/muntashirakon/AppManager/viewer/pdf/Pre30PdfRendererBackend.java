// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.pdf;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

final class Pre30PdfRendererBackend extends AbstractPdfRendererBackend {
    @Nullable
    private PdfRenderer mRenderer;

    @Override
    public void open(@NonNull ParcelFileDescriptor fileDescriptor, String password) throws IOException {
        if (password != null && !password.isEmpty()) {
            throw new UnsupportedOperationException("Password loading is unavailable on this API");
        }
        try {
            mRenderer = new PdfRenderer(fileDescriptor);
            if (getPageCount() > MAX_PAGE_COUNT) {
                close();
                throw new IOException("PDF contains too many pages");
            }
        } catch (RuntimeException e) {
            close();
            closeFileDescriptor(fileDescriptor);
            throw e;
        }
    }

    @Override
    public int getPageCount() {
        if (mRenderer == null) throw new IllegalStateException("PDF renderer is closed");
        return mRenderer.getPageCount();
    }

    @NonNull
    @Override
    public int[] getPageDimensions(int pageIndex) {
        if (mRenderer == null) throw new IllegalStateException("PDF renderer is closed");
        try (PdfRenderer.Page page = mRenderer.openPage(pageIndex)) {
            return new int[]{page.getWidth(), page.getHeight()};
        }
    }

    @NonNull
    @Override
    public Bitmap renderPage(int pageIndex, int targetWidth, int renderMode, int renderFlags) {
        if (mRenderer == null) throw new IllegalStateException("PDF renderer is closed");
        try (PdfRenderer.Page page = mRenderer.openPage(pageIndex)) {
            Bitmap bitmap = createBitmap(page.getWidth(), page.getHeight(), targetWidth);
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

    private static void closeFileDescriptor(ParcelFileDescriptor fileDescriptor) {
        try {
            fileDescriptor.close();
        } catch (IOException ignored) {
        }
    }
}
