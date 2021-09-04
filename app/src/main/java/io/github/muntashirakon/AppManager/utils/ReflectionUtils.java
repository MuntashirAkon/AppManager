// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

// Copyright 2017 Zheng Li
@SuppressWarnings("rawtypes")
public class ReflectionUtils {
    private static final Map<String, Field> sFieldCache = new HashMap<>();
    private static final Map<String, Method> sMethodCache = new HashMap<>();

    @Nullable
    public static Object getFieldValue(Object obj, String fieldName) {
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
    public static Object invokeObjectMethod(@NonNull Object object, @NonNull String methodName,
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
