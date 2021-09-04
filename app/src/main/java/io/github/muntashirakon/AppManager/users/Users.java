// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.Build;
import android.os.IUserManager;
import android.os.RemoteException;
import android.os.UserHandleHidden;

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

    public static List<UserInfo> userInfoList;

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
            return new int[]{UserHandleHidden.myUserId()};
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
}
