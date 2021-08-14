// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.RemoteException;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.util.Pair;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;

public class RunningAppsViewModel extends AndroidViewModel {
    @RunningAppsActivity.SortOrder
    private int sortOrder;
    @RunningAppsActivity.Filter
    private int filter;
    private final MultithreadedExecutor executor = MultithreadedExecutor.getNewInstance();

    public RunningAppsViewModel(@NonNull Application application) {
        super(application);
        sortOrder = (int) AppPref.get(AppPref.PrefKey.PREF_RUNNING_APPS_SORT_ORDER_INT);
        filter = (int) AppPref.get(AppPref.PrefKey.PREF_RUNNING_APPS_FILTER_FLAGS_INT);
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
        super.onCleared();
    }

    private MutableLiveData<List<Integer>> processLiveData;

    public LiveData<List<Integer>> getProcessLiveData() {
        if (processLiveData == null) {
            processLiveData = new MutableLiveData<>();
        }
        return processLiveData;
    }

    private HashMap<Integer, ProcessItem> processList;
    @NonNull
    private final List<Integer> originalProcessList = new ArrayList<>();
    @NonNull
    private final List<Integer> filteredProcessList = new ArrayList<>();

    @AnyThread
    public void loadProcesses() {
        executor.submit(() -> {
            processList = new ProcessParser().parse();
            for (int pid : selections) {
                ProcessItem processItem = processList.get(pid);
                if (processItem != null) processItem.selected = false;
            }
            synchronized (originalProcessList) {
                originalProcessList.clear();
                originalProcessList.addAll(new ArrayList<>(processList.keySet()));
                filterAndSort();
            }
        });
    }

    private final MutableLiveData<Pair<ProcessItem, Boolean>> killProcessResult = new MutableLiveData<>();

    public void killProcess(ProcessItem processItem) {
        executor.submit(() -> killProcessResult.postValue(new Pair<>(processItem, Runner.runCommand(
                new String[]{"kill", "-9", String.valueOf(processItem.pid)}).isSuccessful())));
    }

    public LiveData<Pair<ProcessItem, Boolean>> observeKillProcess() {
        return killProcessResult;
    }

    private final MutableLiveData<Pair<ApplicationInfo, Boolean>> forceStopAppResult = new MutableLiveData<>();

    public void forceStop(@NonNull ApplicationInfo info) {
        executor.submit(() -> {
            try {
                PackageManagerCompat.forceStopPackage(info.packageName, Users.getUserId(info.uid));
                forceStopAppResult.postValue(new Pair<>(info, true));
            } catch (RemoteException e) {
                e.printStackTrace();
                forceStopAppResult.postValue(new Pair<>(info, false));
            }
        });
    }

    public LiveData<Pair<ApplicationInfo, Boolean>> observeForceStop() {
        return forceStopAppResult;
    }

    private final MutableLiveData<Pair<ApplicationInfo, Boolean>> preventBackgroundRunResult = new MutableLiveData<>();

    public void preventBackgroundRun(@NonNull ApplicationInfo info) {
        executor.submit(() -> {
            try {
                AppOpsService appOpsService = new AppOpsService();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appOpsService.setMode(AppOpsManager.OP_RUN_IN_BACKGROUND, info.uid, info.packageName,
                            AppOpsManager.MODE_IGNORED);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    appOpsService.setMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND, info.uid, info.packageName,
                            AppOpsManager.MODE_IGNORED);
                }
                preventBackgroundRunResult.postValue(new Pair<>(info, true));
            } catch (RemoteException e) {
                e.printStackTrace();
                preventBackgroundRunResult.postValue(new Pair<>(info, false));
            }
        });
    }

    public LiveData<Pair<ApplicationInfo, Boolean>> observePreventBackgroundRun() {
        return preventBackgroundRunResult;
    }

    public int getCount() {
        return filteredProcessList.size();
    }

    @NonNull
    public ProcessItem getProcessItem(int index) {
        ProcessItem processItem = null;
        Throwable throwable = null;
        try {
            processItem = processList.get(filteredProcessList.get(index));
        } catch (Exception e) {
            throwable = e;
        }
        if (processItem == null) {
            throw new IllegalArgumentException("No process found for index " + index, throwable);
        }
        return processItem;
    }

    private String query;

    public void setQuery(@Nullable String query) {
        this.query = query == null ? null : query.toLowerCase(Locale.ROOT);
        executor.submit(this::filterAndSort);
    }

    public String getQuery() {
        return query;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
        AppPref.set(AppPref.PrefKey.PREF_RUNNING_APPS_SORT_ORDER_INT, this.sortOrder);
        executor.submit(this::filterAndSort);
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void addFilter(int filter) {
        this.filter |= filter;
        AppPref.set(AppPref.PrefKey.PREF_RUNNING_APPS_FILTER_FLAGS_INT, this.filter);
        executor.submit(this::filterAndSort);
    }

    public void removeFilter(int filter) {
        this.filter &= ~filter;
        AppPref.set(AppPref.PrefKey.PREF_RUNNING_APPS_FILTER_FLAGS_INT, this.filter);
        executor.submit(this::filterAndSort);
    }

    public int getFilter() {
        return filter;
    }

    @WorkerThread
    public void filterAndSort() {
        filteredProcessList.clear();
        // Apply filters
        // There are 3 filters with “and” relations: query > apps > user apps
        boolean hasQuery = !TextUtils.isEmpty(query);
        boolean filterUserApps = (filter & RunningAppsActivity.FILTER_USER_APPS) != 0;
        // If user apps filter is enabled, disable it since it'll be just an overhead
        boolean filterApps = !filterUserApps && (filter & RunningAppsActivity.FILTER_APPS) != 0;
        ProcessItem processItem;
        ApplicationInfo info;
        for (int item : originalProcessList) {
            // Process items are always nonNull
            processItem = Objects.requireNonNull(processList.get(item));
            // Filter by query
            if (hasQuery && !processItem.name.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            // Filter by apps
            if (filterApps && !(processItem instanceof AppProcessItem)) {
                continue;
            }
            // Filter by user apps
            if (filterUserApps) {
                if (processItem instanceof AppProcessItem) {
                    info = ((AppProcessItem) processItem).packageInfo.applicationInfo;
                    if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                    // else it's an user app
                } else continue;
            }
            filteredProcessList.add(item);
        }
        // Apply sorts
        // Sort by pid first
        Collections.sort(filteredProcessList);
        if (sortOrder != RunningAppsActivity.SORT_BY_PID) {
            Collections.sort(filteredProcessList, (o1, o2) -> {
                ProcessItem p1 = Objects.requireNonNull(processList.get(o1));
                ProcessItem p2 = Objects.requireNonNull(processList.get(o2));
                switch (sortOrder) {
                    case RunningAppsActivity.SORT_BY_APPS_FIRST:
                        return -Boolean.compare(p1 instanceof AppProcessItem, p2 instanceof AppProcessItem);
                    case RunningAppsActivity.SORT_BY_MEMORY_USAGE:
                        return -Long.compare(p1.rss, p2.rss);
                    case RunningAppsActivity.SORT_BY_PROCESS_NAME:
                        return p1.name.compareToIgnoreCase(p2.name);
                    case RunningAppsActivity.SORT_BY_PID:
                    default:
                        return Integer.compare(p1.pid, p2.pid);
                }
            });
        }
        processLiveData.postValue(filteredProcessList);
    }

    private MutableLiveData<Integer> selectionLiveData;

    public LiveData<Integer> getSelection() {
        if (selectionLiveData == null) {
            selectionLiveData = new MutableLiveData<>(selections.size());
        }
        return selectionLiveData;
    }

    private final Set<Integer> selections = new HashSet<>();

    public void select(int pid) {
        try {
            ProcessItem processItem = processList.get(pid);
            if (processItem != null) {
                processItem.selected = true;
                selections.add(pid);
                selectionLiveData.postValue(selections.size());
            }
        } catch (Exception ignore) {
        }
    }

    public void deselect(int pid) {
        try {
            ProcessItem processItem = processList.get(pid);
            if (processItem != null) {
                processItem.selected = false;
                selections.remove(pid);
                selectionLiveData.postValue(selections.size());
            }
        } catch (Exception ignore) {
        }
    }

    public void clearSelections() {
        for (int pid : selections) {
            ProcessItem processItem = processList.get(pid);
            if (processItem != null)
                processItem.selected = false;
        }
        selections.clear();
        selectionLiveData.postValue(selections.size());
    }
}
