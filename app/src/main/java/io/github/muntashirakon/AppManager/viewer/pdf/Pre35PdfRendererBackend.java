// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.pdf;

import android.graphics.Bitmap;
import android.graphics.pdf.LoadParams.Builder;
import android.graphics.pdf.PdfRendererPreV;
import android.graphics.pdf.RenderParams;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresExtension;

import java.io.IOException;

@RequiresApi(Build.VERSION_CODES.R)
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
final class Pre35PdfRendererBackend extends AbstractPdfRendererBackend {
    @Nullable
    private PdfRendererPreV mRenderer;

    @Override
    public void open(@NonNull ParcelFileDescriptor fileDescriptor, String password) throws IOException {
        try {
            mRenderer = new PdfRendererPreV(fileDescriptor, new Builder().setPassword(password).build());
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
        if (mRenderer == null) {
            throw new IllegalStateException("PDF renderer is closed");
        }
        return mRenderer.getPageCount();
    }

    @Override
    public int getDocumentLinearizationType() {
        if (mRenderer == null) {
            throw new IllegalStateException("PDF renderer is closed");
        }
        return mRenderer.getDocumentLinearizationType();
    }

    @Override
    public int getPdfFormType() {
        if (mRenderer == null) {
            throw new IllegalStateException("PDF renderer is closed");
        }
        return mRenderer.getPdfFormType();
    }

    @Override
    public boolean shouldScaleForPrinting() {
        // PdfRendererPreV does not expose PdfRenderer.shouldScaleForPrinting().
        return false;
    }

    @Override
    public void writeDocument(@NonNull ParcelFileDescriptor destination, boolean removePasswordProtection)
            throws IOException {
        if (mRenderer == null) {
            throw new IllegalStateException("PDF renderer is closed");
        }
        mRenderer.write(destination, removePasswordProtection);
    }

    @Override
    public int getCapabilities() {
        return IPdfRenderService.CAPABILITY_PASSWORD
                | IPdfRenderService.CAPABILITY_ANNOTATIONS
                | IPdfRenderService.CAPABILITY_FORMS
                | IPdfRenderService.CAPABILITY_METADATA
                | IPdfRenderService.CAPABILITY_PDF_WRITE;
    }

    @NonNull
    @Override
    public int[] getPageDimensions(int pageIndex) {
        if (mRenderer == null) {
            throw new IllegalStateException("PDF renderer is closed");
        }
        try (PdfRendererPreV.Page page = mRenderer.openPage(pageIndex)) {
            return new int[]{page.getWidth(), page.getHeight()};
        }
    }

    @NonNull
    @Override
    public Bitmap renderPage(int pageIndex, int targetWidth, int renderMode, int renderFlags) {
        if (mRenderer == null) {
            throw new IllegalStateException("PDF renderer is closed");
        }
        try (PdfRendererPreV.Page page = mRenderer.openPage(pageIndex)) {
            Bitmap bitmap = createBitmap(page.getWidth(), page.getHeight(), targetWidth);
            int mode = renderMode == IPdfRenderService.RENDER_MODE_PRINT
                    ? RenderParams.RENDER_MODE_FOR_PRINT : RenderParams.RENDER_MODE_FOR_DISPLAY;
            RenderParams params = new RenderParams.Builder(mode).setRenderFlags(renderFlags).build();
            page.render(bitmap, null, null, params);
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
