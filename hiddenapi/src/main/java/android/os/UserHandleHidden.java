// SPDX-License-Identifier: GPL-3.0-or-later

package android.os;

import android.annotation.AppIdInt;
import android.annotation.UserIdInt;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

@RefineAs(UserHandle.class)
public class UserHandleHidden {
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
     * Checks to see if the user id is the same for the two uids, i.e., they belong to the same
     * user.
     */
    public static boolean isSameUser(int uid1, int uid2) {
        return HiddenUtil.throwUOE(uid1, uid2);
    }

    /**
     * Checks to see if both uids are referring to the same app id, ignoring the user id part of the
     * uids.
     *
     * @param uid1 uid to compare
     * @param uid2 other uid to compare
     * @return whether the appId is the same for both uids
     */
    public static boolean isSameApp(int uid1, int uid2) {
        return HiddenUtil.throwUOE(uid1, uid2);
    }

    /**
     * Whether a UID is an "isolated" UID.
     */
    public static boolean isIsolated(int uid) {
        return HiddenUtil.throwUOE(uid);
    }

    /**
     * Whether a UID belongs to a regular app. *Note* "Not a regular app" does not mean
     * "it's system", because of isolated UIDs. Use {@code #isCore} for that.
     */
    public static boolean isApp(int uid) {
        return HiddenUtil.throwUOE(uid);
    }

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
    @AppIdInt
    public static int getAppId(int uid) {
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

    /**
     * Generate a text representation of the uid, breaking out its individual
     * components -- user, app, isolated, etc.
     */
    public static void formatUid(StringBuilder sb, int uid) {
        HiddenUtil.throwUOE(sb, uid);
    }

    public int getIdentifier() {
        return HiddenUtil.throwUOE();
    }
}