// SPDX-License-Identifier: GPL-3.0-or-later

package android.os;

import android.annotation.AppIdInt;
import android.annotation.UserIdInt;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

@RefineAs(UserHandle.class)
public class UserHandleHidden implements Parcelable {
    /**
     * A user id to indicate all users on the device
     */
    @UserIdInt
    public static final int USER_ALL = -1;

    /**
     * An undefined user id
     */
    @UserIdInt
    public static final int USER_NULL = -10000;

    /**
     * Returns the user id for a given uid.
     */
    @UserIdInt
    public static int getUserId(int uid) {
        return HiddenUtil.throwUOE(uid);
    }

    /**
     * Returns the uid that is composed from the userId and the appId.
     */
    public static int getUid(@UserIdInt int userId, @AppIdInt int appId) {
        return HiddenUtil.throwUOE(userId, appId);
    }

    /**
     * Returns the app id (or base uid) for a given uid, stripping out the user id from it.
     */
    public static @AppIdInt
    int getAppId(int uid) {
        return HiddenUtil.throwUOE(uid);
    }

    /**
     * Returns the user id of the current process
     *
     * @return user id of the current process
     */
    @UserIdInt
    public static int myUserId() {
        return HiddenUtil.throwUOE();
    }
}