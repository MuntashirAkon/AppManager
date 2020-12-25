/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.permission;

import android.content.pm.ParceledListSlice;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.permission.SplitPermissionInfoParcelable;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import java.util.List;

import androidx.annotation.RequiresApi;

/**
 * Interface to communicate directly with the permission manager service.
 * @see PermissionManager
 */
@RequiresApi(Build.VERSION_CODES.R)
interface IPermissionManager extends IInterface {
    String[] getAppOpPermissionPackages(String permName) throws RemoteException;

    ParceledListSlice<PermissionGroupInfo> getAllPermissionGroups(int flags) throws RemoteException;

    PermissionGroupInfo getPermissionGroupInfo(String groupName, int flags) throws RemoteException;

    PermissionInfo getPermissionInfo(String permName, String packageName, int flags) throws RemoteException;

    ParceledListSlice<PermissionInfo> queryPermissionsByGroup(String groupName, int flags) throws RemoteException;

    boolean addPermission(PermissionInfo info, boolean async) throws RemoteException;

    void removePermission(String name) throws RemoteException;

    int getPermissionFlags(String permName, String packageName, int userId) throws RemoteException;

    void updatePermissionFlags(String permName, String packageName, int flagMask,
            int flagValues, boolean checkAdjustPolicyFlagPermission, int userId) throws RemoteException;

    void updatePermissionFlagsForAllApps(int flagMask, int flagValues, int userId) throws RemoteException;

    int checkPermission(String permName, String pkgName, int userId) throws RemoteException;

    int checkUidPermission(String permName, int uid) throws RemoteException;

    int checkDeviceIdentifierAccess(String packageName, String callingFeatureId, String message, int pid, int uid) throws RemoteException;

    void addOnPermissionsChangeListener(IOnPermissionsChangeListener listener) throws RemoteException;

    void removeOnPermissionsChangeListener(IOnPermissionsChangeListener listener) throws RemoteException;

    List<String> getWhitelistedRestrictedPermissions(String packageName, int flags, int userId) throws RemoteException;

    boolean addWhitelistedRestrictedPermission(String packageName, String permName,
            int flags, int userId) throws RemoteException;

    boolean removeWhitelistedRestrictedPermission(String packageName, String permName,
            int flags, int userId) throws RemoteException;

    void grantRuntimePermission(String packageName, String permName, int userId) throws RemoteException;

    void revokeRuntimePermission(String packageName, String permName, int userId, String reason) throws RemoteException;

    void resetRuntimePermissions() throws RemoteException;

    boolean setDefaultBrowser(String packageName, int userId) throws RemoteException;

    String getDefaultBrowser(int userId) throws RemoteException;

    void grantDefaultPermissionsToEnabledCarrierApps(String[] packageNames, int userId) throws RemoteException;

    void grantDefaultPermissionsToEnabledImsServices(String[] packageNames, int userId) throws RemoteException;

    void grantDefaultPermissionsToEnabledTelephonyDataServices(String[] packageNames, int userId) throws RemoteException;

    void revokeDefaultPermissionsFromDisabledTelephonyDataServices(String[] packageNames, int userId) throws RemoteException;

    void grantDefaultPermissionsToActiveLuiApp(String packageName, int userId) throws RemoteException;

    void revokeDefaultPermissionsFromLuiApps(String[] packageNames, int userId) throws RemoteException;

    void setPermissionEnforced(String permName, boolean enforced) throws RemoteException;

    boolean isPermissionEnforced(String permName) throws RemoteException;

    boolean shouldShowRequestPermissionRationale(String permName,
            String packageName, int userId) throws RemoteException;

    boolean isPermissionRevokedByPolicy(String permName, String packageName, int userId) throws RemoteException;

    List<SplitPermissionInfoParcelable> getSplitPermissions() throws RemoteException;

    void startOneTimePermissionSession(String packageName, int userId, long timeout,
            int importanceToResetTimer, int importanceToKeepSessionAlive) throws RemoteException;

    void stopOneTimePermissionSession(String packageName, int userId) throws RemoteException;

    List<String> getAutoRevokeExemptionRequestedPackages(int userId) throws RemoteException;

    List<String> getAutoRevokeExemptionGrantedPackages(int userId) throws RemoteException;

    boolean setAutoRevokeWhitelisted(String packageName, boolean whitelisted, int userId) throws RemoteException;

    boolean isAutoRevokeWhitelisted(String packageName, int userId) throws RemoteException;

    abstract class Stub extends Binder implements IPermissionManager {
        public static IPermissionManager asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }
    }
}