// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self.imagecache;

import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.AnyThread;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.collection.LruCache;
import androidx.core.content.ContextCompat;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Objects;

import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class ImageLoader implements Closeable {
    @AnyThread
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

    public interface ImageFetcherInterface {
        @WorkerThread
        @NonNull
        ImageFetcherResult fetchImage(@NonNull String tag);
    }

    private static final ImageLoader sInstance = new ImageLoader();

    @NonNull
    public static ImageLoader getInstance() {
        return sInstance;
    }

    private final LruCache<String, Bitmap> mMemoryCache = new LruCache<>(300);
    private final ImageFileCache mImageFileCache = new ImageFileCache();
    private boolean mIsClosed = false;

    private ImageLoader() {
    }

    @WorkerThread
    @Nullable
    public Bitmap getCachedImage(@NonNull String tag) {
        Bitmap image = mMemoryCache.get(tag);
        if (image != null) {
            return image;
        }
        // Load from file system
        return mImageFileCache.getImage(tag);
    }

    @UiThread
    public void displayImage(@NonNull String tag, @NonNull ImageView imageView,
                             @NonNull ImageFetcherInterface imageFetcherInterface) {
        Bitmap image = mMemoryCache.get(tag);
        if (image != null) {
            imageView.setImageBitmap(image);
        } else {
            queueImage(tag, imageView, imageFetcherInterface);
        }
    }

    @UiThread
    public void displayImage(@NonNull String tag, @Nullable PackageItemInfo info, @NonNull ImageView imageView) {
        Bitmap image = mMemoryCache.get(tag);
        if (image != null) {
            imageView.setImageBitmap(image);
        } else {
            queueImage(tag, info, imageView);
        }
    }

    @AnyThread
    private void queueImage(@NonNull String tag, @Nullable PackageItemInfo info, @NonNull ImageView imageView) {
        queueImage(tag, imageView, new PackageInfoImageFetcher(info));
    }

    @AnyThread
    private void queueImage(@NonNull String tag, @NonNull ImageView imageView,
                            @NonNull ImageFetcherInterface imageFetcherInterface) {
        ImageLoaderQueueItem queueItem = new ImageLoaderQueueItem(tag, imageFetcherInterface, imageView);
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

    private static class PackageInfoImageFetcher implements ImageFetcherInterface {
        @Nullable
        private final PackageItemInfo mInfo;

        public PackageInfoImageFetcher(@Nullable PackageItemInfo info) {
            mInfo = info;
        }

        @Override
        @NonNull
        public ImageFetcherResult fetchImage(@NonNull String tag) {
            PackageManager pm = ContextUtils.getContext().getPackageManager();
            Drawable drawable = mInfo != null ? mInfo.loadIcon(pm) : null;
            return new ImageFetcherResult(tag, drawable != null ? UIUtils.getBitmapFromDrawable(drawable) : null,
                    mInfo != null && tag.equals(mInfo.packageName), true,
                    new DefaultImageDrawable("android_default_icon", pm.getDefaultActivityIcon()));
        }
    }

    public interface DefaultImage {
        @Nullable
        default String getTag() {
            return null;
        }

        @NonNull
        Bitmap getImage();
    }

    public static class DefaultImageDrawableRes implements DefaultImage {
        @Nullable
        private final String mTag;
        @DrawableRes
        private final int mDrawableRes;
        @Px
        private final int mPadding;

        public DefaultImageDrawableRes(@Nullable String tag, int drawableRes) {
            this(tag, drawableRes, 0);
        }

        public DefaultImageDrawableRes(@Nullable String tag, int drawableRes, @Px int padding) {
            mTag = tag;
            mDrawableRes = drawableRes;
            mPadding = padding;
        }

        @Override
        @Nullable
        public String getTag() {
            return mTag;
        }

        @NonNull
        @Override
        public Bitmap getImage() {
            return UIUtils.getBitmapFromDrawable(Objects.requireNonNull(ContextCompat
                    .getDrawable(ContextUtils.getContext(), mDrawableRes)), mPadding);
        }
    }

    public static class DefaultImageDrawable implements DefaultImage {
        @Nullable
        private final String mTag;
        @NonNull
        private final Drawable mDrawable;

        public DefaultImageDrawable(@Nullable String tag, @NonNull Drawable drawable) {
            mTag = tag;
            mDrawable = drawable;
        }

        @Override
        @Nullable
        public String getTag() {
            return mTag;
        }

        @NonNull
        @Override
        public Bitmap getImage() {
            return UIUtils.getBitmapFromDrawable(mDrawable);
        }
    }

    public static class DefaultImageString implements DefaultImage {
        @Nullable
        private final String mTag;
        @NonNull
        private final String mText;

        public DefaultImageString(@Nullable String tag, @NonNull String text) {
            mTag = tag;
            mText = text;
        }

        @Override
        @Nullable
        public String getTag() {
            return mTag;
        }

        @NonNull
        @Override
        public Bitmap getImage() {
            return UIUtils.generateBitmapFromText(mText, null);
        }
    }

    public static class ImageFetcherResult {
        @NonNull
        public final String tag;
        @Nullable
        public final Bitmap bitmap;
        public final boolean cacheInMemory;
        public final boolean persistCache;
        @NonNull
        public final DefaultImage defaultImage;

        public ImageFetcherResult(@NonNull String tag, @Nullable Bitmap bitmap, @NonNull DefaultImage defaultImage) {
            this(tag, bitmap, true, true, defaultImage);
        }

        public ImageFetcherResult(@NonNull String tag, @Nullable Bitmap bitmap, boolean cacheInMemory,
                                  boolean persistCache, @NonNull DefaultImage defaultImage) {
            this.tag = tag;
            this.bitmap = bitmap;
            this.cacheInMemory = cacheInMemory;
            this.persistCache = persistCache;
            this.defaultImage = defaultImage;
        }
    }

    @AnyThread
    public static class ImageLoaderQueueItem {
        public final String tag;
        public final WeakReference<ImageView> imageView;

        private final ImageFetcherInterface mImageFetcherInterface;

        public ImageLoaderQueueItem(@NonNull String tag, @NonNull ImageFetcherInterface imageFetcherInterface,
                                    @NonNull ImageView imageView) {
            this.tag = tag;
            this.imageView = new WeakReference<>(imageView);
            mImageFetcherInterface = imageFetcherInterface;
        }
    }

    private class LoadQueueItem implements Runnable {
        private final ImageLoaderQueueItem mQueueItem;

        LoadQueueItem(ImageLoaderQueueItem queueItem) {
            mQueueItem = queueItem;
        }

        @WorkerThread
        public void run() {
            if (imageViewReusedOrClosed(mQueueItem)) return;
            Bitmap image = mImageFileCache.getImage(mQueueItem.tag);
            if (image != null) {
                // Cache hit
                mMemoryCache.put(mQueueItem.tag, image);
            } else {
                // Cache miss
                ImageFetcherResult result = mQueueItem.mImageFetcherInterface.fetchImage(mQueueItem.tag);
                if (result.bitmap == null) {
                    // No image produced, try default
                    DefaultImage defaultImage = result.defaultImage;
                    String tag = defaultImage.getTag();
                    if (tag == null) {
                        // No tag listed, use the image directly
                        image = defaultImage.getImage();
                    } else {
                        // Listed a tag, try cache first
                        image = mMemoryCache.get(tag);
                        if (image == null) {
                            image = mImageFileCache.getImage(tag);
                        }
                        if (image == null) {
                            // Cache miss
                            image = defaultImage.getImage();
                            mMemoryCache.put(tag, image);
                            try {
                                mImageFileCache.putImage(tag, image);
                            } catch (IOException ignore) {
                            }
                        }
                    }
                } else {
                    image = result.bitmap;
                    if (result.cacheInMemory) {
                        mMemoryCache.put(mQueueItem.tag, image);
                    }
                    if (result.persistCache) {
                        try {
                            mImageFileCache.putImage(result.tag, image);
                        } catch (IOException ignore) {
                        }
                    }
                }
            }
            if (imageViewReusedOrClosed(mQueueItem)) return;
            ThreadUtils.postOnMainThread(new LoadImageInImageView(image, mQueueItem));
        }
    }

    // Used to display bitmap in the UI thread
    private class LoadImageInImageView implements Runnable {
        private final Bitmap mImage;
        private final ImageLoaderQueueItem mQueueItem;

        public LoadImageInImageView(@NonNull Bitmap image, ImageLoaderQueueItem queueItem) {
            mImage = image;
            mQueueItem = queueItem;
        }

        @UiThread
        public void run() {
            if (imageViewReusedOrClosed(mQueueItem)) return;
            ImageView iv = mQueueItem.imageView.get();
            if (iv != null) {
                Object tag = iv.getTag();
                if (tag == null || tag.equals(mQueueItem.tag)) {
                    iv.setImageBitmap(mImage);
                }
            }
        }
    }

    @AnyThread
    private boolean imageViewReusedOrClosed(@NonNull ImageLoaderQueueItem imageLoaderQueueItem) {
        ImageView iv = imageLoaderQueueItem.imageView.get();
        return mIsClosed || iv == null;
    }
}
