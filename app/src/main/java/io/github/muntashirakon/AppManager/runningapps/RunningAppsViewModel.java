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
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class RunningAppsViewModel extends AndroidViewModel {
    public RunningAppsViewModel(@NonNull Application application) {
        super(application);
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
        // TODO(13/9/20): Apply sorting, search, and filters
        filter();
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

    public void setQuery(String query) {
        this.query = query;
        filter();
    }

    public String getQuery() {
        return query;
    }

    @WorkerThread
    public void filter() {
        filteredProcessList.clear();
        if (!TextUtils.isEmpty(query)) {
            for (int item : processList.keySet()) {
                if (Objects.requireNonNull(processList.get(item)).name
                        .toLowerCase(Locale.ROOT).contains(query)) {
                    filteredProcessList.add(item);
                }
            }
        } else filteredProcessList.addAll(originalProcessList);
        // TODO: Apply filters and sorting
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
