// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.StorageManagerCompat;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagDisabledComponents;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagMatchUninstalled;

public class OneClickOpsViewModel extends AndroidViewModel {
    public static final String TAG = OneClickOpsViewModel.class.getSimpleName();

    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final PackageManager pm;

    private final MutableLiveData<List<ItemCount>> trackerCount = new MutableLiveData<>();
    private final MutableLiveData<Pair<List<ItemCount>, String[]>> componentCount = new MutableLiveData<>();
    private final MutableLiveData<Pair<List<AppOpCount>, Pair<int[], Integer>>> appOpsCount = new MutableLiveData<>();
    private final MutableLiveData<Boolean> trimCachesResult = new MutableLiveData<>();

    public OneClickOpsViewModel(@NonNull Application application) {
        super(application);
        pm = application.getPackageManager();
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
        super.onCleared();
    }

    public LiveData<List<ItemCount>> watchTrackerCount() {
        return trackerCount;
    }

    public LiveData<Pair<List<ItemCount>, String[]>> watchComponentCount() {
        return componentCount;
    }

    public LiveData<Pair<List<AppOpCount>, Pair<int[], Integer>>> watchAppOpsCount() {
        return appOpsCount;
    }

    public LiveData<Boolean> watchTrimCachesResult() {
        return trimCachesResult;
    }

    @AnyThread
    public void blockTrackers(boolean systemApps) {
        executor.submit(() -> {
            List<ItemCount> trackerCounts = new ArrayList<>();
            HashSet<String> packageNames = new HashSet<>();
            ItemCount trackerCount;
            for (PackageInfo packageInfo : PackageUtils.getAllPackages(PackageManager.GET_ACTIVITIES
                    | PackageManager.GET_RECEIVERS | flagDisabledComponents | flagMatchUninstalled
                    | PackageManager.GET_PROVIDERS | PackageManager.GET_SERVICES)) {
                if (packageNames.contains(packageInfo.packageName)) {
                    continue;
                }
                packageNames.add(packageInfo.packageName);
                if (Thread.currentThread().isInterrupted()) return;
                ApplicationInfo applicationInfo = packageInfo.applicationInfo;
                if (!Ops.isRoot() && !PackageUtils.isTestOnlyApp(applicationInfo))
                    continue;
                if (!systemApps && (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                    continue;
                trackerCount = getTrackerCountForApp(packageInfo);
                if (trackerCount.count > 0) trackerCounts.add(trackerCount);
            }
            this.trackerCount.postValue(trackerCounts);
        });
    }

    @AnyThread
    public void blockComponents(boolean systemApps, @NonNull String[] signatures) {
        if (signatures.length == 0) return;
        executor.submit(() -> {
            List<ItemCount> componentCounts = new ArrayList<>();
            HashSet<String> packageNames = new HashSet<>();
            for (ApplicationInfo applicationInfo : PackageUtils.getAllApplications(flagMatchUninstalled)) {
                if (packageNames.contains(applicationInfo.packageName)) {
                    continue;
                }
                packageNames.add(applicationInfo.packageName);
                if (Thread.currentThread().isInterrupted()) return;
                if (!Ops.isRoot() && !PackageUtils.isTestOnlyApp(applicationInfo))
                    continue;
                if (!systemApps && (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                    continue;
                ItemCount componentCount = new ItemCount();
                componentCount.packageName = applicationInfo.packageName;
                componentCount.packageLabel = applicationInfo.loadLabel(pm).toString();
                componentCount.count = PackageUtils.getFilteredComponents(applicationInfo.packageName,
                        UserHandleHidden.myUserId(), signatures).size();
                if (componentCount.count > 0) componentCounts.add(componentCount);
            }
            this.componentCount.postValue(new Pair<>(componentCounts, signatures));
        });
    }

    @AnyThread
    public void setAppOps(int[] appOpList, int mode, boolean systemApps) {
        executor.submit(() -> {
            Pair<int[], Integer> appOpsModePair = new Pair<>(appOpList, mode);
            List<AppOpCount> appOpCounts = new ArrayList<>();
            HashSet<String> packageNames = new HashSet<>();
            for (ApplicationInfo applicationInfo : PackageUtils.getAllApplications(flagMatchUninstalled)) {
                if (packageNames.contains(applicationInfo.packageName)) {
                    continue;
                }
                packageNames.add(applicationInfo.packageName);
                if (Thread.currentThread().isInterrupted()) return;
                if (!systemApps && (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                    continue;
                AppOpCount appOpCount = new AppOpCount();
                appOpCount.packageName = applicationInfo.packageName;
                appOpCount.packageLabel = applicationInfo.loadLabel(pm).toString();
                appOpCount.appOps = PackageUtils.getFilteredAppOps(applicationInfo.packageName,
                        UserHandleHidden.myUserId(), appOpList, mode);
                appOpCount.count = appOpCount.appOps.size();
                if (appOpCount.count > 0) appOpCounts.add(appOpCount);
            }
            this.appOpsCount.postValue(new Pair<>(appOpCounts, appOpsModePair));
        });
    }

    @AnyThread
    public void trimCaches() {
        executor.submit(() -> {
            long size = 1024L * 1024L * 1024L * 1024L;  // 1 TB
            try {
                // TODO: 30/8/21 Iterate all volumes?
                PackageManagerCompat.freeStorageAndNotify(null /* internal */, size,
                        StorageManagerCompat.FLAG_ALLOCATE_DEFY_ALL_RESERVED);
                this.trimCachesResult.postValue(true);
            } catch (RemoteException e) {
                this.trimCachesResult.postValue(false);
            }
        });
    }

    @NonNull
    private ItemCount getTrackerCountForApp(@NonNull PackageInfo packageInfo) {
        ItemCount trackerCount = new ItemCount();
        trackerCount.packageName = packageInfo.packageName;
        trackerCount.packageLabel = packageInfo.applicationInfo.loadLabel(pm).toString();
        trackerCount.count = ComponentUtils.getTrackerComponentsForPackage(packageInfo).size();
        return trackerCount;
    }
}
