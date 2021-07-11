// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IUserManager;
import android.os.RemoteException;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;

public final class Users {
    public static final String TAG = "Users";

    @UserIdInt
    public static final int USER_ALL = -1;
    @UserIdInt
    public static final int USER_NULL = -10000;
    @UserIdInt
    public static final int USER_SYSTEM = 0;

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
    public static List<UserInfo> getAllUsers() {
        if (userInfoList == null) {
            try {
                IUserManager userManager = IUserManager.Stub.asInterface(ProxyBinder.getService(Context.USER_SERVICE));
                try {
                    userInfoList = userManager.getUsers(true);
                } catch (NoSuchMethodError e) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        userInfoList = userManager.getUsers(true, true, true);
                    } else throw new SecurityException(e);
                }
            } catch (RemoteException | SecurityException e) {
                Log.e(TAG, "Could not get list of users", e);
            }
        }
        return userInfoList;
    }

    @WorkerThread
    @Nullable
    public static List<UserInfo> getUsers() {
        getAllUsers();
        if (userInfoList == null) return null;
        int[] selectedUserIds = AppPref.getSelectedUsers();
        List<UserInfo> users = new ArrayList<>();
        for (UserInfo userInfo : userInfoList) {
            if (selectedUserIds == null || ArrayUtils.contains(selectedUserIds, userInfo.id)) {
                users.add(userInfo);
            }
        }
        return users;
    }

    @WorkerThread
    @NonNull
    @UserIdInt
    public static int[] getUsersIds() {
        getAllUsers();
        if (userInfoList == null) {
            return new int[]{myUserId()};
        }
        int[] selectedUserIds = AppPref.getSelectedUsers();
        List<Integer> users = new ArrayList<>();
        for (UserInfo userInfo : userInfoList) {
            if (selectedUserIds == null || ArrayUtils.contains(selectedUserIds, userInfo.id)) {
                users.add(userInfo.id);
            }
        }
        return ArrayUtils.convertToIntArray(users);
    }

    @UserIdInt
    public static int myUserId() {
        return MU_ENABLED ? Binder.getCallingUid() / PER_USER_RANGE : 0;
    }

    @UserIdInt
    public static int getUserId(int uid) {
        return MU_ENABLED ? uid / PER_USER_RANGE : USER_SYSTEM;
    }

    public static int getAppId(int uid) {
        return uid % PER_USER_RANGE;
    }
}
