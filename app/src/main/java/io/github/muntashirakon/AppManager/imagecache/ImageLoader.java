// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.imagecache;

import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    private final MemoryCache memoryCache = new MemoryCache();
    private final FileCache fileCache = new FileCache();
    private final Map<ImageView, String> imageViews = Collections.synchronizedMap(new WeakHashMap<>());
    private final ExecutorService executor;
    private final boolean shutdownExecutor;
    private boolean isClosed = false;

    public ImageLoader() {
        executor = Executors.newFixedThreadPool(5);
        shutdownExecutor = true;
    }

    public ImageLoader(@NonNull ExecutorService executor) {
        this.executor = executor;
        shutdownExecutor = false;
    }

    public void displayImage(@NonNull String name, @Nullable PackageItemInfo info, @NonNull ImageView imageView) {
        imageViews.put(imageView, name);
        Drawable image = memoryCache.get(name);
        if (image != null) imageView.setImageDrawable(image);
        else {
            queueImage(name, info, imageView);
        }
    }

    private void queueImage(@NonNull String name, @Nullable PackageItemInfo info, @NonNull ImageView imageView) {
        ImageLoaderQueueItem queueItem = new ImageLoaderQueueItem(name, info, imageView);
        executor.submit(new LoadQueueItem(queueItem));
    }

    @Override
    public void close() {
        isClosed = true;
        if (shutdownExecutor) {
            executor.shutdownNow();
        }
        memoryCache.clear();
        fileCache.clear();
    }

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

    private class LoadQueueItem implements Runnable {
        ImageLoaderQueueItem queueItem;

        LoadQueueItem(ImageLoaderQueueItem queueItem) {
            this.queueItem = queueItem;
        }

        public void run() {
            if (imageViewReusedOrClosed(queueItem)) return;
            Drawable image = null;
            try {
                image = fileCache.getImage(queueItem.name);
            } catch (FileNotFoundException ignore) {
            }
            if (image == null) { // Cache miss
                if (queueItem.info != null) {
                    image = queueItem.info.loadIcon(queueItem.pm);
                    try {
                        fileCache.putImage(queueItem.name, image);
                    } catch (IOException ignore) {
                    }
                } else image = queueItem.pm.getDefaultActivityIcon();
            }
            memoryCache.put(queueItem.name, image);
            if (imageViewReusedOrClosed(queueItem)) return;
            UiThreadHandler.run(new LoadImageInImageView(image, queueItem));
        }
    }

    //Used to display bitmap in the UI thread
    private class LoadImageInImageView implements Runnable {
        private final Drawable image;
        private final ImageLoaderQueueItem queueItem;

        public LoadImageInImageView(@NonNull Drawable image, ImageLoaderQueueItem queueItem) {
            this.image = image;
            this.queueItem = queueItem;
        }

        public void run() {
            if (imageViewReusedOrClosed(queueItem)) return;
            queueItem.imageView.setImageDrawable(image);
        }
    }

    private boolean imageViewReusedOrClosed(@NonNull ImageLoaderQueueItem imageLoaderQueueItem) {
        String tag = imageViews.get(imageLoaderQueueItem.imageView);
        return isClosed || tag == null || !tag.equals(imageLoaderQueueItem.name);
    }

}
