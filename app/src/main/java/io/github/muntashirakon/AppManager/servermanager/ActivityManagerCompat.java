package io.github.muntashirakon.AppManager.servermanager;

import android.annotation.UserIdInt;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.IContentProvider;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;

public final class ActivityManagerCompat {
    public static final String SHELL_PACKAGE_NAME = "com.android.shell";

    @SuppressWarnings("deprecation")
    public static int startActivity(Context context, Intent intent, @UserIdInt int userHandle)
            throws RemoteException {
        IActivityManager am = getActivityManager();
        String callingPackage = LocalServer.isAMServiceAlive() ? SHELL_PACKAGE_NAME : context.getPackageName();
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            result = am.startActivityAsUserWithFeature(null, callingPackage,
                    null, intent, intent.getType(), null, null,
                    0, 0, null, null, userHandle);
        } else {
            result = am.startActivityAsUser(null, callingPackage, intent, intent.getType(),
                    null, null, 0, 0, null,
                    null, userHandle);
        }
        return result;
    }

    @SuppressWarnings("deprecation")
    public static ComponentName startService(Context context, Intent intent,
                                             @UserIdInt int userHandle, boolean asForeground)
            throws RemoteException {
        IActivityManager am = getActivityManager();
        String callingPackage = LocalServer.isAMServiceAlive() ? SHELL_PACKAGE_NAME : context.getPackageName();
        ComponentName cn;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            cn = am.startService(null, intent, intent.getType(), asForeground, callingPackage, null, userHandle);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            cn = am.startService(null, intent, intent.getType(), asForeground, callingPackage, userHandle);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cn = am.startService(null, intent, intent.getType(), callingPackage, userHandle);
        } else cn = am.startService(null, intent, intent.getType(), userHandle);
        return cn;
    }

    public static int sendBroadcast(Intent intent, @UserIdInt int userHandle)
            throws RemoteException {
        IActivityManager am = getActivityManager();
        int res;
        IIntentReceiver receiver = new IntentReceiver();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            res = am.broadcastIntentWithFeature(null, null, intent, null, receiver, 0, null, null, null, AppOpsManager.OP_NONE, null, true, false, userHandle);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            res = am.broadcastIntent(null, intent, null, null, 0, null, null, null, AppOpsManager.OP_NONE, null, true, false, userHandle);
        } else {
            res = am.broadcastIntent(null, intent, null, null, 0, null, null, null, AppOpsManager.OP_NONE, true, false, userHandle);
        }
        return res;
    }

    @Nullable
    public static IContentProvider getContentProviderExternal(String name, int userId, IBinder token, String tag)
            throws RemoteException {
        IActivityManager am = getActivityManager();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return am.getContentProviderExternal(name, userId, token, tag).provider;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return ((android.app.ContentProviderHolder) am.getContentProviderExternal(name, userId, token)).provider;
            } else {
                return ((IActivityManager.ContentProviderHolder) am.getContentProviderExternal(name, userId, token)).provider;
            }
        } catch (NullPointerException e) {
            return null;
        }
    }

    public static IActivityManager getActivityManager() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return IActivityManager.Stub.asInterface(ProxyBinder.getService(Context.ACTIVITY_SERVICE));
        } else {
            return ActivityManagerNative.asInterface(ProxyBinder.getService(Context.ACTIVITY_SERVICE));
        }
    }

    final static class IntentReceiver extends IIntentReceiver.Stub {
        private boolean mFinished = false;

        public void performReceive(Intent intent, int resultCode, String data, Bundle extras,
                                   boolean ordered, boolean sticky, int sendingUser) {
            String line = "Broadcast completed: result=" + resultCode;
            if (data != null) line = line + ", data=\"" + data + "\"";
            if (extras != null) line = line + ", extras: " + extras;
            Log.e("AM", line);
            synchronized (this) {
                mFinished = true;
                notifyAll();
            }
        }

        public synchronized void waitForFinish() {
            try {
                while (!mFinished) wait();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
