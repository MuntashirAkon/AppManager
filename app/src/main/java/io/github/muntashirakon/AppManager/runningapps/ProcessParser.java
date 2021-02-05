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

package io.github.muntashirakon.AppManager.runningapps;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.system.Os;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.collection.SparseArrayCompat;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.ipc.IPCUtils;
import io.github.muntashirakon.AppManager.ipc.ps.ProcessEntry;
import io.github.muntashirakon.AppManager.ipc.ps.Ps;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.Utils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

@WorkerThread
final class ProcessParser {
    private final Context context;
    private final PackageManager pm;
    private HashMap<String, PackageInfo> installedPackages;

    ProcessParser() {
        context = AppManager.getContext();
        pm = AppManager.getContext().getPackageManager();
        getInstalledPackages();
    }

    @VisibleForTesting
    ProcessParser(boolean isUnitTest) {
        if (isUnitTest) {
            installedPackages = new HashMap<>();
            pm = null;
            context = null;
        } else {
            context = AppManager.getContext();
            pm = AppManager.getContext().getPackageManager();
            getInstalledPackages();
        }
    }

    @SuppressWarnings("unchecked")
    @NonNull
    HashMap<Integer, ProcessItem> parse() {
        HashMap<Integer, ProcessItem> processItems = new HashMap<>();
        try {
            List<ProcessEntry> processEntries = (List<ProcessEntry>) IPCUtils.getServiceSafe().getRunningProcesses();
            for (ProcessEntry processEntry : processEntries) {
                if (processEntry.seLinuxPolicy.contains(":kernel:")) continue;
                try {
                    ProcessItem processItem = parseProcess(processEntry);
                    processItems.put(processItem.pid, processItem);
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
                ProcessItem processItem = parseProcess(processEntry);
                processItems.put(processItem.pid, processItem);
            } catch (Exception ignore) {
            }
        }
        return processItems;
    }

    @NonNull
    private ProcessItem parseProcess(ProcessEntry processEntry) {
        String processName = processEntry.name;
        ProcessItem processItem;
        if (installedPackages.containsKey(processName)) {
            processItem = new AppProcessItem();
            @NonNull PackageInfo packageInfo = Objects.requireNonNull(installedPackages.get(processName));
            ((AppProcessItem) processItem).packageInfo = packageInfo;
            processItem.name = pm.getApplicationLabel(packageInfo.applicationInfo).toString();
        } else {
            processItem = new ProcessItem();
            processItem.name = processName;
        }
        processItem.context = processEntry.seLinuxPolicy;
        processItem.pid = processEntry.pid;
        processItem.ppid = processEntry.ppid;
        processItem.rss = processEntry.residentSetSize;
        processItem.vsz = processEntry.virtualMemorySize;
        processItem.uid = processEntry.users.fsUid;
        processItem.user = getNameForUid(processEntry.users.fsUid);
        if (context == null) {
            processItem.state = processEntry.processState;
            processItem.state_extra = processEntry.processStatePlus;
        } else {
            processItem.state = context.getString(Utils.getProcessStateName(processEntry.processState));
            processItem.state_extra = context.getString(Utils.getProcessStateExtraName(processEntry.processStatePlus));
        }
        return processItem;
    }

    private void getInstalledPackages() {
        List<PackageInfo> packageInfoList = new ArrayList<>();
        for (int userHandle : Users.getUsersHandles()) {
            try {
                packageInfoList.addAll(PackageManagerCompat.getInstalledPackages(0, userHandle));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        installedPackages = new HashMap<>(packageInfoList.size());
        for (PackageInfo info : packageInfoList) {
            installedPackages.put(info.packageName, info);
        }
    }

    private static final SparseArrayCompat<String> uidNameCache = new SparseArrayCompat<>(150);

    @SuppressWarnings("JavaReflectionMemberAccess")
    @NonNull
    private static String getNameForUid(int uid) {
        String username = uidNameCache.get(uid);
        if (username != null) return username;
        try {
            Method getpwuid = Os.class.getMethod("getpwuid", int.class);
            if (!getpwuid.isAccessible()) {
                getpwuid.setAccessible(true);
            }
            Object passwd = getpwuid.invoke(null, uid);  // StructPasswd
            if (passwd != null) {
                Field pw_name = passwd.getClass().getDeclaredField("pw_name");
                if (!pw_name.isAccessible()) {
                    pw_name.setAccessible(true);
                }
                username = (String) pw_name.get(passwd);
            }
        } catch (Exception ignored) {
        }
        if (username == null) {
            username = String.valueOf(uid);
        }
        uidNameCache.put(uid, username);
        return username;
    }
}
