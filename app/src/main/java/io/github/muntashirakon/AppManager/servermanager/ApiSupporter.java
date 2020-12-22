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

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Bundle;

import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.ClassCaller;
import io.github.muntashirakon.AppManager.server.common.Shell;
import io.github.muntashirakon.AppManager.servermanager.remote.RestartHandler;
import io.github.muntashirakon.AppManager.servermanager.remote.ShellCommandHandler;
import io.github.muntashirakon.toybox.ToyboxInitializer;

@SuppressWarnings("RedundantThrows")  // Unfortunately, throws are not redundant here
public final class ApiSupporter {
    private static final String TAG = "ApiSupporter";

    private ApiSupporter() {
    }

    public static List<PackageInfo> getInstalledPackages(int flags, int userHandle) throws Exception {
        return AppManager.getIPackageManager().getInstalledPackages(flags, userHandle).getList();
    }

    @NonNull
    public static PackageInfo getPackageInfo(String packageName, int flags, int userHandle) throws Exception {
        return AppManager.getIPackageManager().getPackageInfo(packageName, flags, userHandle);
    }

    @NonNull
    public static ApplicationInfo getApplicationInfo(String packageName, int flags, int userHandle) throws Exception {
        return AppManager.getIPackageManager().getApplicationInfo(packageName, flags, userHandle);
    }

    public static Shell.Result runCommand(String command) throws Exception {
        LocalServer localServer = LocalServer.getInstance();
        Bundle args = new Bundle();
        args.putString("command", command);
        args.putString("path", ToyboxInitializer.getToyboxLib(localServer.getContext()).getParent());
        ClassCaller classCaller = new ClassCaller(BuildConfig.APPLICATION_ID, ShellCommandHandler.class.getName(), args);
        CallerResult result = localServer.exec(classCaller);
        Bundle replyBundle = result.getReplyBundle();
        return replyBundle.getParcelable("return");
    }

    public static void restartServer() throws Exception {
        LocalServer localServer = LocalServer.getInstance();
        ClassCaller classCaller = new ClassCaller(BuildConfig.APPLICATION_ID, RestartHandler.class.getName(), new Bundle());
        CallerResult result = localServer.exec(classCaller);
        Log.e(TAG, "restartServer --> " + result);
    }
}
