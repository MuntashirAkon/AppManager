/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.server.common;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.ResultReceiver;
import android.os.UserHandle;
import android.os.WorkSource;
import android.util.LruCache;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.Nullable;

@SuppressWarnings("rawtypes")
public class ClassUtils {
    private static final Map<String, Class> sDefaultClassMap = new HashMap<>();
    private static final LruCache<String, Class> sClassCache = new LruCache<>(128);

    static {
        // Primitive types
        defCacheClass(byte.class);
        defCacheClass(boolean.class);
        defCacheClass(short.class);
        defCacheClass(char.class);
        defCacheClass(int.class);
        defCacheClass(float.class);
        defCacheClass(long.class);
        defCacheClass(double.class);
        // Non-primitive types
        defCacheClass(String.class);
        defCacheClass(Bundle.class);
        defCacheClass(ComponentName.class);
        defCacheClass(Message.class);
        defCacheClass(ParcelFileDescriptor.class);
        defCacheClass(ResultReceiver.class);
        defCacheClass(WorkSource.class);
        defCacheClass(Intent.class);
        defCacheClass(IntentFilter.class);
        defCacheClass(UserHandle.class);
        // Arrays
        defCacheClass(byte[].class);
        defCacheClass(int[].class);
        defCacheClass(String[].class);
        defCacheClass(Intent[].class);
    }

    private static void defCacheClass(Class clazz) {
        sDefaultClassMap.put(clazz.getName(), clazz);
    }

    @Nullable
    public static Class[] string2Class(String... names) {
        if (names != null) {
            Class[] ret = new Class[names.length];
            for (int i = 0; i < names.length; i++) {
                ret[i] = string2Class(names[i]);
            }
            return ret;
        }
        return null;
    }

    @Nullable
    public static Class string2Class(String name) {
        try {
            Class clazz = sDefaultClassMap.get(name);
            if (clazz == null) {
                clazz = sClassCache.get(name);
            }
            if (clazz == null) {
                clazz = Class.forName(name, false, null);
                sClassCache.put(name, clazz);
            }
            return clazz;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
