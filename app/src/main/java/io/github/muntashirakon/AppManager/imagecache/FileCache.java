// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.imagecache;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.FileUtils;

public class FileCache {
    private static final long sLastModifiedDate = System.currentTimeMillis() - 604_800_000;

    private final File mCacheDir;

    public FileCache() {
        if (AppManager.getContext().getExternalCacheDir() != null) {
            mCacheDir = new File(AppManager.getContext().getExternalCacheDir(), "images");
        } else {
            mCacheDir = new File(AppManager.getContext().getCacheDir(), "images");
        }
        if (!mCacheDir.exists()) mCacheDir.mkdirs();
    }

    public void putImage(String name, InputStream inputStream) throws IOException {
        File iconFile = getImageFile(name);
        try (OutputStream os = new FileOutputStream(iconFile)) {
            FileUtils.copy(inputStream, os);
        }
    }

    public void putImage(String name, Drawable drawable) throws IOException {
        File iconFile = getImageFile(name);
        try (OutputStream os = new FileOutputStream(iconFile)) {
            Bitmap bitmap = FileUtils.getBitmapFromDrawable(drawable);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.flush();
        }
    }

    @NonNull
    public Drawable getImage(@NonNull String name) throws FileNotFoundException {
        File iconFile = getImageFile(name);
        if (iconFile.exists() && iconFile.lastModified() >= sLastModifiedDate) {
            return Drawable.createFromStream(new FileInputStream(iconFile), name);
        } else {
            throw new FileNotFoundException("Icon for " + name + " doesn't exist.");
        }
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
        Log.d("Cache", "Deleted " + count + " images.");
    }
}
