// SPDX-License-Identifier: Apache-2.0

package android.app;

import android.content.ComponentName;
import android.content.IContentProvider;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.UriPermission;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.ParceledListSlice;
import android.content.pm.UserInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import java.util.List;

import misc.utils.HiddenUtil;

/**
 * System private API for talking with the activity manager service.  This
 * provides calls from the application back to the activity manager.
 */
public interface IActivityManager extends IInterface {
    // WARNING: when these transactions are updated, check if they are any callers on the native
    // side. If so, make sure they are using the correct transaction ids and arguments.
    // If a transaction which will also be used on the native side is being inserted, add it to
    // below block of transactions.
    // Since these transactions are also called from native code, these must be kept in sync with
    // the ones in frameworks/native/libs/binder/include/binder/IActivityManager.h
    // =============== Beginning of transactions used on native side as well ======================

    ParcelFileDescriptor openContentUri(String uriString) throws RemoteException;

    boolean isUidActive(int uid, String callingPackage) throws RemoteException;

    int getUidProcessState(int uid, String callingPackage) throws RemoteException;
    // =============== End of transactions used on native side as well ============================
    // Special low-level communication with activity manager.

    /**
     * @deprecated Use {@link #startActivityWithFeature} instead
     */
    int startActivity(IApplicationThread caller, String callingPackage, Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode, int flags, ProfilerInfo profilerInfo, Bundle options) throws RemoteException;

    int startActivityWithFeature(IApplicationThread caller, String callingPackage, String callingFeatureId, Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode, int flags, ProfilerInfo profilerInfo, Bundle options) throws RemoteException;

    boolean finishActivity(IBinder token, int code, Intent data, int finishTask) throws RemoteException;

    Intent registerReceiver(IApplicationThread caller, String callerPackage, IIntentReceiver receiver, IntentFilter filter, String requiredPermission, int userId, int flags) throws RemoteException;

    Intent registerReceiverWithFeature(IApplicationThread caller, String callerPackage, String callingFeatureId, IIntentReceiver receiver, IntentFilter filter, String requiredPermission, int userId, int flags) throws RemoteException;

    void unregisterReceiver(IIntentReceiver receiver) throws RemoteException;


    /**
     * @deprecated Removed in Android M.
     */
    @Deprecated
    int broadcastIntent(IApplicationThread caller, Intent intent, String resolvedType,
                        IIntentReceiver resultTo, int resultCode, String resultData, Bundle map,
                        String requiredPermission, int appOp, boolean serialized, boolean sticky,
                        int userId) throws RemoteException;

    /**
     * @deprecated Deprecated in Android 11. Use {@link #broadcastIntentWithFeature} instead
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    int broadcastIntent(IApplicationThread caller, Intent intent, String resolvedType,
                        IIntentReceiver resultTo, int resultCode, String resultData, Bundle map,
                        String[] requiredPermissions, int appOp, Bundle options, boolean serialized,
                        boolean sticky, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.R)
    int broadcastIntentWithFeature(IApplicationThread caller, String callingFeatureId,
                                   Intent intent, String resolvedType, IIntentReceiver resultTo,
                                   int resultCode, String resultData, Bundle map,
                                   String[] requiredPermissions, int appOp, Bundle options,
                                   boolean serialized, boolean sticky, int userId)
            throws RemoteException;

    void unbroadcastIntent(IApplicationThread caller, Intent intent, int userId) throws RemoteException;

    void finishReceiver(IBinder who, int resultCode, String resultData, Bundle map, boolean abortBroadcast, int flags) throws RemoteException;

    void attachApplication(IApplicationThread app, long startSeq) throws RemoteException;

    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum) throws RemoteException;

    @RequiresApi(29)
    android.app.ContentProviderHolder getContentProviderExternal(String name, int userId, IBinder token, String tag) throws RemoteException;

    /**
     * @deprecated Removed in Android Q
     * @return {@link ContentProviderHolder} before Android O and {@link android.app.ContentProviderHolder} after Android O
     */
    @Deprecated
    Object getContentProviderExternal(String name, int userId, IBinder token) throws RemoteException;

    void removeContentProvider(IBinder connection, boolean stable) throws RemoteException;

    void removeContentProviderExternal(String name, IBinder token) throws RemoteException;

    /**
     * @deprecated Removed in Android O.
     */
    @Deprecated
    class ContentProviderHolder implements Parcelable {
        public IContentProvider provider;

        public static final Creator<ContentProviderHolder> CREATOR = HiddenUtil.creator();

        @Override
        public int describeContents() {
            return HiddenUtil.throwUOE();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            HiddenUtil.throwUOE(dest, flags);
        }
    }

    /**
     * @deprecated Removed in Android M
     */
    @Deprecated
    ComponentName startService(IApplicationThread caller, Intent service, String resolvedType, int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android O
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    ComponentName startService(IApplicationThread caller, Intent service, String resolvedType, String callingPackage, int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android R
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.O)
    ComponentName startService(IApplicationThread caller, Intent service, String resolvedType, boolean requireForeground, String callingPackage, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.R)
    ComponentName startService(IApplicationThread caller, Intent service, String resolvedType, boolean requireForeground, String callingPackage, String callingFeatureId, int userId) throws RemoteException;

    int stopService(IApplicationThread caller, Intent service, String resolvedType, int userId) throws RemoteException;
    // Currently keeping old bindService because it is on the greylist

    int bindService(IApplicationThread caller, IBinder token, Intent service, String resolvedType, IServiceConnection connection, int flags, String callingPackage, int userId) throws RemoteException;

    int bindIsolatedService(IApplicationThread caller, IBinder token, Intent service, String resolvedType, IServiceConnection connection, int flags, String instanceName, String callingPackage, int userId) throws RemoteException;

    void updateServiceGroup(IServiceConnection connection, int group, int importance) throws RemoteException;

    boolean unbindService(IServiceConnection connection) throws RemoteException;

    void publishService(IBinder token, Intent intent, IBinder service) throws RemoteException;

    void setDebugApp(String packageName, boolean waitForDebugger, boolean persistent) throws RemoteException;

    void setAgentApp(String packageName, String agent) throws RemoteException;

    void setAlwaysFinish(boolean enabled) throws RemoteException;

    boolean stopServiceToken(ComponentName className, IBinder token, int startId) throws RemoteException;

    void setProcessLimit(int max) throws RemoteException;

    int getProcessLimit() throws RemoteException;

    int checkPermission(String permission, int pid, int uid) throws RemoteException;

    /**
     * @deprecated Removed in Android LOLLIPOP_MR1.
     */
    @Deprecated
    int checkUriPermission(Uri uri, int pid, int uid, int mode, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    int checkUriPermission(Uri uri, int pid, int uid, int mode, int userId, IBinder callerToken) throws RemoteException;

    void grantUriPermission(IApplicationThread caller, String targetPkg, Uri uri, int mode, int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android O.
     */
    @Deprecated
    void revokeUriPermission(IApplicationThread caller, Uri uri, int mode, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    void revokeUriPermission(IApplicationThread caller, String targetPkg, Uri uri, int mode, int userId) throws RemoteException;

    /**
     * Gets the URI permissions granted to an arbitrary package (or all packages if null)
     * <p>
     * NOTE: this is different from getPersistedUriPermissions(), which returns the URIs the package granted to another
     * packages (instead of those granted to it).
     * @return {@link UriPermission} before Android P and {@link GrantedUriPermission} from Android P
     */
    @RequiresApi(Build.VERSION_CODES.N)
    ParceledListSlice<Parcelable> getGrantedUriPermissions(String packageName, int userId)
            throws RemoteException;

    // Clears the URI permissions granted to an arbitrary package.
    @RequiresApi(Build.VERSION_CODES.N)
    void clearGrantedUriPermissions(String packageName, int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android Q.
     */
    @Deprecated
    IBinder newUriPermissionOwner(String name) throws RemoteException;

    /**
     * @deprecated Removed in Android Q.
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    IBinder getUriPermissionOwnerForActivity(IBinder activityToken) throws RemoteException;

    /**
     * @deprecated Removed in Android Q.
     */
    @Deprecated
    void grantUriPermissionFromOwner(IBinder owner, int fromUid, String targetPkg, Uri uri, int mode, int sourceUserId, int targetUserId) throws RemoteException;

    /**
     * @deprecated Removed in Android Q.
     */
    @Deprecated
    void revokeUriPermissionFromOwner(IBinder owner, Uri uri, int mode, int userId) throws RemoteException;

    /**
     * @deprecated Removed in Android Q.
     */
    @Deprecated
    int checkGrantUriPermission(int callingUid, String targetPkg, Uri uri, int modeFlags, int userId) throws RemoteException;

    android.content.pm.ParceledListSlice getRecentTasks(int maxNum, int flags, int userId) throws RemoteException;

    void serviceDoneExecuting(IBinder token, int type, int startId, int res) throws RemoteException;

    /**
     * @deprecated Use {@link #getIntentSenderWithFeature} instead
     */
    IIntentSender getIntentSender(int type, String packageName, IBinder token, String resultWho, int requestCode, Intent[] intents, String[] resolvedTypes, int flags, Bundle options, int userId) throws RemoteException;

    IIntentSender getIntentSenderWithFeature(int type, String packageName, String featureId, IBinder token, String resultWho, int requestCode, Intent[] intents, String[] resolvedTypes, int flags, Bundle options, int userId) throws RemoteException;

    void cancelIntentSender(IIntentSender sender) throws RemoteException;

    String getPackageForIntentSender(IIntentSender sender) throws RemoteException;

    void setProcessImportant(IBinder token, int pid, boolean isForeground, String reason) throws RemoteException;

    void setServiceForeground(ComponentName className, IBinder token, int id, Notification notification, int flags, int foregroundServiceType) throws RemoteException;

    int getForegroundServiceType(ComponentName className, IBinder token) throws RemoteException;

    void getMemoryInfo(ActivityManager.MemoryInfo outInfo) throws RemoteException;

    boolean clearApplicationUserData(String packageName, boolean keepState, IPackageDataObserver observer, int userId) throws RemoteException;

    void forceStopPackage(String packageName, int userId) throws RemoteException;

    boolean killPids(int[] pids, String reason, boolean secure) throws RemoteException;

    List<ActivityManager.RunningServiceInfo> getServices(int maxNum, int flags) throws RemoteException;
    // Retrieve running application processes in the system

    List<ActivityManager.RunningAppProcessInfo> getRunningAppProcesses() throws RemoteException;

    IBinder peekService(Intent service, String resolvedType, String callingPackage) throws RemoteException;
    // Turn on/off profiling in a particular process.

    boolean profileControl(String process, int userId, boolean start, ProfilerInfo profilerInfo, int profileType) throws RemoteException;

    boolean shutdown(int timeout) throws RemoteException;

    void stopAppSwitches() throws RemoteException;

    void resumeAppSwitches() throws RemoteException;

    boolean bindBackupAgent(String packageName, int backupRestoreMode, int targetUserId) throws RemoteException;

    void backupAgentCreated(String packageName, IBinder agent, int userId) throws RemoteException;

    void unbindBackupAgent(ApplicationInfo appInfo) throws RemoteException;

    int getUidForIntentSender(IIntentSender sender) throws RemoteException;

    int handleIncomingUser(int callingPid, int callingUid, int userId, boolean allowAll, boolean requireFull, String name, String callerPackage) throws RemoteException;

    void addPackageDependency(String packageName) throws RemoteException;

    void killApplication(String pkg, int appId, int userId, String reason) throws RemoteException;

    void killApplicationProcess(String processName, int uid) throws RemoteException;
    // Special low-level communication with activity manager.

    void killBackgroundProcesses(String packageName, int userId) throws RemoteException;

    boolean isUserAMonkey() throws RemoteException;
    // Retrieve info of applications installed on external media that are currently
    // running.

    List<ApplicationInfo> getRunningExternalApplications() throws RemoteException;

    void finishHeavyWeightApp() throws RemoteException;
    // A StrictMode violation to be handled.

    boolean isTopActivityImmersive() throws RemoteException;

    void crashApplication(int uid, int initialPid, String packageName, int userId, String message, boolean force) throws RemoteException;

    boolean isUserRunning(int userid, int flags) throws RemoteException;

    void setPackageScreenCompatMode(String packageName, int mode) throws RemoteException;

    boolean switchUser(int userid) throws RemoteException;

    boolean removeTask(int taskId) throws RemoteException;

    boolean isIntentSenderTargetedToPackage(IIntentSender sender) throws RemoteException;

    long[] getProcessPss(int[] pids) throws RemoteException;

    void showBootMessage(java.lang.CharSequence msg, boolean always) throws RemoteException;

    void killAllBackgroundProcesses() throws RemoteException;

    void getMyMemoryState(ActivityManager.RunningAppProcessInfo outInfo) throws RemoteException;

    boolean killProcessesBelowForeground(String reason) throws RemoteException;

    UserInfo getCurrentUser() throws RemoteException;
    // This is not because you need to be very careful in how you
    // manage your activity to make sure it is always the uid you expect.

    int getLaunchedFromUid(IBinder activityToken) throws RemoteException;

    void unstableProviderDied(IBinder connection) throws RemoteException;

    boolean isIntentSenderAnActivity(IIntentSender sender) throws RemoteException;

    boolean isIntentSenderAForegroundService(IIntentSender sender) throws RemoteException;

    boolean isIntentSenderABroadcast(IIntentSender sender) throws RemoteException;

    /**
     * @deprecated Since Android 11. Use {@link #startActivityAsUserWithFeature} instead
     */
    int startActivityAsUser(IApplicationThread caller, String callingPackage, Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode, int flags, ProfilerInfo profilerInfo, Bundle options, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.R)
    int startActivityAsUserWithFeature(IApplicationThread caller, String callingPackage, String callingFeatureId, Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode, int flags, ProfilerInfo profilerInfo, Bundle options, int userId) throws RemoteException;

    int[] getRunningUserIds() throws RemoteException;

    /**
     * Takes a telephony bug report and notifies the user with the title and description
     * that are passed to this API as parameters
     *
     * @param shareTitle       should be a valid legible string less than 50 chars long
     * @param shareDescription should be less than 150 chars long
     * @throws IllegalArgumentException if shareTitle or shareDescription is too big or if the
     *                                  paremeters cannot be encoding to an UTF-8 charset.
     */
    void requestTelephonyBugReport(String shareTitle, String shareDescription) throws RemoteException;

    /**
     * This method is only used by Wifi.
     * <p>
     * Takes a minimal bugreport of Wifi-related state.
     *
     * @param shareTitle       should be a valid legible string less than 50 chars long
     * @param shareDescription should be less than 150 chars long
     * @throws IllegalArgumentException if shareTitle or shareDescription is too big or if the
     *                                  parameters cannot be encoding to an UTF-8 charset.
     */
    void requestWifiBugReport(String shareTitle, String shareDescription) throws RemoteException;

    void requestInteractiveBugReportWithDescription(String shareTitle, String shareDescription) throws RemoteException;

    void requestInteractiveBugReport() throws RemoteException;

    void requestFullBugReport() throws RemoteException;

    void requestRemoteBugReport() throws RemoteException;

    boolean launchBugReportHandlerApp() throws RemoteException;

    List<String> getBugreportWhitelistedPackages() throws RemoteException;

    Intent getIntentForIntentSender(IIntentSender sender) throws RemoteException;
    // This is not because you need to be very careful in how you
    // manage your activity to make sure it is always the uid you expect.

    String getLaunchedFromPackage(IBinder activityToken) throws RemoteException;

    void killUid(int appId, int userId, String reason) throws RemoteException;

    void setUserIsMonkey(boolean monkey) throws RemoteException;

    void hang(IBinder who, boolean allowRestart) throws RemoteException;

    void restart() throws RemoteException;

    void performIdleMaintenance() throws RemoteException;

    void appNotRespondingViaProvider(IBinder connection) throws RemoteException;

    boolean setProcessMemoryTrimLevel(String process, int uid, int level) throws RemoteException;
    // Start of L transactions

    String getTagForIntentSender(IIntentSender sender, String prefix) throws RemoteException;

    boolean startUserInBackground(int userid) throws RemoteException;

    boolean isInLockTaskMode() throws RemoteException;

    void startSystemLockTaskMode(int taskId) throws RemoteException;

    boolean isTopOfTask(IBinder token) throws RemoteException;

    void bootAnimationComplete() throws RemoteException;

    int checkPermissionWithToken(String permission, int pid, int uid, IBinder callerToken) throws RemoteException;

    void notifyCleartextNetwork(int uid, byte[] firstPacket) throws RemoteException;

    int getLockTaskModeState() throws RemoteException;

    void setDumpHeapDebugLimit(String processName, int uid, long maxMemSize, String reportPackage) throws RemoteException;

    void dumpHeapFinished(String path) throws RemoteException;

    void updateLockTaskPackages(int userId, String[] packages) throws RemoteException;

    int getPackageProcessState(String packageName, String callingPackage) throws RemoteException;

    void updateDeviceOwner(String packageName) throws RemoteException;
    // Start of N transactions
    // Start Binder transaction tracking for all applications.

    boolean startBinderTracking() throws RemoteException;
    // Stop Binder transaction tracking for all applications and dump trace data to the given file
    // descriptor.

    boolean stopBinderTrackingAndDump(ParcelFileDescriptor fd) throws RemoteException;

    /**
     * Try to place task to provided position. The final position might be different depending on
     * current user and stacks state. The task will be moved to target stack if it's currently in
     * different stack.
     */
    void positionTaskInStack(int taskId, int stackId, int position) throws RemoteException;

    void suppressResizeConfigChanges(boolean suppress) throws RemoteException;

    boolean isAppStartModeDisabled(int uid, String packageName) throws RemoteException;

    void killPackageDependents(String packageName, int userId) throws RemoteException;

    void removeStack(int stackId) throws RemoteException;

    void makePackageIdle(String packageName, int userId) throws RemoteException;

    int getMemoryTrimLevel() throws RemoteException;

    boolean isVrModePackageEnabled(ComponentName packageName) throws RemoteException;

    void notifyLockedProfile(int userId) throws RemoteException;

    void startConfirmDeviceCredentialIntent(Intent intent, Bundle options) throws RemoteException;

    void sendIdleJobTrigger() throws RemoteException;

    int sendIntentSender(IIntentSender target, IBinder whitelistToken, int code, Intent intent, String resolvedType, IIntentReceiver finishedReceiver, String requiredPermission, Bundle options) throws RemoteException;

    boolean isBackgroundRestricted(String packageName) throws RemoteException;
    // Start of N MR1 transactions

    void setRenderThread(int tid) throws RemoteException;

    /**
     * Lets activity manager know whether the calling process is currently showing "top-level" UI
     * that is not an activity, i.e. windows on the screen the user is currently interacting with.
     *
     * <p>This flag can only be set for persistent processes.
     *
     * @param hasTopUi Whether the calling process has "top-level" UI.
     */
    void setHasTopUi(boolean hasTopUi) throws RemoteException;
    // Start of O transactions

    int restartUserInBackground(int userId) throws RemoteException;

    void scheduleApplicationInfoChanged(List<String> packageNames, int userId) throws RemoteException;

    void setPersistentVrThread(int tid) throws RemoteException;

    void waitForNetworkStateUpdate(long procStateSeq) throws RemoteException;

    /**
     * Add a bare uid to the background restrictions whitelist.  Only the system uid may call this.
     */
    void backgroundWhitelistUid(int uid) throws RemoteException;
    // Start of P transactions

    /**
     * Method for the shell UID to start deletating its permission identity to an
     * active instrumenation. The shell can delegate permissions only to one active
     * instrumentation at a time. An active instrumentation is one running and
     * started from the shell.
     */
    void startDelegateShellPermissionIdentity(int uid, String[] permissions) throws RemoteException;

    /**
     * Method for the shell UID to stop deletating its permission identity to an
     * active instrumenation. An active instrumentation is one running and
     * started from the shell.
     */
    void stopDelegateShellPermissionIdentity() throws RemoteException;

    /**
     * Returns a file descriptor that'll be closed when the system server process dies.
     */
    ParcelFileDescriptor getLifeMonitor() throws RemoteException;

    /**
     * Method for the app to tell system that it's wedged and would like to trigger an ANR.
     */
    void appNotResponding(String reason) throws RemoteException;

    /**
     * Return a list of {@link ApplicationExitInfo} records.
     *
     * <p class="note"> Note: System stores these historical information in a ring buffer, older
     * records would be overwritten by newer records. </p>
     *
     * <p class="note"> Note: In the case that this application bound to an external service with
     * flag {@link android.content.Context#BIND_EXTERNAL_SERVICE}, the process of that external
     * service will be included in this package's exit info. </p>
     *
     * @param packageName Optional, an empty value means match all packages belonging to the
     *                    caller's UID. If this package belongs to another UID, you must hold
     *                    {@link android.Manifest.permission#DUMP} in order to retrieve it.
     * @param pid         Optional, it could be a process ID that used to belong to this package but
     *                    died later; A value of 0 means to ignore this parameter and return all
     *                    matching records.
     * @param maxNum      Optional, the maximum number of results should be returned; A value of 0
     *                    means to ignore this parameter and return all matching records
     * @param userId      The userId in the multi-user environment.
     * @return a list of {@link ApplicationExitInfo} records with the matching criteria, sorted in
     * the order from most recent to least recent.
     */
    ParceledListSlice<ApplicationExitInfo> getHistoricalProcessExitReasons(String packageName, int pid, int maxNum, int userId) throws RemoteException;

    /*
     * Kill the given PIDs, but the killing will be delayed until the device is idle
     * and the given process is imperceptible.
     */
    void killProcessesWhenImperceptible(int[] pids, String reason) throws RemoteException;

    /**
     * Set custom state data for this process. It will be included in the record of
     * {@link ApplicationExitInfo} on the death of the current calling process; the new process
     * of the app can retrieve this state data by calling
     * {@link ApplicationExitInfo#getProcessStateSummary} on the record returned by
     * {@link #getHistoricalProcessExitReasons}.
     *
     * <p> This would be useful for the calling app to save its stateful data: if it's
     * killed later for any reason, the new process of the app can know what the
     * previous process of the app was doing. For instance, you could use this to encode
     * the current level in a game, or a set of features/experiments that were enabled. Later you
     * could analyze under what circumstances the app tends to crash or use too much memory.
     * However, it's not suggested to rely on this to restore the applications previous UI state
     * or so, it's only meant for analyzing application healthy status.</p>
     *
     * <p> System might decide to throttle the calls to this API; so call this API in a reasonable
     * manner, excessive calls to this API could result a {@link java.lang.RuntimeException}.
     * </p>
     *
     * @param state The customized state data
     */
    void setProcessStateSummary(byte[] state) throws RemoteException;

    /**
     * Return whether the app freezer is supported (true) or not (false) by this system.
     */
    boolean isAppFreezerSupported() throws RemoteException;

    /**
     * Kills uid with the reason of permission change.
     */
    void killUidForPermissionChange(int appId, int userId, String reason) throws RemoteException;

    /**
     * Control the app freezer state. Returns true in case of success, false if the operation
     * didn't succeed (for example, when the app freezer isn't supported).
     * Handling the freezer state via this method is reentrant, that is it can be
     * disabled and re-enabled multiple times in parallel. As long as there's a 1:1 disable to
     * enable match, the freezer is re-enabled at last enable only.
     *
     * @param enable set it to true to enable the app freezer, false to disable it.
     */
    boolean enableAppFreezer(boolean enable) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    abstract class Stub extends Binder implements IActivityManager {
        public static IActivityManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}