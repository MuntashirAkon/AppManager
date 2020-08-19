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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.util.Log;

import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.servermanager.remote.RestartHandler;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.ClassCaller;
import io.github.muntashirakon.AppManager.server.common.SystemServiceCaller;

public class ApiSupporter {
    private static final String TAG = "ApiSupporter";
    private LocalServerManager localServerManager;

    ApiSupporter(LocalServerManager localServerManager) {
        this.localServerManager = localServerManager;
    }

    private void checkConnection() throws Exception {
        localServerManager.start();
    }

    public List<PackageInfo> getInstalledPackages(int flags, int uid) throws Exception {
        checkConnection();
        SystemServiceCaller caller = new SystemServiceCaller("package", "getInstalledPackages", new Class[]{int.class, int.class}, new Object[]{flags, uid});
        CallerResult callerResult = localServerManager.execNew(caller);
        callerResult.getReplyObj();
        if (callerResult.getThrowable() != null) {
            throw new Exception(callerResult.getThrowable());
        } else {
            Object replyObj = callerResult.getReplyObj();
            if (replyObj instanceof List) {
                try {
                    return ((List<PackageInfo>) replyObj);
                } catch (ClassCastException ignore) {}
            }
        }

        return null;
    }

    public List<UserInfo> getUsers(boolean excludeDying) throws Exception {
        checkConnection();
        SystemServiceCaller caller = new SystemServiceCaller(
                Context.USER_SERVICE, "getUsers", new Class[]{boolean.class}, new Object[]{excludeDying});
        CallerResult callerResult = localServerManager.execNew(caller);
        callerResult.getReplyObj();
        if (callerResult.getThrowable() != null) {
            throw new Exception(callerResult.getThrowable());
        } else {
            Object replyObj = callerResult.getReplyObj();
            if (replyObj instanceof List) {
                return ((List<UserInfo>) replyObj);
            }
        }
        return null;
    }

    public void restartServer(@NonNull Context context) throws Exception {
        ClassCaller classCaller = new ClassCaller(context.getPackageName(), RestartHandler.class.getName(), new Bundle());
        CallerResult result = localServerManager.execNew(classCaller);
        Log.e(TAG, "restartServer --> " + result);
    }

}
