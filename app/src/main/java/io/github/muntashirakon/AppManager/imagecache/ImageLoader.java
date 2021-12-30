// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.imagecache;

import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.AnyThread;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.utils.UiThreadHandler;

public class ImageLoader implements AutoCloseable {
    private final MemoryCache mMemoryCache = new MemoryCache();
    private final FileCache mFileCache = new FileCache();
    private final Map<ImageView, String> mImageViews = Collections.synchronizedMap(new WeakHashMap<>());
    private final ExecutorService mExecutor;
    private final boolean mShutdownExecutor;
    private boolean mIsClosed = false;

    public ImageLoader() {
        mExecutor = Executors.newFixedThreadPool(5);
        mShutdownExecutor = true;
    }

    public ImageLoader(@NonNull ExecutorService executor) {
        this.mExecutor = executor;
        mShutdownExecutor = false;
    }

    @UiThread
    public void displayImage(@NonNull String name, @Nullable PackageItemInfo info, @NonNull ImageView imageView) {
        mImageViews.put(imageView, name);
        Drawable image = mMemoryCache.get(name);
        if (image != null) {
            imageView.setImageDrawable(image);
        } else {
            queueImage(name, info, imageView);
        }
    }

    @AnyThread
    private void queueImage(@NonNull String name, @Nullable PackageItemInfo info, @NonNull ImageView imageView) {
        ImageLoaderQueueItem queueItem = new ImageLoaderQueueItem(name, info, imageView);
        mExecutor.submit(new LoadQueueItem(queueItem));
    }

    @Override
    public void close() {
        mIsClosed = true;
        if (mShutdownExecutor) {
            mExecutor.shutdownNow();
        }
        mMemoryCache.clear();
        mFileCache.clear();
    }

    @AnyThread
    private static class ImageLoaderQueueItem {
        public final String name;
        public final ImageView imageView;
        public final PackageManager pm;
        public final PackageItemInfo info;

        public ImageLoaderQueueItem(@NonNull String name, @Nullable PackageItemInfo info, @NonNull ImageView imageView) {
            this.name = name;
            this.info = info;
            this.imageView = imageView;
            this.pm = AppManager.getContext().getPackageManager();
        }
    }

    @WorkerThread
    private class LoadQueueItem implements Runnable {
        private final ImageLoaderQueueItem mQueueItem;

        LoadQueueItem(ImageLoaderQueueItem queueItem) {
            this.mQueueItem = queueItem;
        }

        public void run() {
            if (imageViewReusedOrClosed(mQueueItem)) return;
            Drawable image = null;
            try {
                image = mFileCache.getImage(mQueueItem.name);
            } catch (FileNotFoundException ignore) {
            }
            if (image == null) { // Cache miss
                if (mQueueItem.info != null) {
                    image = mQueueItem.info.loadIcon(mQueueItem.pm);
                    try {
                        mFileCache.putImage(mQueueItem.name, image);
                    } catch (IOException ignore) {
                    }
                } else image = mQueueItem.pm.getDefaultActivityIcon();
            }
            mMemoryCache.put(mQueueItem.name, image);
            if (imageViewReusedOrClosed(mQueueItem)) return;
            UiThreadHandler.run(new LoadImageInImageView(getScaledBitmap(mQueueItem.imageView, image, 1.0f), mQueueItem));
        }
    }

    // Used to display bitmap in the UI thread
    @UiThread
    private class LoadImageInImageView implements Runnable {
        private final Bitmap mImage;
        private final ImageLoaderQueueItem mQueueItem;

        public LoadImageInImageView(@NonNull Bitmap image, ImageLoaderQueueItem queueItem) {
            this.mImage = image;
            this.mQueueItem = queueItem;
        }

        public void run() {
            if (imageViewReusedOrClosed(mQueueItem)) return;
            mQueueItem.imageView.setImageBitmap(mImage);
        }
    }

    @AnyThread
    private boolean imageViewReusedOrClosed(@NonNull ImageLoaderQueueItem imageLoaderQueueItem) {
        String tag = mImageViews.get(imageLoaderQueueItem.imageView);
        return mIsClosed || tag == null || !tag.equals(imageLoaderQueueItem.name);
    }

    /**
     * Get a scaled {@link Bitmap} from the given {@link Drawable} that fits the frame.
     *
     * @param frame         The frame to scale. The frame must be initialised beforehand.
     * @param drawable      The drawable to resize
     * @param scalingFactor A number between 0 and 1. E.g. 1.0 fits the frame and 0.1 only fits 10% of the frame.
     */
    @WorkerThread
    public static Bitmap getScaledBitmap(@NonNull View frame, @NonNull Drawable drawable,
                                         @FloatRange(from = 0.0, to = 1.0) float scalingFactor) {
        int imgWidth = drawable.getIntrinsicWidth();
        int imgHeight = drawable.getIntrinsicHeight();
        int frameHeight = frame.getHeight();
        int frameWidth = frame.getWidth();
        double scale;
        if (imgHeight <= 0 || imgWidth <= 0) {
            scale = 1;
        } else if (frameHeight == 0 && frameWidth == 0) {
            // The view isn't initialised
            scale = 1;
        } else {
            scale = Math.min(Math.min(frameHeight, frameWidth) * scalingFactor / (float) Math.max(imgHeight, imgWidth), 1);
        }
        int newWidth = (int) (imgWidth * scale);
        int newHeight = (int) (imgHeight * scale);
        drawable.setBounds(0, 0, newWidth, newHeight);
        Bitmap bitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas();
        c.setBitmap(bitmap);
        drawable.draw(c);
        return bitmap;
    }
}
