// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users;

import android.annotation.UserIdInt;
import android.content.Context;
import android.os.Build;
import android.os.IUserManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserHandleHidden;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;

public final class Users {
    public static final String TAG = "Users";

    private static final List<UserInfo> sUserInfoList = new ArrayList<>();
    private static boolean sUnprivilegedMode = false;

    @NonNull
    public static List<UserInfo> getAllUsers() {
        if (sUserInfoList.isEmpty() || sUnprivilegedMode) {
            IUserManager userManager = IUserManager.Stub.asInterface(ProxyBinder.getService(Context.USER_SERVICE));
            if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS)
                    || SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.CREATE_USERS)) {
                if (sUnprivilegedMode) {
                    // User info were previously fetched in unprivileged mode. We need to fetch them again.
                    sUnprivilegedMode = false;
                    sUserInfoList.clear();
                }
                List<android.content.pm.UserInfo> userInfoList = null;
                try {
                    userInfoList = userManager.getUsers(true);
                } catch (RemoteException | NoSuchMethodError e) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        userInfoList = ExUtils.exceptionAsNull(() -> userManager.getUsers(true, true, true));
                    }
                }
                if (userInfoList != null) {
                    for (android.content.pm.UserInfo userInfo : userInfoList) {
                        sUserInfoList.add(new UserInfo(userInfo));
                    }
                }
            }
            if (sUserInfoList.isEmpty()) {
                sUnprivilegedMode = true;
                // The above didn't succeed, try no-root mode
                Log.d(TAG, "Missing required permission: MANAGE_USERS or CREATE_USERS (7+). Falling back to unprivileged mode.");
                List<android.content.pm.UserInfo> userInfoList = userManager.getProfiles(
                        UserHandleHidden.getUserId(getSelfOrRemoteUid()), false);
                for (android.content.pm.UserInfo userInfo : userInfoList) {
                    sUserInfoList.add(new UserInfo(userInfo));
                }
            }
        }
        return sUserInfoList;
    }

    @NonNull
    @UserIdInt
    public static int[] getAllUserIds() {
        getAllUsers();
        List<Integer> users = new ArrayList<>();
        for (UserInfo userInfo : sUserInfoList) {
            users.add(userInfo.id);
        }
        return ArrayUtils.convertToIntArray(users);
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

    @IntRange(from = 0)
    public static int getSelfOrRemoteUid() {
        try {
            return LocalServices.getAmService().getUid();
        } catch (RemoteException e) {
            return Process.myUid();
        }
    }
}
