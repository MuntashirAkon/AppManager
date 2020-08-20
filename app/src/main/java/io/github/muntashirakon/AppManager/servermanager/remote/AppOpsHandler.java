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

package io.github.muntashirakon.AppManager.servermanager.remote;

import android.Manifest;
import android.app.ActivityThread;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Process;
import android.os.ServiceManager;
import android.util.Log;

import com.android.internal.app.IAppOpsService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.server.common.ClassCallerProcessor;
import io.github.muntashirakon.AppManager.server.common.FLog;
import io.github.muntashirakon.AppManager.server.common.OpEntry;
import io.github.muntashirakon.AppManager.server.common.OpsCommands;
import io.github.muntashirakon.AppManager.server.common.OpsResult;
//import io.github.muntashirakon.AppManager.server.common.OtherOp;
import io.github.muntashirakon.AppManager.server.common.PackageOps;
import io.github.muntashirakon.AppManager.server.common.ReflectUtils;
import io.github.muntashirakon.AppManager.server.common.Shell;

/**
 * Handler class for App Ops. This class is executed by the server, therefore, any privileged code
 * can be run here.
 */
public class AppOpsHandler extends ClassCallerProcessor {
    private static IpTablesController mIpTablesController = null;

    static {
        try {
            if (Process.myUid() == 0) {
                mIpTablesController = new IpTablesController(Shell.getRootShell());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public AppOpsHandler(Context mPackageContext, Context mSystemContext, byte[] bytes) {
        super(mPackageContext, mSystemContext, bytes);
    }

    @Override
    public Bundle proxyInvoke(Bundle args) throws Throwable {
        OpsResult result;
        try {
            OpsCommands.Builder builder = args.getParcelable("args");
            args.clear();
            if (builder == null) throw new Exception("Builder is null.");
            FLog.log(" appops " + builder);
            result = handleCommand(builder);
            if (result == null) {
                result = new OpsResult(null, null);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            result = new OpsResult(null, throwable);
        }
        args.putParcelable("return", result);
        return args;
    }

    @Nullable
    private OpsResult handleCommand(@NonNull OpsCommands.Builder builder) throws Throwable {
        switch (builder.getAction()) {
            case OpsCommands.ACTION_GET:
                return runGet(builder);
            case OpsCommands.ACTION_SET:
                runSet(builder);
                break;
            case OpsCommands.ACTION_CHECK:
                return runCheck(builder);
            case OpsCommands.ACTION_RESET:
                runReset(builder);
                break;
            case OpsCommands.ACTION_GET_FOR_OPS:
                return runGetForOps(builder);
        }
        return null;
    }

    @NonNull
    private OpsResult runGet(@NonNull OpsCommands.Builder builder) throws Throwable {
        final IAppOpsService appOpsService = IAppOpsService.Stub.asInterface(
                ServiceManager.getService(Context.APP_OPS_SERVICE));
        if (appOpsService == null) throw new Exception("AppOpsService is null");
        String packageName = builder.getPackageName();
        int uid = Helper.getPackageUid(packageName, builder.getUserHandleId());
        System.out.println(Arrays.toString(builder.getOps()));
        List<Parcelable> opsForPackage = appOpsService.getOpsForPackage(uid, packageName, builder.getOps());
        ArrayList<PackageOps> packageOpsList = new ArrayList<>();
        if (opsForPackage != null) {
            for (Parcelable o : opsForPackage) {
                PackageOps packageOps = ReflectUtils.opsConvert(o);
//                addSupport(appOpsService, packageOps, builder.getUserHandleId());
                packageOpsList.add(packageOps);
            }
        } else {
            PackageOps packageOps = new PackageOps(packageName, uid, new ArrayList<>());
//            addSupport(appOpsService, packageOps, builder.getUserHandleId());
            packageOpsList.add(packageOps);
        }

        return new OpsResult(packageOpsList, null);

    }


    private void addSupport(IAppOpsService appOpsService, PackageOps ops, int userHandleId) {
        addSupport(appOpsService, ops, userHandleId, true);
    }

    private void addSupport(IAppOpsService appOpsService, PackageOps ops, int userHandleId, boolean checkNet) {
        try {
            if (checkNet && mIpTablesController != null) {
                int mode = mIpTablesController.isMobileDataEnable(ops.getUid()) ? AppOpsManager.MODE_ALLOWED
                        : AppOpsManager.MODE_IGNORED;
//                OpEntry opEntry = new OpEntry(OtherOp.OP_ACCESS_PHONE_DATA, mode, 0, 0, 0, 0, null);
//                ops.getOps().add(opEntry);

                mode = mIpTablesController.isWifiDataEnable(ops.getUid()) ? AppOpsManager.MODE_ALLOWED
                        : AppOpsManager.MODE_IGNORED;
//                opEntry = new OpEntry(OtherOp.OP_ACCESS_WIFI_NETWORK, mode, 0, 0, 0, 0, null);
//                ops.getOps().add(opEntry);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(Log.getStackTraceString(e));
        }
        try {
            PackageInfo packageInfo = ActivityThread.getPackageManager()
                    .getPackageInfo(ops.getPackageName(), PackageManager.GET_PERMISSIONS, userHandleId);
            if (packageInfo != null && packageInfo.requestedPermissions != null) {
                for (String permission : packageInfo.requestedPermissions) {
                    int code = Helper.permissionToCode(permission);

                    if (code <= 0) {
                        //correct OP_WIFI_SCAN code.
                        if (Manifest.permission.ACCESS_WIFI_STATE.equals(permission)) {
//                            code = OtherOp.getWifiScanOp();
                        }
                    }

                    if (code > 0 && !ops.hasOp(code)) {
                        int mode = appOpsService.checkOperation(code, ops.getUid(), ops.getPackageName());
                        if (mode != AppOpsManager.MODE_ERRORED) {
//                            ops.getOps().add(new OpEntry(code, mode, 0, 0, 0, 0, null));
                        }
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void runSet(@NonNull OpsCommands.Builder builder) throws Throwable {
        final int uid = Helper.getPackageUid(builder.getPackageName(), builder.getUserHandleId());
//        if (OtherOp.isOtherOp(builder.getOpInt())) {
//            setOther(builder, uid);
//        } else {
        final IAppOpsService appOpsService = IAppOpsService.Stub.asInterface(
                ServiceManager.getService(Context.APP_OPS_SERVICE));
        if (appOpsService == null) throw new Exception("AppOpsService is null");
        appOpsService.setMode(builder.getOpInt(), uid, builder.getPackageName(), builder.getModeInt());
        if (appOpsService.checkOperation(builder.getOpInt(), uid, builder.getPackageName()) != builder.getModeInt())
            throw new Exception("Failed to set mode " + builder.getModeInt() + " for op " + builder.getOpInt() + " in package " + builder.getPackageName());
//        }
    }

//    private void setOther(OpsCommands.Builder builder, int uid) {
//        if (mIpTablesController != null) {
//            boolean enable = builder.getModeInt() == AppOpsManager.MODE_ALLOWED;
//            switch (builder.getOpInt()) {
//                case OtherOp.OP_ACCESS_PHONE_DATA:
//                    mIpTablesController.setMobileData(uid, enable);
//                    break;
//                case OtherOp.OP_ACCESS_WIFI_NETWORK:
//                    mIpTablesController.setWifiData(uid, enable);
//                    break;
//            }
//        }
//    }

    @NonNull
    private OpsResult runCheck(@NonNull OpsCommands.Builder builder) throws Throwable {
        final IAppOpsService appOpsService = IAppOpsService.Stub.asInterface(
                ServiceManager.getService(Context.APP_OPS_SERVICE));
        if (appOpsService == null) throw new Exception("AppOpsService is null");
        String packageName = builder.getPackageName();
        int uid = Helper.getPackageUid(packageName, builder.getUserHandleId());
        int mode = appOpsService.checkOperation(builder.getOpInt(), uid, packageName);
        return new OpsResult(mode, null);
    }

    private void runReset(@NonNull OpsCommands.Builder builder) throws Throwable {
        final IAppOpsService appOpsService = IAppOpsService.Stub.asInterface(
                ServiceManager.getService(Context.APP_OPS_SERVICE));
        if (appOpsService == null) throw new Exception("AppOpsService is null");
        appOpsService.resetAllModes(builder.getUserHandleId(), builder.getPackageName());
    }

    @NonNull
    private OpsResult runGetForOps(@NonNull OpsCommands.Builder builder) throws Throwable {
        final IAppOpsService appOpsService = IAppOpsService.Stub.asInterface(
                ServiceManager.getService(Context.APP_OPS_SERVICE));
        if (appOpsService == null) throw new Exception("AppOpsService is null");
        List<Parcelable> opsForPackage = appOpsService.getPackagesForOps(builder.getOps());
        ArrayList<PackageOps> packageOpses = new ArrayList<>();
        if (opsForPackage != null) {
            for (Parcelable o : opsForPackage) {
                PackageOps packageOps = ReflectUtils.opsConvert(o);
//                addSupport(appOpsService, packageOps, builder.getUserHandleId(), builder.isReqNet());
                packageOpses.add(packageOps);
            }
        }

        return new OpsResult(packageOpses, null);
    }


}
