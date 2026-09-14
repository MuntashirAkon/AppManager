// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.pdf;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfRenderService extends Service {
    private static final int MAX_PAGE_COUNT = 10_000;
    private static final int MAX_BITMAP_DIMENSION = 4096;
    private static final long MAX_BITMAP_PIXELS = 16L * 1024L * 1024L;

    private final Object mRendererLock = new Object();
    private final ExecutorService mRenderExecutor = Executors.newSingleThreadExecutor();
    @Nullable
    private PdfRenderer mRenderer;

    private final IPdfRenderService.Stub mBinder = new IPdfRenderService.Stub() {
        @Override
        public void openDocument(ParcelFileDescriptor fileDescriptor) throws RemoteException {
            if (fileDescriptor == null) throw new RemoteException("Missing PDF file descriptor");
            synchronized (mRendererLock) {
                closeRendererLocked();
                try {
                    PdfRenderer renderer = new PdfRenderer(fileDescriptor);
                    if (renderer.getPageCount() > MAX_PAGE_COUNT) {
                        renderer.close();
                        throw new IOException("PDF contains too many pages");
                    }
                    mRenderer = renderer;
                } catch (IOException | RuntimeException e) {
                    try {
                        fileDescriptor.close();
                    } catch (IOException ignored) {
                    }
                    throw remoteException(e);
                }
            }
        }

        @Override
        public int getPageCount() throws RemoteException {
            synchronized (mRendererLock) {
                if (mRenderer == null) throw new RemoteException("PDF renderer is closed");
                return mRenderer.getPageCount();
            }
        }

        @Override
        public ParcelFileDescriptor renderPage(int pageIndex, int targetWidth) throws RemoteException {
            final ParcelFileDescriptor[] pipe;
            synchronized (mRendererLock) {
                if (mRenderer == null) throw new RemoteException("PDF renderer is closed");
                if (pageIndex < 0 || pageIndex >= mRenderer.getPageCount()) {
                    throw new RemoteException("Invalid PDF page");
                }
                try {
                    pipe = ParcelFileDescriptor.createPipe();
                    mRenderExecutor.execute(() -> renderPageToPipe(pageIndex, targetWidth, pipe[1]));
                } catch (IOException | RuntimeException e) {
                    throw remoteException(e);
                }
            }
            return pipe[0];
        }

        @Override
        public void closeDocument() {
            synchronized (mRendererLock) {
                closeRendererLocked();
            }
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        synchronized (mRendererLock) {
            closeRendererLocked();
        }
        mRenderExecutor.shutdownNow();
        super.onDestroy();
    }

    private void renderPageToPipe(int pageIndex, int targetWidth, ParcelFileDescriptor writeEnd) {
        Bitmap bitmap = null;
        try (OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(writeEnd)) {
            synchronized (mRendererLock) {
                if (mRenderer == null) return;
                try (PdfRenderer.Page page = mRenderer.openPage(pageIndex)) {
                    int width = Math.max(1, Math.min(targetWidth, MAX_BITMAP_DIMENSION));
                    int height = Math.max(1, Math.round(width * page.getHeight() / (float) page.getWidth()));
                    if (height > MAX_BITMAP_DIMENSION || (long) width * height > MAX_BITMAP_PIXELS) {
                        float scale = Math.min(MAX_BITMAP_DIMENSION / (float) width,
                                (float) Math.sqrt(MAX_BITMAP_PIXELS / (double) width / height));
                        width = Math.max(1, Math.round(width * scale));
                        height = Math.max(1, Math.round(height * scale));
                    }
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(0xffffffff);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                }
            }
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (IOException | RuntimeException ignored) {
            // Closing the pipe makes the client observe a failed render.
        } finally {
            if (bitmap != null) bitmap.recycle();
        }
    }

    private void closeRendererLocked() {
        if (mRenderer != null) {
            mRenderer.close();
            mRenderer = null;
        }
    }

    private static RemoteException remoteException(Throwable throwable) {
        RemoteException exception = new RemoteException(throwable.getMessage());
        exception.initCause(throwable);
        return exception;
    }
}
