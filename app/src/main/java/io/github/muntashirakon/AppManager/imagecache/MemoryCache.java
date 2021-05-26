// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.imagecache;

import android.graphics.drawable.Drawable;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MemoryCache {
    private final Map<String, SoftReference<Drawable>> cache = Collections.synchronizedMap(new HashMap<>());

    public Drawable get(String id) {
        SoftReference<Drawable> ref = cache.get(id);
        if (ref == null) return null;
        return ref.get();
    }

    public void put(String id, Drawable image) {
        cache.put(id, new SoftReference<>(image));
    }

    public void clear() {
        cache.clear();
    }
}