package io.github.muntashirakon.AppManager.compat;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyManager.OnClearApplicationUserDataListener;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.dpc.DpcReceiver;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

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

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static void setOrganizationName(String name) {
        getDevicePolicyManager().setOrganizationName(DPC_ADMIN, name);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static void setOrganizationColor(int color) {
        getDevicePolicyManager().setOrganizationColor(DPC_ADMIN, color);
    }

    @NonNull
    @RequiresApi(api = Build.VERSION_CODES.N)
    public static Bundle getUserRestrictions() {
        return getDevicePolicyManager().getUserRestrictions(DPC_ADMIN);
    }

    public static boolean setApplicationHidden(String packageName, boolean hidden) {
        return getDevicePolicyManager().setApplicationHidden(DPC_ADMIN, packageName, hidden);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static boolean setPackageSuspended(String packageName, boolean suspended) {
        return !Arrays.stream(getDevicePolicyManager().setPackagesSuspended(DPC_ADMIN, new String[]{packageName}, suspended)).anyMatch((s)-> Objects.equals(s, packageName));
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    public static void clearApplicationUserData(String packageName, DevicePolicyManager.OnClearApplicationUserDataListener listener) {
        getDevicePolicyManager().clearApplicationUserData(DPC_ADMIN, packageName, ThreadUtils.getBackgroundThreadExecutor(), listener);
    }
}
