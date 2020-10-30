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

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.github.muntashirakon.AppManager.utils.AppPref;

public class RunningAppsViewModel extends AndroidViewModel {
    @RunningAppsActivity.SortOrder
    private int sortOrder;
    @RunningAppsActivity.Filter
    private int filter;

    public RunningAppsViewModel(@NonNull Application application) {
        super(application);
        sortOrder = (int) AppPref.get(AppPref.PrefKey.PREF_RUNNING_APPS_SORT_ORDER_INT);
        filter = (int) AppPref.get(AppPref.PrefKey.PREF_RUNNING_APPS_FILTER_FLAGS_INT);
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

    @WorkerThread
    public void loadProcesses() {
        processList = new ProcessParser().parse();
        for (int pid : selections) {
            ProcessItem processItem = processList.get(pid);
            if (processItem != null) processItem.selected = false;
        }
        originalProcessList.clear();
        originalProcessList.addAll(new ArrayList<>(processList.keySet()));
        filterAndSort();
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
        new Thread(this::filterAndSort).start();
    }

    public String getQuery() {
        return query;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
        AppPref.set(AppPref.PrefKey.PREF_RUNNING_APPS_SORT_ORDER_INT, this.sortOrder);
        new Thread(this::filterAndSort).start();
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void addFilter(int filter) {
        this.filter |= filter;
        AppPref.set(AppPref.PrefKey.PREF_RUNNING_APPS_FILTER_FLAGS_INT, this.filter);
        new Thread(this::filterAndSort).start();
    }

    public void removeFilter(int filter) {
        this.filter &= ~filter;
        AppPref.set(AppPref.PrefKey.PREF_RUNNING_APPS_FILTER_FLAGS_INT, this.filter);
        new Thread(this::filterAndSort).start();
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

    private Set<Integer> selections = new HashSet<>();

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
