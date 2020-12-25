/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package android.content.pm;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.os.BaseBundle;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.PersistableBundle;
import android.os.RemoteException;

import java.util.List;
import java.util.Map;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

public interface IPackageManager extends IInterface {
    @RequiresApi(Build.VERSION_CODES.N)
    void checkPackageStartable(String packageName, int userId);

    @RequiresApi(Build.VERSION_CODES.M)
    boolean isPackageFrozen(String packageName);

    boolean isPackageAvailable(String packageName, int userId);

    PackageInfo getPackageInfo(String packageName, int flags, int userId);

    @RequiresApi(Build.VERSION_CODES.O)
    PackageInfo getPackageInfoVersioned(VersionedPackage versionedPackage, int flags, int userId);

    /**
     * @deprecated Removed in API 24 (Android N)
     */
    @Deprecated
    int getPackageUid(String packageName, int userId);

    @RequiresApi(Build.VERSION_CODES.N)
    int getPackageUid(String packageName, int flags, int userId);

    /**
     * @deprecated Removed in API 23 (Android M)
     */
    @Deprecated
    int[] getPackageGids(String packageName);

    /**
     * @deprecated Removed in API 24 (Android N)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    int[] getPackageGids(String packageName, int userId);

    @RequiresApi(Build.VERSION_CODES.N)
    int[] getPackageGids(String packageName, int flags, int userId);

    String[] currentToCanonicalPackageNames(String[] names);

    String[] canonicalToCurrentPackageNames(String[] names);

    /**
     * @deprecated Removed in API 26 (Android O)
     */
    @Deprecated
    PermissionInfo getPermissionInfo(String name, int flags);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.O)
    PermissionInfo getPermissionInfo(String name, String packageName, int flags);

    /**
     * @return It used to return {@link List<PermissionInfo>} but from Android N (API 24), it
     * returns {@link ParceledListSlice<PermissionInfo>}.
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    Object queryPermissionsByGroup(String group, int flags);

    /**
     * @deprecated Deprecated since API 30 (Android R)
     */
    @Deprecated
    PermissionGroupInfo getPermissionGroupInfo(String name, int flags);

    /**
     * @return It used to return {@link List<PermissionGroupInfo>} but from Android N (API 24), it
     * returns {@link ParceledListSlice<PermissionGroupInfo>}.
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    Object getAllPermissionGroups(int flags);

    ApplicationInfo getApplicationInfo(String packageName, int flags, int userId);

    ActivityInfo getActivityInfo(ComponentName className, int flags, int userId);

    boolean activitySupportsIntent(ComponentName className, Intent intent,
                                   String resolvedType);

    ActivityInfo getReceiverInfo(ComponentName className, int flags, int userId);

    ServiceInfo getServiceInfo(ComponentName className, int flags, int userId);

    ProviderInfo getProviderInfo(ComponentName className, int flags, int userId);

    /**
     * @deprecated Removed in API 23 (Android M)
     */
    @Deprecated
    int checkPermission(String permName, String pkgName);

    /**
     * @deprecated Deprecated since API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    int checkPermission(String permName, String pkgName, int userId);

    /**
     * @deprecated Deprecated since API 30 (Android R)
     */
    @Deprecated
    int checkUidPermission(String permName, int uid);

    /**
     * @deprecated Deprecated since API 30 (Android R)
     */
    @Deprecated
    boolean addPermission(PermissionInfo info);

    /**
     * @deprecated Deprecated since API 30 (Android R)
     */
    @Deprecated
    void removePermission(String name);

    /**
     * @deprecated Removed in API 23 (Android M)
     */
    @Deprecated
    void grantPermission(String packageName, String permissionName);

    /**
     * @deprecated Removed in API 23 (Android M)
     */
    @Deprecated
    void revokePermission(String packageName, String permissionName);

    /**
     * @deprecated Deprecated since API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void grantRuntimePermission(String packageName, String permissionName, int userId);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void revokeRuntimePermission(String packageName, String permissionName, int userId);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void resetRuntimePermissions();

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    int getPermissionFlags(String permissionName, String packageName, int userId);

    /**
     * @deprecated Removed in API 29 (Android Q)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void updatePermissionFlags(String permissionName, String packageName, int flagMask,
                               int flagValues, int userId);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.Q)
    void updatePermissionFlags(String permissionName, String packageName, int flagMask,
                               int flagValues, boolean checkAdjustPolicyFlagPermission, int userId);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void updatePermissionFlagsForAllApps(int flagMask, int flagValues, int userId);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.Q)
    List<String> getWhitelistedRestrictedPermissions(String packageName, int flags, int userId);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.Q)
    boolean addWhitelistedRestrictedPermission(String packageName, String permission, int whitelistFlags, int userId);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.Q)
    boolean removeWhitelistedRestrictedPermission(String packageName, String permission, int whitelistFlags, int userId);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    boolean shouldShowRequestPermissionRationale(String permissionName, String packageName, int userId);

    boolean isProtectedBroadcast(String actionName);

    int checkSignatures(String pkg1, String pkg2);

    int checkUidSignatures(int uid1, int uid2);

    @RequiresApi(Build.VERSION_CODES.N)
    List<String> getAllPackages();

    String[] getPackagesForUid(int uid);

    String getNameForUid(int uid);

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    String[] getNamesForUids(int[] uids);

    int getUidForSharedUser(String sharedUserName);

    int getFlagsForUid(int uid);

    @RequiresApi(Build.VERSION_CODES.M)
    int getPrivateFlagsForUid(int uid);

    boolean isUidPrivileged(int uid);

    /**
     * @deprecated Deprecated since API 30 (Android R)
     */
    @Deprecated
    String[] getAppOpPermissionPackages(String permissionName);

    ResolveInfo resolveIntent(Intent intent, String resolvedType, int flags, int userId);

    @RequiresApi(Build.VERSION_CODES.O)
    ResolveInfo findPersistentPreferredActivity(Intent intent, int userId);

    boolean canForwardTo(Intent intent, String resolvedType, int sourceUserId, int targetUserId);

    /**
     * @return It used to return {@link List<ResolveInfo>} but from Android N (API 24), it
     * returns {@link ParceledListSlice<ResolveInfo>}.
     */
    Object queryIntentActivities(Intent intent, String resolvedType, int flags, int userId);

    /**
     * @return It used to return {@link List<ResolveInfo>} but from Android N (API 24), it
     * returns {@link ParceledListSlice<ResolveInfo>}.
     */
    Object queryIntentActivityOptions(ComponentName caller, Intent[] specifics,
                                      String[] specificTypes, Intent intent,
                                      String resolvedType, int flags, int userId);

    /**
     * @return It used to return {@link List<ResolveInfo>} but from Android N (API 24), it
     * returns {@link ParceledListSlice<ResolveInfo>}.
     */
    Object queryIntentReceivers(Intent intent, String resolvedType, int flags, int userId);

    ResolveInfo resolveService(Intent intent, String resolvedType, int flags, int userId);

    /**
     * @return It used to return {@link List<ResolveInfo>} but from Android N (API 24), it
     * returns {@link ParceledListSlice<ResolveInfo>}.
     */
    Object queryIntentServices(Intent intent, String resolvedType, int flags, int userId);

    /**
     * @return It used to return {@link List<ResolveInfo>} but from Android N (API 24), it
     * returns {@link ParceledListSlice<ResolveInfo>}.
     */
    Object queryIntentContentProviders(Intent intent, String resolvedType, int flags, int userId);

    @RequiresApi(Build.VERSION_CODES.O)
    ParceledListSlice<ResolveInfo> queryContentProviders(String processName, int uid, int flags, String metaDataKey);

    ParceledListSlice<PackageInfo> getInstalledPackages(int flags, int userId);

    ParceledListSlice<PackageInfo> getPackagesHoldingPermissions(String[] permissions,
                                                                 int flags, int userId);

    ParceledListSlice<ApplicationInfo> getInstalledApplications(int flags, int userId);

    /**
     * @return It used to return {@link List<ApplicationInfo>} but from Android N (API 24), it
     * returns {@link ParceledListSlice<ApplicationInfo>}.
     */
    Object getPersistentApplications(int flags);

    ProviderInfo resolveContentProvider(String name, int flags, int userId);

    /**
     * Retrieve sync information for all content providers.
     *
     * @param outNames Filled in with a list of the root names of the content
     *                 providers that can sync.
     * @param outInfo  Filled in with a list of the ProviderInfo for each
     *                 name in 'outNames'.
     */
    void querySyncProviders(List<String> outNames, List<ProviderInfo> outInfo);

    /**
     * @return It used to return {@link List<ProviderInfo>} but from Android M (API 23), it returns
     * {@link ParceledListSlice<ProviderInfo>}.
     */
    Object queryContentProviders(String processName, int uid, int flags);

    InstrumentationInfo getInstrumentationInfo(ComponentName className, int flags);

    /**
     * @return It used to return {@link List<InstrumentationInfo>} but from Android N (API 24), it
     * returns {@link ParceledListSlice<InstrumentationInfo>}.
     */
    Object queryInstrumentation(String targetPackage, int flags);

    @RequiresApi(Build.VERSION_CODES.O)
    void setApplicationCategoryHint(String packageName, int categoryHint, String callerPackageName);

    /**
     * @deprecated Removed in API 26 (Android O)
     */
    void deletePackageAsUser(String packageName, IPackageDeleteObserver observer, int userId, int flags);

    @RequiresApi(Build.VERSION_CODES.O)
    void deletePackageAsUser(String packageName, int versionCode, IPackageDeleteObserver observer, int userId, int flags);

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
    void deletePackage(String packageName, IPackageDeleteObserver2 observer, int userId, int flags);

    @RequiresApi(Build.VERSION_CODES.O)
    void deletePackageVersioned(VersionedPackage versionedPackage, IPackageDeleteObserver2 observer, int userId, int flags);

    /**
     * Delete a package for a specific user.
     *
     * @param versionedPackage The package to delete.
     * @param observer         a callback to use to notify when the package deletion in finished.
     * @param userId           the id of the user for whom to delete the package
     */
    @RequiresApi(Build.VERSION_CODES.R)
    void deleteExistingPackageAsUser(VersionedPackage versionedPackage, IPackageDeleteObserver2 observer, int userId);

    String getInstallerPackageName(String packageName);

    @RequiresApi(Build.VERSION_CODES.R)
    InstallSourceInfo getInstallSourceInfo(String packageName);

    /**
     * @deprecated Removed in API 23 (Android M)
     */
    @Deprecated
    void resetPreferredActivities(int userId);

    @RequiresApi(Build.VERSION_CODES.M)
    void resetApplicationPreferences(int userId);

    ResolveInfo getLastChosenActivity(Intent intent,
                                      String resolvedType, int flags);

    void setLastChosenActivity(Intent intent, String resolvedType, int flags,
                               IntentFilter filter, int match, ComponentName activity);

    void addPreferredActivity(IntentFilter filter, int match, ComponentName[] set,
                              ComponentName activity, int userId);

    void replacePreferredActivity(IntentFilter filter, int match, ComponentName[] set,
                                  ComponentName activity, int userId);

    void clearPackagePreferredActivities(String packageName);

    int getPreferredActivities(List<IntentFilter> outFilters, List<ComponentName> outActivities,
                               String packageName);

    void addPersistentPreferredActivity(IntentFilter filter, ComponentName activity, int userId);

    void clearPackagePersistentPreferredActivities(String packageName, int userId);

    /**
     * @deprecated Removed in API 23 (Android M)
     */
    @Deprecated
    void addCrossProfileIntentFilter(IntentFilter intentFilter, String ownerPackage,
                                     int ownerUserId, int sourceUserId, int targetUserId, int flags);

    @RequiresApi(Build.VERSION_CODES.M)
    void addCrossProfileIntentFilter(IntentFilter intentFilter, String ownerPackage,
                                     int sourceUserId, int targetUserId, int flags);

    /**
     * @deprecated Removed in API 23 (Android M)
     */
    @Deprecated
    void clearCrossProfileIntentFilters(int sourceUserId, String ownerPackage, int ownerUserId);

    @RequiresApi(Build.VERSION_CODES.M)
    void clearCrossProfileIntentFilters(int sourceUserId, String ownerPackage);

    @RequiresApi(Build.VERSION_CODES.Q)
    String[] setDistractingPackageRestrictionsAsUser(String[] packageNames, int restrictionFlags,
                                                     int userId);

    /**
     * @deprecated Removed in API 28 (Android P)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.N)
    String[] setPackagesSuspendedAsUser(String[] packageNames, boolean suspended, int userId);

    /**
     * @deprecated Removed in API 29 (Android Q)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.P)
    String[] setPackagesSuspendedAsUser(String[] packageNames, boolean suspended,
                                        PersistableBundle appExtras, PersistableBundle launcherExtras,
                                        String dialogMessage, String callingPackage, int userId);

    @RequiresApi(Build.VERSION_CODES.Q)
    String[] setPackagesSuspendedAsUser(String[] packageNames, boolean suspended,
                                        PersistableBundle appExtras, PersistableBundle launcherExtras,
                                        SuspendDialogInfo dialogInfo, String callingPackage, int userId);

    @RequiresApi(Build.VERSION_CODES.N)
    boolean isPackageSuspendedForUser(String packageName, int userId);

    /**
     * @return In Android P (API 28) and Q (API 29), it returned {@link PersistableBundle} but from
     * Android R (API 30) it returns {@link Bundle}.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    BaseBundle getSuspendedPackageAppExtras(String packageName, int userId);

    @RequiresApi(Build.VERSION_CODES.Q)
    String[] getUnsuspendablePackagesForUser(String[] packageNames, int userId);

    /**
     * Backup/restore support - only the system uid may use these.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    byte[] getPreferredActivityBackup(int userId);

    @RequiresApi(Build.VERSION_CODES.M)
    void restorePreferredActivities(byte[] backup, int userId);

    @RequiresApi(Build.VERSION_CODES.M)
    byte[] getDefaultAppsBackup(int userId);

    @RequiresApi(Build.VERSION_CODES.M)
    void restoreDefaultApps(byte[] backup, int userId);

    @RequiresApi(Build.VERSION_CODES.M)
    byte[] getIntentFilterVerificationBackup(int userId);

    @RequiresApi(Build.VERSION_CODES.M)
    void restoreIntentFilterVerification(byte[] backup, int userId);

    @RequiresApi(Build.VERSION_CODES.N)
    byte[] getPermissionGrantBackup(int userId);

    @RequiresApi(Build.VERSION_CODES.N)
    void restorePermissionGrants(byte[] backup, int userId);

    /**
     * Report the set of 'Home' activity candidates, plus (if any) which of them
     * is the current "always use this one" setting.
     */
    ComponentName getHomeActivities(List<ResolveInfo> outHomeCandidates);

    @RequiresApi(Build.VERSION_CODES.N)
    void setHomeActivity(ComponentName className, int userId);

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
                              int icon, int userId);

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
    void restoreLabelAndIcon(ComponentName componentName, int userId);

    /**
     * As per {@link android.content.pm.PackageManager#setComponentEnabledSetting}.
     */
    void setComponentEnabledSetting(ComponentName componentName, int newState, int flags, int userId);

    /**
     * As per {@link android.content.pm.PackageManager#getComponentEnabledSetting}.
     */
    int getComponentEnabledSetting(ComponentName componentName, int userId);

    /**
     * As per {@link android.content.pm.PackageManager#setApplicationEnabledSetting}.
     */
    void setApplicationEnabledSetting(String packageName, int newState, int flags,
                                      int userId, String callingPackage);

    /**
     * As per {@link android.content.pm.PackageManager#getApplicationEnabledSetting}.
     */
    int getApplicationEnabledSetting(String packageName, int userId);

    /**
     * Logs process start information (including APK hash) to the security log.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    void logAppProcessStartIfNeeded(String processName, int uid, String seinfo, String apkFile,
                                    int pid);

    /**
     * As per {@link android.content.pm.PackageManager#flushPackageRestrictionsAsUser}.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    void flushPackageRestrictionsAsUser(int userId);

    /**
     * Set whether the given package should be considered stopped, making
     * it not visible to implicit intents that filter out stopped packages.
     */
    void setPackageStoppedState(String packageName, boolean stopped, int userId);

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
    void freeStorageAndNotify(long freeStorageSize, IPackageDataObserver observer);

    /**
     * @deprecated Removed in API 26 (Android O)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void freeStorageAndNotify(String volumeUuid, long freeStorageSize, IPackageDataObserver observer);

    @RequiresApi(Build.VERSION_CODES.O)
    void freeStorageAndNotify(String volumeUuid, long freeStorageSize, int storageFlags,
                              IPackageDataObserver observer);

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
    void freeStorage(long freeStorageSize, IntentSender pi);

    /**
     * @deprecated Removed in API 26 (Android O)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void freeStorage(String volumeUuid, long freeStorageSize, IntentSender pi);

    @RequiresApi(Build.VERSION_CODES.O)
    void freeStorage(String volumeUuid, long freeStorageSize, int storageFlags, IntentSender pi);

    /**
     * Delete all the cache files in an applications cache directory
     *
     * @param packageName The package name of the application whose cache
     *                    files need to be deleted
     * @param observer    a callback used to notify when the deletion is finished.
     */
    void deleteApplicationCacheFiles(String packageName, IPackageDataObserver observer);

    /**
     * Delete all the cache files in an applications cache directory
     *
     * @param packageName The package name of the application whose cache
     *                    files need to be deleted
     * @param userId      the user to delete application cache for
     * @param observer    a callback used to notify when the deletion is finished.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    void deleteApplicationCacheFilesAsUser(String packageName, int userId, IPackageDataObserver observer);

    /**
     * Clear the user data directory of an application.
     *
     * @param packageName The package name of the application whose cache
     *                    files need to be deleted
     * @param observer    a callback used to notify when the operation is completed.
     */
    void clearApplicationUserData(String packageName, IPackageDataObserver observer, int userId);

    /**
     * Clear the profile data of an application.
     *
     * @param packageName The package name of the application whose profile data
     *                    need to be deleted
     */
    @RequiresApi(Build.VERSION_CODES.N)
    void clearApplicationProfileData(String packageName);

    /**
     * Get package statistics including the code, data and cache size for
     * an already installed package
     *
     * @param packageName The package name of the application
     * @param userHandle  Which user the size should be retrieved for
     * @param observer    a callback to use to notify when the asynchronous
     *                    retrieval of information is complete.
     */
    void getPackageSizeInfo(String packageName, int userHandle, IPackageStatsObserver observer);

    /**
     * Get a list of shared libraries that are available on the
     * system.
     */
    String[] getSystemSharedLibraryNames();

    /**
     * Get a list of features that are available on the
     * system.
     *
     * @return It used to return {@code FeatureInfo[]} but from Android N (API 24), it returns
     * {@link ParceledListSlice<FeatureInfo>}.
     */
    FeatureInfo[] getSystemAvailableFeatures();

    boolean hasSystemFeature(String name);

    @RequiresApi(Build.VERSION_CODES.N)
    boolean hasSystemFeature(String name, int version);

    void enterSafeMode();

    boolean isSafeMode();

    void systemReady();

    boolean hasSystemUidErrors();

    /**
     * Ask the package manager to perform boot-time dex-opt of all
     * existing packages.
     *
     * @deprecated Removed in API 24 (Android N)
     */
    @Deprecated
    void performBootDexOpt();

    /**
     * Ask the package manager to fstrim the disk if needed.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    void performFstrimIfNeeded();

    /**
     * Ask the package manager to update packages if needed.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    void updatePackagesIfNeeded();

    /**
     * Notify the package manager that a package is going to be used and why.
     * <p>
     * See PackageManager.NOTIFY_PACKAGE_USE_* for reasons.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    void notifyPackageUse(String packageName, int reason);

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
    boolean performDexOptIfNeeded(String packageName, String instructionSet);

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
    boolean performDexOptIfNeeded(String packageName);

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
    void notifyDexLoad(String loadingPackageName, List<String> dexPaths, String loaderIsa);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    void notifyDexLoad(String loadingPackageName, List<String> classLoadersNames, List<String> classPaths, String loaderIsa);

    @RequiresApi(Build.VERSION_CODES.R)
    void notifyDexLoad(String loadingPackageName, Map<String, String> classLoaderContextMap, String loaderIsa);

    /**
     * Ask the package manager to perform a dex-opt for the given reason. The package
     * manager will map the reason to a compiler filter according to the current system
     * configuration.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    boolean performDexOpt(String packageName, boolean checkProfiles, int compileReason, boolean force);

    /**
     * Ask the package manager to perform a dex-opt with the given compiler filter.
     * <p>
     * Note: exposed only for the shell command to allow moving packages explicitly to a
     * definite state.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    boolean performDexOptMode(String packageName, boolean checkProfiles,
                              String targetCompilerFilter, boolean force);

    /**
     * Ask the package manager to perform a dex-opt with the given compiler filter on the
     * secondary dex files belonging to the given package.
     * <p>
     * Note: exposed only for the shell command to allow moving packages explicitly to a
     * definite state.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    boolean performDexOptSecondary(String packageName, String targetCompilerFilter, boolean force);

    /**
     * Ask the package manager to compile layouts in the given package.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    boolean compileLayouts(String packageName);

    /**
     * Ask the package manager to dump profiles associated with a package.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    void dumpProfiles(String packageName);

    void forceDexOpt(String packageName);

    /**
     * Execute the background dexopt job immediately.
     *
     * @deprecated Removed in API 29 (Android P)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.O)
    boolean runBackgroundDexoptJob();

    @RequiresApi(Build.VERSION_CODES.P)
    boolean runBackgroundDexoptJob(@Nullable List<String> packageNames);

    /**
     * Reconcile the information we have about the secondary dex files belonging to
     * {@code packagName} and the actual dex files. For all dex files that were
     * deleted, update the internal records and delete the generated oat files.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    void reconcileSecondaryDexFiles(String packageName);

    PackageCleanItem nextPackageToClean(PackageCleanItem lastPackage);

    /**
     * @deprecated Removed in API 23 (Android M)
     */
    @Deprecated
    void movePackage(String packageName, IPackageMoveObserver observer, int flags);

    @RequiresApi(Build.VERSION_CODES.M)
    int getMoveStatus(int moveId);

    @RequiresApi(Build.VERSION_CODES.M)
    void registerMoveCallback(IPackageMoveObserver callback);

    @RequiresApi(Build.VERSION_CODES.M)
    void unregisterMoveCallback(IPackageMoveObserver callback);

    @RequiresApi(Build.VERSION_CODES.M)
    int movePackage(String packageName, String volumeUuid);

    @RequiresApi(Build.VERSION_CODES.M)
    int movePrimaryStorage(String volumeUuid);

    /**
     * @deprecated Deprecated since API 30 (Android R)
     */
    @Deprecated
    boolean addPermissionAsync(PermissionInfo info);

    boolean setInstallLocation(int loc);

    int getInstallLocation();

    /**
     * @deprecated Removed in API 26 (Android O)
     */
    @Deprecated
    int installExistingPackageAsUser(String packageName, int userId);

    /**
     * @deprecated Removed in API 29 (Android Q)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.O)
    int installExistingPackageAsUser(String packageName, int userId, int installFlags, int installReason);

    @RequiresApi(Build.VERSION_CODES.Q)
    int installExistingPackageAsUser(String packageName, int userId, int installFlags,
                                     int installReason, List<String> whiteListedPermissions);

    void verifyPendingInstall(int id, int verificationCode);

    void extendVerificationTimeout(int id, int verificationCodeAtTimeout, long millisecondsToDelay);

    @RequiresApi(Build.VERSION_CODES.M)
    void verifyIntentFilter(int id, int verificationCode, List<String> failedDomains);

    @RequiresApi(Build.VERSION_CODES.M)
    int getIntentVerificationStatus(String packageName, int userId);

    @RequiresApi(Build.VERSION_CODES.M)
    boolean updateIntentVerificationStatus(String packageName, int status, int userId);

    /**
     * @return In Android M (API 23), it returned {@link List<IntentFilterVerificationInfo>} but
     * from Android N, it returns {@link ParceledListSlice<IntentFilterVerificationInfo>}.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    Object getIntentFilterVerifications(String packageName);

    /**
     * @return In Android M (API 23), it returned {@link List<IntentFilter>} but from Android N, it
     * returns {@link ParceledListSlice<IntentFilter>}.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    Object getAllIntentFilters(String packageName);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    boolean setDefaultBrowserPackageName(String packageName, int userId);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    String getDefaultBrowserPackageName(int userId); //

    VerifierDeviceIdentity getVerifierDeviceIdentity();

    boolean isFirstBoot();

    boolean isOnlyCoreApps();

    /**
     * @deprecated Removed in API 29 (Android Q)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    boolean isUpgrade();

    @RequiresApi(Build.VERSION_CODES.Q)
    boolean isDeviceUpgrading();

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    void setPermissionEnforced(String permission, boolean enforced);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    boolean isPermissionEnforced(String permission);

    /**
     * Reflects current DeviceStorageMonitorService state
     */
    boolean isStorageLow();

    boolean setApplicationHiddenSettingAsUser(String packageName, boolean hidden, int userId);

    boolean getApplicationHiddenSettingAsUser(String packageName, int userId);

    @RequiresApi(Build.VERSION_CODES.Q)
    void setSystemAppHiddenUntilInstalled(String packageName, boolean hidden);

    @RequiresApi(Build.VERSION_CODES.Q)
    boolean setSystemAppInstallState(String packageName, boolean installed, int userId);

    IPackageInstaller getPackageInstaller();

    boolean setBlockUninstallForUser(String packageName, boolean blockUninstall, int userId);

    boolean getBlockUninstallForUser(String packageName, int userId);

    KeySet getKeySetByAlias(String packageName, String alias);

    KeySet getSigningKeySet(String packageName);

    boolean isPackageSignedByKeySet(String packageName, KeySet ks);

    boolean isPackageSignedByKeySetExactly(String packageName, KeySet ks);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void addOnPermissionsChangeListener(IOnPermissionsChangeListener listener);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void removeOnPermissionsChangeListener(IOnPermissionsChangeListener listener);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void grantDefaultPermissionsToEnabledCarrierApps(String[] packageNames, int userId);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.O)
    void grantDefaultPermissionsToEnabledImsServices(String[] packageNames, int userId);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.P)
    void grantDefaultPermissionsToEnabledTelephonyDataServices(String[] packageNames, int userId);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.P)
    void revokeDefaultPermissionsFromDisabledTelephonyDataServices(String[] packageNames, int userId);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.P)
    void grantDefaultPermissionsToActiveLuiApp(String packageName, int userId);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.P)
    void revokeDefaultPermissionsFromLuiApps(String[] packageNames, int userId);

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    boolean isPermissionRevokedByPolicy(String permission, String packageName, int userId);

    @RequiresApi(Build.VERSION_CODES.M)
    String getPermissionControllerPackageName();

    /**
     * @deprecated Replaced by {@link #getInstantApps(int)} in API 26 (Android O)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.N)
    ParceledListSlice getEphemeralApplications(int userId);

    @RequiresApi(Build.VERSION_CODES.O)
    ParceledListSlice getInstantApps(int userId);

    /**
     * @deprecated Deprecated in API 26 (Android O)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.N)
    byte[] getEphemeralApplicationCookie(String packageName, int userId);

    @RequiresApi(Build.VERSION_CODES.O)
    byte[] getInstantAppCookie(String packageName, int userId);

    /**
     * @deprecated Deprecated in API 26 (Android O)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.N)
    boolean setEphemeralApplicationCookie(String packageName, byte[] cookie, int userId);

    @RequiresApi(Build.VERSION_CODES.O)
    boolean setInstantAppCookie(String packageName, byte[] cookie, int userId);

    /**
     * @deprecated Deprecated in API 26 (Android O)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.N)
    Bitmap getEphemeralApplicationIcon(String packageName, int userId);

    @RequiresApi(Build.VERSION_CODES.O)
    Bitmap getInstantAppIcon(String packageName, int userId);

    /**
     * @deprecated Deprecated in API 26 (Android O)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.N)
    boolean isEphemeralApplication(String packageName, int userId);

    @RequiresApi(Build.VERSION_CODES.O)
    boolean isInstantApp(String packageName, int userId);

    @RequiresApi(Build.VERSION_CODES.N)
    boolean setRequiredForSystemUser(String packageName, boolean systemUserApp);

    /**
     * Sets whether or not an update is available. Ostensibly for instant apps
     * to force exteranl resolution.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    void setUpdateAvailable(String packageName, boolean updateAvailable);

    @RequiresApi(Build.VERSION_CODES.N)
    String getServicesSystemSharedLibraryPackageName();

    @RequiresApi(Build.VERSION_CODES.N)
    String getSharedSystemSharedLibraryPackageName();

    @RequiresApi(Build.VERSION_CODES.O)
    ChangedPackages getChangedPackages(int sequenceNumber, int userId);

    @RequiresApi(Build.VERSION_CODES.N)
    boolean isPackageDeviceAdminOnAnyUser(String packageName);

    /**
     * @deprecated Removed in API 29 (Android P)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.N)
    List<String> getPreviousCodePaths(String packageName);

    @RequiresApi(Build.VERSION_CODES.O)
    int getInstallReason(String packageName, int userId);

    @RequiresApi(Build.VERSION_CODES.O)
    ParceledListSlice getSharedLibraries(String packageName, int flags, int userId);

    @RequiresApi(Build.VERSION_CODES.Q)
    ParceledListSlice getDeclaredSharedLibraries(String packageName, int flags, int userId);

    @RequiresApi(Build.VERSION_CODES.O)
    boolean canRequestPackageInstalls(String packageName, int userId);

    @RequiresApi(Build.VERSION_CODES.O)
    void deletePreloadsFileCache();

    @RequiresApi(Build.VERSION_CODES.O)
    ComponentName getInstantAppResolverComponent();

    @RequiresApi(Build.VERSION_CODES.O)
    ComponentName getInstantAppResolverSettingsComponent();

    @RequiresApi(Build.VERSION_CODES.O)
    ComponentName getInstantAppInstallerComponent();

    @RequiresApi(Build.VERSION_CODES.O)
    String getInstantAppAndroidId(String packageName, int userId);

//    @RequiresApi(Build.VERSION_CODES.P)
//    IArtManager getArtManager();  // TODO(25/12/20)

    @RequiresApi(Build.VERSION_CODES.P)
    void setHarmfulAppWarning(String packageName, CharSequence warning, int userId);

    @RequiresApi(Build.VERSION_CODES.P)
    CharSequence getHarmfulAppWarning(String packageName, int userId);

    @RequiresApi(Build.VERSION_CODES.P)
    boolean hasSigningCertificate(String packageName, byte[] signingCertificate, int flags);

    @RequiresApi(Build.VERSION_CODES.P)
    boolean hasUidSigningCertificate(int uid, byte[] signingCertificate, int flags);

    @RequiresApi(Build.VERSION_CODES.R)
    String getDefaultTextClassifierPackageName();

    @RequiresApi(Build.VERSION_CODES.P)
    String getSystemTextClassifierPackageName();

    @RequiresApi(Build.VERSION_CODES.Q)
    String getAttentionServicePackageName();

    @RequiresApi(Build.VERSION_CODES.Q)
    String getWellbeingPackageName();

    @RequiresApi(Build.VERSION_CODES.Q)
    String getAppPredictionServicePackageName();

    @RequiresApi(Build.VERSION_CODES.Q)
    String getSystemCaptionsServicePackageName();

    @RequiresApi(Build.VERSION_CODES.R)
    String getSetupWizardPackageName();

    @RequiresApi(Build.VERSION_CODES.Q)
    String getIncidentReportApproverPackageName();

    @RequiresApi(Build.VERSION_CODES.R)
    String getContentCaptureServicePackageName();

    @RequiresApi(Build.VERSION_CODES.P)
    boolean isPackageStateProtected(String packageName, int userId);

    @RequiresApi(Build.VERSION_CODES.Q)
    void sendDeviceCustomizationReadyBroadcast();

    @RequiresApi(Build.VERSION_CODES.Q)
    List<ModuleInfo> getInstalledModules(int flags);

    @RequiresApi(Build.VERSION_CODES.Q)
    ModuleInfo getModuleInfo(String packageName, int flags);

    @RequiresApi(Build.VERSION_CODES.Q)
    int getRuntimePermissionsVersion(int userId);

    @RequiresApi(Build.VERSION_CODES.Q)
    void setRuntimePermissionsVersion(int version, int userId);

    @RequiresApi(Build.VERSION_CODES.Q)
    void notifyPackagesReplacedReceived(String[] packages);

    abstract class Stub extends Binder implements IPackageManager {
        public static IPackageManager asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }
    }
}
