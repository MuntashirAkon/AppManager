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

package io.github.muntashirakon.AppManager.servermanager;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.server.common.Shell;
import io.github.muntashirakon.AppManager.servermanager.remote.PackageHandler;
import io.github.muntashirakon.AppManager.servermanager.remote.RestartHandler;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.ClassCaller;
import io.github.muntashirakon.AppManager.server.common.SystemServiceCaller;
import io.github.muntashirakon.AppManager.servermanager.remote.ShellCommandHandler;
import io.github.muntashirakon.AppManager.servermanager.remote.UserHandler;

public class ApiSupporter {
    private static ApiSupporter INSTANCE;

    public static ApiSupporter getInstance(LocalServer localServer) {
        if (INSTANCE == null) INSTANCE = new ApiSupporter(localServer);
        return INSTANCE;
    }

    private static final String TAG = "ApiSupporter";
    private LocalServer localServer;
    private String packageName;
    private int userHandle;

    ApiSupporter(@NonNull LocalServer localServer) {
        this.localServer = localServer;
        this.packageName = BuildConfig.APPLICATION_ID;
        this.userHandle = Users.getCurrentUserHandle();
    }

    public List<PackageInfo> getInstalledPackages(int flags, int userHandle) throws Exception {
        SystemServiceCaller caller = new SystemServiceCaller("package",
                "getInstalledPackages", new Class[]{int.class, int.class}, new Object[]{flags, userHandle});
        CallerResult callerResult = localServer.exec(caller);
        callerResult.getReplyObj();
        if (callerResult.getThrowable() != null) {
            throw new Exception(callerResult.getThrowable());
        } else {
            Object replyObj = callerResult.getReplyObj();
            if (replyObj instanceof List) {
                try {
                    return ((List<PackageInfo>) replyObj);
                } catch (ClassCastException ignore) {
                }
            }
        }
        return null;
    }

    public void setComponentEnabledSetting(String packageName, String componentName, int newState, int flags, int userHandle) throws Exception {
        Bundle args = new Bundle();
        args.putInt(PackageHandler.ARG_ACTION, PackageHandler.ACTION_COMPONENT_SETTING);
        args.putString(PackageHandler.ARG_PACKAGE_NAME, packageName);
        args.putString(PackageHandler.ARG_COMPONENT_NAME, componentName);
        args.putInt(PackageHandler.ARG_COMPONENT_STATE, newState);
        args.putInt(PackageHandler.ARG_FLAGS, flags);
        args.putInt(PackageHandler.ARG_USER_HANDLE, userHandle);
        ClassCaller classCaller = new ClassCaller(this.packageName, PackageHandler.class.getName(), args);
        CallerResult result = localServer.exec(classCaller);
        result.getReplyObj();
        if (result.getThrowable() != null) {
            throw new Exception(result.getThrowable());
        }
    }

    @NonNull
    public PackageInfo getPackageInfo(String packageName, int flags, int userHandle) throws Exception {
        if (this.userHandle == userHandle) {
            // Get using PackageManager if the handler are the same
            return localServer.getContext().getPackageManager().getPackageInfo(packageName, flags);
        }
        Bundle args = new Bundle();
        args.putInt(PackageHandler.ARG_ACTION, PackageHandler.ACTION_PACKAGE_INFO);
        args.putString(PackageHandler.ARG_PACKAGE_NAME, packageName);
        args.putInt(PackageHandler.ARG_FLAGS, flags);
        args.putInt(PackageHandler.ARG_USER_HANDLE, userHandle);
        ClassCaller classCaller = new ClassCaller(this.packageName, PackageHandler.class.getName(), args);
        CallerResult result = localServer.exec(classCaller);
        result.getReplyObj();
        if (result.getThrowable() != null) {
            throw new Exception(result.getThrowable());
        } else {
            Bundle reply = result.getReplyBundle();
            PackageInfo packageInfo = reply.getParcelable("return");
            if (packageInfo == null) {
                throw new PackageManager.NameNotFoundException("Package doesn't exist.");
            }
            return packageInfo;
        }
    }

    public List<UserInfo> getUsers() throws Exception {
        Bundle args = new Bundle();
        args.putInt(UserHandler.ARG_ACTION, UserHandler.ACTION_GET_ALL_USER_INFO);
        ClassCaller classCaller = new ClassCaller(this.packageName, UserHandler.class.getName(), args);
        CallerResult callerResult = localServer.exec(classCaller);
        callerResult.getReplyObj();
        if (callerResult.getThrowable() != null) {
            // Try new API
            if (Build.VERSION.SDK_INT >= 29) {
                args.putInt(UserHandler.ARG_ACTION, UserHandler.ACTION_GET_ALL_USER_INFO_NEW_API);
                classCaller = new ClassCaller(this.packageName, UserHandler.class.getName(), args);
                callerResult = localServer.exec(classCaller);
                callerResult.getReplyObj();
                if (callerResult.getThrowable() != null) {
                    throw new Exception(callerResult.getThrowable());
                } else {
                    Bundle bundle = callerResult.getReplyBundle();
                    return bundle.getParcelableArrayList("return");
                }
            } else throw new Exception(callerResult.getThrowable());
        } else {
            Bundle bundle = callerResult.getReplyBundle();
            return bundle.getParcelableArrayList("return");
        }
    }

    public Shell.Result runCommand(String command) throws Exception {
        Bundle args = new Bundle();
        args.putString("command", command);
        ClassCaller classCaller = new ClassCaller(packageName, ShellCommandHandler.class.getName(), args);
        CallerResult result = localServer.exec(classCaller);
        Bundle replyBundle = result.getReplyBundle();
        return replyBundle.getParcelable("return");
    }

    public void restartServer() throws Exception {
        ClassCaller classCaller = new ClassCaller(packageName, RestartHandler.class.getName(), new Bundle());
        CallerResult result = localServer.exec(classCaller);
        Log.e(TAG, "restartServer --> " + result);
    }
}
