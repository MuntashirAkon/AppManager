// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users;

import android.annotation.UserIdInt;
import android.content.Context;
import android.os.Build;
import android.os.IUserManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;

public final class Users {
    public static final String TAG = "Users";

    private static final List<UserInfo> sUserInfoList = new ArrayList<>();

    @NonNull
    public static List<UserInfo> getAllUsers() {
        if (sUserInfoList.isEmpty()) {
            try {
                List<android.content.pm.UserInfo> userInfoList;
                IUserManager userManager = IUserManager.Stub.asInterface(ProxyBinder.getService(Context.USER_SERVICE));
                try {
                    userInfoList = userManager.getUsers(true);
                } catch (NoSuchMethodError e) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        userInfoList = userManager.getUsers(true, true, true);
                    } else throw new SecurityException(e);
                }
                if (userInfoList == null) {
                    throw new SecurityException();
                }
                for (android.content.pm.UserInfo userInfo : userInfoList) {
                    sUserInfoList.add(new UserInfo(userInfo));
                }
            } catch (RemoteException | SecurityException e) {
                // Try other means in no-root mode
                Log.d(TAG, "Could not fetch list of users using privileged mode, falling back to no-root check");
                Context ctx = AppManager.getContext();
                UserManager manager = (UserManager) ctx.getSystemService(Context.USER_SERVICE);
                for (UserHandle userHandle : manager.getUserProfiles()) {
                    sUserInfoList.add(new UserInfo(userHandle, null));
                }
            }
        }
        return sUserInfoList;
    }

    @NonNull
    public static List<UserInfo> getUsers() {
        getAllUsers();
        int[] selectedUserIds = Prefs.Misc.getSelectedUsers();
        List<UserInfo> users = new ArrayList<>();
        for (UserInfo userInfo : sUserInfoList) {
            if (selectedUserIds == null || ArrayUtils.contains(selectedUserIds, userInfo.id)) {
                users.add(userInfo);
            }
        }
        return users;
    }

    @NonNull
    @UserIdInt
    public static int[] getUsersIds() {
        getAllUsers();
        int[] selectedUserIds = Prefs.Misc.getSelectedUsers();
        List<Integer> users = new ArrayList<>();
        for (UserInfo userInfo : sUserInfoList) {
            if (selectedUserIds == null || ArrayUtils.contains(selectedUserIds, userInfo.id)) {
                users.add(userInfo.id);
            }
        }
        return ArrayUtils.convertToIntArray(users);
    }

    @Nullable
    public static UserHandle getUserHandle(@UserIdInt int userId) {
        getAllUsers();
        for (UserInfo userInfo : sUserInfoList) {
            if (userInfo.id == userId) {
                return userInfo.userHandle;
            }
        }
        return null;
    }
}
