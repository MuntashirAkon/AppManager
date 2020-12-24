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

package io.github.muntashirakon.AppManager.appops;

import android.os.Build;

import com.android.internal.util.ArrayUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

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

    private static class PackageOpsConverter {
        Object object;

        private PackageOpsConverter(Object object) {
            this.object = object;
        }

        @NonNull
        public String getPackageName() {
            Object packageName = getFieldValue(object, "mPackageName");
            if (packageName instanceof String) return (String) packageName;
            else return "";
        }

        public int getUid() {
            Object uid = getFieldValue(object, "mUid");
            if (uid instanceof Integer) return (int) uid;
            return AppOpsManager.OP_NONE;
        }

        public List<OpEntry> getOpEntries() {
            List<OpEntry> opEntries = new ArrayList<>();
            Object entries = getFieldValue(object, "mEntries");
            if (entries instanceof List) {
                for (Object o : (List) entries) {
                    OpEntryConverter converter = new OpEntryConverter(o);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        opEntries.add(new OpEntry(converter.getOp(), converter.getMode(),
                                converter.getTime(), converter.getRejectTime(), converter.getDuration(),
                                converter.getProxyUid(), converter.getProxyPackageName()));
                    } else {
                        opEntries.add(new OpEntry(converter.getOp(), converter.getMode(),
                                converter.getTime(), converter.getRejectTime(), converter.getDuration(),
                                0, null));
                    }
                }
            }
            return opEntries;
        }
    }

    private static class OpEntryConverter {
        Object object;

        private OpEntryConverter(Object object) {
            this.object = object;
        }

        public int getOp() throws ClassCastException {
            Object op = getFieldValue(object, "mOp");
            if (op instanceof Integer) return (int) op;
            throw new ClassCastException("Invalid property mOp");
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        @NonNull
        public String getOpStr() throws ClassCastException {
            Object opStr = invokeObjectMethod(object, "getOpStr", null, null);
            if (opStr instanceof String) return (String) opStr;
            throw new ClassCastException("Invalid method getOpStr()");
        }

        public int getMode() throws ClassCastException {
            Object mode = getFieldValue(object, "mMode");
            if (mode instanceof Integer) return (int) mode;
            throw new ClassCastException("Invalid property mMode");
        }

        // Deprecated in R
        public long getTime() throws ClassCastException {
            Object time = invokeObjectMethod(object, "getTime", null, null);
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getTime()");
        }

        @RequiresApi(Build.VERSION_CODES.P)  // Removed in Q
        public long getLastAccessTime() throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastAccessTime", null, null);
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastAccessTime()");
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastAccessTime(@AppOpsManager.OpFlags int flags) throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastAccessTime", new Class[]{int.class}, new Object[]{flags});
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastAccessTime(int)");
        }

        @RequiresApi(Build.VERSION_CODES.P)  // Removed in Q
        public long getLastAccessForegroundTime() throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastAccessForegroundTime", null, null);
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastAccessForegroundTime()");
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastAccessForegroundTime(@AppOpsManager.OpFlags int flags) throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastAccessForegroundTime", new Class[]{int.class}, new Object[]{flags});
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastAccessForegroundTime(int)");
        }

        @RequiresApi(Build.VERSION_CODES.P)  // removed in Q
        public long getLastAccessBackgroundTime() throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastAccessBackgroundTime", null, null);
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastAccessBackgroundTime()");
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastAccessBackgroundTime(@AppOpsManager.OpFlags int flags) throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastAccessBackgroundTime", new Class[]{int.class}, new Object[]{flags});
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastAccessBackgroundTime(int)");
        }

        @RequiresApi(Build.VERSION_CODES.P)  // removed in Q
        public long getLastTimeFor(@AppOpsManager.UidState int uidState) throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastTimeFor", new Class[]{int.class}, new Object[]{uidState});
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastTimeFor(int)");
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastAccessTime(@AppOpsManager.UidState int fromUidState,
                                      @AppOpsManager.UidState int toUidState,
                                      @AppOpsManager.OpFlags int flags) throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastAccessTime", new Class[]{int.class, int.class, int.class}, new Object[]{fromUidState, toUidState, flags});
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastAccessTime(int, int, int)");
        }

        // Deprecated in R
        public long getRejectTime() throws ClassCastException {
            Object time = invokeObjectMethod(object, "getRejectTime", null, null);
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getRejectTime()");
        }

        @RequiresApi(Build.VERSION_CODES.P)  // removed in Q
        public long getLastRejectTime() throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastRejectTime", null, null);
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastRejectTime()");
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastRejectTime(@AppOpsManager.OpFlags int flags) throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastRejectTime", new Class[]{int.class}, new Object[]{flags});
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastRejectTime(int)");
        }

        @RequiresApi(Build.VERSION_CODES.P)  // removed in Q
        public long getLastRejectForegroundTime() throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastRejectForegroundTime", null, null);
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastRejectForegroundTime()");
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastRejectForegroundTime(@AppOpsManager.OpFlags int flags) throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastRejectForegroundTime", new Class[]{int.class}, new Object[]{flags});
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastRejectForegroundTime(int)");
        }

        @RequiresApi(Build.VERSION_CODES.P)  // removed in Q
        public long getLastRejectBackgroundTime() throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastRejectBackgroundTime", null, null);
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastRejectBackgroundTime()");
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastRejectBackgroundTime(@AppOpsManager.OpFlags int flags) throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastRejectBackgroundTime", new Class[]{int.class}, new Object[]{flags});
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastRejectBackgroundTime(int)");
        }

        @RequiresApi(Build.VERSION_CODES.P)  // removed in Q
        public long getLastRejectTimeFor(@AppOpsManager.UidState int uidState) throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastRejectTimeFor", new Class[]{int.class}, new Object[]{uidState});
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastRejectTimeFor(int)");
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastRejectTime(@AppOpsManager.UidState int fromUidState,
                                      @AppOpsManager.UidState int toUidState,
                                      @AppOpsManager.OpFlags int flags) throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastRejectTime", new Class[]{int.class, int.class, int.class}, new Object[]{fromUidState, toUidState, flags});
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastRejectTime(int, int, int)");
        }

        public boolean isRunning() throws ClassCastException {
            Object time = invokeObjectMethod(object, "isRunning", null, null);
            if (time instanceof Boolean) return (boolean) time;
            throw new ClassCastException("Invalid method isRunning()");
        }

        // Deprecated in R
        public long getDuration() throws ClassCastException {
            Object time = invokeObjectMethod(object, "getDuration", null, null);
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getDuration()");
        }

        @RequiresApi(Build.VERSION_CODES.R)
        public long getLastDuration(@AppOpsManager.OpFlags int flags) throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastDuration", new Class[]{int.class}, new Object[]{flags});
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastDuration(int)");
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastForegroundDuration(@AppOpsManager.OpFlags int flags) throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastForegroundDuration", new Class[]{int.class}, new Object[]{flags});
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastForegroundDuration(int)");
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastBackgroundDuration(@AppOpsManager.OpFlags int flags) throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastBackgroundDuration", new Class[]{int.class}, new Object[]{flags});
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastBackgroundDuration(int)");
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastDuration(@AppOpsManager.UidState int fromUidState,
                                    @AppOpsManager.UidState int toUidState,
                                    @AppOpsManager.OpFlags int flags) throws ClassCastException {
            Object time = invokeObjectMethod(object, "getLastDuration", new Class[]{int.class, int.class, int.class}, new Object[]{fromUidState, toUidState, flags});
            if (time instanceof Long) return (long) time;
            throw new ClassCastException("Invalid method getLastDuration(int, int, int)");
        }

        // Deprecated in R
        @RequiresApi(Build.VERSION_CODES.M)
        public int getProxyUid() throws ClassCastException {
            Object proxyUid = invokeObjectMethod(object, "getProxyUid", null, null);
            if (proxyUid instanceof Integer) return (int) proxyUid;
            throw new ClassCastException("Invalid method getProxyUid()");
        }

        // Deprecated in R
        @RequiresApi(Build.VERSION_CODES.Q)
        public int getProxyUid(@AppOpsManager.UidState int uidState, @AppOpsManager.OpFlags int flags) {
            Object proxyUid = invokeObjectMethod(object, "getProxyUid", new Class[]{int.class, int.class}, new Object[]{uidState, flags});
            if (proxyUid instanceof Integer) return (int) proxyUid;
            throw new ClassCastException("Invalid method getProxyUid(int, int)");
        }

        // Deprecated in R
        @RequiresApi(Build.VERSION_CODES.M)
        @Nullable
        public String getProxyPackageName() throws ClassCastException {
            Object proxyPackageName = invokeObjectMethod(object, "getProxyPackageName", null, null);
            if (proxyPackageName == null || proxyPackageName instanceof String) {
                return (String) proxyPackageName;
            }
            throw new ClassCastException("Invalid method getProxyPackageName()");
        }

        // Deprecated in R
        @RequiresApi(Build.VERSION_CODES.Q)
        @Nullable
        public String getProxyPackageName(@AppOpsManager.UidState int uidState, @AppOpsManager.OpFlags int flags) {
            Object proxyPackageName = invokeObjectMethod(object, "getProxyPackageName", new Class[]{int.class, int.class}, new Object[]{uidState, flags});
            if (proxyPackageName == null || proxyPackageName instanceof String) {
                return (String) proxyPackageName;
            }
            throw new ClassCastException("Invalid method getProxyPackageName(int, int)");
        }

        // TODO(24/12/20): Get proxy info (From API 30)
    }

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
