// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.muntashirakon.AppManager.apk.list.ListExporter;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.misc.ListOptions;
import io.github.muntashirakon.AppManager.profiles.ProfileManager;
import io.github.muntashirakon.AppManager.profiles.struct.AppsProfile;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.PackageChangeReceiver;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.Path;

public class MainViewModel extends AndroidViewModel implements ListOptions.ListOptionActions {
    private static final Collator sCollator = Collator.getInstance();

    private final PackageManager mPackageManager;
    private final PackageIntentReceiver mPackageObserver;
    private final Handler mHandler;
    @MainListOptions.SortOrder
    private int mSortBy;
    private boolean mReverseSort;
    @MainListOptions.Filter
    private int mFilterFlags;
    @Nullable
    private String mFilterProfileName;
    @Nullable
    private int[] mSelectedUsers;
    private String mSearchQuery;
    @AdvancedSearchView.SearchType
    private int mSearchType;
    private final Map<String, ApplicationItem> mSelectedPackageApplicationItemMap = Collections.synchronizedMap(new LinkedHashMap<>());
    final MultithreadedExecutor executor = MultithreadedExecutor.getNewInstance();

    public MainViewModel(@NonNull Application application) {
        super(application);
        Log.d("MVM", "New instance created");
        mPackageManager = application.getPackageManager();
        mHandler = new Handler(application.getMainLooper());
        mPackageObserver = new PackageIntentReceiver(this);
        mSortBy = Prefs.MainPage.getSortOrder();
        mReverseSort = Prefs.MainPage.isReverseSort();
        mFilterFlags = Prefs.MainPage.getFilters();
        mFilterProfileName = Prefs.MainPage.getFilteredProfileName();
        mSelectedUsers = null; // TODO: 5/6/23 Load from prefs?
        if ("".equals(mFilterProfileName)) mFilterProfileName = null;
    }

    private final MutableLiveData<Boolean> mOperationStatus = new MutableLiveData<>();
    @NonNull
    private final MutableLiveData<List<ApplicationItem>> mApplicationItemsLiveData = new MutableLiveData<>();
    private final List<ApplicationItem> mApplicationItems = new ArrayList<>();

    public int getApplicationItemCount() {
        return mApplicationItems.size();
    }

    @NonNull
    public LiveData<List<ApplicationItem>> getApplicationItems() {
        if (mApplicationItemsLiveData.getValue() == null) {
            loadApplicationItems();
        }
        return mApplicationItemsLiveData;
    }

    public LiveData<Boolean> getOperationStatus() {
        return mOperationStatus;
    }

    @GuardedBy("applicationItems")
    public ApplicationItem deselect(@NonNull ApplicationItem item) {
        synchronized (mApplicationItems) {
            int i = mApplicationItems.indexOf(item);
            if (i == -1) return item;
            item = mApplicationItems.get(i);
            mSelectedPackageApplicationItemMap.remove(item.packageName);
            item.isSelected = false;
            mApplicationItems.set(i, item);
            return item;
        }
    }

    @GuardedBy("applicationItems")
    public ApplicationItem select(@NonNull ApplicationItem item) {
        synchronized (mApplicationItems) {
            int i = mApplicationItems.indexOf(item);
            if (i == -1) return item;
            item = mApplicationItems.get(i);
            // Removal is needed because LinkedHashMap insertion-oriented
            mSelectedPackageApplicationItemMap.remove(item.packageName);
            mSelectedPackageApplicationItemMap.put(item.packageName, item);
            item.isSelected = true;
            mApplicationItems.set(i, item);
            return item;
        }
    }

    public void cancelSelection() {
        synchronized (mApplicationItems) {
            for (ApplicationItem item : getSelectedApplicationItems()) {
                int i = mApplicationItems.indexOf(item);
                if (i != -1) {
                    mApplicationItems.get(i).isSelected = false;
                }
            }
            mSelectedPackageApplicationItemMap.clear();
        }
    }

    @Nullable
    public ApplicationItem getLastSelectedPackage() {
        // Last selected package is the same as the last added package.
        Iterator<ApplicationItem> it = mSelectedPackageApplicationItemMap.values().iterator();
        ApplicationItem lastItem = null;
        while (it.hasNext()) {
            lastItem = it.next();
        }
        return lastItem;
    }

    public Map<String, ApplicationItem> getSelectedPackages() {
        return mSelectedPackageApplicationItemMap;
    }

    @NonNull
    public ArrayList<UserPackagePair> getSelectedPackagesWithUsers() {
        ArrayList<UserPackagePair> userPackagePairs = new ArrayList<>();
        int myUserId = UserHandleHidden.myUserId();
        int[] userIds = Users.getUsersIds();
        for (String packageName : mSelectedPackageApplicationItemMap.keySet()) {
            int[] userIds1 = Objects.requireNonNull(mSelectedPackageApplicationItemMap.get(packageName)).userIds;
            if (userIds1.length == 0) {
                // Could be a backup only item
                // Assign current user in it
                userPackagePairs.add(new UserPackagePair(packageName, myUserId));
            } else {
                for (int userHandle : userIds1) {
                    if (!ArrayUtils.contains(userIds, userHandle)) continue;
                    userPackagePairs.add(new UserPackagePair(packageName, userHandle));
                }
            }
        }
        return userPackagePairs;
    }

    public Collection<ApplicationItem> getSelectedApplicationItems() {
        return mSelectedPackageApplicationItemMap.values();
    }

    public String getSearchQuery() {
        return mSearchQuery;
    }

    public void setSearchQuery(String searchQuery, @AdvancedSearchView.SearchType int searchType) {
        this.mSearchQuery = searchType != AdvancedSearchView.SEARCH_TYPE_REGEX ? searchQuery.toLowerCase(Locale.ROOT) : searchQuery;
        this.mSearchType = searchType;
        executor.submit(this::filterItemsByFlags);
    }

    @Override
    public int getSortBy() {
        return mSortBy;
    }

    @Override
    public void setReverseSort(boolean reverseSort) {
        executor.submit(() -> {
            sortApplicationList(mSortBy, mReverseSort);
            filterItemsByFlags();
        });
        mReverseSort = reverseSort;
        Prefs.MainPage.setReverseSort(mReverseSort);
    }

    @Override
    public boolean isReverseSort() {
        return mReverseSort;
    }

    @Override
    public void setSortBy(int sortBy) {
        if (mSortBy != sortBy) {
            executor.submit(() -> {
                sortApplicationList(sortBy, mReverseSort);
                filterItemsByFlags();
            });
        }
        mSortBy = sortBy;
        Prefs.MainPage.setSortOrder(mSortBy);
    }

    @Override
    public boolean hasFilterFlag(@MainListOptions.Filter int flag) {
        return (mFilterFlags & flag) != 0;
    }

    @Override
    public void addFilterFlag(@MainListOptions.Filter int filterFlag) {
        mFilterFlags |= filterFlag;
        Prefs.MainPage.setFilters(mFilterFlags);
        executor.submit(this::filterItemsByFlags);
    }

    @Override
    public void removeFilterFlag(@MainListOptions.Filter int filterFlag) {
        mFilterFlags &= ~filterFlag;
        Prefs.MainPage.setFilters(mFilterFlags);
        executor.submit(this::filterItemsByFlags);
    }

    public void setFilterProfileName(@Nullable String filterProfileName) {
        if (mFilterProfileName == null) {
            if (filterProfileName == null) return;
        } else if (mFilterProfileName.equals(filterProfileName)) return;
        mFilterProfileName = filterProfileName;
        Prefs.MainPage.setFilteredProfileName(filterProfileName);
        executor.submit(this::filterItemsByFlags);
    }

    @Nullable
    public String getFilterProfileName() {
        return mFilterProfileName;
    }

    public void setSelectedUsers(@Nullable int[] selectedUsers) {
        if (selectedUsers == null) {
            if (mSelectedUsers == null) {
                // No change
                return;
            }
        } else if (mSelectedUsers != null) {
            if (mSelectedUsers.length == selectedUsers.length) {
                boolean differs = false;
                for (int user : selectedUsers) {
                    if (!ArrayUtils.contains(mSelectedUsers, user)) {
                        differs = true;
                        break;
                    }
                }
                if (!differs) {
                    // No change detected
                    return;
                }
            }
        }
        mSelectedUsers = selectedUsers;
        // TODO: 5/6/23 Store value to prefs
        executor.submit(this::filterItemsByFlags);
    }

    @Nullable
    public int[] getSelectedUsers() {
        return mSelectedUsers;
    }

    @AnyThread
    public void onResume() {
        if ((mFilterFlags & MainListOptions.FILTER_RUNNING_APPS) != 0) {
            // Reload filters to get running apps again
            executor.submit(this::filterItemsByFlags);
        }
    }

    public void saveExportedAppList(@ListExporter.ExportType int exportType, @NonNull Path path) {
        executor.submit(() -> {
            try (OutputStream os = path.openOutputStream()) {
                List<PackageInfo> packageInfoList = new ArrayList<>();
                for (String packageName : getSelectedPackages().keySet()) {
                    int[] userIds = Objects.requireNonNull(getSelectedPackages().get(packageName)).userIds;
                    for (int userId : userIds) {
                        packageInfoList.add(PackageManagerCompat.getPackageInfo(packageName,
                                PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId));
                        break;
                    }
                }
                os.write(ListExporter.export(getApplication(), exportType, packageInfoList).getBytes(StandardCharsets.UTF_8));
                mOperationStatus.postValue(true);
            } catch (IOException | RemoteException | PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                mOperationStatus.postValue(false);
            }
        });
    }

    @GuardedBy("applicationItems")
    public void loadApplicationItems() {
        executor.submit(() -> {
            List<ApplicationItem> updatedApplicationItems = PackageUtils
                    .getInstalledOrBackedUpApplicationsFromDb(getApplication(), true, true);
            synchronized (mApplicationItems) {
                mApplicationItems.clear();
                mApplicationItems.addAll(updatedApplicationItems);
                // select apps again
                for (ApplicationItem item : getSelectedApplicationItems()) {
                    select(item);
                }
                sortApplicationList(mSortBy, mReverseSort);
                filterItemsByFlags();
            }
        });
    }

    private void filterItemsByQuery(@NonNull List<ApplicationItem> applicationItems) {
        List<ApplicationItem> filteredApplicationItems;
        if (mSearchType == AdvancedSearchView.SEARCH_TYPE_REGEX) {
            filteredApplicationItems = AdvancedSearchView.matches(mSearchQuery, applicationItems,
                    (AdvancedSearchView.ChoicesGenerator<ApplicationItem>) item -> new ArrayList<String>() {{
                        add(item.packageName);
                        add(item.label);
                    }}, AdvancedSearchView.SEARCH_TYPE_REGEX);
            mHandler.post(() -> mApplicationItemsLiveData.postValue(filteredApplicationItems));
            return;
        }
        // Others
        filteredApplicationItems = new ArrayList<>();
        for (ApplicationItem item : applicationItems) {
            if (AdvancedSearchView.matches(mSearchQuery, item.packageName.toLowerCase(Locale.ROOT), mSearchType)) {
                filteredApplicationItems.add(item);
            } else if (mSearchType == AdvancedSearchView.SEARCH_TYPE_CONTAINS) {
                if (Utils.containsOrHasInitials(mSearchQuery, item.label)) {
                    filteredApplicationItems.add(item);
                }
            } else if (AdvancedSearchView.matches(mSearchQuery, item.label.toLowerCase(Locale.ROOT), mSearchType)) {
                filteredApplicationItems.add(item);
            }
        }
        mHandler.post(() -> mApplicationItemsLiveData.postValue(filteredApplicationItems));
    }

    @WorkerThread
    @GuardedBy("applicationItems")
    private void filterItemsByFlags() {
        synchronized (mApplicationItems) {
            List<ApplicationItem> candidateApplicationItems = new ArrayList<>();
            if (mFilterProfileName != null) {
                String profileId = ProfileManager.getProfileIdCompat(mFilterProfileName);
                Path profilePath = ProfileManager.findProfilePathById(profileId);
                try {
                    AppsProfile profile = AppsProfile.fromPath(profilePath);
                    List<Integer> indexes = new ArrayList<>();
                    for (String packageName : profile.packages) {
                        ApplicationItem item = new ApplicationItem();
                        item.packageName = packageName;
                        int index = mApplicationItems.indexOf(item);
                        if (index != -1) {
                            indexes.add(index);
                        }
                    }
                    Collections.sort(indexes);
                    for (int index : indexes) {
                        ApplicationItem item = mApplicationItems.get(index);
                        if (isAmongSelectedUsers(item)) {
                            candidateApplicationItems.add(item);
                        }
                    }
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            } else {
                for (ApplicationItem item : mApplicationItems) {
                    if (isAmongSelectedUsers(item)) {
                        candidateApplicationItems.add(item);
                    }
                }
            }
            // Other filters
            if (mFilterFlags == MainListOptions.FILTER_NO_FILTER) {
                if (!TextUtils.isEmpty(mSearchQuery)) {
                    filterItemsByQuery(candidateApplicationItems);
                } else {
                    mHandler.post(() -> mApplicationItemsLiveData.postValue(candidateApplicationItems));
                }
            } else {
                List<ApplicationItem> filteredApplicationItems = new ArrayList<>();
                if ((mFilterFlags & MainListOptions.FILTER_RUNNING_APPS) != 0) {
                    loadRunningApps();
                }
                for (ApplicationItem item : candidateApplicationItems) {
                    // Filter user and system apps first (if requested)
                    if ((mFilterFlags & MainListOptions.FILTER_USER_APPS) != 0 && !item.isUser) {
                        continue;
                    } else if ((mFilterFlags & MainListOptions.FILTER_SYSTEM_APPS) != 0 && item.isUser) {
                        continue;
                    }
                    // Filter installed/uninstalled
                    if ((mFilterFlags & MainListOptions.FILTER_INSTALLED_APPS) != 0 && !item.isInstalled) {
                        continue;
                    } else if ((mFilterFlags & MainListOptions.FILTER_UNINSTALLED_APPS) != 0 && item.isInstalled) {
                        continue;
                    }
                    // Filter backups
                    if ((mFilterFlags & MainListOptions.FILTER_APPS_WITH_BACKUPS) != 0 && item.backup == null) {
                        continue;
                    } else if ((mFilterFlags & MainListOptions.FILTER_APPS_WITHOUT_BACKUPS) != 0 && item.backup != null) {
                        continue;
                    }
                    // Filter rests
                    if ((mFilterFlags & MainListOptions.FILTER_FROZEN_APPS) != 0 && !item.isDisabled) {
                        continue;
                    } else if ((mFilterFlags & MainListOptions.FILTER_APPS_WITH_RULES) != 0 && item.blockedCount <= 0) {
                        continue;
                    } else if ((mFilterFlags & MainListOptions.FILTER_APPS_WITH_ACTIVITIES) != 0 && !item.hasActivities) {
                        continue;
                    } else if ((mFilterFlags & MainListOptions.FILTER_APPS_WITH_SPLITS) != 0 && !item.hasSplits) {
                        continue;
                    } else if ((mFilterFlags & MainListOptions.FILTER_RUNNING_APPS) != 0 && !item.isRunning) {
                        continue;
                    } else if ((mFilterFlags & MainListOptions.FILTER_APPS_WITH_KEYSTORE) != 0 && !item.hasKeystore) {
                        continue;
                    } else if ((mFilterFlags & MainListOptions.FILTER_APPS_WITH_SAF) != 0 && !item.usesSaf) {
                        continue;
                    } else if ((mFilterFlags & MainListOptions.FILTER_APPS_WITH_SSAID) != 0 && item.ssaid == null) {
                        continue;
                    } else if ((mFilterFlags & MainListOptions.FILTER_STOPPED_APPS) != 0 && (item.flags & ApplicationInfo.FLAG_STOPPED) == 0) {
                        continue;
                    }
                    filteredApplicationItems.add(item);
                }
                if (!TextUtils.isEmpty(mSearchQuery)) {
                    filterItemsByQuery(filteredApplicationItems);
                } else {
                    mHandler.post(() -> mApplicationItemsLiveData.postValue(filteredApplicationItems));
                }
            }
        }
    }

    private boolean isAmongSelectedUsers(@NonNull ApplicationItem applicationItem) {
        if (mSelectedUsers == null) {
            // All users
            return true;
        }
        for (int userId : mSelectedUsers) {
            if (ArrayUtils.contains(applicationItem.userIds, userId)) {
                return true;
            }
        }
        return false;
    }

    @GuardedBy("applicationItems")
    private void loadRunningApps() {
        synchronized (mApplicationItems) {
            try {
                List<ActivityManager.RunningAppProcessInfo> runningAppProcessInfoList;
                runningAppProcessInfoList = ActivityManagerCompat.getRunningAppProcesses();
                Set<String> runningPackages = new HashSet<>();
                for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : runningAppProcessInfoList) {
                    Collections.addAll(runningPackages, runningAppProcessInfo.pkgList);
                }
                for (int i = 0; i < mApplicationItems.size(); ++i) {
                    ApplicationItem applicationItem = mApplicationItems.get(i);
                    applicationItem.isRunning = applicationItem.isInstalled
                            && runningPackages.contains(applicationItem.packageName);
                    mApplicationItems.set(i, applicationItem);
                }
            } catch (Throwable th) {
                Log.e("MVM", th);
            }
        }
    }

    @GuardedBy("applicationItems")
    private void sortApplicationList(@MainListOptions.SortOrder int sortBy, boolean reverse) {
        synchronized (mApplicationItems) {
            if (sortBy != MainListOptions.SORT_BY_APP_LABEL) {
                sortApplicationList(MainListOptions.SORT_BY_APP_LABEL, false);
            }
            int mode = reverse ? -1 : 1;
            Collections.sort(mApplicationItems, (o1, o2) -> {
                switch (sortBy) {
                    case MainListOptions.SORT_BY_APP_LABEL:
                        return mode * sCollator.compare(o1.label, o2.label);
                    case MainListOptions.SORT_BY_PACKAGE_NAME:
                        return mode * o1.packageName.compareTo(o2.packageName);
                    case MainListOptions.SORT_BY_DOMAIN:
                        boolean isSystem1 = (o1.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        boolean isSystem2 = (o2.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        return mode * Boolean.compare(isSystem1, isSystem2);
                    case MainListOptions.SORT_BY_LAST_UPDATE:
                        // Sort in decreasing order
                        return -mode * o1.lastUpdateTime.compareTo(o2.lastUpdateTime);
                    case MainListOptions.SORT_BY_TOTAL_SIZE:
                        // Sort in decreasing order
                        return -mode * o1.totalSize.compareTo(o2.totalSize);
                    case MainListOptions.SORT_BY_DATA_USAGE:
                        // Sort in decreasing order
                        return -mode * o1.dataUsage.compareTo(o2.dataUsage);
                    case MainListOptions.SORT_BY_OPEN_COUNT:
                        // Sort in decreasing order
                        return -mode * Integer.compare(o1.openCount, o2.openCount);
                    case MainListOptions.SORT_BY_INSTALLATION_DATE:
                        // Sort in decreasing order
                        return -mode * Long.compare(o1.firstInstallTime, o2.firstInstallTime);
                    case MainListOptions.SORT_BY_SCREEN_TIME:
                        // Sort in decreasing order
                        return -mode * Long.compare(o1.screenTime, o2.screenTime);
                    case MainListOptions.SORT_BY_LAST_USAGE_TIME:
                        // Sort in decreasing order
                        return -mode * Long.compare(o1.lastUsageTime, o2.lastUsageTime);
                    case MainListOptions.SORT_BY_TARGET_SDK:
                        // null on top
                        if (o1.sdk == null) return -mode;
                        else if (o2.sdk == null) return +mode;
                        return mode * o1.sdk.compareTo(o2.sdk);
                    case MainListOptions.SORT_BY_SHARED_ID:
                        return mode * Integer.compare(o1.uid, o2.uid);
                    case MainListOptions.SORT_BY_SHA:
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
                    case MainListOptions.SORT_BY_BLOCKED_COMPONENTS:
                        return -mode * o1.blockedCount.compareTo(o2.blockedCount);
                    case MainListOptions.SORT_BY_FROZEN_APP:
                        return -mode * Boolean.compare(o1.isDisabled, o2.isDisabled);
                    case MainListOptions.SORT_BY_BACKUP:
                        return -mode * Boolean.compare(o1.backup != null, o2.backup != null);
                    case MainListOptions.SORT_BY_LAST_ACTION:
                        return -mode * o1.lastActionTime.compareTo(o2.lastActionTime);
                    case MainListOptions.SORT_BY_TRACKERS:
                        return -mode * o1.trackerCount.compareTo(o2.trackerCount);
                }
                return 0;
            });
        }
    }

    @WorkerThread
    private void updateInfoForUid(int uid, String action) {
        Log.d("updateInfoForUid", "Uid: %d", uid);
        String[] packages;
        if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) packages = getPackagesForUid(uid);
        else packages = mPackageManager.getPackagesForUid(uid);
        updateInfoForPackages(packages, action);
    }

    @WorkerThread
    private void updateInfoForPackages(@Nullable String[] packages, @NonNull String action) {
        Log.d("updateInfoForPackages", "packages: %s", Arrays.toString(packages));
        if (packages == null || packages.length == 0) return;
        boolean modified = false;
        switch (action) {
            case PackageChangeReceiver.ACTION_DB_PACKAGE_REMOVED:
            case PackageChangeReceiver.ACTION_DB_PACKAGE_ALTERED:
            case PackageChangeReceiver.ACTION_DB_PACKAGE_ADDED: {
                AppDb appDb = new AppDb();
                for (String packageName : packages) {
                    ApplicationItem item = getNewApplicationItem(packageName, appDb.getAllApplications(packageName));
                    modified |= item != null ? insertOrAddApplicationItem(item) : deleteApplicationItem(packageName);
                }
                break;
            }
            case PackageChangeReceiver.ACTION_PACKAGE_REMOVED:
            case PackageChangeReceiver.ACTION_PACKAGE_ALTERED:
            case PackageChangeReceiver.ACTION_PACKAGE_ADDED:
            // case BatchOpsService.ACTION_BATCH_OPS_COMPLETED:
            case Intent.ACTION_PACKAGE_REMOVED:
            case Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE:
            case Intent.ACTION_PACKAGE_ADDED:
            case Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE:
            case Intent.ACTION_PACKAGE_CHANGED: {
                List<App> appList = new AppDb().updateApplications(getApplication(), packages);
                for (String packageName : packages) {
                    ApplicationItem item = getNewApplicationItem(packageName, appList);
                    modified |= item != null ? insertOrAddApplicationItem(item) : deleteApplicationItem(packageName);
                }
                break;
            }
            default:
                return;
        }
        if (modified) {
            sortApplicationList(mSortBy, mReverseSort);
            filterItemsByFlags();
        }
    }

    @GuardedBy("applicationItems")
    private boolean insertOrAddApplicationItem(@Nullable ApplicationItem item) {
        if (item == null) return false;
        synchronized (mApplicationItems) {
            if (insertApplicationItem(item)) {
                return true;
            }
            boolean inserted = mApplicationItems.add(item);
            if (mSelectedPackageApplicationItemMap.containsKey(item.packageName)) {
                select(item);
            }
            return inserted;
        }
    }

    @GuardedBy("applicationItems")
    private boolean insertApplicationItem(@NonNull ApplicationItem item) {
        synchronized (mApplicationItems) {
            boolean isInserted = false;
            for (int i = 0; i < mApplicationItems.size(); ++i) {
                if (item.equals(mApplicationItems.get(i))) {
                    mApplicationItems.set(i, item);
                    isInserted = true;
                    if (mSelectedPackageApplicationItemMap.containsKey(item.packageName)) {
                        select(item);
                    }
                }
            }
            return isInserted;
        }
    }

    private boolean deleteApplicationItem(@NonNull String packageName) {
        synchronized (mApplicationItems) {
            ListIterator<ApplicationItem> it = mApplicationItems.listIterator();
            while (it.hasNext()) {
                ApplicationItem item = it.next();
                if (item.packageName.equals(packageName)) {
                    mSelectedPackageApplicationItemMap.remove(packageName);
                    it.remove();
                    return true;
                }
            }
            return false;
        }
    }

    @WorkerThread
    @Nullable
    private ApplicationItem getNewApplicationItem(@NonNull String packageName, @NonNull List<App> apps) {
        ApplicationItem item = new ApplicationItem();
        int thisUser = UserHandleHidden.myUserId();
        for (App app : apps) {
            if (!packageName.equals(app.packageName)) {
                // Package name didn't match
                continue;
            }
            if (app.isInstalled) {
                boolean newItem = item.packageName == null || !item.isInstalled;
                if (item.packageName == null) {
                    item.packageName = app.packageName;
                }
                item.userIds = ArrayUtils.appendInt(item.userIds, app.userId);
                item.isInstalled = true;
                item.openCount += app.openCount;
                item.screenTime += app.screenTime;
                if (item.lastUsageTime == 0L || item.lastUsageTime < app.lastUsageTime) {
                    item.lastUsageTime = app.lastUsageTime;
                }
                item.hasKeystore |= app.hasKeystore;
                item.usesSaf |= app.usesSaf;
                if (app.ssaid != null) {
                    item.ssaid = app.ssaid;
                }
                item.totalSize += app.codeSize + app.dataSize;
                item.dataUsage += app.wifiDataUsage + app.mobileDataUsage;
                if (!newItem && app.userId != thisUser) {
                    // This user has the highest priority
                    continue;
                }
            } else {
                // App not installed but may be installed in other profiles
                if (item.packageName != null) {
                    // Item exists, use the previous status
                    continue;
                } else {
                    item.packageName = app.packageName;
                    item.isInstalled = false;
                    item.hasKeystore |= app.hasKeystore;
                }
            }
            item.flags = app.flags;
            item.uid = app.uid;
            item.debuggable = app.isDebuggable();
            item.isUser = !app.isSystemApp();
            item.isDisabled = !app.isEnabled;
            item.label = app.packageLabel;
            item.sdk = app.sdk;
            item.versionName = app.versionName;
            item.versionCode = app.versionCode;
            item.sharedUserId = app.sharedUserId;
            item.sha = new Pair<>(app.certName, app.certAlgo);
            item.firstInstallTime = app.firstInstallTime;
            item.lastUpdateTime = app.lastUpdateTime;
            item.hasActivities = app.hasActivities;
            item.hasSplits = app.hasSplits;
            item.blockedCount = app.rulesCount;
            item.trackerCount = app.trackerCount;
            item.lastActionTime = app.lastActionTime;
            try {
                if (item.backup == null) {
                    item.backup = BackupUtils.getLatestBackupMetadataFromDbNoLockValidate(packageName);
                }
            } catch (Exception ignore) {
            }
        }
        if (item.packageName == null) {
            return null;
        }
        return item;
    }

    @GuardedBy("applicationItems")
    @NonNull
    private String[] getPackagesForUid(int uid) {
        synchronized (mApplicationItems) {
            List<String> packages = new LinkedList<>();
            for (ApplicationItem item : mApplicationItems) {
                if (item.uid == uid) packages.add(item.packageName);
            }
            return packages.toArray(new String[0]);
        }
    }

    @Override
    protected void onCleared() {
        if (mPackageObserver != null) getApplication().unregisterReceiver(mPackageObserver);
        executor.shutdownNow();
        super.onCleared();
    }

    public static class PackageIntentReceiver extends PackageChangeReceiver {
        private final MainViewModel mModel;

        public PackageIntentReceiver(@NonNull MainViewModel model) {
            super(model.getApplication());
            mModel = model;
        }

        @Override
        @WorkerThread
        protected void onPackageChanged(Intent intent, @Nullable Integer uid, @Nullable String[] packages) {
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
