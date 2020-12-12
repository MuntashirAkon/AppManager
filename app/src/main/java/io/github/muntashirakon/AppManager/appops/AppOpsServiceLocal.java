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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Parcelable;

import com.android.internal.app.IAppOpsService;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.server.common.PackageOps;
import io.github.muntashirakon.AppManager.server.common.ReflectUtils;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;

/**
 * Reimplementation of IAppOpsService, every methods reroute to their respective methods
 */
public class AppOpsServiceLocal {
    @SuppressLint("StaticFieldLeak")
    private static AppOpsServiceLocal appOpsService = null;
    public static AppOpsServiceLocal getInstance(Context context) throws Exception {
        if (appOpsService == null) {
            appOpsService = new AppOpsServiceLocal(context.getApplicationContext());
        }
        return appOpsService;
    }

    Context context;
    IAppOpsService iAppOpsService;

    private AppOpsServiceLocal() {}

    private AppOpsServiceLocal(Context ctx) throws Exception {
        context = ctx;
        if (PermissionUtils.hasAppOpsPermission(context)) {
            init();
        } else {
            Runner.Result result = RunnerUtils.grantPermission(ctx.getPackageName(),
                    PermissionUtils.PERMISSION_GET_APP_OPS_STATS, Users.getCurrentUserHandle());
            if (result.isSuccessful()) init();
            else throw new Exception("Permission not granted.");
        }
    }

    private void init() throws Exception {
        try {
            // Invoke function android.os.ServiceManager.getService(Context.APP_OPS_SERVICE)
            @SuppressLint("PrivateApi")
            @SuppressWarnings("rawtypes")
            Class localClass = Class.forName("android.os.ServiceManager");
            @SuppressWarnings("unchecked")
            Method getService = localClass.getMethod("getService", String.class);
            Object result = getService.invoke(localClass, Context.APP_OPS_SERVICE);
            if(result != null) {
                iAppOpsService = IAppOpsService.Stub.asInterface((IBinder) result);
                if (iAppOpsService == null) {
                    throw new Exception("AppOpsService cannot be null");
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                | InvocationTargetException | PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    public int checkOperation(int code, int uid, String packageName) {
        return iAppOpsService.checkOperation(code, uid, packageName);
    }

    public int noteOperation(int code, int uid, String packageName) {
        return iAppOpsService.noteOperation(code, uid, packageName);
    }

    public int permissionToOpCode(String permission) {
        return iAppOpsService.permissionToOpCode(permission);
    }

    // Remaining methods are only used in Java.
    public int checkPackage(int uid, String packageName) {
        return iAppOpsService.checkPackage(uid, packageName);
    }

    @NonNull
    public List<PackageOps> getPackagesForOps(@Nullable int[] ops) {
        List<Parcelable> opsForPackage = iAppOpsService.getPackagesForOps(ops);
        List<PackageOps> packageOpsList = new ArrayList<>();
        if (opsForPackage != null) {
            for (Object o : opsForPackage) {
                PackageOps packageOps = ReflectUtils.opsConvert(o);
                packageOpsList.add(packageOps);
            }
        }
        return packageOpsList;
    }

    @NonNull
    public List<PackageOps> getOpsForPackage(int uid, @NonNull String packageName, @Nullable int[] ops) {
        List<Parcelable> parcelablePackageOps = iAppOpsService.getOpsForPackage(uid, packageName, ops);
        List<PackageOps> packageOpsList = new ArrayList<>();
        if (parcelablePackageOps != null) {
            // Convert parcelable to PackageOps
            for (Parcelable o : parcelablePackageOps) {
                PackageOps packageOps = ReflectUtils.opsConvert(o);
                packageOpsList.add(packageOps);
            }
        } else {
            // Add an empty array
            PackageOps packageOps = new PackageOps(packageName, uid, new ArrayList<>());
            packageOpsList.add(packageOps);
        }
        return packageOpsList;
    }

    public void setUidMode(int code, int uid, int mode) {
        iAppOpsService.setUidMode(code, uid, mode);
    }

    public void setMode(int code, int uid, String packageName, int mode) {
        iAppOpsService.setMode(code, uid, packageName, mode);
    }

    public void resetAllModes(int reqUserId, String reqPackageName) {
        iAppOpsService.resetAllModes(reqUserId, reqPackageName);
    }
}
