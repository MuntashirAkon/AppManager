// SPDX-License-Identifier: Apache-2.0

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

import androidx.annotation.RequiresApi;

import java.util.List;

/**
 * Interface to communicate directly with the permission manager service.
 *
 * @see PermissionManager
 */
@RequiresApi(Build.VERSION_CODES.R)
public interface IPermissionManager extends IInterface {
    /**
     * @deprecated Removed in Android 12 (S), use {@link android.content.pm.IPackageManager#getAppOpPermissionPackages(String)} instead.
     */
    @Deprecated
    String[] getAppOpPermissionPackages(String permName) throws RemoteException;

    ParceledListSlice<PermissionGroupInfo> getAllPermissionGroups(int flags) throws RemoteException;

    PermissionGroupInfo getPermissionGroupInfo(String groupName, int flags) throws RemoteException;

    PermissionInfo getPermissionInfo(String permName, String packageName, int flags) throws RemoteException;

    ParceledListSlice<PermissionInfo> queryPermissionsByGroup(String groupName, int flags) throws RemoteException;

    boolean addPermission(PermissionInfo info, boolean async) throws RemoteException;

    void removePermission(String name) throws RemoteException;

    /**
     * First two parameters are permuted since Android 12 (S)
     *
     * @deprecated Replaced in Android 14 r29 (Upside Down Cake) by {@link #getPermissionFlags(String, String, int, int)}
     */
    @Deprecated
    int getPermissionFlags(String permName, String packageName, int userId) throws RemoteException;

    /**
     * Introduced in Android 14.0.0_r29
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    int getPermissionFlags(String packageName, String permName, int deviceId, int userId) throws RemoteException;

    /**
     * First two parameters are permuted since Android 12 (S)
     *
     * @deprecated Replaced in Android 14 r29 (Upside Down Cake) by {@link #updatePermissionFlags(String, String, int, int, boolean, int, int)}
     */
    @Deprecated
    void updatePermissionFlags(String permName, String packageName, int flagMask,
                               int flagValues, boolean checkAdjustPolicyFlagPermission, int userId) throws RemoteException;

    /**
     * Introduced in Android 14.0.0_r29
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    void updatePermissionFlags(String packageName, String permName, int flagMask,
                               int flagValues, boolean checkAdjustPolicyFlagPermission, int deviceId, int userId) throws RemoteException;

    void updatePermissionFlagsForAllApps(int flagMask, int flagValues, int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android 12 (S), use {@link android.content.pm.IPackageManager#checkPermission(String, String, int)} instead.
     */
    @Deprecated
    int checkPermission(String permName, String pkgName, int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android 12 (S), use {@link android.content.pm.IPackageManager#checkUidPermission(String, int)} instead.
     */
    @Deprecated
    int checkUidPermission(String permName, int uid) throws RemoteException;

    /**
     * @deprecated Removed in Android 12 (S)
     */
    @Deprecated
    int checkDeviceIdentifierAccess(String packageName, String callingFeatureId, String message, int pid, int uid) throws RemoteException;

    void addOnPermissionsChangeListener(IOnPermissionsChangeListener listener) throws RemoteException;

    void removeOnPermissionsChangeListener(IOnPermissionsChangeListener listener) throws RemoteException;

    /**
     * @deprecated Removed in Android 12 (S)
     */
    @Deprecated
    List<String> getWhitelistedRestrictedPermissions(String packageName, int flags, int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android 12 (S)
     */
    @Deprecated
    boolean addWhitelistedRestrictedPermission(String packageName, String permName,
                                               int flags, int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android 12 (S)
     */
    @Deprecated
    boolean removeWhitelistedRestrictedPermission(String packageName, String permName,
                                                  int flags, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.S)
    List<String> getAllowlistedRestrictedPermissions(String packageName, int flags, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.S)
    boolean addAllowlistedRestrictedPermission(String packageName, String permissionName, int flags, int userId)
            throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.S)
    boolean removeAllowlistedRestrictedPermission(String packageName, String permissionName, int flags, int userId)
            throws RemoteException;

    /**
     * @deprecated Replaced in Android 14 r29 (Upside Down Cake) by {@link #grantRuntimePermission(String, String, int, int)}
     */
    @Deprecated
    void grantRuntimePermission(String packageName, String permName, int userId) throws RemoteException;

    /**
     * Introduced in Android 14.0.0_r29
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    void grantRuntimePermission(String packageName, String permName, int deviceId, int userId) throws RemoteException;

    /**
     * @deprecated Replaced in Android 14 r29 (Upside Down Cake) by {@link #revokeRuntimePermission(String, String, int, int, String)}
     */
    @Deprecated
    void revokeRuntimePermission(String packageName, String permName, int userId, String reason) throws RemoteException;

    /**
     * Introduced in Android 14.0.0_r29
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    void revokeRuntimePermission(String packageName, String permName, int deviceId,
                                 int userId, String reason) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    void revokePostNotificationPermissionWithoutKillForTest(String packageName, int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android 12 (S)
     */
    @Deprecated
    void resetRuntimePermissions() throws RemoteException;

    /**
     * @deprecated Removed in Android 12 (S)
     */
    @Deprecated
    boolean setDefaultBrowser(String packageName, int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android 12 (S)
     */
    @Deprecated
    String getDefaultBrowser(int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android 12 (S)
     */
    @Deprecated
    void grantDefaultPermissionsToEnabledCarrierApps(String[] packageNames, int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android 12 (S)
     */
    @Deprecated
    void grantDefaultPermissionsToEnabledImsServices(String[] packageNames, int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android 12 (S)
     */
    @Deprecated
    void grantDefaultPermissionsToEnabledTelephonyDataServices(String[] packageNames, int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android 12 (S)
     */
    @Deprecated
    void revokeDefaultPermissionsFromDisabledTelephonyDataServices(String[] packageNames, int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android 12 (S)
     */
    @Deprecated
    void grantDefaultPermissionsToActiveLuiApp(String packageName, int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android 12 (S)
     */
    @Deprecated
    void revokeDefaultPermissionsFromLuiApps(String[] packageNames, int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android 12 (S)
     */
    @Deprecated
    void setPermissionEnforced(String permName, boolean enforced) throws RemoteException;

    /**
     * @deprecated Removed in Android 12 (S)
     */
    @Deprecated
    boolean isPermissionEnforced(String permName) throws RemoteException;

    /**
     * First two parameters are permuted since Android 12 (S)
     *
     * @deprecated Replaced in Android 14 r29 (Upside Down Cake) by {@link #shouldShowRequestPermissionRationale(String, String, int, int)}
     */
    @Deprecated
    boolean shouldShowRequestPermissionRationale(String permName, String packageName, int userId)
            throws RemoteException;

    /**
     * Introduced in Android 14.0.0_r29
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    boolean shouldShowRequestPermissionRationale(String packageName, String permName, int deviceId, int userId)
            throws RemoteException;

    /**
     * First two parameters are permuted since Android 12 (S)
     *
     * @deprecated Replaced in Android 14 r29 (Upside Down Cake) by {@link #isPermissionRevokedByPolicy(String, String, int, int)}
     */
    @Deprecated
    boolean isPermissionRevokedByPolicy(String permName, String packageName, int userId) throws RemoteException;

    /**
     * Introduced in Android 14.0.0_r29
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    boolean isPermissionRevokedByPolicy(String packageName, String permName, int deviceId,
                                        int userId) throws RemoteException;

    List<SplitPermissionInfoParcelable> getSplitPermissions() throws RemoteException;

    /**
     * @deprecated Replaced in Android 13 (Tiramisu) by {@link #startOneTimePermissionSession(String, int, long, long, int, int)}
     */
    @Deprecated
    void startOneTimePermissionSession(String packageName, int userId, long timeout,
                                       int importanceToResetTimer, int importanceToKeepSessionAlive) throws RemoteException;

    /**
     * @deprecated Replaced in Android 14 (Upside Down Cake) by {@link #startOneTimePermissionSession(String, int, long, long)}
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    void startOneTimePermissionSession(String packageName, int userId, long timeout,
                                       long revokeAfterKilledDelay, int importanceToResetTimer,
                                       int importanceToKeepSessionAlive) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    void startOneTimePermissionSession(String packageName, int userId, long timeout,
                                       long revokeAfterKilledDelay) throws RemoteException;

    void stopOneTimePermissionSession(String packageName, int userId) throws RemoteException;

    List<String> getAutoRevokeExemptionRequestedPackages(int userId) throws RemoteException;

    List<String> getAutoRevokeExemptionGrantedPackages(int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android 12 (S), replaced by {@link #setAutoRevokeExempted(String, boolean, int)}.
     */
    @Deprecated
    boolean setAutoRevokeWhitelisted(String packageName, boolean whitelisted, int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android 12 (S), replaced by {@link #isAutoRevokeExempted(String, int)}.
     */
    @Deprecated
    boolean isAutoRevokeWhitelisted(String packageName, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.S)
    boolean setAutoRevokeExempted(String packageName, boolean exempted, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.S)
    boolean isAutoRevokeExempted(String packageName, int userId) throws RemoteException;

    /**
     * Introduced in Android 14.0.0_r29
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    int checkPermission(String packageName, String permissionName, int deviceId, int userId) throws RemoteException;

    /**
     * Introduced in Android 14.0.0_r29
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    int checkUidPermission(int uid, String permissionName, int deviceId) throws RemoteException;

    abstract class Stub extends Binder implements IPermissionManager {
        public static IPermissionManager asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }
    }
}