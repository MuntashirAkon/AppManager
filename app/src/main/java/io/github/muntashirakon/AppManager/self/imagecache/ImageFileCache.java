// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self.imagecache;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.IoUtils;

class ImageFileCache {
    private static final long sLastModifiedDate = System.currentTimeMillis() - 604_800_000;

    private final File mCacheDir;

    public ImageFileCache() {
        mCacheDir = new File(FileUtils.getCachePath(), "images");
        if (!mCacheDir.exists()) {
            if (!mCacheDir.mkdirs()) {
                throw new IllegalStateException("Could not create cache. Is this OS broken?");
            }
        }
    }

    public void putImage(@NonNull String name, @NonNull InputStream inputStream) throws IOException {
        File iconFile = getImageFile(name);
        try (OutputStream os = new FileOutputStream(iconFile)) {
            IoUtils.copy(inputStream, os);
        }
    }

    public void putImage(@NonNull String name, @NonNull Drawable drawable) throws IOException {
        putImage(name, UIUtils.getBitmapFromDrawable(drawable));
    }

    public void putImage(@NonNull String name, @NonNull Bitmap bitmap) throws IOException {
        File iconFile = getImageFile(name);
        try (OutputStream os = new FileOutputStream(iconFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.flush();
        }
    }

    @Nullable
    public Bitmap getImage(@NonNull String name) {
        File iconFile = getImageFile(name);
        if (iconFile.lastModified() >= sLastModifiedDate) {
            try (FileInputStream fis = new FileInputStream(iconFile)) {
                return BitmapFactory.decodeStream(fis);
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    @Nullable
    public Drawable getImageDrawable(@NonNull String name) {
        File iconFile = getImageFile(name);
        if (iconFile.lastModified() >= sLastModifiedDate) {
            try (FileInputStream fis = new FileInputStream(iconFile)) {
                return Drawable.createFromStream(fis, name);
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    @NonNull
    private File getImageFile(@NonNull String name) {
        return new File(mCacheDir, name + ".png");
    }

    public void clear() {
        File[] files = mCacheDir.listFiles();
        if (files == null) return;
        int count = 0;
        for (File f : files) {
            if (f.lastModified() < sLastModifiedDate) {
                if (f.delete()) ++count;
            }
        }
        Log.d("Cache", "Deleted %d images.", count);
    }
}
