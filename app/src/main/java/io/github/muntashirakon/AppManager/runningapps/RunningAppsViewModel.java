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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.scanner.vt.VirusTotal;
import io.github.muntashirakon.AppManager.scanner.vt.VtFileReport;
import io.github.muntashirakon.AppManager.scanner.vt.VtFileScanMeta;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.io.ProxyFile;
import io.github.muntashirakon.io.ProxyInputStream;

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
        mSortOrder = (int) AppPref.get(AppPref.PrefKey.PREF_RUNNING_APPS_SORT_ORDER_INT);
        mFilter = (int) AppPref.get(AppPref.PrefKey.PREF_RUNNING_APPS_FILTER_FLAGS_INT);
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
    private final MutableLiveData<Pair<ProcessItem, VtFileScanMeta>> mVtFileScanMeta = new MutableLiveData<>();
    // Null = Failed, NonNull = Result generated
    private final MutableLiveData<Pair<ProcessItem, VtFileReport>> mVtFileReport = new MutableLiveData<>();

    public MutableLiveData<Pair<ProcessItem, VtFileReport>> getVtFileReport() {
        return mVtFileReport;
    }

    public MutableLiveData<Pair<ProcessItem, VtFileScanMeta>> getVtFileScanMeta() {
        return mVtFileScanMeta;
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
            ProxyFile proxyFile = new ProxyFile(file);
            if (!proxyFile.canRead()) {
                mVtFileReport.postValue(new Pair<>(processItem, null));
                return;
            }
            String sha256 = DigestUtils.getHexDigest(DigestUtils.SHA_256, proxyFile);
            try (InputStream is = new ProxyInputStream(proxyFile)) {
                mVt.fetchReportsOrScan(proxyFile.getName(), proxyFile.length(), is, sha256,
                        new VirusTotal.FullScanResponseInterface() {
                            @Override
                            public boolean scanFile() {
                                mUploadingEnabled = false;
                                mUploadingEnabledWatcher = new CountDownLatch(1);
                                mVtFileScanMeta.postValue(new Pair<>(processItem, null));
                                try {
                                    mUploadingEnabledWatcher.await(2, TimeUnit.MINUTES);
                                } catch (InterruptedException ignore) {
                                }
                                return mUploadingEnabled;
                            }

                            @Override
                            public void onScanningInitiated() {
                            }

                            @Override
                            public void onScanCompleted(@NonNull VtFileScanMeta meta) {
                                mVtFileScanMeta.postValue(new Pair<>(processItem, meta));
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

    private MutableLiveData<List<ProcessItem>> mProcessLiveData;

    public LiveData<List<ProcessItem>> getProcessLiveData() {
        if (mProcessLiveData == null) {
            mProcessLiveData = new MutableLiveData<>();
        }
        return mProcessLiveData;
    }


    private final MutableLiveData<ProcessItem> mProcessItemLiveData = new MutableLiveData<>();

    public LiveData<ProcessItem> observeProcessDetails() {
        return mProcessItemLiveData;
    }

    @AnyThread
    public void requestDisplayProcessDetails(@NonNull ProcessItem processItem) {
        mProcessItemLiveData.postValue(processItem);
    }

    @NonNull
    private final List<ProcessItem> mProcessList = new ArrayList<>();
    @NonNull
    private final List<ProcessItem> mFilteredProcessList = new ArrayList<>();

    @AnyThread
    public void loadProcesses() {
        mExecutor.submit(() -> {
            synchronized (mProcessList) {
                mProcessList.clear();
                mProcessList.addAll(new ProcessParser().parse());
                filterAndSort();
            }
        });
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
            } catch (RemoteException e) {
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
            AppOpsService appOpsService = new AppOpsService();
            boolean canRun;
            {
                int mode = appOpsService.checkOperation(AppOpsManager.OP_RUN_IN_BACKGROUND, info.uid, info.packageName);
                canRun = (mode != AppOpsManager.MODE_IGNORED && mode != AppOpsManager.MODE_ERRORED);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                int mode = appOpsService.checkOperation(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND, info.uid, info.packageName);
                canRun |= (mode != AppOpsManager.MODE_IGNORED && mode != AppOpsManager.MODE_ERRORED);
            }
            return canRun;
        } catch (RemoteException e) {
            return true;
        }
    }

    public void preventBackgroundRun(@NonNull ApplicationInfo info) {
        mExecutor.submit(() -> {
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

    public void setQuery(@Nullable String query) {
        this.mQuery = query == null ? null : query.toLowerCase(Locale.ROOT);
        mExecutor.submit(this::filterAndSort);
    }

    public String getQuery() {
        return mQuery;
    }

    public void setSortOrder(int sortOrder) {
        this.mSortOrder = sortOrder;
        AppPref.set(AppPref.PrefKey.PREF_RUNNING_APPS_SORT_ORDER_INT, this.mSortOrder);
        mExecutor.submit(this::filterAndSort);
    }

    public int getSortOrder() {
        return mSortOrder;
    }

    public void addFilter(int filter) {
        this.mFilter |= filter;
        AppPref.set(AppPref.PrefKey.PREF_RUNNING_APPS_FILTER_FLAGS_INT, this.mFilter);
        mExecutor.submit(this::filterAndSort);
    }

    public void removeFilter(int filter) {
        this.mFilter &= ~filter;
        AppPref.set(AppPref.PrefKey.PREF_RUNNING_APPS_FILTER_FLAGS_INT, this.mFilter);
        mExecutor.submit(this::filterAndSort);
    }

    public int getFilter() {
        return mFilter;
    }

    @WorkerThread
    public void filterAndSort() {
        mFilteredProcessList.clear();
        // Apply filters
        // There are 3 filters with “and” relations: query > apps > user apps
        boolean hasQuery = !TextUtils.isEmpty(mQuery);
        boolean filterUserApps = (mFilter & RunningAppsActivity.FILTER_USER_APPS) != 0;
        // If user apps filter is enabled, disable it since it'll be just an overhead
        boolean filterApps = !filterUserApps && (mFilter & RunningAppsActivity.FILTER_APPS) != 0;
        ApplicationInfo info;
        for (ProcessItem item : mProcessList) {
            // Filter by query
            if (hasQuery && !item.name.toLowerCase(Locale.ROOT).contains(mQuery)) {
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
            mFilteredProcessList.add(item);
        }
        // Apply sorts
        // Sort by pid first
        //noinspection ComparatorCombinators
        Collections.sort(mFilteredProcessList, (o1, o2) -> Integer.compare(o1.pid, o2.pid));
        if (mSortOrder != RunningAppsActivity.SORT_BY_PID) {
            Collections.sort(mFilteredProcessList, (o1, o2) -> {
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
        mProcessLiveData.postValue(mFilteredProcessList);
    }

    private final Set<ProcessItem> mSelectedItems = new HashSet<>();

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
