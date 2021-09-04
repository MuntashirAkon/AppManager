// SPDX-License-Identifier: GPL-3.0-or-later

package android.os;

public class UserHandleHidden {
    public static final int PER_USER_RANGE = 100000;
    public static final int USER_ALL = -1;
    public static final int USER_NULL = -10000;
    public static final int USER_SYSTEM = 0;
    public static final boolean MU_ENABLED = true;

    public static int getUserId(int uid) {
        if (MU_ENABLED) {
            return uid / PER_USER_RANGE;
        } else {
            return USER_SYSTEM;
        }
    }

    public static int getUid(int userId, int appId) {
        if (MU_ENABLED) {
            return userId * PER_USER_RANGE + (appId % PER_USER_RANGE);
        } else {
            return appId;
        }
    }

    public static int getAppId(int uid) {
        return uid % PER_USER_RANGE;
    }

    public static int myUserId() {
        return 0;
    }
}