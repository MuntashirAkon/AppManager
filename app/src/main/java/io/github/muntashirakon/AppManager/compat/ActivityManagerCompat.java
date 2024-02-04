// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.Manifest;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IContentProvider;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public final class ActivityManagerCompat {
    public interface ActivityLaunchUserInteractionRequiredCallback {
        @WorkerThread
        void onInteraction();
    }

    @RequiresPermission(allOf = {
            Manifest.permission.WRITE_SECURE_SETTINGS,
            ManifestCompat.permission.INJECT_EVENTS
    })
    @MainThread
    public static void startActivityViaAssist(@NonNull Context context, @NonNull ComponentName activity,
                                              @Nullable ActivityLaunchUserInteractionRequiredCallback callback)
            throws SecurityException {
        // Need two permissions: WRITE_SECURE_SETTINGS and INJECT_EVENTS
        SelfPermissions.requireSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS);
        boolean canInjectEvents = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.INJECT_EVENTS);
        ContentResolver resolver = context.getContentResolver();
        // Backup assistant value
        String assistantComponent = Settings.Secure.getString(resolver, "assistant");
        if (canInjectEvents) {
            try {
                // Set assistant value to the target activity component
                Settings.Secure.putString(resolver, "assistant", activity.flattenToShortString());
                // Run it as an assistant by injecting KEYCODE_ASSIST (219)
                InputManagerCompat.sendKeyEvent(KeyEvent.KEYCODE_ASSIST, false);
            } finally {
                // Restore assistant value
                Settings.Secure.putString(resolver, "assistant", assistantComponent);
            }
        } else if (callback != null) {
            // Cannot launch event by default, use callback
            ThreadUtils.postOnBackgroundThread(() -> {
                try {
                    // Set assistant value to the target activity component
                    Settings.Secure.putString(resolver, "assistant", activity.flattenToShortString());
                    // Trigger callback
                    callback.onInteraction();
                } finally {
                    // Restore assistant value
                    Settings.Secure.putString(resolver, "assistant", assistantComponent);
                }
            });
        } // else do nothing
    }

    @SuppressWarnings("deprecation")
    public static int startActivity(Intent intent, @UserIdInt int userHandle) throws RemoteException {
        IActivityManager am = getActivityManager();
        String callingPackage = SelfPermissions.getCallingPackage(Users.getSelfOrRemoteUid());
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            result = am.startActivityAsUserWithFeature(null, callingPackage,
                    null, intent, intent.getType(), null, null,
                    0, 0, null, null, userHandle);
        } else {
            result = am.startActivityAsUser(null, callingPackage, intent, intent.getType(),
                    null, null, 0, 0, null,
                    null, userHandle);
        }
        return result;
    }

    @SuppressWarnings("deprecation")
    public static ComponentName startService(Intent intent, @UserIdInt int userHandle, boolean asForeground)
            throws RemoteException {
        IActivityManager am = getActivityManager();
        String callingPackage = SelfPermissions.getCallingPackage(Users.getSelfOrRemoteUid());
        ComponentName cn;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            cn = am.startService(null, intent, intent.getType(), asForeground, callingPackage, null, userHandle);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            cn = am.startService(null, intent, intent.getType(), asForeground, callingPackage, userHandle);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cn = am.startService(null, intent, intent.getType(), callingPackage, userHandle);
        } else cn = am.startService(null, intent, intent.getType(), userHandle);
        return cn;
    }

    public static int sendBroadcast(Intent intent, @UserIdInt int userHandle)
            throws RemoteException {
        IActivityManager am = getActivityManager();
        int res;
        IIntentReceiver receiver = new IntentReceiver();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            res = am.broadcastIntentWithFeature(null, null, intent, null, receiver, 0, null, null, null, AppOpsManagerCompat.OP_NONE, null, true, false, userHandle);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            res = am.broadcastIntent(null, intent, null, null, 0, null, null, null, AppOpsManagerCompat.OP_NONE, null, true, false, userHandle);
        } else {
            res = am.broadcastIntent(null, intent, null, null, 0, null, null, null, AppOpsManagerCompat.OP_NONE, true, false, userHandle);
        }
        return res;
    }

    @Nullable
    public static IContentProvider getContentProviderExternal(String name, int userId, IBinder token, String tag)
            throws RemoteException {
        IActivityManager am = getActivityManager();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return am.getContentProviderExternal(name, userId, token, tag).provider;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return ((android.app.ContentProviderHolder) am.getContentProviderExternal(name, userId, token)).provider;
            } else {
                return ((IActivityManager.ContentProviderHolder) am.getContentProviderExternal(name, userId, token)).provider;
            }
        } catch (NullPointerException e) {
            return null;
        }
    }

    @NonNull
    public static List<ActivityManager.RunningServiceInfo> getRunningServices(String packageName, @UserIdInt int userId) {
        List<ActivityManager.RunningServiceInfo> runningServices;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1
                && !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.REAL_GET_TASKS)
                && canDumpRunningServices()) {
            // Fetch running services by parsing dumpsys output
            runningServices = getRunningServicesUsingDumpSys(packageName);
        } else {
            // For no-root, this returns services running in the current UID since Android Oreo
            try {
                runningServices = getActivityManager().getServices(100, 0);
            } catch (RemoteException e) {
                return Collections.emptyList();
            }
        }
        List<ActivityManager.RunningServiceInfo> res = new ArrayList<>();
        for (ActivityManager.RunningServiceInfo info : runningServices) {
            if (info.service.getPackageName().equals(packageName) && userId == UserHandleHidden.getUserId(info.uid)) {
                res.add(info);
            }
        }
        return res;
    }

    @NonNull
    public static List<ActivityManager.RunningAppProcessInfo> getRunningAppProcesses() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.REAL_GET_TASKS)
                && canDumpRunningServices()) {
            // Fetch running app processes by parsing dumpsys output if root/ADB is disabled
            // and android.permission.DUMP is granted
            return getRunningAppProcessesUsingDumpSys();
        } else {
            // For no-root, this returns app processes running in the current UID since Android M
            return ExUtils.requireNonNullElse(() -> getActivityManager().getRunningAppProcesses(), Collections.emptyList());
        }
    }

    @RequiresPermission("android.permission.KILL_UID")
    public static void killUid(int uid, String reason) throws RemoteException {
        getActivityManager().killUid(UserHandleHidden.getAppId(uid), UserHandleHidden.getUserId(uid), reason);
    }

    @SuppressWarnings("deprecation")
    public static IActivityManager getActivityManager() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return IActivityManager.Stub.asInterface(ProxyBinder.getService(Context.ACTIVITY_SERVICE));
        } else {
            return ActivityManagerNative.asInterface(ProxyBinder.getService(Context.ACTIVITY_SERVICE));
        }
    }

    @SuppressWarnings("RegExpRedundantEscape")
    private static final Pattern APP_PROCESS_REGEX = Pattern.compile("\\*[A-Z]+\\* UID (\\d+) ProcessRecord\\{[0-9a-f]+ (\\d+):([^/]+)/[^\\}]+\\}");
    @SuppressWarnings("RegExpRedundantEscape")
    private static final Pattern PKG_LIST_REGEX = Pattern.compile("packageList=\\{([^/]+)\\}");

    @NonNull
    private static List<ActivityManager.RunningAppProcessInfo> getRunningAppProcessesUsingDumpSys() {
        List<ActivityManager.RunningAppProcessInfo> runningAppProcessInfos = new ArrayList<>();
        Runner.Result result = Runner.runCommand(new String[]{"dumpsys", "activity", "processes"});
        if (!result.isSuccessful()) return runningAppProcessInfos;
        List<String> appProcessDump = result.getOutputAsList(1);
        return parseRunningAppProcesses(appProcessDump);
    }

    @VisibleForTesting
    @NonNull
    static List<ActivityManager.RunningAppProcessInfo> parseRunningAppProcesses(@NonNull List<String> appProcessesDump) {
        List<ActivityManager.RunningAppProcessInfo> runningAppProcessInfos = new ArrayList<>();
        Matcher aprMatcher;
        Matcher pkgrMatcher;
        String line;
        ListIterator<String> it = appProcessesDump.listIterator();
        if (!it.hasNext()) return runningAppProcessInfos;
        aprMatcher = APP_PROCESS_REGEX.matcher(it.next());
        while (it.hasNext()) {
            if (!aprMatcher.find(0)) {
                // No matches found, check the next line
                aprMatcher = APP_PROCESS_REGEX.matcher(it.next());
                continue;
            }
            // Matches found
            String uid = aprMatcher.group(1);
            String pid = aprMatcher.group(2);
            String processName = aprMatcher.group(3);
            if (uid == null || pid == null || processName == null) {
                // Criteria didn't match
                aprMatcher = APP_PROCESS_REGEX.matcher(it.next());
                continue;
            }
            line = it.next();
            aprMatcher = APP_PROCESS_REGEX.matcher(line);
            while (it.hasNext()) {
                if (aprMatcher.find(0)) {
                    // found next ProcessRecord, no need to continue the search for pkgList
                    break;
                }
                pkgrMatcher = PKG_LIST_REGEX.matcher(line);
                if (!pkgrMatcher.find(0)) {
                    // Process didn't match, find next line
                    line = it.next();
                    aprMatcher = APP_PROCESS_REGEX.matcher(line);
                    continue;
                }
                // Found a pkgList
                String pkgList = pkgrMatcher.group(1);
                if (pkgList != null) {
                    ActivityManager.RunningAppProcessInfo info = new ActivityManager.RunningAppProcessInfo();
                    info.uid = Integer.decode(uid);
                    info.pid = Integer.decode(pid);
                    info.processName = processName;
                    String[] split = pkgList.split(", ");
                    info.pkgList = new String[split.length];
                    System.arraycopy(split, 0, info.pkgList, 0, split.length);
                    runningAppProcessInfos.add(info);
                }
                line = it.next();
                aprMatcher = APP_PROCESS_REGEX.matcher(line);
            }
        }
        return runningAppProcessInfos;
    }

    @SuppressWarnings("RegExpRedundantEscape")
    private static final Pattern SERVICE_RECORD_REGEX = Pattern.compile("\\* ServiceRecord\\{[0-9a-f]+ u(\\d+) ([^\\}]+)\\}");
    @SuppressWarnings("RegExpRedundantEscape")
    private static final Pattern PROCESS_RECORD_REGEX = Pattern.compile("app=ProcessRecord\\{[0-9a-f]+ (\\d+):([^/]+)/([^\\}]+)\\}");

    @NonNull
    private static List<ActivityManager.RunningServiceInfo> getRunningServicesUsingDumpSys(String packageName) {
        List<ActivityManager.RunningServiceInfo> runningServices = new ArrayList<>();
        Runner.Result result = Runner.runCommand(new String[]{"dumpsys", "activity", "services", "-p", packageName});
        if (!result.isSuccessful()) return runningServices;
        List<String> serviceDump = result.getOutputAsList(1);
        return parseRunningServices(serviceDump);
    }

    @VisibleForTesting
    @NonNull
    static List<ActivityManager.RunningServiceInfo> parseRunningServices(@NonNull List<String> serviceDump) {
        List<ActivityManager.RunningServiceInfo> runningServices = new ArrayList<>();
        Matcher srMatcher;
        Matcher prMatcher;
        ComponentName service;
        String line;
        ListIterator<String> it = serviceDump.listIterator();
        if (!it.hasNext()) return runningServices;
        srMatcher = SERVICE_RECORD_REGEX.matcher(it.next());
        while (it.hasNext()) { // hasNext check doesn't omit anything since we'd still have to check for ProcessRecord
            if (!srMatcher.find(0)) {
                // No matches found, check the next line
                srMatcher = SERVICE_RECORD_REGEX.matcher(it.next());
                continue;
            }
            // Matches found
            String userId = srMatcher.group(1);
            String serviceName = srMatcher.group(2);
            if (userId == null || serviceName == null) {
                // Criteria didn't match
                srMatcher = SERVICE_RECORD_REGEX.matcher(it.next());
                continue;
            }
            // This is actually the short process name, original service name is under intent (in the next line)
            int i = serviceName.indexOf(':');
            service = ComponentName.unflattenFromString(i == -1 ? serviceName : serviceName.substring(0, i));
            line = it.next();
            srMatcher = SERVICE_RECORD_REGEX.matcher(line);
            while (it.hasNext()) {
                if (srMatcher.find(0)) {
                    // found next ServiceRecord, no need to continue the search for ProcessRecord
                    break;
                }
                prMatcher = PROCESS_RECORD_REGEX.matcher(line);
                if (!prMatcher.find(0)) {
                    // Process didn't match, find next line
                    line = it.next();
                    srMatcher = SERVICE_RECORD_REGEX.matcher(line);
                    continue;
                }
                // Found a ProcessRecord
                String pid = prMatcher.group(1);
                String processName = prMatcher.group(2);
                String userInfo = prMatcher.group(3);
                if (pid != null && processName != null && userInfo != null) {
                    ActivityManager.RunningServiceInfo info = new ActivityManager.RunningServiceInfo();
                    info.pid = Integer.decode(pid);
                    info.process = processName;
                    info.service = service;
                    // UID
                    if (TextUtils.isDigitsOnly(userInfo)) {  // UID < 10000
                        info.uid = Integer.decode(userInfo);
                    } else if (userInfo.startsWith("u")) {  // u<USER_ID>(a|s)<APP_ID>[i<ISOLATION_ID>]
                        userInfo = userInfo.substring(("u" + userId).length()); // u<USER_ID> removed
                        int iIdx = userInfo.indexOf('i');
                        int iIndex = iIdx == -1 ? userInfo.length() : iIdx;
                        if (userInfo.startsWith("a")) {
                            // User app
                            info.uid = UserHandleHidden.getUid(Integer.decode(userId), 10_000 + Integer.decode(userInfo.substring(1, iIndex)));
                        } else if (userInfo.startsWith("s")) {
                            // System app
                            info.uid = UserHandleHidden.getUid(Integer.decode(userId), Integer.decode(userInfo.substring(1, iIndex)));
                        } else throw new IllegalStateException("No valid UID info found in ProcessRecord");
                    } else throw new IllegalStateException("Invalid user info section in ProcessRecord");
                    // TODO: 1/9/21 Parse others
                    runningServices.add(info);
                }
                line = it.next();
                srMatcher = SERVICE_RECORD_REGEX.matcher(line);
            }
        }
        return runningServices;
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static boolean canDumpRunningServices() {
        return SelfPermissions.checkSelfPermission(Manifest.permission.DUMP)
                && SelfPermissions.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS);
    }

    final static class IntentReceiver extends IIntentReceiver.Stub {
        private boolean mFinished = false;

        public void performReceive(Intent intent, int resultCode, String data, Bundle extras,
                                   boolean ordered, boolean sticky, int sendingUser) {
            String line = "Broadcast completed: result=" + resultCode;
            if (data != null) line = line + ", data=\"" + data + "\"";
            if (extras != null) line = line + ", extras: " + extras;
            Log.e("AM", line);
            synchronized (this) {
                mFinished = true;
                notifyAll();
            }
        }

        public synchronized void waitForFinish() {
            try {
                while (!mFinished) wait();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
