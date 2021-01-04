package io.github.muntashirakon.AppManager.servermanager;

import android.content.ComponentName;
import android.content.pm.IPackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.permission.IPermissionManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.IntDef;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.misc.UserIdInt;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.content.pm.PackageManager.SYNCHRONOUS;

public class PackageManagerCompat {
    @IntDef({
            COMPONENT_ENABLED_STATE_DEFAULT,
            COMPONENT_ENABLED_STATE_ENABLED,
            COMPONENT_ENABLED_STATE_DISABLED,
            COMPONENT_ENABLED_STATE_DISABLED_USER,
            COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface EnabledState {}

    @IntDef(flag = true, value = {
            DONT_KILL_APP,
            SYNCHRONOUS
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface EnabledFlags {}

    public static void setComponentEnabledSetting(ComponentName componentName,
                                                  @EnabledState int newState,
                                                  @EnabledFlags int flags,
                                                  @UserIdInt int userId)
            throws RemoteException {
        AppManager.getIPackageManager().setComponentEnabledSetting(componentName, newState, flags, userId);
    }

    public static void setApplicationEnabledSetting(String packageName, @EnabledState int newState,
                                                    @EnabledFlags int flags, @UserIdInt int userId)
            throws RemoteException {
        AppManager.getIPackageManager().setApplicationEnabledSetting(packageName, newState, flags, userId, null);
    }

    public static void grantPermission(String packageName, String permissionName, int userId)
            throws RemoteException {
        IPackageManager pm = AppManager.getIPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.grantRuntimePermission(packageName, permissionName, userId);
        } else {
            pm.grantPermission(packageName, permissionName);
        }
    }

    public static void revokePermission(String packageName, String permissionName, int userId)
            throws RemoteException {
        IPackageManager pm = AppManager.getIPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            IPermissionManager permissionManager = IPermissionManager.Stub.asInterface(ProxyBinder.getService("permissionmgr"));
            permissionManager.revokeRuntimePermission(packageName, permissionName, userId, null);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.revokeRuntimePermission(packageName, permissionName, userId);
        } else {
            pm.revokePermission(packageName, permissionName);
        }
    }
}
