// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.permission.SplitPermissionInfoParcelable;
import android.graphics.Bitmap;
import android.os.BaseBundle;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.PersistableBundle;
import android.os.RemoteException;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.List;
import java.util.Map;

public interface IPackageManager extends IInterface {
    @RequiresApi(Build.VERSION_CODES.N)
    void checkPackageStartable(String packageName, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    boolean isPackageFrozen(String packageName) throws RemoteException;

    boolean isPackageAvailable(String packageName, int userId) throws RemoteException;

    PackageInfo getPackageInfo(String packageName, int flags, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    PackageInfo getPackageInfoVersioned(VersionedPackage versionedPackage, int flags, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 24 (Android N)
     */
    @Deprecated
    int getPackageUid(String packageName, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.N)
    int getPackageUid(String packageName, int flags, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 23 (Android M)
     */
    @Deprecated
    int[] getPackageGids(String packageName) throws RemoteException;

    /**
     * @deprecated Removed in API 24 (Android N)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    int[] getPackageGids(String packageName, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.N)
    int[] getPackageGids(String packageName, int flags, int userId) throws RemoteException;

    String[] currentToCanonicalPackageNames(String[] names) throws RemoteException;

    String[] canonicalToCurrentPackageNames(String[] names) throws RemoteException;

    /**
     * @deprecated Removed in API 26 (Android O)
     */
    @Deprecated
    PermissionInfo getPermissionInfo(String name, int flags) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.O)
    PermissionInfo getPermissionInfo(String name, String packageName, int flags) throws RemoteException;

    /**
     * @return It used to return {@link List<PermissionInfo>} but from Android N (API 24), it
     * returns {@link ParceledListSlice<PermissionInfo>}.
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    Object queryPermissionsByGroup(String group, int flags) throws RemoteException;

    /**
     * @deprecated Deprecated since API 30 (Android R)
     */
    @Deprecated
    PermissionGroupInfo getPermissionGroupInfo(String name, int flags) throws RemoteException;

    /**
     * @return It used to return {@link List<PermissionGroupInfo>} but from Android N (API 24), it
     * returns {@link ParceledListSlice<PermissionGroupInfo>}.
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    Object getAllPermissionGroups(int flags) throws RemoteException;

    ApplicationInfo getApplicationInfo(String packageName, int flags, int userId) throws RemoteException;

    ActivityInfo getActivityInfo(ComponentName className, int flags, int userId) throws RemoteException;

    boolean activitySupportsIntent(ComponentName className, Intent intent,
                                   String resolvedType) throws RemoteException;

    ActivityInfo getReceiverInfo(ComponentName className, int flags, int userId) throws RemoteException;

    ServiceInfo getServiceInfo(ComponentName className, int flags, int userId) throws RemoteException;

    ProviderInfo getProviderInfo(ComponentName className, int flags, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 23 (Android M)
     */
    @Deprecated
    int checkPermission(String permName, String pkgName) throws RemoteException;

    /**
     * @deprecated Deprecated since API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    int checkPermission(String permName, String pkgName, int userId) throws RemoteException;

    /**
     * @deprecated Deprecated since API 30 (Android R)
     */
    @Deprecated
    int checkUidPermission(String permName, int uid) throws RemoteException;

    /**
     * @deprecated Deprecated since API 30 (Android R)
     */
    @Deprecated
    boolean addPermission(PermissionInfo info) throws RemoteException;

    /**
     * @deprecated Deprecated since API 30 (Android R)
     */
    @Deprecated
    void removePermission(String name) throws RemoteException;

    /**
     * @deprecated Removed in API 23 (Android M)
     */
    @Deprecated
    void grantPermission(String packageName, String permissionName) throws RemoteException;

    /**
     * @deprecated Removed in API 23 (Android M)
     */
    @Deprecated
    void revokePermission(String packageName, String permissionName) throws RemoteException;

    /**
     * @deprecated Deprecated since API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void grantRuntimePermission(String packageName, String permissionName, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void revokeRuntimePermission(String packageName, String permissionName, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void resetRuntimePermissions() throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    int getPermissionFlags(String permissionName, String packageName, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 29 (Android Q)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void updatePermissionFlags(String permissionName, String packageName, int flagMask,
                               int flagValues, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.Q)
    void updatePermissionFlags(String permissionName, String packageName, int flagMask,
                               int flagValues, boolean checkAdjustPolicyFlagPermission, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void updatePermissionFlagsForAllApps(int flagMask, int flagValues, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.Q)
    List<String> getWhitelistedRestrictedPermissions(String packageName, int flags, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.Q)
    boolean addWhitelistedRestrictedPermission(String packageName, String permission, int whitelistFlags, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.Q)
    boolean removeWhitelistedRestrictedPermission(String packageName, String permission, int whitelistFlags, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    boolean shouldShowRequestPermissionRationale(String permissionName, String packageName, int userId) throws RemoteException;

    boolean isProtectedBroadcast(String actionName) throws RemoteException;

    int checkSignatures(String pkg1, String pkg2) throws RemoteException;

    int checkUidSignatures(int uid1, int uid2) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.N)
    List<String> getAllPackages() throws RemoteException;

    String[] getPackagesForUid(int uid) throws RemoteException;

    String getNameForUid(int uid) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    String[] getNamesForUids(int[] uids) throws RemoteException;

    int getUidForSharedUser(String sharedUserName) throws RemoteException;

    int getFlagsForUid(int uid) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    int getPrivateFlagsForUid(int uid) throws RemoteException;

    boolean isUidPrivileged(int uid) throws RemoteException;

    /**
     * @deprecated Deprecated since API 30 (Android R)
     */
    @Deprecated
    String[] getAppOpPermissionPackages(String permissionName) throws RemoteException;

    ResolveInfo resolveIntent(Intent intent, String resolvedType, int flags, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    ResolveInfo findPersistentPreferredActivity(Intent intent, int userId) throws RemoteException;

    boolean canForwardTo(Intent intent, String resolvedType, int sourceUserId, int targetUserId) throws RemoteException;

    /**
     * @return It used to return {@link List<ResolveInfo>} but from Android N (API 24), it
     * returns {@link ParceledListSlice<ResolveInfo>}.
     */
    Object queryIntentActivities(Intent intent, String resolvedType, int flags, int userId) throws RemoteException;

    /**
     * @return It used to return {@link List<ResolveInfo>} but from Android N (API 24), it
     * returns {@link ParceledListSlice<ResolveInfo>}.
     */
    Object queryIntentActivityOptions(ComponentName caller, Intent[] specifics,
                                      String[] specificTypes, Intent intent,
                                      String resolvedType, int flags, int userId) throws RemoteException;

    /**
     * @return It used to return {@link List<ResolveInfo>} but from Android N (API 24), it
     * returns {@link ParceledListSlice<ResolveInfo>}.
     */
    Object queryIntentReceivers(Intent intent, String resolvedType, int flags, int userId) throws RemoteException;

    ResolveInfo resolveService(Intent intent, String resolvedType, int flags, int userId) throws RemoteException;

    /**
     * @return It used to return {@link List<ResolveInfo>} but from Android N (API 24), it
     * returns {@link ParceledListSlice<ResolveInfo>}.
     */
    Object queryIntentServices(Intent intent, String resolvedType, int flags, int userId) throws RemoteException;

    /**
     * @return It used to return {@link List<ResolveInfo>} but from Android N (API 24), it
     * returns {@link ParceledListSlice<ResolveInfo>}.
     */
    Object queryIntentContentProviders(Intent intent, String resolvedType, int flags, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    ParceledListSlice<ResolveInfo> queryContentProviders(String processName, int uid, int flags, String metaDataKey) throws RemoteException;

    ParceledListSlice<PackageInfo> getInstalledPackages(int flags, int userId) throws RemoteException;

    ParceledListSlice<PackageInfo> getPackagesHoldingPermissions(String[] permissions,
                                                                 int flags, int userId) throws RemoteException;

    ParceledListSlice<ApplicationInfo> getInstalledApplications(int flags, int userId) throws RemoteException;

    /**
     * @return It used to return {@link List<ApplicationInfo>} but from Android N (API 24), it
     * returns {@link ParceledListSlice<ApplicationInfo>}.
     */
    Object getPersistentApplications(int flags) throws RemoteException;

    ProviderInfo resolveContentProvider(String name, int flags, int userId) throws RemoteException;

    /**
     * Retrieve sync information for all content providers.
     *
     * @param outNames Filled in with a list of the root names of the content
     *                 providers that can sync.
     * @param outInfo  Filled in with a list of the ProviderInfo for each
     *                 name in 'outNames'.
     */
    void querySyncProviders(List<String> outNames, List<ProviderInfo> outInfo) throws RemoteException;

    /**
     * @return It used to return {@link List<ProviderInfo>} but from Android M (API 23), it returns
     * {@link ParceledListSlice<ProviderInfo>}.
     */
    Object queryContentProviders(String processName, int uid, int flags) throws RemoteException;

    InstrumentationInfo getInstrumentationInfo(ComponentName className, int flags) throws RemoteException;

    /**
     * @return It used to return {@link List<InstrumentationInfo>} but from Android N (API 24), it
     * returns {@link ParceledListSlice<InstrumentationInfo>}.
     */
    Object queryInstrumentation(String targetPackage, int flags) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    void setApplicationCategoryHint(String packageName, int categoryHint, String callerPackageName) throws RemoteException;

    /**
     * @deprecated Removed in API 26 (Android O)
     */
    void deletePackageAsUser(String packageName, IPackageDeleteObserver observer, int userId, int flags) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    void deletePackageAsUser(String packageName, int versionCode, IPackageDeleteObserver observer, int userId, int flags) throws RemoteException;

    /**
     * Delete a package for a specific user.
     *
     * @param packageName The fully qualified name of the package to delete.
     * @param observer    a callback to use to notify when the package deletion in finished.
     * @param userId      the id of the user for whom to delete the package
     * @param flags       - possible values: {@code #DONT_DELETE_DATA}
     * @deprecated Removed in API 26 (Android O)
     */
    @Deprecated
    void deletePackage(String packageName, IPackageDeleteObserver2 observer, int userId, int flags) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    void deletePackageVersioned(VersionedPackage versionedPackage, IPackageDeleteObserver2 observer, int userId, int flags) throws RemoteException;

    /**
     * Delete a package for a specific user.
     *
     * @param versionedPackage The package to delete.
     * @param observer         a callback to use to notify when the package deletion in finished.
     * @param userId           the id of the user for whom to delete the package
     */
    @RequiresApi(Build.VERSION_CODES.R)
    void deleteExistingPackageAsUser(VersionedPackage versionedPackage, IPackageDeleteObserver2 observer, int userId) throws RemoteException;

    String getInstallerPackageName(String packageName) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.R)
    InstallSourceInfo getInstallSourceInfo(String packageName) throws RemoteException;

    /**
     * @deprecated Removed in API 23 (Android M)
     */
    @Deprecated
    void resetPreferredActivities(int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    void resetApplicationPreferences(int userId) throws RemoteException;

    ResolveInfo getLastChosenActivity(Intent intent,
                                      String resolvedType, int flags) throws RemoteException;

    void setLastChosenActivity(Intent intent, String resolvedType, int flags,
                               IntentFilter filter, int match, ComponentName activity) throws RemoteException;

    void addPreferredActivity(IntentFilter filter, int match, ComponentName[] set,
                              ComponentName activity, int userId) throws RemoteException;

    void replacePreferredActivity(IntentFilter filter, int match, ComponentName[] set,
                                  ComponentName activity, int userId) throws RemoteException;

    void clearPackagePreferredActivities(String packageName) throws RemoteException;

    int getPreferredActivities(List<IntentFilter> outFilters, List<ComponentName> outActivities,
                               String packageName) throws RemoteException;

    void addPersistentPreferredActivity(IntentFilter filter, ComponentName activity, int userId) throws RemoteException;

    void clearPackagePersistentPreferredActivities(String packageName, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 23 (Android M)
     */
    @Deprecated
    void addCrossProfileIntentFilter(IntentFilter intentFilter, String ownerPackage,
                                     int ownerUserId, int sourceUserId, int targetUserId, int flags) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    void addCrossProfileIntentFilter(IntentFilter intentFilter, String ownerPackage,
                                     int sourceUserId, int targetUserId, int flags) throws RemoteException;

    /**
     * @deprecated Removed in API 23 (Android M)
     */
    @Deprecated
    void clearCrossProfileIntentFilters(int sourceUserId, String ownerPackage, int ownerUserId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    void clearCrossProfileIntentFilters(int sourceUserId, String ownerPackage) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    String[] setDistractingPackageRestrictionsAsUser(String[] packageNames, int restrictionFlags,
                                                     int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 28 (Android P)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.N)
    String[] setPackagesSuspendedAsUser(String[] packageNames, boolean suspended, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 29 (Android Q)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.P)
    String[] setPackagesSuspendedAsUser(String[] packageNames, boolean suspended,
                                        PersistableBundle appExtras, PersistableBundle launcherExtras,
                                        String dialogMessage, String callingPackage, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    String[] setPackagesSuspendedAsUser(String[] packageNames, boolean suspended,
                                        PersistableBundle appExtras, PersistableBundle launcherExtras,
                                        SuspendDialogInfo dialogInfo, String callingPackage, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.N)
    boolean isPackageSuspendedForUser(String packageName, int userId) throws RemoteException;

    /**
     * @return In Android P (API 28) and Q (API 29), it returned {@link PersistableBundle} but from
     * Android R (API 30) it returns {@link Bundle}.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    BaseBundle getSuspendedPackageAppExtras(String packageName, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    String[] getUnsuspendablePackagesForUser(String[] packageNames, int userId) throws RemoteException;

    /**
     * Backup/restore support - only the system uid may use these.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    byte[] getPreferredActivityBackup(int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    void restorePreferredActivities(byte[] backup, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    byte[] getDefaultAppsBackup(int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    void restoreDefaultApps(byte[] backup, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    byte[] getIntentFilterVerificationBackup(int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    void restoreIntentFilterVerification(byte[] backup, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.N)
    byte[] getPermissionGrantBackup(int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.N)
    void restorePermissionGrants(byte[] backup, int userId) throws RemoteException;

    /**
     * Report the set of 'Home' activity candidates, plus (if any) which of them
     * is the current "always use this one" setting.
     */
    ComponentName getHomeActivities(List<ResolveInfo> outHomeCandidates) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.N)
    void setHomeActivity(ComponentName className, int userId) throws RemoteException;

    /**
     * Overrides the label and icon of the component specified by the component name. The component
     * must belong to the calling app.
     * <p>
     * These changes will be reset on the next boot and whenever the package is updated.
     * <p>
     * Only the app defined as com.android.internal.R.config_overrideComponentUiPackage is allowed
     * to call this.
     *
     * @param componentName     The component name to override the label/icon of.
     * @param nonLocalizedLabel The label to be displayed.
     * @param icon              The icon to be displayed.
     * @param userId            The user id.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    void overrideLabelAndIcon(ComponentName componentName, String nonLocalizedLabel,
                              int icon, int userId) throws RemoteException;

    /**
     * Restores the label and icon of the activity specified by the component name if either has
     * been overridden. The component must belong to the calling app.
     * <p>
     * Only the app defined as com.android.internal.R.config_overrideComponentUiPackage is allowed
     * to call this.
     *
     * @param componentName The component name.
     * @param userId        The user id.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    void restoreLabelAndIcon(ComponentName componentName, int userId) throws RemoteException;

    /**
     * As per {@link android.content.pm.PackageManager#setComponentEnabledSetting}.
     */
    void setComponentEnabledSetting(ComponentName componentName, int newState, int flags, int userId) throws RemoteException;

    /**
     * As per {@link android.content.pm.PackageManager#getComponentEnabledSetting}.
     */
    int getComponentEnabledSetting(ComponentName componentName, int userId) throws RemoteException;

    /**
     * As per {@link android.content.pm.PackageManager#setApplicationEnabledSetting}.
     */
    void setApplicationEnabledSetting(String packageName, int newState, int flags,
                                      int userId, String callingPackage) throws RemoteException;

    /**
     * As per {@link android.content.pm.PackageManager#getApplicationEnabledSetting}.
     */
    int getApplicationEnabledSetting(String packageName, int userId) throws RemoteException;

    /**
     * Logs process start information (including APK hash) to the security log.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    void logAppProcessStartIfNeeded(String processName, int uid, String seinfo, String apkFile,
                                    int pid) throws RemoteException;

    /**
     * As per {@link android.content.pm.PackageManager#flushPackageRestrictionsAsUser}.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    void flushPackageRestrictionsAsUser(int userId) throws RemoteException;

    /**
     * Set whether the given package should be considered stopped, making
     * it not visible to implicit intents that filter out stopped packages.
     */
    void setPackageStoppedState(String packageName, boolean stopped, int userId) throws RemoteException;

    /**
     * Free storage by deleting LRU sorted list of cache files across
     * all applications. If the currently available free storage
     * on the device is greater than or equal to the requested
     * free storage, no cache files are cleared. If the currently
     * available storage on the device is less than the requested
     * free storage, some or all of the cache files across
     * all applications are deleted (based on last accessed time)
     * to increase the free storage space on the device to
     * the requested value. There is no guarantee that clearing all
     * the cache files from all applications will clear up
     * enough storage to achieve the desired value.
     *
     * @param freeStorageSize The number of bytes of storage to be
     *                        freed by the system. Say if freeStorageSize is XX,
     *                        and the current free storage is YY,
     *                        if XX is less than YY, just return. if not free XX-YY number
     *                        of bytes if possible.
     * @param observer        call back used to notify when
     *                        the operation is completed
     * @deprecated Removed in API 23 (Android M)
     */
    @Deprecated
    void freeStorageAndNotify(long freeStorageSize, IPackageDataObserver observer) throws RemoteException;

    /**
     * @deprecated Removed in API 26 (Android O)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void freeStorageAndNotify(String volumeUuid, long freeStorageSize, IPackageDataObserver observer) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    void freeStorageAndNotify(String volumeUuid, long freeStorageSize, int storageFlags,
                              IPackageDataObserver observer) throws RemoteException;

    /**
     * Free storage by deleting LRU sorted list of cache files across
     * all applications. If the currently available free storage
     * on the device is greater than or equal to the requested
     * free storage, no cache files are cleared. If the currently
     * available storage on the device is less than the requested
     * free storage, some or all of the cache files across
     * all applications are deleted (based on last accessed time)
     * to increase the free storage space on the device to
     * the requested value. There is no guarantee that clearing all
     * the cache files from all applications will clear up
     * enough storage to achieve the desired value.
     *
     * @param freeStorageSize The number of bytes of storage to be
     *                        freed by the system. Say if freeStorageSize is XX,
     *                        and the current free storage is YY,
     *                        if XX is less than YY, just return. if not free XX-YY number
     *                        of bytes if possible.
     * @param pi              IntentSender call back used to
     *                        notify when the operation is completed.May be null
     *                        to indicate that no call back is desired.
     * @deprecated Removed in API 23 (Android M)
     */
    @Deprecated
    void freeStorage(long freeStorageSize, IntentSender pi) throws RemoteException;

    /**
     * @deprecated Removed in API 26 (Android O)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void freeStorage(String volumeUuid, long freeStorageSize, IntentSender pi) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    void freeStorage(String volumeUuid, long freeStorageSize, int storageFlags, IntentSender pi) throws RemoteException;

    /**
     * Delete all the cache files in an applications cache directory
     *
     * @param packageName The package name of the application whose cache
     *                    files need to be deleted
     * @param observer    a callback used to notify when the deletion is finished.
     */
    void deleteApplicationCacheFiles(String packageName, IPackageDataObserver observer) throws RemoteException;

    /**
     * Delete all the cache files in an applications cache directory
     *
     * @param packageName The package name of the application whose cache
     *                    files need to be deleted
     * @param userId      the user to delete application cache for
     * @param observer    a callback used to notify when the deletion is finished.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    void deleteApplicationCacheFilesAsUser(String packageName, int userId, IPackageDataObserver observer) throws RemoteException;

    /**
     * Clear the user data directory of an application.
     *
     * @param packageName The package name of the application whose cache
     *                    files need to be deleted
     * @param observer    a callback used to notify when the operation is completed.
     */
    void clearApplicationUserData(String packageName, IPackageDataObserver observer, int userId) throws RemoteException;

    /**
     * Clear the profile data of an application.
     *
     * @param packageName The package name of the application whose profile data
     *                    need to be deleted
     */
    @RequiresApi(Build.VERSION_CODES.N)
    void clearApplicationProfileData(String packageName) throws RemoteException;

    /**
     * Get package statistics including the code, data and cache size for
     * an already installed package
     *
     * @param packageName The package name of the application
     * @param userHandle  Which user the size should be retrieved for
     * @param observer    a callback to use to notify when the asynchronous
     *                    retrieval of information is complete.
     */
    void getPackageSizeInfo(String packageName, int userHandle, IPackageStatsObserver observer) throws RemoteException;

    /**
     * Get a list of shared libraries that are available on the
     * system.
     */
    String[] getSystemSharedLibraryNames() throws RemoteException;

    /**
     * Get a list of features that are available on the
     * system.
     *
     * @return It used to return {@code FeatureInfo[]} but from Android N (API 24), it returns
     * {@link ParceledListSlice<FeatureInfo>}.
     */
    FeatureInfo[] getSystemAvailableFeatures() throws RemoteException;

    boolean hasSystemFeature(String name) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.N)
    boolean hasSystemFeature(String name, int version) throws RemoteException;

    void enterSafeMode() throws RemoteException;

    boolean isSafeMode() throws RemoteException;

    void systemReady() throws RemoteException;

    boolean hasSystemUidErrors() throws RemoteException;

    /**
     * Ask the package manager to perform boot-time dex-opt of all
     * existing packages.
     *
     * @deprecated Removed in API 24 (Android N)
     */
    @Deprecated
    void performBootDexOpt() throws RemoteException;

    /**
     * Ask the package manager to fstrim the disk if needed.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    void performFstrimIfNeeded() throws RemoteException;

    /**
     * Ask the package manager to update packages if needed.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    void updatePackagesIfNeeded() throws RemoteException;

    /**
     * Notify the package manager that a package is going to be used and why.
     * <p>
     * See PackageManager.NOTIFY_PACKAGE_USE_* for reasons.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    void notifyPackageUse(String packageName, int reason) throws RemoteException;

    /**
     * Ask the package manager to perform dex-opt (if needed) on the given
     * package and for the given instruction set if it already hasn't done
     * so.
     * <p>
     * If the supplied instructionSet is null, the package manager will use
     * the packages default instruction set.
     * <p>
     * In most cases, apps are dexopted in advance and this function will
     * be a no-op.
     *
     * @deprecated Removed in API 24 (Android N)
     */
    @Deprecated
    boolean performDexOptIfNeeded(String packageName, String instructionSet) throws RemoteException;

    /**
     * Ask the package manager to perform dex-opt (if needed) on the given
     * package if it already hasn't done so.
     * <p>
     * In most cases, apps are dexopted in advance and this function will
     * be a no-op.
     *
     * @deprecated Removed in API 26 (Android O)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.N)
    boolean performDexOptIfNeeded(String packageName) throws RemoteException;

    /**
     * Notify the package manager that a list of dex files have been loaded.
     *
     * @param loadingPackageName the name of the package who performs the load
     * @param dexPaths           the list of the dex files paths that have been loaded
     * @param loaderIsa          the ISA of the loader process
     * @deprecated Removed in API 27 (Android O MR1)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.O)
    void notifyDexLoad(String loadingPackageName, List<String> dexPaths, String loaderIsa) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    void notifyDexLoad(String loadingPackageName, List<String> classLoadersNames, List<String> classPaths, String loaderIsa) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.R)
    void notifyDexLoad(String loadingPackageName, Map<String, String> classLoaderContextMap, String loaderIsa) throws RemoteException;

    /**
     * Ask the package manager to perform a dex-opt for the given reason. The package
     * manager will map the reason to a compiler filter according to the current system
     * configuration.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    boolean performDexOpt(String packageName, boolean checkProfiles, int compileReason, boolean force) throws RemoteException;

    /**
     * Ask the package manager to perform a dex-opt with the given compiler filter.
     * <p>
     * Note: exposed only for the shell command to allow moving packages explicitly to a
     * definite state.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    boolean performDexOptMode(String packageName, boolean checkProfiles,
                              String targetCompilerFilter, boolean force) throws RemoteException;

    /**
     * Ask the package manager to perform a dex-opt with the given compiler filter on the
     * secondary dex files belonging to the given package.
     * <p>
     * Note: exposed only for the shell command to allow moving packages explicitly to a
     * definite state.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    boolean performDexOptSecondary(String packageName, String targetCompilerFilter, boolean force) throws RemoteException;

    /**
     * Ask the package manager to compile layouts in the given package.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    boolean compileLayouts(String packageName) throws RemoteException;

    /**
     * Ask the package manager to dump profiles associated with a package.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    void dumpProfiles(String packageName) throws RemoteException;

    void forceDexOpt(String packageName) throws RemoteException;

    /**
     * Execute the background dexopt job immediately.
     *
     * @deprecated Removed in API 29 (Android P)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.O)
    boolean runBackgroundDexoptJob() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    boolean runBackgroundDexoptJob(@Nullable List<String> packageNames) throws RemoteException;

    /**
     * Reconcile the information we have about the secondary dex files belonging to
     * {@code packagName} and the actual dex files. For all dex files that were
     * deleted, update the internal records and delete the generated oat files.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    void reconcileSecondaryDexFiles(String packageName) throws RemoteException;

    PackageCleanItem nextPackageToClean(PackageCleanItem lastPackage) throws RemoteException;

    /**
     * @deprecated Removed in API 23 (Android M)
     */
    @Deprecated
    void movePackage(String packageName, IPackageMoveObserver observer, int flags) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    int getMoveStatus(int moveId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    void registerMoveCallback(IPackageMoveObserver callback) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    void unregisterMoveCallback(IPackageMoveObserver callback) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    int movePackage(String packageName, String volumeUuid) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    int movePrimaryStorage(String volumeUuid) throws RemoteException;

    /**
     * @deprecated Deprecated since API 30 (Android R)
     */
    @Deprecated
    boolean addPermissionAsync(PermissionInfo info) throws RemoteException;

    boolean setInstallLocation(int loc) throws RemoteException;

    int getInstallLocation() throws RemoteException;

    /**
     * @deprecated Removed in API 26 (Android O)
     */
    @Deprecated
    int installExistingPackageAsUser(String packageName, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 29 (Android Q)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.O)
    int installExistingPackageAsUser(String packageName, int userId, int installFlags, int installReason) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    int installExistingPackageAsUser(String packageName, int userId, int installFlags,
                                     int installReason, List<String> whiteListedPermissions) throws RemoteException;

    void verifyPendingInstall(int id, int verificationCode) throws RemoteException;

    void extendVerificationTimeout(int id, int verificationCodeAtTimeout, long millisecondsToDelay) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    void verifyIntentFilter(int id, int verificationCode, List<String> failedDomains) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    int getIntentVerificationStatus(String packageName, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    boolean updateIntentVerificationStatus(String packageName, int status, int userId) throws RemoteException;

    /**
     * @return In Android M (API 23), it returned {@link List<IntentFilterVerificationInfo>} but
     * from Android N, it returns {@link ParceledListSlice<IntentFilterVerificationInfo>}.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    Object getIntentFilterVerifications(String packageName) throws RemoteException;

    /**
     * @return In Android M (API 23), it returned {@link List<IntentFilter>} but from Android N, it
     * returns {@link ParceledListSlice<IntentFilter>}.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    Object getAllIntentFilters(String packageName) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    boolean setDefaultBrowserPackageName(String packageName, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    String getDefaultBrowserPackageName(int userId) throws RemoteException; //

    VerifierDeviceIdentity getVerifierDeviceIdentity() throws RemoteException;

    boolean isFirstBoot() throws RemoteException;

    boolean isOnlyCoreApps() throws RemoteException;

    /**
     * @deprecated Removed in API 29 (Android Q)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    boolean isUpgrade() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    boolean isDeviceUpgrading() throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    void setPermissionEnforced(String permission, boolean enforced) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    boolean isPermissionEnforced(String permission) throws RemoteException;

    /**
     * Reflects current DeviceStorageMonitorService state
     */
    boolean isStorageLow() throws RemoteException;

    boolean setApplicationHiddenSettingAsUser(String packageName, boolean hidden, int userId) throws RemoteException;

    boolean getApplicationHiddenSettingAsUser(String packageName, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    void setSystemAppHiddenUntilInstalled(String packageName, boolean hidden) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    boolean setSystemAppInstallState(String packageName, boolean installed, int userId) throws RemoteException;

    IPackageInstaller getPackageInstaller() throws RemoteException;

    boolean setBlockUninstallForUser(String packageName, boolean blockUninstall, int userId) throws RemoteException;

    boolean getBlockUninstallForUser(String packageName, int userId) throws RemoteException;

    KeySet getKeySetByAlias(String packageName, String alias) throws RemoteException;

    KeySet getSigningKeySet(String packageName) throws RemoteException;

    boolean isPackageSignedByKeySet(String packageName, KeySet ks) throws RemoteException;

    boolean isPackageSignedByKeySetExactly(String packageName, KeySet ks) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void addOnPermissionsChangeListener(IOnPermissionsChangeListener listener) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void removeOnPermissionsChangeListener(IOnPermissionsChangeListener listener) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void grantDefaultPermissionsToEnabledCarrierApps(String[] packageNames, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.O)
    void grantDefaultPermissionsToEnabledImsServices(String[] packageNames, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.P)
    void grantDefaultPermissionsToEnabledTelephonyDataServices(String[] packageNames, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.P)
    void revokeDefaultPermissionsFromDisabledTelephonyDataServices(String[] packageNames, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.P)
    void grantDefaultPermissionsToActiveLuiApp(String packageName, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.P)
    void revokeDefaultPermissionsFromLuiApps(String[] packageNames, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    boolean isPermissionRevokedByPolicy(String permission, String packageName, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    String getPermissionControllerPackageName() throws RemoteException;

    /**
     * @deprecated Replaced by {@link #getInstantApps(int)} in API 26 (Android O)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.N)
    ParceledListSlice getEphemeralApplications(int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    ParceledListSlice getInstantApps(int userId) throws RemoteException;

    /**
     * @deprecated Deprecated in API 26 (Android O)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.N)
    byte[] getEphemeralApplicationCookie(String packageName, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    byte[] getInstantAppCookie(String packageName, int userId) throws RemoteException;

    /**
     * @deprecated Deprecated in API 26 (Android O)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.N)
    boolean setEphemeralApplicationCookie(String packageName, byte[] cookie, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    boolean setInstantAppCookie(String packageName, byte[] cookie, int userId) throws RemoteException;

    /**
     * @deprecated Deprecated in API 26 (Android O)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.N)
    Bitmap getEphemeralApplicationIcon(String packageName, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    Bitmap getInstantAppIcon(String packageName, int userId) throws RemoteException;

    /**
     * @deprecated Deprecated in API 26 (Android O)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.N)
    boolean isEphemeralApplication(String packageName, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    boolean isInstantApp(String packageName, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.N)
    boolean setRequiredForSystemUser(String packageName, boolean systemUserApp) throws RemoteException;

    /**
     * Sets whether or not an update is available. Ostensibly for instant apps
     * to force exteranl resolution.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    void setUpdateAvailable(String packageName, boolean updateAvailable) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.N)
    String getServicesSystemSharedLibraryPackageName() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.N)
    String getSharedSystemSharedLibraryPackageName() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    ChangedPackages getChangedPackages(int sequenceNumber, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.N)
    boolean isPackageDeviceAdminOnAnyUser(String packageName) throws RemoteException;

    /**
     * @deprecated Removed in API 29 (Android P)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.N)
    List<String> getPreviousCodePaths(String packageName) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    int getInstallReason(String packageName, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    ParceledListSlice getSharedLibraries(String packageName, int flags, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    ParceledListSlice getDeclaredSharedLibraries(String packageName, int flags, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    boolean canRequestPackageInstalls(String packageName, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    void deletePreloadsFileCache() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    ComponentName getInstantAppResolverComponent() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    ComponentName getInstantAppResolverSettingsComponent() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    ComponentName getInstantAppInstallerComponent() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    String getInstantAppAndroidId(String packageName, int userId) throws RemoteException;

//    @RequiresApi(Build.VERSION_CODES.P)
//    IArtManager getArtManager() throws RemoteException;  // TODO(25/12/20)

    @RequiresApi(Build.VERSION_CODES.P)
    void setHarmfulAppWarning(String packageName, CharSequence warning, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    CharSequence getHarmfulAppWarning(String packageName, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    boolean hasSigningCertificate(String packageName, byte[] signingCertificate, int flags) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    boolean hasUidSigningCertificate(int uid, byte[] signingCertificate, int flags) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.R)
    String getDefaultTextClassifierPackageName() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    String getSystemTextClassifierPackageName() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    String getAttentionServicePackageName() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    String getWellbeingPackageName() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    String getAppPredictionServicePackageName() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    String getSystemCaptionsServicePackageName() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.R)
    String getSetupWizardPackageName() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    String getIncidentReportApproverPackageName() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.R)
    String getContentCaptureServicePackageName() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    boolean isPackageStateProtected(String packageName, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    void sendDeviceCustomizationReadyBroadcast() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    List<ModuleInfo> getInstalledModules(int flags) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    ModuleInfo getModuleInfo(String packageName, int flags) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    int getRuntimePermissionsVersion(int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    void setRuntimePermissionsVersion(int version, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    void notifyPackagesReplacedReceived(String[] packages) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    List<SplitPermissionInfoParcelable> getSplitPermissions();

    abstract class Stub extends Binder implements IPackageManager {
        public static IPackageManager asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }
    }
}
