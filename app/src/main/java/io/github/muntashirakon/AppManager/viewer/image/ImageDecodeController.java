// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.image;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

final class ImageDecodeController implements AutoCloseable {
    private static final int MAX_BITMAP_DIMENSION = 4096;
    private static final long MAX_BITMAP_PIXELS = 16L * 1024L * 1024L;

    interface Listener {
        void onImageDecoded(@NonNull Bitmap bitmap);

        void onError(@NonNull Throwable throwable);
    }

    private final Listener mListener;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    @Nullable
    private Future<?> mDecodeTask;
    private boolean mClosed;
    private int mGeneration;

    ImageDecodeController(@NonNull Listener listener) {
        mListener = listener;
    }

    void decode(@NonNull Uri uri, int targetWidth, int targetHeight) {
        cancel();
        final int generation = ++mGeneration;
        final int width = Math.max(1, Math.min(targetWidth, MAX_BITMAP_DIMENSION));
        final int height = Math.max(1, Math.min(targetHeight, MAX_BITMAP_DIMENSION));
        mDecodeTask = mExecutor.submit(() -> decodeImage(uri, width, height, generation));
    }

    private void decodeImage(@NonNull Uri uri, int targetWidth, int targetHeight, int generation) {
        Bitmap bitmap = null;
        try {
            Path path = Paths.getStrict(uri);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = path.openInputStream()) {
                BitmapFactory.decodeStream(input, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outMimeType == null) {
                throw new IOException("Unsupported or invalid image");
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight,
                    targetWidth, targetHeight);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream input = path.openInputStream()) {
                bitmap = BitmapFactory.decodeStream(input, null, options);
            }
            if (bitmap == null) {
                throw new IOException("Unable to decode image");
            }
            if (isCancelled(generation)) {
                bitmap.recycle();
                return;
            }
            Bitmap decodedBitmap = bitmap;
            mMainHandler.post(() -> {
                if (isCancelled(generation)) {
                    decodedBitmap.recycle();
                    return;
                }
                mListener.onImageDecoded(decodedBitmap);
            });
            bitmap = null;
        } catch (Throwable throwable) {
            if (isCancelled(generation) || Thread.currentThread().isInterrupted()) {
                return;
            }
            mMainHandler.post(() -> {
                if (!isCancelled(generation)) {
                    mListener.onError(throwable);
                }
            });
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private static int calculateSampleSize(int sourceWidth, int sourceHeight,
                                           int targetWidth, int targetHeight) {
        int sampleSize = 1;
        while (sourceWidth / sampleSize > targetWidth * 2
                || sourceHeight / sampleSize > targetHeight * 2
                || ((long) sourceWidth / sampleSize) * (sourceHeight / sampleSize)
                > MAX_BITMAP_PIXELS) {
            if (sampleSize > Integer.MAX_VALUE / 2) {
                break;
            }
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private boolean isCancelled(int generation) {
        return mClosed || generation != mGeneration || Thread.currentThread().isInterrupted();
    }

    private void cancel() {
        if (mDecodeTask != null) {
            mDecodeTask.cancel(true);
            mDecodeTask = null;
        }
    }

    @Override
    public void close() {
        if (mClosed) return;
        mClosed = true;
        ++mGeneration;
        cancel();
        mMainHandler.removeCallbacksAndMessages(null);
        mExecutor.shutdownNow();
    }
}
