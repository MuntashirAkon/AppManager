/*
 * Copyright (c) 2021 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.users;

import android.content.Context;
import android.content.pm.UserInfo;
import android.os.Build;
import android.os.IUserManager;
import android.os.RemoteException;
import android.os.UserHandle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;

import java.util.ArrayList;
import java.util.List;

public final class Users {
    public static final String TAG = "Users";

    @UserIdInt
    public static final int USER_ALL = -1;
    @UserIdInt
    public static final int USER_NULL = -10000;

    public static final boolean MU_ENABLED;
    public static final int PER_USER_RANGE;

    public static List<UserInfo> userInfoList;

    static {
        boolean muEnabled = true;
        int perUserRange = 100000;
        try {
            // using reflection to get id of calling user since method getCallingUserId of UserHandle is hidden
            // https://github.com/android/platform_frameworks_base/blob/master/core/java/android/os/UserHandle.java#L123
            //noinspection JavaReflectionMemberAccess
            muEnabled = UserHandle.class.getField("MU_ENABLED").getBoolean(null);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            Log.e(TAG, "Could not get UserHandle#MU_ENABLED", e);
        }
        try {
            //noinspection JavaReflectionMemberAccess
            perUserRange = UserHandle.class.getField("PER_USER_RANGE").getInt(null);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            Log.e(TAG, "Could not get UserHandle#PER_USER_RANGE", e);
        }
        MU_ENABLED = muEnabled;
        PER_USER_RANGE = perUserRange;
    }

    @WorkerThread
    @Nullable
    public static List<UserInfo> getUsers() {
        if (userInfoList == null) {
            try {
                IUserManager userManager = IUserManager.Stub.asInterface(ProxyBinder.getService(Context.USER_SERVICE));
                try {
                    return userInfoList = userManager.getUsers(true);
                } catch (NoSuchMethodError e) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        return userInfoList = userManager.getUsers(true, true, true);
                    } else throw new RemoteException(e.getMessage());
                }
            } catch (RemoteException | SecurityException e) {
                Log.e(TAG, "Could not get list of users", e);
            }
        }
        return userInfoList;
    }

    @WorkerThread
    @NonNull
    @UserIdInt
    public static int[] getUsersHandles() {
        getUsers();
        if (userInfoList != null) {
            List<Integer> users = new ArrayList<>();
            for (UserInfo userInfo : userInfoList) {
                try {
                    users.add(userInfo.id);
                } catch (Exception ignore) {
                }
            }
            return ArrayUtils.convertToIntArray(users);
        } else {
            return new int[]{getCurrentUserHandle()};
        }
    }

    @UserIdInt
    private static Integer currentUserHandle = null;

    @UserIdInt
    public static int getCurrentUserHandle() {
        if (currentUserHandle == null) {
            if (MU_ENABLED) currentUserHandle = android.os.Binder.getCallingUid() / PER_USER_RANGE;
            else currentUserHandle = 0;
            // Another way
//            try {
//                @SuppressWarnings("JavaReflectionMemberAccess")
//                Method myUserId = UserHandle.class.getMethod("myUserId");
//                currentUserHandle = (int) myUserId.invoke(null);
//            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
//                e.printStackTrace();
//            }
        }
        return currentUserHandle;
    }

    @UserIdInt
    public static int getUserHandle(int uid) {
        if (MU_ENABLED && uid >= (PER_USER_RANGE / 10)) return uid / PER_USER_RANGE;
        return uid;
    }

    public static int getAppId(int uid) {
        return uid % PER_USER_RANGE;
    }
}
