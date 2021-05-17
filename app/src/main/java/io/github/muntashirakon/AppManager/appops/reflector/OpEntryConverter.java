// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.appops.reflector;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;

import static io.github.muntashirakon.AppManager.appops.reflector.ReflectUtils.getFieldValue;
import static io.github.muntashirakon.AppManager.appops.reflector.ReflectUtils.invokeObjectMethod;

class OpEntryConverter {
    Object object;

    /* package */ OpEntryConverter(Object object) {
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
        else if (time instanceof Integer) return (int) time;
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