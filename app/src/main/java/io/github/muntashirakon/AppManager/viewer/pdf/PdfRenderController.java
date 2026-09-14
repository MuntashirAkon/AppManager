// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.pdf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.utils.FileUtils;

final class PdfRenderController {
    private static final int MAX_BITMAP_DIMENSION = 4096;
    private static final int PAGE_CACHE_SIZE_BYTES = 32 * 1024 * 1024;

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
    private final List<Future<?>> mPendingRenders = new ArrayList<>();
    private final PdfRenderServiceConnection mServiceConnection;
    private final File mPageCacheDirectory;
    private final LruCache<Integer, Bitmap> mPageCache = new LruCache<Integer, Bitmap>(PAGE_CACHE_SIZE_BYTES) {
        @Override
        protected int sizeOf(@NonNull Integer key, @NonNull Bitmap bitmap) {
            return bitmap.getAllocationByteCount();
        }
    };
    private volatile boolean mClosed;

    PdfRenderController(@NonNull Context context, @NonNull Listener listener) {
        mContext = context.getApplicationContext();
        mListener = listener;
        mPageCacheDirectory = new File(mContext.getCacheDir(), "pdf-pages");
        mServiceConnection = new PdfRenderServiceConnection(mContext, () ->
                postError(new IOException("PDF render service disconnected")));
    }

    void open(@NonNull Uri uri) {
        cancelPendingRenders();
        mPageCache.evictAll();
        clearDiskPageCache();
        mExecutor.execute(() -> {
            try {
                if (mClosed) return;
                IPdfRenderService service = mServiceConnection.getService();
                if (mClosed) return;
                try (ParcelFileDescriptor fileDescriptor = FileUtils.getFdFromUri(mContext, uri, "r")) {
                    service.openDocument(fileDescriptor);
                }
                postDocumentOpened(service.getPageCount());
            } catch (Throwable throwable) {
                postError(throwable);
            }
        });
    }

    Future<?> renderPage(int pageIndex, int targetWidth, @NonNull PageListener listener) {
        Future<?> future = mExecutor.submit(() -> {
            ParcelFileDescriptor renderedPage = null;
            try {
                if (mClosed) return;
                Bitmap cachedBitmap = mPageCache.get(pageIndex);
                if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
                    mMainHandler.post(() -> {
                        if (!mClosed) listener.onPageRendered(cachedBitmap);
                    });
                    return;
                }
                File cachedPage = getCachedPage(pageIndex);
                if (cachedPage.isFile()) {
                    Bitmap diskBitmap = BitmapFactory.decodeFile(cachedPage.getPath());
                    if (diskBitmap != null) {
                        mPageCache.put(pageIndex, diskBitmap);
                        mMainHandler.post(() -> {
                            if (!mClosed) listener.onPageRendered(diskBitmap);
                        });
                        return;
                    }
                    // The file may have been left incomplete after a killed render.
                    //noinspection ResultOfMethodCallIgnored
                    cachedPage.delete();
                }
                IPdfRenderService service = mServiceConnection.getService();
                if (mClosed) return;
                renderedPage = service.renderPage(pageIndex, Math.min(targetWidth, MAX_BITMAP_DIMENSION));
                copyToCache(renderedPage, cachedPage);
                Bitmap bitmap = BitmapFactory.decodeFile(cachedPage.getPath());
                if (bitmap == null) throw new IOException("Unable to decode rendered PDF page");
                mPageCache.put(pageIndex, bitmap);
                mMainHandler.post(() -> {
                    if (!mClosed) listener.onPageRendered(bitmap);
                });
            } catch (Throwable throwable) {
                if (throwable instanceof InterruptedException || Thread.currentThread().isInterrupted())
                    return;
                mMainHandler.post(() -> {
                    if (!mClosed) listener.onError(throwable);
                });
            } finally {
                if (renderedPage != null) {
                    try {
                        renderedPage.close();
                    } catch (IOException ignored) {
                    }
                }
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
        mPageCache.evictAll();
        clearDiskPageCache();
        mExecutor.execute(() -> {
            try {
                IPdfRenderService service = mServiceConnection.getConnectedService();
                if (service != null) {
                    service.closeDocument();
                }
            } catch (Exception ignored) {
            } finally {
                mServiceConnection.close();
            }
        });
        mExecutor.shutdown();
    }

    void releaseBitmap(@NonNull Bitmap bitmap) {
        // The controller owns rendered pages through mPageCache. The cache is bounded and
        // discarded with the document, so a recycled view must not recycle its bitmap.
    }

    private void cancelPendingRenders() {
        synchronized (mPendingRenders) {
            for (Future<?> future : mPendingRenders) future.cancel(true);
            mPendingRenders.clear();
        }
    }

    private File getCachedPage(int pageIndex) {
        return new File(mPageCacheDirectory, "page-" + pageIndex + ".png");
    }

    private void copyToCache(@NonNull ParcelFileDescriptor source, @NonNull File destination)
            throws IOException {
        if (!mPageCacheDirectory.exists() && !mPageCacheDirectory.mkdirs()
                && !mPageCacheDirectory.isDirectory()) {
            throw new IOException("Unable to create PDF page cache");
        }
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(source);
             OutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            destination.delete();
            throw e;
        }
    }

    private void clearDiskPageCache() {
        File[] files = mPageCacheDirectory.listFiles();
        if (files == null) return;
        for (File file : files) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
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
