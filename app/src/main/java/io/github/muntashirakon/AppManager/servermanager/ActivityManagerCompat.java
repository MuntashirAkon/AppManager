package io.github.muntashirakon.AppManager.servermanager;

import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.RemoteException;

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.misc.UserIdInt;

public final class ActivityManagerCompat {
    public static final String SHELL_PACKAGE_NAME = "com.android.shell";
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

    public static IActivityManager getActivityManager() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return IActivityManager.Stub.asInterface(ProxyBinder.getService(Context.ACTIVITY_SERVICE));
        } else {
            return ActivityManagerNative.asInterface(ProxyBinder.getService(Context.ACTIVITY_SERVICE));
        }
    }
}
