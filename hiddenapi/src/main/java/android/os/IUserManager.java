// SPDX-License-Identifier: Apache-2.0

package android.os;

import android.annotation.UserIdInt;
import android.content.pm.UserInfo;

import androidx.annotation.RequiresApi;

import java.util.List;

import misc.utils.HiddenUtil;

public interface IUserManager extends IInterface {
    UserInfo getPrimaryUser() throws RemoteException;

    List<UserInfo> getUsers(boolean excludeDying) throws RemoteException;

    // Changed in 10.0.0_r30
    @RequiresApi(Build.VERSION_CODES.Q)
    List<UserInfo> getUsers(boolean excludePartial, boolean excludeDying, boolean excludePreCreated)
            throws RemoteException;

    List<UserInfo> getProfiles(@UserIdInt int userId, boolean enabledOnly);

    int getManagedProfileBadge(int userId) throws RemoteException;

    abstract class Stub {
        public static IUserManager asInterface(android.os.IBinder obj) {
            return HiddenUtil.throwUOE(obj);
        }
    }
}
