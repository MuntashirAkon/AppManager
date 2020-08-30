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

package io.github.muntashirakon.AppManager.main;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Tuple;
import io.github.muntashirakon.AppManager.utils.Utils;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagDisabledComponents;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagSigningInfo;

public class MainViewModel extends AndroidViewModel {
    private static Collator sCollator = Collator.getInstance();

    private PackageManager mPackageManager;
    private PackageIntentReceiver mPackageObserver;
    private Handler mHandler;
    private @MainActivity.SortOrder
    int mSortBy;
    private @MainActivity.Filter
    int mFilterFlags;
    private String searchQuery;
    private List<String> backupApplications;
    private Set<String> selectedPackages = new HashSet<>();
    private List<ApplicationItem> selectedApplicationItems = new LinkedList<>();

    public MainViewModel(@NonNull Application application) {
        super(application);
        Log.d("MVM", "New instance created");
        mPackageManager = application.getPackageManager();
        mHandler = new Handler(application.getMainLooper());
        mPackageObserver = new PackageIntentReceiver(this);
        mSortBy = (int) AppPref.get(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_ORDER_INT);
        mFilterFlags = (int) AppPref.get(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_FLAGS_INT);
    }

    private MutableLiveData<List<ApplicationItem>> applicationItemsLiveData;
    final private List<ApplicationItem> applicationItems = new ArrayList<>();

    @NonNull
    public LiveData<List<ApplicationItem>> getApplicationItems() {
        if (applicationItemsLiveData == null) {
            applicationItemsLiveData = new MutableLiveData<>();
            loadApplicationItems();
        }
        return applicationItemsLiveData;
    }

    public ApplicationItem deselect(@NonNull ApplicationItem item) {
        int i = applicationItems.indexOf(item);
        if (i == -1) return item;
        selectedPackages.remove(item.packageName);
        selectedApplicationItems.remove(item);
        item.isSelected = false;
        applicationItems.set(i, item);
        return item;
    }

    public ApplicationItem select(@NonNull ApplicationItem item) {
        int i = applicationItems.indexOf(item);
        if (i == -1) return item;
        selectedPackages.add(item.packageName);
        item.isSelected = true;
        applicationItems.set(i, item);
        selectedApplicationItems.add(item);
        return item;
    }

    public void clearSelection() {
        selectedPackages.clear();
        int i;
        for (ApplicationItem item : selectedApplicationItems) {
            i = applicationItems.indexOf(item);
            if (i == -1) continue;
            item.isSelected = false;
            applicationItems.set(i, item);
        }
        selectedApplicationItems.clear();
    }

    public Set<String> getSelectedPackages() {
        return selectedPackages;
    }

    public List<ApplicationItem> getSelectedApplicationItems() {
        return selectedApplicationItems;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
        new Thread(this::filterItemsByFlags).start();
    }

    public void setSortBy(int sortBy) {
        if (mSortBy != sortBy) {
            new Thread(() -> {
                sortApplicationList(sortBy);
                filterItemsByFlags();
            }).start();
        }
        mSortBy = sortBy;
        AppPref.getInstance().setPref(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_ORDER_INT, mSortBy);
    }

    public int getFilterFlags() {
        return mFilterFlags;
    }

    public void addFilterFlag(@MainActivity.Filter int filterFlag) {
        mFilterFlags |= filterFlag;
        AppPref.getInstance().setPref(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_FLAGS_INT, mFilterFlags);
        new Thread(() -> {
            synchronized (applicationItems) {
                filterItemsByFlags();
            }
        }).start();
    }

    public void removeFilterFlag(@MainActivity.Filter int filterFlag) {
        mFilterFlags &= ~filterFlag;
        AppPref.getInstance().setPref(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_FLAGS_INT, mFilterFlags);
        new Thread(() -> {
            synchronized (applicationItems) {
                filterItemsByFlags();
            }
        }).start();
    }

    @SuppressLint("PackageManagerGetSignatures")
    public void loadApplicationItems() {
        new Thread(() -> {
            synchronized (applicationItems) {
                applicationItems.clear();
                backupApplications = BackupUtils.getBackupApplications();
                Log.d("backupApplications", backupApplications.toString());
                if (MainActivity.packageList != null) {
                    String[] packageList = MainActivity.packageList.split("[\\r\\n]+");
                    for (String packageName : packageList) {
                        try {
                            @SuppressLint("WrongConstant")
                            PackageInfo packageInfo = mPackageManager.getPackageInfo(packageName,
                                    PackageManager.GET_META_DATA | flagSigningInfo |
                                            PackageManager.GET_ACTIVITIES | flagDisabledComponents);
                            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
                            ApplicationItem item = new ApplicationItem(applicationInfo);
                            item.versionName = packageInfo.versionName;
                            item.versionCode = PackageUtils.getVersionCode(packageInfo);
                            if (backupApplications.contains(packageName)) {
                                item.metadataV1 = BackupUtils.getBackupInfo(packageName);
                                backupApplications.remove(packageName);
                            }
                            item.flags = applicationInfo.flags;
                            item.uid = applicationInfo.uid;
                            item.sharedUserId = packageInfo.sharedUserId;
                            item.debuggable = (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
                            item.isUser = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
                            item.isDisabled = !applicationInfo.enabled;
                            item.hasActivities = packageInfo.activities != null;
                            item.label = applicationInfo.loadLabel(mPackageManager).toString();
                            item.firstInstallTime = packageInfo.firstInstallTime;
                            item.lastUpdateTime = packageInfo.lastUpdateTime;
                            item.sha = Utils.getIssuerAndAlg(packageInfo);
                            item.sdk = applicationInfo.targetSdkVersion;
                            applicationItems.add(item);
                        } catch (PackageManager.NameNotFoundException ignored) {
                        }
                    }
                } else {
                    List<ApplicationInfo> applicationInfoList = mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA);
                    for (ApplicationInfo applicationInfo : applicationInfoList) {
                        ApplicationItem item = new ApplicationItem(applicationInfo);
                        if (backupApplications.contains(applicationInfo.packageName)) {
                            item.metadataV1 = BackupUtils.getBackupInfo(applicationInfo.packageName);
                            backupApplications.remove(applicationInfo.packageName);
                        }
                        item.flags = applicationInfo.flags;
                        item.uid = applicationInfo.uid;
                        item.debuggable = (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
                        item.isUser = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
                        item.isDisabled = !applicationInfo.enabled;
                        item.label = applicationInfo.loadLabel(mPackageManager).toString();
                        item.sdk = applicationInfo.targetSdkVersion;
                        try {
                            @SuppressLint("WrongConstant")
                            PackageInfo packageInfo = mPackageManager.getPackageInfo(
                                    applicationInfo.packageName, flagSigningInfo |
                                            PackageManager.GET_ACTIVITIES | flagDisabledComponents);
                            item.versionName = packageInfo.versionName;
                            item.versionCode = PackageUtils.getVersionCode(packageInfo);
                            item.sharedUserId = packageInfo.sharedUserId;
                            item.sha = Utils.getIssuerAndAlg(packageInfo);
                            item.firstInstallTime = packageInfo.firstInstallTime;
                            item.lastUpdateTime = packageInfo.lastUpdateTime;
                            item.hasActivities = packageInfo.activities != null;
                        } catch (PackageManager.NameNotFoundException e) {
                            item.lastUpdateTime = 0L;
                            item.sha = new Tuple<>("?", "?");
                        }
                        item.blockedCount = 0;
                        applicationItems.add(item);
                    }
                }
                for (String packageName : backupApplications) {
                    ApplicationItem item = new ApplicationItem();
                    item.packageName = packageName;
                    item.metadataV1 = BackupUtils.getBackupInfo(packageName);
                    if (item.metadataV1 == null) continue;
                    item.versionName = item.metadataV1.versionName;
                    item.versionCode = item.metadataV1.versionCode;
                    item.label = item.metadataV1.label;
                    Log.e("MVM", item.label);
                    item.firstInstallTime = item.metadataV1.backupTime;
                    item.lastUpdateTime = item.metadataV1.backupTime;
                    item.isUser = !item.metadataV1.isSystem;
                    item.isDisabled = false;
                    item.isInstalled = false;
                    applicationItems.add(item);
                }
                sortApplicationList(mSortBy);
                filterItemsByFlags();
            }
        }).start();
    }

    private void filterItemsByQuery(@NonNull List<ApplicationItem> applicationItems) {
        List<ApplicationItem> filteredApplicationItems = new ArrayList<>();
        ApplicationItem item;
        for (int i = 0; i < applicationItems.size(); ++i) {
            item = applicationItems.get(i);
            if (item.label.toLowerCase(Locale.ROOT).contains(searchQuery)
                    || item.packageName.toLowerCase(Locale.ROOT).contains(searchQuery))
                filteredApplicationItems.add(item);
        }
        mHandler.post(() -> applicationItemsLiveData.postValue(filteredApplicationItems));
    }

    private void filterItemsByFlags() {
        if (mFilterFlags == MainActivity.FILTER_NO_FILTER) {
            if (!TextUtils.isEmpty(searchQuery)) {
                filterItemsByQuery(applicationItems);
            } else {
                mHandler.post(() -> applicationItemsLiveData.postValue(applicationItems));
            }
        } else {
            List<ApplicationItem> filteredApplicationItems = new ArrayList<>();
            if ((mFilterFlags & MainActivity.FILTER_APPS_WITH_RULES) != 0) {
                loadBlockingRules();
            }
            ApplicationItem item;
            for (int i = 0; i < applicationItems.size(); ++i) {
                item = applicationItems.get(i);
                // Filter user and system apps first (if requested)
                if ((mFilterFlags & MainActivity.FILTER_USER_APPS) != 0 && !item.isUser) {
                    continue;
                } else if ((mFilterFlags & MainActivity.FILTER_SYSTEM_APPS) != 0 && item.isUser) {
                    continue;
                }
                // Filter rests
                if ((mFilterFlags & MainActivity.FILTER_DISABLED_APPS) != 0 && !item.isDisabled) {
                    continue;
                } else if ((mFilterFlags & MainActivity.FILTER_APPS_WITH_RULES) != 0 && item.blockedCount <= 0) {
                    continue;
                } else if ((mFilterFlags & MainActivity.FILTER_APPS_WITH_ACTIVITIES) != 0 && !item.hasActivities) {
                    continue;
                }
                filteredApplicationItems.add(item);
            }
            if (!TextUtils.isEmpty(searchQuery)) {
                filterItemsByQuery(filteredApplicationItems);
            } else {
                mHandler.post(() -> applicationItemsLiveData.postValue(filteredApplicationItems));
            }
        }
    }

    private void loadBlockingRules() {
        for (int i = 0; i < applicationItems.size(); ++i) {
            ApplicationItem applicationItem = applicationItems.get(i);
            try (ComponentsBlocker cb = ComponentsBlocker.getInstance(applicationItem.packageName, true)) {
                applicationItem.blockedCount = cb.componentCount();
            }
            applicationItems.set(i, applicationItem);
        }
    }

    private void sortApplicationList(@MainActivity.SortOrder int sortBy) {
        final boolean isRootEnabled = AppPref.isRootEnabled();
        if (sortBy != MainActivity.SORT_BY_APP_LABEL)
            sortApplicationList(MainActivity.SORT_BY_APP_LABEL);
        if (sortBy == MainActivity.SORT_BY_BLOCKED_COMPONENTS && isRootEnabled) loadBlockingRules();
        Collections.sort(applicationItems, (o1, o2) -> {
            switch (sortBy) {
                case MainActivity.SORT_BY_APP_LABEL:
                    return sCollator.compare(o1.label, o2.label);
                case MainActivity.SORT_BY_PACKAGE_NAME:
                    return o1.packageName.compareTo(o2.packageName);
                case MainActivity.SORT_BY_DOMAIN:
                    boolean isSystem1 = (o1.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    boolean isSystem2 = (o2.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    return Utils.compareBooleans(isSystem1, isSystem2);
                case MainActivity.SORT_BY_LAST_UPDATE:
                    // Sort in decreasing order
                    return -o1.lastUpdateTime.compareTo(o2.lastUpdateTime);
                case MainActivity.SORT_BY_TARGET_SDK:
                    return -o1.sdk.compareTo(o2.sdk);
                case MainActivity.SORT_BY_SHARED_ID:
                    return o2.uid - o1.uid;
                case MainActivity.SORT_BY_SHA:
                    if (o1.sha == null && o2.sha != null) return 0;
                    else if (o1.sha == null) return -1;
                    else if (o2.sha == null) return +1;
                    else {
                        try {
                            return o1.sha.compareTo(o2.sha);
                        } catch (NullPointerException ignored) {
                        }
                    }
                    break;
                case MainActivity.SORT_BY_BLOCKED_COMPONENTS:
                    if (isRootEnabled)
                        return -o1.blockedCount.compareTo(o2.blockedCount);
                    break;
                case MainActivity.SORT_BY_DISABLED_APP:
                    return Utils.compareBooleans(!o1.isDisabled, !o2.isDisabled);
            }
            return 0;
        });
    }

    private void updateInfoForUid(int uid, String action) {
        Log.d("updateInfoForUid", "Uid: " + uid);
        String[] packages;
        if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) packages = getPackagesForUid(uid);
        else packages = mPackageManager.getPackagesForUid(uid);
        updateInfoForPackages(packages, action);
    }

    private void updateInfoForPackages(@Nullable String[] packages, @NonNull String action) {
        Log.d("updateInfoForPackages", "packages: " + Arrays.toString(packages));
        if (packages == null || packages.length == 0) return;
        switch (action) {
            case Intent.ACTION_PACKAGE_REMOVED:
            case Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE:
                for (String packageName : packages) {
                    try {
                        mPackageManager.getApplicationInfo(packageName, 0);
                    } catch (PackageManager.NameNotFoundException e) {
                        removePackageIfNoBackup(packageName);
                    }
                }
                filterItemsByFlags();
                break;
            case Intent.ACTION_PACKAGE_CHANGED:
                for (String packageName : packages) {
                    ApplicationItem item = getNewApplicationItem(packageName);
                    if (item != null) insertApplicationItem(item);
                }
                sortApplicationList(mSortBy);
                filterItemsByFlags();
                break;
            case Intent.ACTION_PACKAGE_ADDED:
            case Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE:
                for (String packageName : packages) {
                    ApplicationItem item = getNewApplicationItem(packageName);
                    if (item != null) insertOrAddApplicationItem(item);
                }
                sortApplicationList(mSortBy);
                filterItemsByFlags();
        }
    }

    private void removePackageIfNoBackup(String packageName) {
        ApplicationItem item = getApplicationItemFromApplicationItems(packageName);
        if (item != null) {
            if (item.metadataV1 == null) applicationItems.remove(item);
            else {
                ApplicationItem changedItem = getNewApplicationItem(packageName);
                if (changedItem != null) insertOrAddApplicationItem(changedItem);
            }
        }
    }

    private void insertOrAddApplicationItem(ApplicationItem item) {
        if (!insertApplicationItem(item)) {
            applicationItems.add(item);
        }
    }

    private boolean insertApplicationItem(ApplicationItem item) {
        boolean isInserted = false;
        for (int i = 0; i < applicationItems.size(); ++i) {
            if (applicationItems.get(i).equals(item)) {
                applicationItems.set(i, item);
                isInserted = true;
            }
        }
        return isInserted;
    }

    @Nullable
    private ApplicationItem getNewApplicationItem(String packageName) {
        try {
            @SuppressLint("WrongConstant")
            PackageInfo packageInfo = mPackageManager.getPackageInfo(packageName,
                    PackageManager.GET_META_DATA | flagSigningInfo |
                            PackageManager.GET_ACTIVITIES | flagDisabledComponents);
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            ApplicationItem item = new ApplicationItem(applicationInfo);
            item.versionName = packageInfo.versionName;
            item.versionCode = PackageUtils.getVersionCode(packageInfo);
            item.metadataV1 = BackupUtils.getBackupInfo(packageName);
            item.flags = applicationInfo.flags;
            item.uid = applicationInfo.uid;
            item.sharedUserId = packageInfo.sharedUserId;
            item.label = applicationInfo.loadLabel(mPackageManager).toString();
            item.debuggable = (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            item.isUser = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
            item.isDisabled = !applicationInfo.enabled;
            item.hasActivities = packageInfo.activities != null;
            item.firstInstallTime = packageInfo.firstInstallTime;
            item.lastUpdateTime = packageInfo.lastUpdateTime;
            item.sha = Utils.getIssuerAndAlg(packageInfo);
            item.sdk = applicationInfo.targetSdkVersion;
            if (mSortBy == MainActivity.SORT_BY_BLOCKED_COMPONENTS && AppPref.isRootEnabled()) {
                try (ComponentsBlocker cb = ComponentsBlocker.getInstance(packageName, true)) {
                    item.blockedCount = cb.componentCount();
                }
            }
            return item;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return null;
    }

    @Nullable
    private ApplicationItem getApplicationItemFromApplicationItems(String packageName) {
        ApplicationItem item;
        for (int i = 0; i < applicationItems.size(); ++i) {
            item = applicationItems.get(i);
            if (item.packageName.equals(packageName)) return item;
        }
        return null;
    }

    @NonNull
    private String[] getPackagesForUid(int uid) {
        List<String> packages = new LinkedList<>();
        ApplicationItem item;
        for (int i = 0; i < applicationItems.size(); ++i) {
            item = applicationItems.get(i);
            if (item.uid == uid) packages.add(item.packageName);
        }
        return packages.toArray(new String[0]);
    }

    @Override
    protected void onCleared() {
        if (mPackageObserver != null) getApplication().unregisterReceiver(mPackageObserver);
        super.onCleared();
    }

    public static class PackageIntentReceiver extends BroadcastReceiver {

        final MainViewModel mModel;

        public PackageIntentReceiver(@NonNull MainViewModel model) {
            mModel = model;
            IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
            filter.addDataScheme("package");
            // Register for events related to sdcard installation.
            IntentFilter sdFilter = new IntentFilter();
            sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
            sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            mModel.getApplication().registerReceiver(this, filter);
            mModel.getApplication().registerReceiver(this, sdFilter);
            mModel.getApplication().registerReceiver(this, new IntentFilter(BatchOpsService.ACTION_BATCH_OPS_COMPLETED));
        }

        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            switch (Objects.requireNonNull(intent.getAction())) {
                case Intent.ACTION_PACKAGE_REMOVED:
                    if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) break;
                case Intent.ACTION_PACKAGE_ADDED:
                case Intent.ACTION_PACKAGE_CHANGED:
                    mModel.updateInfoForUid(intent.getIntExtra(Intent.EXTRA_UID, -1), intent.getAction());
                    break;
                case Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE:
                case Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE:
                    mModel.updateInfoForPackages(intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST), intent.getAction());
                    break;
                case Intent.ACTION_LOCALE_CHANGED:
                    mModel.loadApplicationItems();
                    break;
                case BatchOpsService.ACTION_BATCH_OPS_COMPLETED:
                    // Trigger for all ops except disable, force-stop and uninstall
                    @BatchOpsManager.OpType int op;
                    op = intent.getIntExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_NONE);
                    if (op != BatchOpsManager.OP_NONE && op != BatchOpsManager.OP_DISABLE
                            && op != BatchOpsManager.OP_UNINSTALL) {
                        String[] packages = intent.getStringArrayExtra(BatchOpsService.EXTRA_OP_PKG);
                        String[] failedPackages = intent.getStringArrayExtra(BatchOpsService.EXTRA_FAILED_PKG);
                        if (packages != null && failedPackages != null) {
                            List<String> packageList = new ArrayList<>();
                            List<String> failedPackageList = Arrays.asList(failedPackages);
                            for (String packageName : packages) {
                                if (!failedPackageList.contains(packageName))
                                    packageList.add(packageName);
                            }
                            mModel.updateInfoForPackages(packageList.toArray(new String[0]),
                                    Intent.ACTION_PACKAGE_CHANGED);
                        }
                    }
            }
        }
    }
}
