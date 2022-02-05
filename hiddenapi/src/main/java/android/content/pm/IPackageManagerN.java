// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.pm;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.RefineAs;

@RequiresApi(Build.VERSION_CODES.N)
@RefineAs(IPackageManager.class)
public interface IPackageManagerN {
    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    ParceledListSlice<PermissionInfo> queryPermissionsByGroup(String group, int flags) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    ParceledListSlice<PermissionGroupInfo> getAllPermissionGroups(int flags) throws RemoteException;

    ParceledListSlice<ResolveInfo> queryIntentActivities(Intent intent, String resolvedType, int flags, int userId)
            throws RemoteException;

    ParceledListSlice<ResolveInfo> queryIntentActivityOptions(ComponentName caller, Intent[] specifics,
                                                              String[] specificTypes, Intent intent,
                                                              String resolvedType, int flags, int userId) throws RemoteException;

    ParceledListSlice<ResolveInfo> queryIntentReceivers(Intent intent, String resolvedType, int flags, int userId)
            throws RemoteException;

    ParceledListSlice<ResolveInfo> queryIntentServices(Intent intent, String resolvedType, int flags, int userId)
            throws RemoteException;

    ParceledListSlice<ResolveInfo> queryIntentContentProviders(Intent intent, String resolvedType, int flags,
                                                               int userId) throws RemoteException;

    ParceledListSlice<ApplicationInfo> getPersistentApplications(int flags) throws RemoteException;

    ParceledListSlice<ProviderInfo> queryContentProviders(String processName, int uid, int flags) throws RemoteException;

    ParceledListSlice<InstrumentationInfo> queryInstrumentation(String targetPackage, int flags) throws RemoteException;

    ParceledListSlice<IntentFilter> getAllIntentFilters(String packageName) throws RemoteException;
}
