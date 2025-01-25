// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import android.app.AppOpsManager;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.scanner.vt.VirusTotal;
import io.github.muntashirakon.AppManager.scanner.vt.VtFileReport;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.proc.ProcFs;
import io.github.muntashirakon.proc.ProcMemoryInfo;

public class RunningAppsViewModel extends AndroidViewModel {
    @RunningAppsActivity.SortOrder
    private int mSortOrder;
    @RunningAppsActivity.Filter
    private int mFilter;
    private final MultithreadedExecutor mExecutor = MultithreadedExecutor.getNewInstance();
    @Nullable
    private final VirusTotal mVt;

    public RunningAppsViewModel(@NonNull Application application) {
        super(application);
        mSortOrder = Prefs.RunningApps.getSortOrder();
        mFilter = Prefs.RunningApps.getFilters();
        mVt = VirusTotal.getInstance();
    }

    @Override
    protected void onCleared() {
        mExecutor.shutdownNow();
        super.onCleared();
    }

    public boolean isVirusTotalAvailable() {
        return mVt != null;
    }

    // Null = Uploading, NonNull = Queued
    private final MutableLiveData<Pair<ProcessItem, String>> mVtFileUpload = new MutableLiveData<>();
    // Null = Failed, NonNull = Result generated
    private final MutableLiveData<Pair<ProcessItem, VtFileReport>> mVtFileReport = new MutableLiveData<>();

    public MutableLiveData<Pair<ProcessItem, VtFileReport>> getVtFileReport() {
        return mVtFileReport;
    }

    public MutableLiveData<Pair<ProcessItem, String>> getVtFileUpload() {
        return mVtFileUpload;
    }

    @AnyThread
    public void scanWithVt(@NonNull ProcessItem processItem) {
        String file;
        if (processItem instanceof AppProcessItem) {
            file = ((AppProcessItem) processItem).packageInfo.applicationInfo.publicSourceDir;
        } else file = processItem.getCommandlineArgs()[0];
        if (mVt == null || file == null) {
            mVtFileReport.postValue(new Pair<>(processItem, null));
            return;
        }
        mExecutor.submit(() -> {
            Path proxyFile = Paths.get(file);
            if (!proxyFile.canRead()) {
                mVtFileReport.postValue(new Pair<>(processItem, null));
                return;
            }
            String sha256 = DigestUtils.getHexDigest(DigestUtils.SHA_256, proxyFile);
            try {
                mVt.fetchFileReportOrScan(proxyFile, sha256, new VirusTotal.FullScanResponseInterface() {
                    @Override
                    public boolean uploadFile() {
                        mUploadingEnabled = false;
                        mUploadingEnabledWatcher = new CountDownLatch(1);
                        mVtFileUpload.postValue(new Pair<>(processItem, null));
                        try {
                            mUploadingEnabledWatcher.await(2, TimeUnit.MINUTES);
                        } catch (InterruptedException ignore) {
                        }
                        return mUploadingEnabled;
                    }

                    @Override
                    public void onUploadInitiated() {
                    }

                    @Override
                    public void onUploadCompleted(@NonNull String permalink) {
                        mVtFileUpload.postValue(new Pair<>(processItem, permalink));
                    }

                    @Override
                    public void onReportReceived(@NonNull VtFileReport report) {
                        mVtFileReport.postValue(new Pair<>(processItem, report));
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                mVtFileReport.postValue(new Pair<>(processItem, null));
            }
        });
    }

    @NonNull
    private final MutableLiveData<List<ProcessItem>> mProcessLiveData = new MutableLiveData<>();

    @NonNull
    public LiveData<List<ProcessItem>> getProcessLiveData() {
        return mProcessLiveData;
    }

    @NonNull
    private final MutableLiveData<ProcessItem> mProcessItemLiveData = new MutableLiveData<>();

    @NonNull
    public LiveData<ProcessItem> observeProcessDetails() {
        return mProcessItemLiveData;
    }

    @AnyThread
    public void requestDisplayProcessDetails(@NonNull ProcessItem processItem) {
        mProcessItemLiveData.postValue(processItem);
    }

    @NonNull
    private final List<ProcessItem> mProcessList = new ArrayList<>();

    @AnyThread
    public void loadProcesses() {
        mExecutor.submit(() -> {
            synchronized (mProcessList) {
                try {
                    mProcessList.clear();
                    mProcessList.addAll(new ProcessParser().parse());
                    filterAndSort();
                } catch (Throwable th) {
                    Log.e("RunningApps", th);
                }
            }
        });
    }

    @NonNull
    private final MutableLiveData<ProcMemoryInfo> mDeviceMemoryInfo = new MutableLiveData<>();

    @NonNull
    public MutableLiveData<ProcMemoryInfo> getDeviceMemoryInfo() {
        return mDeviceMemoryInfo;
    }

    @AnyThread
    public void loadMemoryInfo() {
        mExecutor.submit(() -> mDeviceMemoryInfo.postValue(ProcFs.getInstance().getMemoryInfo()));
    }

    private final MutableLiveData<Pair<ProcessItem, Boolean>> mKillProcessResult = new MutableLiveData<>();
    private final MutableLiveData<List<ProcessItem>> mKillSelectedProcessesResult = new MutableLiveData<>();

    public void killProcess(ProcessItem processItem) {
        mExecutor.submit(() -> mKillProcessResult.postValue(new Pair<>(processItem, Runner.runCommand(
                new String[]{"kill", "-9", String.valueOf(processItem.pid)}).isSuccessful())));
    }

    public LiveData<Pair<ProcessItem, Boolean>> observeKillProcess() {
        return mKillProcessResult;
    }

    public void killSelectedProcesses() {
        mExecutor.submit(() -> {
            List<ProcessItem> failedProcesses = new ArrayList<>();
            for (ProcessItem processItem : mSelectedItems) {
                if (!Runner.runCommand(new String[]{"kill", "-9", String.valueOf(processItem.pid)}).isSuccessful()) {
                    failedProcesses.add(processItem);
                }
            }
            mKillSelectedProcessesResult.postValue(failedProcesses);
        });
    }

    public LiveData<List<ProcessItem>> observeKillSelectedProcess() {
        return mKillSelectedProcessesResult;
    }

    private final MutableLiveData<Pair<ApplicationInfo, Boolean>> mForceStopAppResult = new MutableLiveData<>();

    public void forceStop(@NonNull ApplicationInfo info) {
        mExecutor.submit(() -> {
            try {
                PackageManagerCompat.forceStopPackage(info.packageName, UserHandleHidden.getUserId(info.uid));
                mForceStopAppResult.postValue(new Pair<>(info, true));
            } catch (SecurityException e) {
                e.printStackTrace();
                mForceStopAppResult.postValue(new Pair<>(info, false));
            }
        });
    }

    public LiveData<Pair<ApplicationInfo, Boolean>> observeForceStop() {
        return mForceStopAppResult;
    }

    private final MutableLiveData<Pair<ApplicationInfo, Boolean>> mPreventBackgroundRunResult = new MutableLiveData<>();

    public boolean canRunInBackground(@NonNull ApplicationInfo info) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return true;
        }
        try {
            AppOpsManagerCompat appOpsManager = new AppOpsManagerCompat();
            boolean canRun;
            {
                int mode = appOpsManager.checkOperation(AppOpsManagerCompat.OP_RUN_IN_BACKGROUND, info.uid, info.packageName);
                canRun = (mode != AppOpsManager.MODE_IGNORED && mode != AppOpsManager.MODE_ERRORED);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                int mode = appOpsManager.checkOperation(AppOpsManagerCompat.OP_RUN_ANY_IN_BACKGROUND, info.uid, info.packageName);
                canRun |= (mode != AppOpsManager.MODE_IGNORED && mode != AppOpsManager.MODE_ERRORED);
            }
            return canRun;
        } catch (RemoteException | SecurityException e) {
            return true;
        }
    }

    public void preventBackgroundRun(@NonNull ApplicationInfo info) {
        mExecutor.submit(() -> {
            try {
                AppOpsManagerCompat appOpsService = new AppOpsManagerCompat();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appOpsService.setMode(AppOpsManagerCompat.OP_RUN_IN_BACKGROUND, info.uid, info.packageName,
                            AppOpsManager.MODE_IGNORED);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    appOpsService.setMode(AppOpsManagerCompat.OP_RUN_ANY_IN_BACKGROUND, info.uid, info.packageName,
                            AppOpsManager.MODE_IGNORED);
                }
                // TODO: 14/2/23 Store it to the rules
                mPreventBackgroundRunResult.postValue(new Pair<>(info, true));
            } catch (RemoteException e) {
                e.printStackTrace();
                mPreventBackgroundRunResult.postValue(new Pair<>(info, false));
            }
        });
    }

    public LiveData<Pair<ApplicationInfo, Boolean>> observePreventBackgroundRun() {
        return mPreventBackgroundRunResult;
    }

    public int getTotalCount() {
        return mProcessList.size();
    }

    private String mQuery;
    @AdvancedSearchView.SearchType
    private int mQueryType;

    public void setQuery(@Nullable String query, int searchType) {
        if (query == null) {
            mQuery = null;
        } else if (searchType == AdvancedSearchView.SEARCH_TYPE_PREFIX) {
            mQuery = query;
        } else {
            mQuery = query.toLowerCase(Locale.ROOT);
        }
        mQueryType = searchType;
        mExecutor.submit(this::filterAndSort);
    }

    public String getQuery() {
        return mQuery;
    }

    public void setSortOrder(int sortOrder) {
        mSortOrder = sortOrder;
        Prefs.RunningApps.setSortOrder(mSortOrder);
        mExecutor.submit(this::filterAndSort);
    }

    public int getSortOrder() {
        return mSortOrder;
    }

    public void addFilter(int filter) {
        mFilter |= filter;
        Prefs.RunningApps.setFilters(mFilter);
        mExecutor.submit(this::filterAndSort);
    }

    public void removeFilter(int filter) {
        mFilter &= ~filter;
        Prefs.RunningApps.setFilters(mFilter);
        mExecutor.submit(this::filterAndSort);
    }

    public int getFilter() {
        return mFilter;
    }

    @WorkerThread
    public void filterAndSort() {
        List<ProcessItem> filteredProcessList = new ArrayList<>();
        // Apply filters
        // There are 3 filters with “and” relations: apps > user apps > query
        boolean filterUserApps = (mFilter & RunningAppsActivity.FILTER_USER_APPS) != 0;
        // If user apps filter is enabled, disable it since it'll be just an overhead
        boolean filterApps = !filterUserApps && (mFilter & RunningAppsActivity.FILTER_APPS) != 0;
        ApplicationInfo info;
        for (ProcessItem item : mProcessList) {
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
        // Apply searching
        if (!TextUtils.isEmpty(mQuery)) {
            filteredProcessList = AdvancedSearchView.matches(mQuery, filteredProcessList,
                    (AdvancedSearchView.ChoicesGenerator<ProcessItem>) item -> {
                        if (item instanceof AppProcessItem) {
                            return Arrays.asList(item.name.toLowerCase(Locale.getDefault()),
                                    ((AppProcessItem) item).packageInfo.packageName.toLowerCase(Locale.getDefault()));
                        }
                        return Collections.singletonList(item.name.toLowerCase(Locale.getDefault()));
                    }, mQueryType);
        }
        // Apply sorts
        // Sort by pid first
        //noinspection ComparatorCombinators
        Collections.sort(filteredProcessList, (o1, o2) -> Integer.compare(o1.pid, o2.pid));
        if (mSortOrder != RunningAppsActivity.SORT_BY_PID) {
            Collections.sort(filteredProcessList, (o1, o2) -> {
                ProcessItem p1 = Objects.requireNonNull(o1);
                ProcessItem p2 = Objects.requireNonNull(o2);
                switch (mSortOrder) {
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
        mProcessLiveData.postValue(filteredProcessList);
    }

    private final Set<ProcessItem> mSelectedItems = new LinkedHashSet<>();

    @Nullable
    public ProcessItem getLastSelectedItem() {
        // Last selected package is the same as the last added package.
        Iterator<ProcessItem> it = mSelectedItems.iterator();
        ProcessItem lastItem = null;
        while (it.hasNext()) {
            lastItem = it.next();
        }
        return lastItem;
    }

    public int getSelectionCount() {
        return mSelectedItems.size();
    }

    public boolean isSelected(@NonNull ProcessItem processItem) {
        return mSelectedItems.contains(processItem);
    }

    public void select(@Nullable ProcessItem processItem) {
        if (processItem != null) {
            mSelectedItems.add(processItem);
        }
    }

    public void deselect(@Nullable ProcessItem processItem) {
        if (processItem != null) {
            mSelectedItems.remove(processItem);
        }
    }

    public ArrayList<ProcessItem> getSelections() {
        return new ArrayList<>(mSelectedItems);
    }

    @NonNull
    public ArrayList<UserPackagePair> getSelectedPackagesWithUsers() {
        ArrayList<UserPackagePair> userPackagePairs = new ArrayList<>();
        for (ProcessItem processItem : mSelectedItems) {
            if (processItem instanceof AppProcessItem) {
                ApplicationInfo applicationInfo = ((AppProcessItem) processItem).packageInfo.applicationInfo;
                userPackagePairs.add(new UserPackagePair(applicationInfo.packageName,
                        UserHandleHidden.getUserId(applicationInfo.uid)));
            }
        }
        return userPackagePairs;
    }

    public void clearSelections() {
        mSelectedItems.clear();
    }

    private boolean mUploadingEnabled;
    private CountDownLatch mUploadingEnabledWatcher;

    public void enableUploading() {
        mUploadingEnabled = true;
        if (mUploadingEnabledWatcher != null) {
            mUploadingEnabledWatcher.countDown();
        }
    }

    public void disableUploading() {
        mUploadingEnabled = false;
        if (mUploadingEnabledWatcher != null) {
            mUploadingEnabledWatcher.countDown();
        }
    }
}
