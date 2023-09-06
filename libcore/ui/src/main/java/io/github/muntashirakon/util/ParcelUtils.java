// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.util;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;

import java.util.HashMap;
import java.util.Map;

public class ParcelUtils {
    /**
     * Write an array set to the parcel.
     *
     * @param val The array set to write.
     */
    public static void writeArraySet(@Nullable ArraySet<?> val, @NonNull Parcel dest) {
        final int size = (val != null) ? val.size() : -1;
        dest.writeInt(size);
        for (int i = 0; i < size; i++) {
            dest.writeValue(val.valueAt(i));
        }
    }

    /**
     * Reads an array set.
     *
     * @param loader The class loader to use.
     */
    @Nullable
    public static ArraySet<?> readArraySet(@NonNull Parcel in, @Nullable ClassLoader loader) {
        final int size = in.readInt();
        if (size < 0) {
            return null;
        }
        ArraySet<Object> result = new ArraySet<>(size);
        for (int i = 0; i < size; i++) {
            Object value = in.readValue(loader);
            result.add(value);
        }
        return result;
    }

    public static void writeMap(@NonNull Map<?, ?> map, @NonNull Parcel parcel) {
        parcel.writeInt(map.size());
        for (Map.Entry<?, ?> e : map.entrySet()) {
            parcel.writeValue(e.getKey());
            parcel.writeValue(e.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    @NonNull
    public static <K, V> Map<K, V> readMap(@NonNull Parcel parcel, @Nullable ClassLoader keyCl, @Nullable ClassLoader valCl) {
        int size = parcel.readInt();
        Map<K, V> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            map.put((K) parcel.readValue(keyCl), (V) parcel.readValue(valCl));
        }
        return map;
    }
}
