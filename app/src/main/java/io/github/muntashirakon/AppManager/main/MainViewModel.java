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
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.text.TextUtils;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.types.PackageChangeReceiver;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

import java.text.Collator;
import java.util.*;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagDisabledComponents;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagSigningInfo;

public class MainViewModel extends AndroidViewModel {
    private static final Collator sCollator = Collator.getInstance();

    private final PackageManager mPackageManager;
    private final PackageIntentReceiver mPackageObserver;
    private final Handler mHandler;
    @ListOptions.SortOrder
    private int mSortBy;
    private boolean mSortReverse;
    @ListOptions.Filter
    private int mFilterFlags;
    private String searchQuery;
    private HashMap<String, MetadataManager.Metadata> backupMetadata;
    private final Map<String, int[]> selectedPackages = new HashMap<>();
    private final ArraySet<ApplicationItem> selectedApplicationItems = new ArraySet<>();

    public MainViewModel(@NonNull Application application) {
        super(application);
        Log.d("MVM", "New instance created");
        mPackageManager = application.getPackageManager();
        mHandler = new Handler(application.getMainLooper());
        mPackageObserver = new PackageIntentReceiver(this);
        mSortBy = (int) AppPref.get(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_ORDER_INT);
        mSortReverse = (boolean) AppPref.get(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_REVERSE_BOOL);
        mFilterFlags = (int) AppPref.get(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_FLAGS_INT);
    }

    @Nullable
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
            if (userHandles == null || userHandles.length == 0) {
                // Could be a backup only item
                // Assign current user in it
                userPackagePairs.add(new UserPackagePair(packageName, currentUserHandle));
            } else {
                for (int userHandle : userHandles) {
                    if (onlyForCurrentUser && currentUserHandle != userHandle) continue;
                    userPackagePairs.add(new UserPackagePair(packageName, userHandle));
                }
            }
        }
        return userPackagePairs;
    }

    public List<ApplicationItem> getSelectedApplicationItems() {
        return new ArrayList<>(selectedApplicationItems);
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
        new Thread(this::filterItemsByFlags).start();
    }

    public int getSortBy() {
        return mSortBy;
    }

    public void setSortReverse(boolean sortReverse) {
        new Thread(() -> {
            sortApplicationList(mSortBy, mSortReverse);
            filterItemsByFlags();
        }).start();
        mSortReverse = sortReverse;
        AppPref.set(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_REVERSE_BOOL, mSortReverse);
    }

    public boolean isSortReverse() {
        return mSortReverse;
    }

    public void setSortBy(int sortBy) {
        if (mSortBy != sortBy) {
            new Thread(() -> {
                sortApplicationList(sortBy, mSortReverse);
                filterItemsByFlags();
            }).start();
        }
        mSortBy = sortBy;
        AppPref.set(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_ORDER_INT, mSortBy);
    }

    public boolean hasFilterFlag(@ListOptions.Filter int flag) {
        return (mFilterFlags & flag) != 0;
    }

    public void addFilterFlag(@ListOptions.Filter int filterFlag) {
        mFilterFlags |= filterFlag;
        AppPref.set(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_FLAGS_INT, mFilterFlags);
        new Thread(this::filterItemsByFlags).start();
    }

    public void removeFilterFlag(@ListOptions.Filter int filterFlag) {
        mFilterFlags &= ~filterFlag;
        AppPref.set(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_FLAGS_INT, mFilterFlags);
        new Thread(this::filterItemsByFlags).start();
    }

    public void onResume() {
        if ((mFilterFlags & ListOptions.FILTER_RUNNING_APPS) != 0) {
            // Reload filters to get running apps again
            filterItemsByFlags();
        }
    }

    @GuardedBy("applicationItems")
    public void loadApplicationItems() {
        new Thread(() -> {
            synchronized (applicationItems) {
                applicationItems.clear();
                backupMetadata = BackupUtils.getAllBackupMetadata();
                applicationItems.addAll(PackageUtils.getInstalledOrBackedUpApplicationsFromDb(getApplication(), backupMetadata));
                // select apps again
                for (ApplicationItem item : selectedApplicationItems) {
                    select(item);
                }
                sortApplicationList(mSortBy, mSortReverse);
                filterItemsByFlags();
            }
        }).start();
    }

    private void filterItemsByQuery(@NonNull List<ApplicationItem> applicationItems) {
        List<ApplicationItem> filteredApplicationItems = new ArrayList<>();
        for (ApplicationItem item : applicationItems) {
            if (item.packageName.toLowerCase(Locale.ROOT).contains(searchQuery)) {
                filteredApplicationItems.add(item);
            } else if (Utils.containsOrHasInitials(searchQuery, item.label))
                filteredApplicationItems.add(item);
        }
        mHandler.post(() -> {
            if (applicationItemsLiveData != null) applicationItemsLiveData.postValue(filteredApplicationItems);
        });
    }

    @GuardedBy("applicationItems")
    private void filterItemsByFlags() {
        synchronized (applicationItems) {
            if (mFilterFlags == ListOptions.FILTER_NO_FILTER) {
                if (!TextUtils.isEmpty(searchQuery)) {
                    filterItemsByQuery(applicationItems);
                } else {
                    mHandler.post(() -> {
                        if (applicationItemsLiveData != null) applicationItemsLiveData.postValue(applicationItems);
                    });
                }
            } else {
                List<ApplicationItem> filteredApplicationItems = new ArrayList<>();
                if ((mFilterFlags & ListOptions.FILTER_APPS_WITH_RULES) != 0) {
                    loadBlockingRules();
                }
                if ((mFilterFlags & ListOptions.FILTER_RUNNING_APPS) != 0) {
                    loadRunningApps();
                }
                for (ApplicationItem item : applicationItems) {
                    // Filter user and system apps first (if requested)
                    if ((mFilterFlags & ListOptions.FILTER_USER_APPS) != 0 && !item.isUser) {
                        continue;
                    } else if ((mFilterFlags & ListOptions.FILTER_SYSTEM_APPS) != 0 && item.isUser) {
                        continue;
                    }
                    // Filter rests
                    if ((mFilterFlags & ListOptions.FILTER_DISABLED_APPS) != 0 && !item.isDisabled) {
                        continue;
                    } else if ((mFilterFlags & ListOptions.FILTER_APPS_WITH_RULES) != 0 && item.blockedCount <= 0) {
                        continue;
                    } else if ((mFilterFlags & ListOptions.FILTER_APPS_WITH_ACTIVITIES) != 0 && !item.hasActivities) {
                        continue;
                    } else if ((mFilterFlags & ListOptions.FILTER_APPS_WITH_BACKUPS) != 0 && item.metadata == null) {
                        continue;
                    } else if ((mFilterFlags & ListOptions.FILTER_APPS_WITH_SPLITS) != 0 && !item.hasSplits) {
                        continue;
                    } else if ((mFilterFlags & ListOptions.FILTER_RUNNING_APPS) != 0 && !item.isRunning) {
                        continue;
                    }
                    filteredApplicationItems.add(item);
                }
                if (!TextUtils.isEmpty(searchQuery)) {
                    filterItemsByQuery(filteredApplicationItems);
                } else {
                    mHandler.post(() -> {
                        if (applicationItemsLiveData != null) {
                            applicationItemsLiveData.postValue(filteredApplicationItems);
                        }
                    });
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
    private void sortApplicationList(@ListOptions.SortOrder int sortBy, boolean reverse) {
        synchronized (applicationItems) {
            final boolean isRootEnabled = AppPref.isRootEnabled();
            if (sortBy != ListOptions.SORT_BY_APP_LABEL) {
                sortApplicationList(ListOptions.SORT_BY_APP_LABEL, false);
            }
            if (sortBy == ListOptions.SORT_BY_BLOCKED_COMPONENTS && isRootEnabled) {
                loadBlockingRules();
            }
            int mode = reverse ? -1 : 1;
            Collections.sort(applicationItems, (o1, o2) -> {
                switch (sortBy) {
                    case ListOptions.SORT_BY_APP_LABEL:
                        return mode * sCollator.compare(o1.label, o2.label);
                    case ListOptions.SORT_BY_PACKAGE_NAME:
                        return mode * o1.packageName.compareTo(o2.packageName);
                    case ListOptions.SORT_BY_DOMAIN:
                        boolean isSystem1 = (o1.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        boolean isSystem2 = (o2.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        return mode * Boolean.compare(isSystem1, isSystem2);
                    case ListOptions.SORT_BY_LAST_UPDATE:
                        // Sort in decreasing order
                        return -mode * o1.lastUpdateTime.compareTo(o2.lastUpdateTime);
                    case ListOptions.SORT_BY_TARGET_SDK:
                        // null on top
                        if (o1.sdk == null) return -mode;
                        else if (o2.sdk == null) return +mode;
                        return mode * o1.sdk.compareTo(o2.sdk);
                    case ListOptions.SORT_BY_SHARED_ID:
                        return mode * Integer.compare(o1.uid, o2.uid);
                    case ListOptions.SORT_BY_SHA:
                        // null on top
                        if (o1.sha == null) {
                            return -mode;
                        } else if (o2.sha == null) {
                            return +mode;
                        } else {  // Both aren't null
                            int i = o1.sha.first.compareToIgnoreCase(o2.sha.first);
                            if (i == 0) {
                                return mode * o1.sha.second.compareToIgnoreCase(o2.sha.second);
                            } else return mode * i;
                        }
                    case ListOptions.SORT_BY_BLOCKED_COMPONENTS:
                        if (isRootEnabled)
                            return -mode * o1.blockedCount.compareTo(o2.blockedCount);
                        break;
                    case ListOptions.SORT_BY_DISABLED_APP:
                        return -mode * Boolean.compare(o1.isDisabled, o2.isDisabled);
                    case ListOptions.SORT_BY_BACKUP:
                        return -mode * Boolean.compare(o1.metadata != null, o2.metadata != null);
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
                        // This works because these actions are only registered for the current user
                        mPackageManager.getApplicationInfo(packageName, 0);
                    } catch (PackageManager.NameNotFoundException e) {
                        removePackageIfNoBackup(packageName);
                    }
                }
                break;
            case Intent.ACTION_PACKAGE_CHANGED:
                for (String packageName : packages) {
                    ApplicationItem item = getNewApplicationItem(packageName);
                    if (item != null) insertApplicationItem(item);
                }
                sortApplicationList(mSortBy, mSortReverse);
                break;
            case Intent.ACTION_PACKAGE_ADDED:
            case Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE:
                for (String packageName : packages) {
                    ApplicationItem item = getNewApplicationItem(packageName);
                    if (item != null) insertOrAddApplicationItem(item);
                }
                sortApplicationList(mSortBy, mSortReverse);
                break;
            case BatchOpsService.ACTION_BATCH_OPS_COMPLETED:
                for (String packageName : packages) {
                    ApplicationItem item = getNewApplicationItem(packageName);
                    if (item != null) insertOrAddApplicationItem(item);
                    else removePackageIfNoBackup(packageName);
                }
                sortApplicationList(mSortBy, mSortReverse);
                break;
            default:
                return;
        }
        filterItemsByFlags();
    }

    @GuardedBy("applicationItems")
    private void removePackageIfNoBackup(String packageName) {
        synchronized (applicationItems) {
            ApplicationItem item = getApplicationItemFromApplicationItems(packageName);
            if (item != null) {
                if (item.metadata == null) {
                    applicationItems.remove(item);
                    for (int userHandle : item.userHandles) {
                        AppManager.getDb().appDao().delete(item.packageName, userHandle);
                    }
                }
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
                if (selectedApplicationItems.contains(item)) {
                    select(item);
                }
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
                    if (selectedApplicationItems.contains(item)) {
                        select(item);
                    }
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
                PackageInfo packageInfo = PackageManagerCompat.getPackageInfo(packageName,
                        PackageManager.GET_META_DATA | flagSigningInfo
                                | PackageManager.GET_ACTIVITIES | flagDisabledComponents,
                        userHandle);
                App app = App.fromPackageInfo(getApplication(), packageInfo);
                try (ComponentsBlocker cb = ComponentsBlocker.getInstance(app.packageName, app.userId, true)) {
                    app.rulesCount = cb.entryCount();
                }
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
                item.hasSplits = applicationInfo.splitSourceDirs != null;
                item.firstInstallTime = packageInfo.firstInstallTime;
                item.lastUpdateTime = packageInfo.lastUpdateTime;
                item.sha = Utils.getIssuerAndAlg(packageInfo);
                item.sdk = applicationInfo.targetSdkVersion;
                item.userHandles = ArrayUtils.appendInt(item.userHandles, userHandle);
                item.blockedCount = app.rulesCount;
                oldItem = item;
                AppManager.getDb().appDao().insert(app);
            } catch (Exception ignore) {
            }
        }
        return oldItem;
    }

    @GuardedBy("applicationItems")
    @Nullable
    private ApplicationItem getApplicationItemFromApplicationItems(String packageName) {
        synchronized (applicationItems) {
            for (ApplicationItem item : applicationItems) {
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
            for (ApplicationItem item : applicationItems) {
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

    public static class PackageIntentReceiver extends PackageChangeReceiver {
        final MainViewModel mModel;

        public PackageIntentReceiver(@NonNull MainViewModel model) {
            super(model.getApplication());
            mModel = model;
        }

        @Override
        protected void onPackageChanged(Context context, Intent intent, @Nullable Integer uid, @Nullable String[] packages) {
            if (uid != null) {
                mModel.updateInfoForUid(uid, intent.getAction());
            } else if (packages != null) {
                mModel.updateInfoForPackages(packages, intent.getAction());
            } else {
                mModel.loadApplicationItems();
            }
        }
    }
}
