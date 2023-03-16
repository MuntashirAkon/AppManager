// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self.imagecache;

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
import androidx.collection.LruCache;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class ImageLoader implements Closeable {
    public static void displayImage(@Nullable PackageItemInfo info, @Nullable ImageView imageView) {
        WeakReference<ImageView> ivRef = new WeakReference<>(imageView);
        ThreadUtils.postOnBackgroundThread(() -> {
            ImageView iv = ivRef.get();
            if (info == null || iv == null) {
                return;
            }
            Drawable drawable = info.loadIcon(iv.getContext().getPackageManager());
            ThreadUtils.postOnMainThread(() -> {
                ImageView iv2 = ivRef.get();
                if (iv2 != null) {
                    iv2.setImageDrawable(drawable);
                }
            });
        });
    }

    private final LruCache<String, Bitmap> mMemoryCache = new LruCache<>(300);
    private final ImageFileCache mImageFileCache = new ImageFileCache();
    private final Map<Integer, String> mImageViews = Collections.synchronizedMap(new WeakHashMap<>());
    private boolean mIsClosed = false;

    public ImageLoader() {
    }

    @UiThread
    public void displayImage(@NonNull String tag, @Nullable PackageItemInfo info, @NonNull ImageView imageView) {
        mImageViews.put(imageView.hashCode(), tag);
        Bitmap image = mMemoryCache.get(tag);
        if (image != null) {
            imageView.setImageBitmap(image);
        } else {
            queueImage(tag, info, imageView);
        }
    }

    @AnyThread
    private void queueImage(@NonNull String tag, @Nullable PackageItemInfo info, @NonNull ImageView imageView) {
        ImageLoaderQueueItem queueItem = new ImageLoaderQueueItem(tag, info, imageView);
        ThreadUtils.postOnBackgroundThread(new LoadQueueItem(queueItem));
    }

    @Override
    public void close() {
        mIsClosed = true;
        mMemoryCache.evictAll();
        mImageFileCache.clear();
    }

    @Override
    protected void finalize() {
        if (!mIsClosed) {
            close();
        }
    }

    @AnyThread
    private static class ImageLoaderQueueItem {
        public final String tag;
        public final WeakReference<ImageView> imageView;
        public final PackageManager pm;
        public final PackageItemInfo info;

        public ImageLoaderQueueItem(@NonNull String tag, @Nullable PackageItemInfo info, @NonNull ImageView imageView) {
            this.tag = tag;
            this.info = info;
            this.imageView = new WeakReference<>(imageView);
            this.pm = AppManager.getContext().getPackageManager();
        }
    }

    private class LoadQueueItem implements Runnable {
        private final ImageLoaderQueueItem mQueueItem;

        LoadQueueItem(ImageLoaderQueueItem queueItem) {
            this.mQueueItem = queueItem;
        }

        @WorkerThread
        public void run() {
            if (imageViewReusedOrClosed(mQueueItem)) return;
            Bitmap image = mImageFileCache.getImage(mQueueItem.tag);
            ImageView iv = mQueueItem.imageView.get();
            if (image == null) { // Cache miss
                Drawable drawable;
                if (mQueueItem.info != null) {
                    drawable = mQueueItem.info.loadIcon(mQueueItem.pm);
                    image = getScaledBitmap(iv, drawable, 1.0f);
                    try {
                        mImageFileCache.putImage(mQueueItem.tag, image);
                    } catch (IOException ignore) {
                    }
                } else {
                    drawable = mQueueItem.pm.getDefaultActivityIcon();
                    image = getScaledBitmap(iv, drawable, 1.0f);
                }
            }
            mMemoryCache.put(mQueueItem.tag, image);
            if (imageViewReusedOrClosed(mQueueItem)) return;
            ThreadUtils.postOnMainThread(new LoadImageInImageView(image, mQueueItem));
        }
    }

    // Used to display bitmap in the UI thread
    private class LoadImageInImageView implements Runnable {
        private final Bitmap mImage;
        private final ImageLoaderQueueItem mQueueItem;

        public LoadImageInImageView(@NonNull Bitmap image, ImageLoaderQueueItem queueItem) {
            this.mImage = image;
            this.mQueueItem = queueItem;
        }

        @UiThread
        public void run() {
            if (imageViewReusedOrClosed(mQueueItem)) return;
            ImageView iv = mQueueItem.imageView.get();
            if (iv != null) {
                iv.setImageBitmap(mImage);
            }
        }
    }

    @AnyThread
    private boolean imageViewReusedOrClosed(@NonNull ImageLoaderQueueItem imageLoaderQueueItem) {
        ImageView iv = imageLoaderQueueItem.imageView.get();
        return mIsClosed || iv == null || !Objects.equals(imageLoaderQueueItem.tag, mImageViews.get(iv.hashCode()));
    }

    /**
     * Get a scaled {@link Bitmap} from the given {@link Drawable} that fits the frame.
     *
     * @param frame         The frame to scale. The frame must be initialised beforehand.
     * @param drawable      The drawable to resize
     * @param scalingFactor A number between 0 and 1. E.g. 1.0 fits the frame and 0.1 only fits 10% of the frame.
     */
    @WorkerThread
    public static Bitmap getScaledBitmap(@Nullable View frame, @NonNull Drawable drawable,
                                         @FloatRange(from = 0.0, to = 1.0) float scalingFactor) {
        if (frame == null) {
            return UIUtils.getBitmapFromDrawable(drawable);
        }
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
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }
}
