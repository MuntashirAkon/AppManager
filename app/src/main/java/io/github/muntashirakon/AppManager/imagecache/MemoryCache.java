// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.imagecache;

import android.graphics.Bitmap;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class MemoryCache {
    private final Map<String, SoftReference<Bitmap>> mCache = Collections.synchronizedMap(new HashMap<>());

    public Bitmap get(String id) {
        SoftReference<Bitmap> ref = mCache.get(id);
        if (ref == null) return null;
        return ref.get();
    }

    public void put(String id, Bitmap image) {
        mCache.put(id, new SoftReference<>(image));
    }

    public void clear() {
        mCache.clear();
    }
}