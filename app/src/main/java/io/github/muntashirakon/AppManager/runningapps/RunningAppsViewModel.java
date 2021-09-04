// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandleHidden;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
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

    private MutableLiveData<List<ProcessItem>> processLiveData;

    public LiveData<List<ProcessItem>> getProcessLiveData() {
        if (processLiveData == null) {
            processLiveData = new MutableLiveData<>();
        }
        return processLiveData;
    }

    @NonNull
    private final List<ProcessItem> processList = new ArrayList<>();
    @NonNull
    private final List<ProcessItem> filteredProcessList = new ArrayList<>();

    @AnyThread
    public void loadProcesses() {
        executor.submit(() -> {
            synchronized (processList) {
                processList.clear();
                processList.addAll(new ProcessParser().parse());
                filterAndSort();
            }
        });
    }

    private final MutableLiveData<Pair<ProcessItem, Boolean>> killProcessResult = new MutableLiveData<>();
    private final MutableLiveData<List<ProcessItem>> killSelectedProcessesResult = new MutableLiveData<>();

    public void killProcess(ProcessItem processItem) {
        executor.submit(() -> killProcessResult.postValue(new Pair<>(processItem, Runner.runCommand(
                new String[]{"kill", "-9", String.valueOf(processItem.pid)}).isSuccessful())));
    }

    public LiveData<Pair<ProcessItem, Boolean>> observeKillProcess() {
        return killProcessResult;
    }

    public void killSelectedProcesses() {
        executor.submit(() -> {
            List<ProcessItem> failedProcesses = new ArrayList<>();
            for (ProcessItem processItem : selectedItems) {
                if (!Runner.runCommand(new String[]{"kill", "-9", String.valueOf(processItem.pid)}).isSuccessful()) {
                    failedProcesses.add(processItem);
                }
            }
            killSelectedProcessesResult.postValue(failedProcesses);
        });
    }

    public LiveData<List<ProcessItem>> observeKillSelectedProcess() {
        return killSelectedProcessesResult;
    }

    private final MutableLiveData<Pair<ApplicationInfo, Boolean>> forceStopAppResult = new MutableLiveData<>();

    public void forceStop(@NonNull ApplicationInfo info) {
        executor.submit(() -> {
            try {
                PackageManagerCompat.forceStopPackage(info.packageName, UserHandleHidden.getUserId(info.uid));
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

    public int getTotalCount() {
        return processList.size();
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
        ApplicationInfo info;
        for (ProcessItem item : processList) {
            // Filter by query
            if (hasQuery && !item.name.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            // Filter by apps
            if (filterApps && !(item instanceof AppProcessItem)) {
                continue;
            }
            // Filter by user apps
            if (filterUserApps) {
                if (item instanceof AppProcessItem) {
                    info = ((AppProcessItem) item).packageInfo.applicationInfo;
                    if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                    // else it's an user app
                } else continue;
            }
            filteredProcessList.add(item);
        }
        // Apply sorts
        // Sort by pid first
        //noinspection ComparatorCombinators
        Collections.sort(filteredProcessList, (o1, o2) -> Integer.compare(o1.pid, o2.pid));
        if (sortOrder != RunningAppsActivity.SORT_BY_PID) {
            Collections.sort(filteredProcessList, (o1, o2) -> {
                ProcessItem p1 = Objects.requireNonNull(o1);
                ProcessItem p2 = Objects.requireNonNull(o2);
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

    private final Set<ProcessItem> selectedItems = new HashSet<>();

    public int getSelectionCount() {
        return selectedItems.size();
    }

    public boolean isSelected(@NonNull ProcessItem processItem) {
        return selectedItems.contains(processItem);
    }

    public void select(@Nullable ProcessItem processItem) {
        if (processItem != null) {
            selectedItems.add(processItem);
        }
    }

    public void deselect(@Nullable ProcessItem processItem) {
        if (processItem != null) {
            selectedItems.remove(processItem);
        }
    }

    public ArrayList<ProcessItem> getSelections() {
        return new ArrayList<>(selectedItems);
    }

    @NonNull
    public ArrayList<UserPackagePair> getSelectedPackagesWithUsers() {
        ArrayList<UserPackagePair> userPackagePairs = new ArrayList<>();
        for (ProcessItem processItem : selectedItems) {
            if (processItem instanceof AppProcessItem) {
                ApplicationInfo applicationInfo = ((AppProcessItem) processItem).packageInfo.applicationInfo;
                userPackagePairs.add(new UserPackagePair(applicationInfo.packageName,
                        UserHandleHidden.getUserId(applicationInfo.uid)));
            }
        }
        return userPackagePairs;
    }

    public void clearSelections() {
        selectedItems.clear();
    }
}
