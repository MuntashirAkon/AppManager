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

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.os.Bundle;

import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.ClassCaller;
import io.github.muntashirakon.AppManager.server.common.Shell;
import io.github.muntashirakon.AppManager.servermanager.remote.RestartHandler;
import io.github.muntashirakon.AppManager.servermanager.remote.ShellCommandHandler;
import io.github.muntashirakon.toybox.ToyboxInitializer;

public class ApiSupporter {
    private static ApiSupporter INSTANCE;

    public static ApiSupporter getInstance() {
        if (INSTANCE == null) INSTANCE = new ApiSupporter();
        return INSTANCE;
    }

    private static final String TAG = "ApiSupporter";
    private final LocalServer localServer;
    private final String packageName;

    private ApiSupporter() {
        this.localServer = LocalServer.getInstance();
        this.packageName = BuildConfig.APPLICATION_ID;
    }

    public List<PackageInfo> getInstalledPackages(int flags, int userHandle) throws Exception {
        IPackageManager pm = IPackageManager.Stub.asInterface(ProxyBinder.getService("package"));
        return pm.getInstalledPackages(flags, userHandle).getList();
    }

    public void setComponentEnabledSetting(String packageName, String componentName, int newState, int flags, int userHandle) throws Exception {
        IPackageManager pm = IPackageManager.Stub.asInterface(ProxyBinder.getService("package"));
        pm.setComponentEnabledSetting(new ComponentName(packageName, componentName), newState, flags, userHandle);
    }

    @NonNull
    public PackageInfo getPackageInfo(String packageName, int flags, int userHandle) throws Exception {
        IPackageManager pm = IPackageManager.Stub.asInterface(ProxyBinder.getService("package"));
        return pm.getPackageInfo(packageName, flags, userHandle);
    }

    @NonNull
    public ApplicationInfo getApplicationInfo(String packageName, int flags, int userHandle) throws Exception {
        IPackageManager pm = IPackageManager.Stub.asInterface(ProxyBinder.getService("package"));
        return pm.getApplicationInfo(packageName, flags, userHandle);
    }

    public Shell.Result runCommand(String command) throws Exception {
        Bundle args = new Bundle();
        args.putString("command", command);
        args.putString("path", ToyboxInitializer.getToyboxLib(localServer.getContext()).getParent());
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
