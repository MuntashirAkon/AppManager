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

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.servermanager.ApiSupporter;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagDisabledComponents;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagSigningInfo;

public class MainViewModel extends AndroidViewModel {
    private static final Collator sCollator = Collator.getInstance();

    private final PackageManager mPackageManager;
    private final PackageIntentReceiver mPackageObserver;
    private final Handler mHandler;
    @MainActivity.SortOrder
    private int mSortBy;
    @MainActivity.Filter
    private int mFilterFlags;
    private String searchQuery;
    private List<String> backupApplications;
    private final Map<String, int[]> selectedPackages = new HashMap<>();
    private final List<ApplicationItem> selectedApplicationItems = new LinkedList<>();

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

    @GuardedBy("applicationItems")
    public ApplicationItem deselect(@NonNull ApplicationItem item) {
        synchronized (applicationItems) {
            int i = applicationItems.indexOf(item);
            if (i == -1) return item;
            selectedPackages.remove(item.packageName);
            selectedApplicationItems.remove(item);
            item.isSelected = false;
            applicationItems.set(i, item);
            return item;
        }
    }

    @GuardedBy("applicationItems")
    public ApplicationItem select(@NonNull ApplicationItem item) {
        synchronized (applicationItems) {
            int i = applicationItems.indexOf(item);
            if (i == -1) return item;
            selectedPackages.put(item.packageName, item.userHandles);
            item.isSelected = true;
            applicationItems.set(i, item);
            selectedApplicationItems.add(item);
            return item;
        }
    }

    @GuardedBy("applicationItems")
    public void clearSelection() {
        synchronized (applicationItems) {
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
    }

    public Map<String, int[]> getSelectedPackages() {
        return selectedPackages;
    }

    @NonNull
    public ArrayList<UserPackagePair> getSelectedPackagesWithUsers(boolean onlyForCurrentUser) {
        ArrayList<UserPackagePair> userPackagePairs = new ArrayList<>();
        int currentUserHandle = Users.getCurrentUserHandle();
        for (String packageName : selectedPackages.keySet()) {
            int[] userHandles = selectedPackages.get(packageName);
            if (userHandles == null) continue;
            for (int userHandle : userHandles) {
                if (onlyForCurrentUser && currentUserHandle != userHandle) continue;
                userPackagePairs.add(new UserPackagePair(packageName, userHandle));
            }
        }
        return userPackagePairs;
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
        AppPref.set(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_ORDER_INT, mSortBy);
    }

    public int getFilterFlags() {
        return mFilterFlags;
    }

    public void addFilterFlag(@MainActivity.Filter int filterFlag) {
        mFilterFlags |= filterFlag;
        AppPref.set(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_FLAGS_INT, mFilterFlags);
        new Thread(this::filterItemsByFlags).start();
    }

    public void removeFilterFlag(@MainActivity.Filter int filterFlag) {
        mFilterFlags &= ~filterFlag;
        AppPref.set(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_FLAGS_INT, mFilterFlags);
        new Thread(this::filterItemsByFlags).start();
    }

    public void onResume() {
        if ((mFilterFlags & MainActivity.FILTER_RUNNING_APPS) != 0) {
            // Reload filters to get running apps again
            filterItemsByFlags();
        }
    }

    @GuardedBy("applicationItems")
    public void loadApplicationItems() {
        new Thread(() -> {
            synchronized (applicationItems) {
                applicationItems.clear();
                backupApplications = BackupUtils.getBackupApplications();
                Log.d("backupApplications", backupApplications.toString());
                int[] userHandles = Users.getUsersHandles();
                for (int userHandle : userHandles) {
                    @SuppressLint("WrongConstant")
                    List<PackageInfo> packageInfoList;
                    try {
                        packageInfoList = ApiSupporter.getInstance(LocalServer.getInstance()).getInstalledPackages(flagSigningInfo | PackageManager.GET_ACTIVITIES | flagDisabledComponents, userHandle);
                    } catch (Exception e) {
                        Log.e("MVM", "Could not retrieve package info list for user " + userHandle);
                        e.printStackTrace();
                        continue;
                    }
                    ApplicationInfo applicationInfo;

                    for (PackageInfo packageInfo : packageInfoList) {
                        applicationInfo = packageInfo.applicationInfo;
                        ApplicationItem item = new ApplicationItem(applicationInfo);
                        int i;
                        if ((i = applicationItems.indexOf(item)) != -1) {
                            // Add user handle and continue
                            ApplicationItem oldItem = applicationItems.get(i);
                            oldItem.userHandles = ArrayUtils.appendInt(oldItem.userHandles, userHandle);
                            continue;
                        }
                        if (backupApplications.contains(applicationInfo.packageName)) {
                            item.metadata = BackupUtils.getBackupInfo(applicationInfo.packageName);
                            backupApplications.remove(applicationInfo.packageName);
                        }
                        item.flags = applicationInfo.flags;
                        item.uid = applicationInfo.uid;
                        item.debuggable = (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
                        item.isUser = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
                        item.isDisabled = !applicationInfo.enabled;
                        item.label = applicationInfo.loadLabel(mPackageManager).toString();
                        item.sdk = applicationInfo.targetSdkVersion;
                        item.versionName = packageInfo.versionName;
                        item.versionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
                        item.sharedUserId = packageInfo.sharedUserId;
                        item.sha = Utils.getIssuerAndAlg(packageInfo);
                        item.firstInstallTime = packageInfo.firstInstallTime;
                        item.lastUpdateTime = packageInfo.lastUpdateTime;
                        item.hasActivities = packageInfo.activities != null;
                        item.blockedCount = 0;
                        item.userHandles = ArrayUtils.appendInt(item.userHandles, userHandle);
                        applicationItems.add(item);
                    }
                }
                // Add rest of the backup items, i.e., items that aren't installed
                for (String packageName : backupApplications) {
                    ApplicationItem item = new ApplicationItem();
                    item.packageName = packageName;
                    item.metadata = BackupUtils.getBackupInfo(packageName);
                    if (item.metadata == null) continue;
                    item.versionName = item.metadata.versionName;
                    item.versionCode = item.metadata.versionCode;
                    item.label = item.metadata.label;
                    Log.e("MVM", item.label);
                    item.firstInstallTime = item.metadata.backupTime;
                    item.lastUpdateTime = item.metadata.backupTime;
                    item.isUser = !item.metadata.isSystem;
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

    @GuardedBy("applicationItems")
    private void filterItemsByFlags() {
        synchronized (applicationItems) {
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
                if ((mFilterFlags & MainActivity.FILTER_RUNNING_APPS) != 0) {
                    loadRunningApps();
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
                    } else if ((mFilterFlags & MainActivity.FILTER_APPS_WITH_BACKUPS) != 0 && item.metadata == null) {
                        continue;
                    } else if ((mFilterFlags & MainActivity.FILTER_RUNNING_APPS) != 0 && !item.isRunning) {
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
    }

    @GuardedBy("applicationItems")
    private void loadBlockingRules() {
        synchronized (applicationItems) {
            // Only load blocking rules for the current user
            int userHandle = Users.getCurrentUserHandle();
            for (int i = 0; i < applicationItems.size(); ++i) {
                ApplicationItem applicationItem = applicationItems.get(i);
                try (ComponentsBlocker cb = ComponentsBlocker.getInstance(applicationItem.packageName, userHandle, true)) {
                    applicationItem.blockedCount = cb.componentCount();
                }
                applicationItems.set(i, applicationItem);
            }
        }
    }

    @GuardedBy("applicationItems")
    private void loadRunningApps() {
        synchronized (applicationItems) {
            Runner.Result result = Runner.runCommand(new String[]{Runner.TOYBOX, "ps", "-dw", "-o", "NAME"});
            if (result.isSuccessful()) {
                List<String> processInfoLines = result.getOutputAsList(1);
                for (int i = 0; i < applicationItems.size(); ++i) {
                    ApplicationItem applicationItem = applicationItems.get(i);
                    applicationItem.isRunning = processInfoLines.contains(applicationItem.packageName);
                    applicationItems.set(i, applicationItem);
                }
            }
        }
    }

    @GuardedBy("applicationItems")
    private void sortApplicationList(@MainActivity.SortOrder int sortBy) {
        synchronized (applicationItems) {
            final boolean isRootEnabled = AppPref.isRootEnabled();
            if (sortBy != MainActivity.SORT_BY_APP_LABEL)
                sortApplicationList(MainActivity.SORT_BY_APP_LABEL);
            if (sortBy == MainActivity.SORT_BY_BLOCKED_COMPONENTS && isRootEnabled)
                loadBlockingRules();
            Collections.sort(applicationItems, (o1, o2) -> {
                switch (sortBy) {
                    case MainActivity.SORT_BY_APP_LABEL:
                        return sCollator.compare(o1.label, o2.label);
                    case MainActivity.SORT_BY_PACKAGE_NAME:
                        return o1.packageName.compareTo(o2.packageName);
                    case MainActivity.SORT_BY_DOMAIN:
                        boolean isSystem1 = (o1.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        boolean isSystem2 = (o2.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        return Boolean.compare(isSystem1, isSystem2);
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
                            int i = o1.sha.first.compareToIgnoreCase(o2.sha.first);
                            if (i == 0) {
                                return o1.sha.second.compareToIgnoreCase(o2.sha.second);
                            } else if (i < 0) {
                                return -1;
                            } else return +1;
                        }
                    case MainActivity.SORT_BY_BLOCKED_COMPONENTS:
                        if (isRootEnabled)
                            return -o1.blockedCount.compareTo(o2.blockedCount);
                        break;
                    case MainActivity.SORT_BY_DISABLED_APP:
                        return -Boolean.compare(o1.isDisabled, o2.isDisabled);
                    case MainActivity.SORT_BY_BACKUP:
                        return -Boolean.compare(o1.metadata != null, o2.metadata != null);
                }
                return 0;
            });
        }
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

    @GuardedBy("applicationItems")
    private void removePackageIfNoBackup(String packageName) {
        synchronized (applicationItems) {
            ApplicationItem item = getApplicationItemFromApplicationItems(packageName);
            if (item != null) {
                if (item.metadata == null) applicationItems.remove(item);
                else {
                    ApplicationItem changedItem = getNewApplicationItem(packageName);
                    if (changedItem != null) insertOrAddApplicationItem(changedItem);
                }
            }
        }
    }

    @GuardedBy("applicationItems")
    private void insertOrAddApplicationItem(ApplicationItem item) {
        synchronized (applicationItems) {
            if (!insertApplicationItem(item)) {
                applicationItems.add(item);
            }
        }
    }

    @GuardedBy("applicationItems")
    private boolean insertApplicationItem(ApplicationItem item) {
        synchronized (applicationItems) {
            boolean isInserted = false;
            for (int i = 0; i < applicationItems.size(); ++i) {
                if (applicationItems.get(i).equals(item)) {
                    applicationItems.set(i, item);
                    isInserted = true;
                }
            }
            return isInserted;
        }
    }

    @Nullable
    private ApplicationItem getNewApplicationItem(String packageName) {
        int[] userHandles = Users.getUsersHandles();
        ApplicationItem oldItem = null;
        for (int userHandle : userHandles) {
            try {
                @SuppressLint("WrongConstant")
                PackageInfo packageInfo = ApiSupporter.getInstance(LocalServer.getInstance())
                        .getPackageInfo(packageName, PackageManager.GET_META_DATA
                                | flagSigningInfo | PackageManager.GET_ACTIVITIES
                                | flagDisabledComponents, userHandle);
                ApplicationInfo applicationInfo = packageInfo.applicationInfo;
                ApplicationItem item = new ApplicationItem(applicationInfo);
                if (item.equals(oldItem)) {
                    oldItem.userHandles = ArrayUtils.appendInt(oldItem.userHandles, userHandle);
                    continue;
                }
                item.versionName = packageInfo.versionName;
                item.versionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
                item.metadata = BackupUtils.getBackupInfo(packageName);
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
                item.userHandles = ArrayUtils.appendInt(item.userHandles, userHandle);
                if (mSortBy == MainActivity.SORT_BY_BLOCKED_COMPONENTS && AppPref.isRootEnabled()) {
                    try (ComponentsBlocker cb = ComponentsBlocker.getInstance(packageName, Users.getCurrentUserHandle(), true)) {
                        item.blockedCount = cb.componentCount();
                    }
                }
                oldItem = item;
            } catch (Exception ignore) {
            }
        }
        return oldItem;
    }

    @GuardedBy("applicationItems")
    @Nullable
    private ApplicationItem getApplicationItemFromApplicationItems(String packageName) {
        synchronized (applicationItems) {
            ApplicationItem item;
            for (int i = 0; i < applicationItems.size(); ++i) {
                item = applicationItems.get(i);
                if (item.packageName.equals(packageName)) return item;
            }
            return null;
        }
    }

    @GuardedBy("applicationItems")
    @NonNull
    private String[] getPackagesForUid(int uid) {
        synchronized (applicationItems) {
            List<String> packages = new LinkedList<>();
            ApplicationItem item;
            for (int i = 0; i < applicationItems.size(); ++i) {
                item = applicationItems.get(i);
                if (item.uid == uid) packages.add(item.packageName);
            }
            return packages.toArray(new String[0]);
        }
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
                    if (op != BatchOpsManager.OP_NONE && op != BatchOpsManager.OP_DISABLE &&
                            op != BatchOpsManager.OP_ENABLE && op != BatchOpsManager.OP_UNINSTALL) {
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
