// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.pm;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.RemoteException;
import android.permission.IPermissionManager;

import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.RefineAs;

@RequiresApi(Build.VERSION_CODES.N)
@RefineAs(IPackageManager.class)
public interface IPackageManagerN {
    /**
     * @deprecated Replaced in API 30 (Android R) by {@link IPermissionManager#queryPermissionsByGroup(String, int)}
     */
    @Deprecated
    ParceledListSlice<PermissionInfo> queryPermissionsByGroup(String group, int flags) throws RemoteException;

    /**
     * @deprecated Replaced in API 30 (Android R) by {@link IPermissionManager#getAllPermissionGroups(int)}
     */
    @Deprecated
    ParceledListSlice<PermissionGroupInfo> getAllPermissionGroups(int flags) throws RemoteException;

    /**
     * @deprecated Replaced in API 33 (Android T) by {@link #queryIntentActivities(Intent, String, long, int)}
     */
    @Deprecated
    ParceledListSlice<ResolveInfo> queryIntentActivities(Intent intent, String resolvedType, int flags, int userId)
            throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    ParceledListSlice<ResolveInfo> queryIntentActivities(Intent intent, String resolvedType, long flags, int userId)
            throws RemoteException;

    /**
     * @deprecated Replaced in API 33 (Android T) by {@link #queryIntentActivityOptions(ComponentName, Intent[], String[], Intent, String, long, int)}
     */
    @Deprecated
    ParceledListSlice<ResolveInfo> queryIntentActivityOptions(ComponentName caller, Intent[] specifics,
                                                              String[] specificTypes, Intent intent,
                                                              String resolvedType, int flags, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    ParceledListSlice<ResolveInfo> queryIntentActivityOptions(ComponentName caller, Intent[] specifics,
                                                              String[] specificTypes, Intent intent,
                                                              String resolvedType, long flags, int userId) throws RemoteException;

    /**
     * @deprecated Replaced in API 33 (Android T) by {@link #queryIntentReceivers(Intent, String, long, int)}
     */
    @Deprecated
    ParceledListSlice<ResolveInfo> queryIntentReceivers(Intent intent, String resolvedType, int flags, int userId)
            throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    ParceledListSlice<ResolveInfo> queryIntentReceivers(Intent intent, String resolvedType, long flags, int userId)
            throws RemoteException;

    /**
     * @deprecated Replaced in API 33 (Android T) by {@link #queryIntentServices(Intent, String, long, int)}
     */
    @Deprecated
    ParceledListSlice<ResolveInfo> queryIntentServices(Intent intent, String resolvedType, int flags, int userId)
            throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    ParceledListSlice<ResolveInfo> queryIntentServices(Intent intent, String resolvedType, long flags, int userId)
            throws RemoteException;

    /**
     * @deprecated Replaced in API 33 (Android T) by {@link #queryIntentContentProviders(Intent, String, long, int)}
     */
    @Deprecated
    ParceledListSlice<ResolveInfo> queryIntentContentProviders(Intent intent, String resolvedType, int flags,
                                                               int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    ParceledListSlice<ResolveInfo> queryIntentContentProviders(Intent intent, String resolvedType, long flags,
                                                               int userId) throws RemoteException;

    ParceledListSlice<ApplicationInfo> getPersistentApplications(int flags) throws RemoteException;

    /**
     * @deprecated Replaced in API 26 (Android O) by {@link #queryContentProviders(String, int, long, String)}
     */
    @Deprecated
    ParceledListSlice<ProviderInfo> queryContentProviders(String processName, int uid, int flags) throws RemoteException;

    /**
     * @deprecated Replaced in API 33 (Android T) by {@link #queryContentProviders(String, int, long, String)}
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.O)
    ParceledListSlice<ProviderInfo> queryContentProviders(String processName, int uid, int flags, String metaDataKey)
            throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    ParceledListSlice<ProviderInfo> queryContentProviders(String processName, int uid, long flags, String metaDataKey)
            throws RemoteException;

    ParceledListSlice<FeatureInfo> getSystemAvailableFeatures() throws RemoteException;

    ParceledListSlice<IntentFilter> getAllIntentFilters(String packageName) throws RemoteException;
}
