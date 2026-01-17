package io.github.muntashirakon.AppManager.compat;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.dpc.DpcReceiver;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

public class DevicePolicyManagerCompat {
    public static final String LOG_TAG = "DPC";
    public static final ComponentName DPC_ADMIN = new ComponentName(BuildConfig.APPLICATION_ID, DpcReceiver.class.getCanonicalName());
    public static final String DPC_COMMAND = String.format("dpm set-device-owner %s", DevicePolicyManagerCompat.DPC_ADMIN.flattenToString());
    private static DevicePolicyManager dpm;
    @SuppressLint("WrongConstant")
    public static DevicePolicyManager getDevicePolicyManager() {

        if (dpm==null) {dpm=(DevicePolicyManager) ContextUtils.getContext().getSystemService(Context.DEVICE_POLICY_SERVICE);}
        return dpm;
    }


    public static boolean isOwnerApp() {
        return getDevicePolicyManager().isDeviceOwnerApp(BuildConfig.APPLICATION_ID);
    }

    public static void setSecureSetting(String setting, String value) {
        getDevicePolicyManager().setSecureSetting(DPC_ADMIN, setting, value);
    }
    public static void setGlobalSetting(String setting, String value) {
        getDevicePolicyManager().setGlobalSetting(DPC_ADMIN, setting, value);
    }
    @RequiresApi(api = Build.VERSION_CODES.P)
    public static void setSystemSetting(String setting, String value) {
        getDevicePolicyManager().setSystemSetting(DPC_ADMIN, setting, value);
    }

    public static void clearDeviceOwnerApp() {
        getDevicePolicyManager().clearDeviceOwnerApp(BuildConfig.APPLICATION_ID);
    }

    public static boolean canModifyPermissions() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Ops.isDpc());
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static void revokePermission(String packageName, String permission) {
        getDevicePolicyManager().setPermissionGrantState(DPC_ADMIN, packageName, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static void grantPermission(String packageName, String permission) {
        getDevicePolicyManager().setPermissionGrantState(DPC_ADMIN, packageName, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
    }
}
