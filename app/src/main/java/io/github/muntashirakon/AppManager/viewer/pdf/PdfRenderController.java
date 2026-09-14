// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.pdf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.utils.FileUtils;

final class PdfRenderController {
    private static final int MAX_PAGE_COUNT = 10_000;
    private static final int MAX_BITMAP_DIMENSION = 4096;
    private static final long MAX_BITMAP_PIXELS = 16L * 1024L * 1024L;

    interface Listener {
        void onDocumentOpened(int pageCount);

        void onError(@NonNull Throwable throwable);
    }

    interface PageListener {
        void onPageRendered(@NonNull Bitmap bitmap);

        void onError(@NonNull Throwable throwable);
    }

    private final Context mContext;
    private final Listener mListener;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Object mRendererLock = new Object();
    private final Object mBitmapPoolLock = new Object();
    private final List<Future<?>> mPendingRenders = new ArrayList<>();
    private final ArrayDeque<Bitmap> mBitmapPool = new ArrayDeque<>();
    private volatile boolean mClosed;
    private ParcelFileDescriptor mFileDescriptor;
    private PdfRenderer mRenderer;

    PdfRenderController(@NonNull Context context, @NonNull Listener listener) {
        mContext = context.getApplicationContext();
        mListener = listener;
    }

    void open(@NonNull Uri uri) {
        cancelPendingRenders();
        mExecutor.execute(() -> {
            try {
                closeRenderer();
                ParcelFileDescriptor fd = FileUtils.getFdFromUri(mContext, uri, "r");
                PdfRenderer renderer = new PdfRenderer(fd);
                synchronized (mRendererLock) {
                    if (mClosed) {
                        renderer.close();
                        return;
                    }
                    mFileDescriptor = fd;
                    mRenderer = renderer;
                }
                int pageCount = renderer.getPageCount();
                if (pageCount > MAX_PAGE_COUNT) {
                    renderer.close();
                    throw new IOException("PDF contains too many pages");
                }
                postDocumentOpened(pageCount);
            } catch (Throwable throwable) {
                postError(throwable);
            }
        });
    }

    Future<?> renderPage(int pageIndex, int targetWidth, @NonNull PageListener listener) {
        Future<?> future = mExecutor.submit(() -> {
            Bitmap bitmap = null;
            try {
                synchronized (mRendererLock) {
                    if (mClosed || mRenderer == null) {
                        throw new IOException("PDF renderer is closed");
                    }
                    PdfRenderer.Page page = mRenderer.openPage(pageIndex);
                    try {
                        int width = Math.max(1, Math.min(targetWidth, MAX_BITMAP_DIMENSION));
                        int height = Math.max(1, Math.round(width * page.getHeight() / (float) page.getWidth()));
                        if (height > MAX_BITMAP_DIMENSION || (long) width * height > MAX_BITMAP_PIXELS) {
                            float scale = Math.min(MAX_BITMAP_DIMENSION / (float) width,
                                    (float) Math.sqrt(MAX_BITMAP_PIXELS / (double) width / height));
                            width = Math.max(1, Math.round(width * scale));
                            height = Math.max(1, Math.round(height * scale));
                        }
                        bitmap = obtainBitmap(width, height);
                        bitmap.eraseColor(0xffffffff);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    } finally {
                        page.close();
                    }
                }
                Bitmap renderedBitmap = bitmap;
                mMainHandler.post(() -> {
                    if (!mClosed) listener.onPageRendered(renderedBitmap);
                    else releaseBitmap(renderedBitmap);
                });
            } catch (Throwable throwable) {
                if (bitmap != null) releaseBitmap(bitmap);
                if (throwable instanceof InterruptedException || Thread.currentThread().isInterrupted()) return;
                mMainHandler.post(() -> {
                    if (!mClosed) listener.onError(throwable);
                });
            }
        });
        synchronized (mPendingRenders) {
            mPendingRenders.add(future);
        }
        return future;
    }

    void close() {
        mClosed = true;
        cancelPendingRenders();
        synchronized (mRendererLock) {
            closeRenderer();
        }
        synchronized (mBitmapPoolLock) {
            while (!mBitmapPool.isEmpty()) {
                mBitmapPool.removeFirst().recycle();
            }
        }
        mExecutor.shutdownNow();
    }

    void releaseBitmap(@NonNull Bitmap bitmap) {
        if (bitmap.isRecycled() || mClosed) {
            if (!bitmap.isRecycled()) bitmap.recycle();
            return;
        }
        synchronized (mBitmapPoolLock) {
            if (mBitmapPool.size() < 4) mBitmapPool.addLast(bitmap);
            else bitmap.recycle();
        }
    }

    private Bitmap obtainBitmap(int width, int height) {
        synchronized (mBitmapPoolLock) {
            for (Bitmap bitmap : mBitmapPool) {
                if (bitmap.getWidth() == width && bitmap.getHeight() == height) {
                    mBitmapPool.remove(bitmap);
                    return bitmap;
                }
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }

    private void cancelPendingRenders() {
        synchronized (mPendingRenders) {
            for (Future<?> future : mPendingRenders) future.cancel(true);
            mPendingRenders.clear();
        }
    }

    @WorkerThread
    private void closeRenderer() {
        synchronized (mRendererLock) {
            if (mRenderer != null) {
                mRenderer.close();
                mRenderer = null;
            }
            if (mFileDescriptor != null) {
                try {
                    mFileDescriptor.close();
                } catch (IOException ignored) {
                }
                mFileDescriptor = null;
            }
        }
    }

    private void postDocumentOpened(int pageCount) {
        mMainHandler.post(() -> {
            if (!mClosed) mListener.onDocumentOpened(pageCount);
        });
    }

    private void postError(@NonNull Throwable throwable) {
        mMainHandler.post(() -> {
            if (!mClosed) mListener.onError(throwable);
        });
    }
}
