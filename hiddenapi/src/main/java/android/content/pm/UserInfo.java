// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;

import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

/**
 * Per-user information.
 */
public class UserInfo implements Parcelable {
    /*
     * *************************** NOTE ***************************
     * These flag values CAN NOT CHANGE because they are written
     * directly to storage.
     */

    /**
     * Primary user. Only one user can have this flag set. It identifies the first human user
     * on a device.
     */
    public static final int FLAG_PRIMARY = 0x00000001;

    /**
     * User with administrative privileges. Such a user can create and
     * delete users.
     */
    public static final int FLAG_ADMIN = 0x00000002;

    /**
     * Indicates a guest user that may be transient.
     */
    public static final int FLAG_GUEST = 0x00000004;

    /**
     * Indicates the user has restrictions in privileges, in addition to those for normal users.
     * Exact meaning TBD. For instance, maybe they can't install apps or administer WiFi access pts.
     */
    public static final int FLAG_RESTRICTED = 0x00000008;

    /**
     * Indicates that this user has gone through its first-time initialization.
     */
    public static final int FLAG_INITIALIZED = 0x00000010;

    /**
     * Indicates that this user is a profile of another user, for example holding a users
     * corporate data.
     */
    public static final int FLAG_MANAGED_PROFILE = 0x00000020;

    /**
     * Indicates that this user is disabled.
     *
     * <p>Note: If an ephemeral user is disabled, it shouldn't be later re-enabled. Ephemeral users
     * are disabled as their removal is in progress to indicate that they shouldn't be re-entered.
     */
    public static final int FLAG_DISABLED = 0x00000040;

    @RequiresApi(Build.VERSION_CODES.N)
    public static final int FLAG_QUIET_MODE = 0x00000080;

    /**
     * Indicates that this user is ephemeral. I.e. the user will be removed after leaving
     * the foreground.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    public static final int FLAG_EPHEMERAL = 0x00000100;

    /**
     * User is for demo purposes only and can be removed at any time.
     */
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    public static final int FLAG_DEMO = 0x00000200;

    public static final int NO_PROFILE_GROUP_ID = -1;

    public int id;
    public int serialNumber;
    public String name;
    public String iconPath;
    public int flags;
    public long creationTime;
    public long lastLoggedInTime;
    @RequiresApi(Build.VERSION_CODES.N)
    public String lastLoggedInFingerprint;
    /**
     * If this user is a parent user, it would be its own user id.
     * If this user is a child user, it would be its parent user id.
     * Otherwise, it would be {@link #NO_PROFILE_GROUP_ID}.
     */
    public int profileGroupId;
    @RequiresApi(Build.VERSION_CODES.N)
    public int restrictedProfileParentId;
    /**
     * Which profile badge color/label to use.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    public int profileBadge;
    /**
     * User is only partially created.
     */
    public boolean partial;
    public boolean guestToRemove;

    public boolean isPrimary() {
        return HiddenUtil.throwUOE();
    }

    public boolean isAdmin() {
        return HiddenUtil.throwUOE();
    }

    public boolean isGuest() {
        return HiddenUtil.throwUOE();
    }

    public boolean isRestricted() {
        return HiddenUtil.throwUOE();
    }

    public boolean isManagedProfile() {
        return HiddenUtil.throwUOE();
    }

    public boolean isEnabled() {
        return HiddenUtil.throwUOE();
    }

    @RequiresApi(Build.VERSION_CODES.N)
    public boolean isQuietModeEnabled() {
        return HiddenUtil.throwUOE();
    }

    @RequiresApi(Build.VERSION_CODES.N)
    public boolean isEphemeral() {
        return HiddenUtil.throwUOE();
    }

    @RequiresApi(Build.VERSION_CODES.N)
    public boolean isInitialized() {
        return HiddenUtil.throwUOE();
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    public boolean isDemo() {
        return HiddenUtil.throwUOE();
    }

    /**
     * @return true if this user can be switched to.
     **/
    public boolean supportsSwitchTo() {
        return HiddenUtil.throwUOE();
    }

    public UserHandle getUserHandle() {
        return HiddenUtil.throwUOE();
    }

    @Override
    public int describeContents() {
        return HiddenUtil.throwUOE();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        HiddenUtil.throwUOE(dest, flags);
    }

    public static final Creator<UserInfo> CREATOR = new Creator<UserInfo>() {
        @Override
        public UserInfo createFromParcel(Parcel in) {
            return HiddenUtil.throwUOE(in);
        }

        @Override
        public UserInfo[] newArray(int size) {
            return HiddenUtil.throwUOE(size);
        }
    };
}
