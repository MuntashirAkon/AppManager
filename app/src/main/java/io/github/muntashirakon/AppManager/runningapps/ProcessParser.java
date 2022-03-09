// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.system.ErrnoException;
import android.system.OsHidden;
import android.system.StructPasswd;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.collection.SparseArrayCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.ipc.IPCUtils;
import io.github.muntashirakon.AppManager.ipc.ps.ProcessEntry;
import io.github.muntashirakon.AppManager.ipc.ps.Ps;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.servermanager.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

@WorkerThread
public final class ProcessParser {
    private final Context mContext;
    private final PackageManager mPm;
    private HashMap<String, PackageInfo> mInstalledPackages;
    private HashMap<Integer, PackageInfo> mInstalledUidList;
    private final HashMap<Integer, ActivityManager.RunningAppProcessInfo> mRunningAppProcesses = new HashMap<>(50);

    ProcessParser() {
        if (Utils.isRoboUnitTest()) {
            mInstalledPackages = new HashMap<>();
            mInstalledUidList = new HashMap<>();
            mPm = null;
            mContext = null;
        } else {
            mContext = AppManager.getContext();
            mPm = AppManager.getContext().getPackageManager();
            getInstalledPackages();
        }
    }

    @SuppressWarnings("unchecked")
    @NonNull
    List<ProcessItem> parse() {
        List<ProcessItem> processItems = new ArrayList<>();
        try {
            List<ProcessEntry> processEntries;
            if (LocalServer.isAMServiceAlive()) {
                processEntries = (List<ProcessEntry>) IPCUtils.getServiceSafe().getRunningProcesses().getList();
            } else {
                Ps ps = new Ps();
                ps.loadProcesses();
                processEntries = ps.getProcesses();
            }
            for (ProcessEntry processEntry : processEntries) {
                if (processEntry.seLinuxPolicy.contains(":kernel:")) continue;
                try {
                    processItems.addAll(parseProcess(processEntry));
                } catch (Exception ignore) {
                }
            }
        } catch (Throwable th) {
            Log.e("ProcessParser", th);
        }
        return processItems;
    }

    @VisibleForTesting
    @NonNull
    HashMap<Integer, ProcessItem> parse(@NonNull File procDir) {
        HashMap<Integer, ProcessItem> processItems = new HashMap<>();
        Ps ps = new Ps(procDir);
        ps.loadProcesses();
        List<ProcessEntry> processEntries = ps.getProcesses();
        for (ProcessEntry processEntry : processEntries) {
            try {
                ProcessItem processItem = parseProcess(processEntry).get(0);
                processItems.put(processItem.pid, processItem);
            } catch (Exception ignore) {
            }
        }
        return processItems;
    }

    @NonNull
    private List<ProcessItem> parseProcess(@NonNull ProcessEntry processEntry) {
        String packageName = getSupposedPackageName(processEntry.name);
        List<ProcessItem> processItems = new ArrayList<>(1);
        if (mRunningAppProcesses.containsKey(processEntry.pid)) {
            String[] pkgList = Objects.requireNonNull(mRunningAppProcesses.get(processEntry.pid)).pkgList;
            if (pkgList != null && pkgList.length > 0) {
                for (String pkgName : pkgList) {
                    @NonNull PackageInfo packageInfo = Objects.requireNonNull(mInstalledPackages.get(pkgName));
                    ProcessItem processItem = new AppProcessItem(processEntry, packageInfo);
                    processItem.name = mPm.getApplicationLabel(packageInfo.applicationInfo)
                            + getProcessNameFilteringPackageName(processEntry.name, packageInfo.packageName);
                    processItems.add(processItem);
                }
            } else {
                ProcessItem processItem = new ProcessItem(processEntry);
                processItem.name = getProcessName(processEntry.name);
                processItems.add(processItem);
            }
        } else if (mInstalledPackages.containsKey(packageName)) {
            @NonNull PackageInfo packageInfo = Objects.requireNonNull(mInstalledPackages.get(packageName));
            ProcessItem processItem = new AppProcessItem(processEntry, packageInfo);
            processItem.name = mPm.getApplicationLabel(packageInfo.applicationInfo)
                    + getProcessNameFilteringPackageName(processEntry.name, packageInfo.packageName);
            processItems.add(processItem);
        } else if (mInstalledUidList.containsKey(processEntry.users.fsUid)) {
            @NonNull PackageInfo packageInfo = Objects.requireNonNull(mInstalledUidList.get(processEntry.users.fsUid));
            ProcessItem processItem = new AppProcessItem(processEntry, packageInfo);
            processItem.name = mPm.getApplicationLabel(packageInfo.applicationInfo)
                    + getProcessNameFilteringPackageName(processEntry.name, packageInfo.packageName);
            processItems.add(processItem);
        } else {
            ProcessItem processItem = new ProcessItem(processEntry);
            processItem.name = getProcessName(processEntry.name);
            processItems.add(processItem);
        }
        for (ProcessItem processItem : processItems) {
            if (mContext == null) {
                processItem.state = processEntry.processState;
                processItem.state_extra = processEntry.processStatePlus;
            } else {
                processItem.state = mContext.getString(Utils.getProcessStateName(processEntry.processState));
                processItem.state_extra = mContext.getString(Utils.getProcessStateExtraName(
                        processEntry.processStatePlus));
            }
        }
        return processItems;
    }

    private void getInstalledPackages() {
        List<PackageInfo> packageInfoList = PackageUtils.getAllPackages(0);
        mInstalledPackages = new HashMap<>(packageInfoList.size());
        for (PackageInfo info : packageInfoList) {
            mInstalledPackages.put(info.packageName, info);
        }
        mInstalledUidList = new HashMap<>(packageInfoList.size());
        List<Integer> duplicateUids = new ArrayList<>();
        for (PackageInfo info : packageInfoList) {
            int uid = info.applicationInfo.uid;
            if (mInstalledUidList.containsKey(uid)) {
                // A shared user ID (other way to check user ID will not work since we're only interested in
                // duplicate values)
                duplicateUids.add(uid);
            } else mInstalledUidList.put(uid, info);
        }
        // Remove duplicate UIDs as they might create collisions
        for (int uid : duplicateUids) mInstalledUidList.remove(uid);
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = ActivityManagerCompat.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo info : runningAppProcesses) {
            this.mRunningAppProcesses.put(info.pid, info);
        }
    }

    private static final SparseArrayCompat<String> uidNameCache = new SparseArrayCompat<>(150);

    @NonNull
    static String getNameForUid(int uid) {
        String username = uidNameCache.get(uid);
        if (username != null) return username;
        try {
            StructPasswd passwd = OsHidden.getpwuid(uid);
            username = passwd.pw_name;
        } catch (ErrnoException ignored) {
        }
        if (username == null) {
            username = String.valueOf(uid);
        }
        uidNameCache.put(uid, username);
        return username;
    }

    @NonNull
    public static String getSupposedPackageName(@NonNull String processName) {
        if (!processName.contains(":")) {
            return processName;
        }
        int colonIdx = processName.indexOf(':');
        return processName.substring(0, colonIdx);
    }

    @NonNull
    public static String getProcessName(@NonNull String processName) {
        processName = processName.split("\u0000")[0];
        if (!processName.startsWith("/")) return processName;
        int slashIndex = processName.lastIndexOf('/');
        return processName.substring(slashIndex + 1);
    }

    @NonNull
    private static String getProcessNameFilteringPackageName(@NonNull String processName, @NonNull String packageName) {
        if (processName.equals(packageName)) {
            return "";
        }
        processName = getProcessName(processName);
        int colonIdx = processName.indexOf(':');
        return colonIdx < 0 ? (":" + processName) : processName.substring(colonIdx);
    }
}
