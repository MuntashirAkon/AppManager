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

import android.content.Context;
import android.content.pm.UserInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IUserManager;
import android.os.ServiceManager;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.server.common.ClassCallerProcessor;

public final class UserHandler extends ClassCallerProcessor {
    public static final String ARG_ACTION = "action";

    public static final int ACTION_GET_ALL_USER_INFO = 1;
    public static final int ACTION_GET_ALL_USER_INFO_NEW_API = 2;

    public UserHandler(Context mPackageContext, Context mSystemContext, byte[] bytes) {
        super(mPackageContext, mSystemContext, bytes);
    }

    @NonNull
    @Override
    public Bundle proxyInvoke(@NonNull Bundle bundle) throws Throwable {
        int action = bundle.getInt(ARG_ACTION, ACTION_GET_ALL_USER_INFO);
        IUserManager userManager = IUserManager.Stub.asInterface(
                ServiceManager.getService(Context.USER_SERVICE));
        if (userManager == null) throw new Exception("userManager is null");
        bundle.clear();
        if (action == ACTION_GET_ALL_USER_INFO) {
            List<UserInfo> userInfoList = userManager.getUsers(true);
            if (userInfoList == null) throw new Exception("Empty user info");
            bundle.putParcelableArrayList("return", new ArrayList<>(userInfoList));
        } else if (action == ACTION_GET_ALL_USER_INFO_NEW_API) {
            // Changed in 10.0.0_r30
            List<UserInfo> userInfoList = userManager.getUsers(true, true, true);
            if (userInfoList == null) throw new Exception("Empty user info");
            bundle.putParcelableArrayList("return", new ArrayList<>(userInfoList));
        }
        return bundle;
    }
}
