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

package io.github.muntashirakon.AppManager.appops.reflector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.appops.OpEntry;
import io.github.muntashirakon.AppManager.appops.PackageOps;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;

@SuppressWarnings("rawtypes")
public class ReflectUtils {
    private static final Map<String, Field> sFieldCache = new HashMap<>();
    private static final Map<String, Method> sMethodCache = new HashMap<>();

    @NonNull
    public static PackageOps opsConvert(Object object) {
        PackageOpsConverter converter = new PackageOpsConverter(object);
        String packageName = converter.getPackageName();
        int uid = converter.getUid();
        List<OpEntry> entries = converter.getOpEntries();
        return new PackageOps(packageName, uid, entries);
    }

    @Nullable
    static Object getFieldValue(Object obj, String fieldName) {
        Field field = sFieldCache.get(fieldName);
        if (field == null) {
            try {
                if (obj instanceof Class) {
                    field = ((Class) obj).getDeclaredField(fieldName);
                } else {
                    field = obj.getClass().getDeclaredField(fieldName);
                }
                field.setAccessible(true);
                sFieldCache.put(fieldName, field);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (field != null) {
            try {
                return field.get(obj);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    static Object invokeObjectMethod(@NonNull Object object, @NonNull String methodName,
                                            @Nullable Class[] paramsTypes,
                                            @Nullable Object[] params) {
        StringBuilder sb = new StringBuilder(methodName);
        if (!ArrayUtils.isEmpty(paramsTypes)) {
            sb.append("-");
            for (Class aClass : paramsTypes) {
                sb.append(aClass.getSimpleName()).append(",");
            }
        }

        Method method = sMethodCache.get(sb.toString());
        if (method == null) {
            try {
                Class cls;
                if (object instanceof Class) {
                    //static method
                    cls = ((Class) object);
                } else {
                    cls = object.getClass();
                }

                if (!ArrayUtils.isEmpty(paramsTypes)) {
                    method = cls.getDeclaredMethod(methodName, paramsTypes);
                } else {
                    method = cls.getDeclaredMethod(methodName);

                }
                method.setAccessible(true);


                sMethodCache.put(sb.toString(), method);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
        if (method != null) {
            try {
                if (ArrayUtils.isEmpty(params)) {
                    return method.invoke(object, params);
                } else {
                    return method.invoke(object);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
