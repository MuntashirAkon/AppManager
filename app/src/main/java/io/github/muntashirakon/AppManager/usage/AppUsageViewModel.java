// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import static io.github.muntashirakon.AppManager.usage.UsageUtils.USAGE_TODAY;

import android.app.Application;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public class AppUsageViewModel extends AndroidViewModel {
    private final MutableLiveData<List<PackageUsageInfo>> mPackageUsageInfoListLiveData = new MutableLiveData<>();
    private final MutableLiveData<PackageUsageInfo> mPackageUsageInfoLiveData = new MutableLiveData<>();
    private final List<PackageUsageInfo> mPackageUsageInfoList = Collections.synchronizedList(new ArrayList<>());

    private long mTotalScreenTime;
    private boolean mHasMultipleUsers;
    @UsageUtils.IntervalType
    private int mCurrentInterval = USAGE_TODAY;
    private int mSortOrder = SortOrder.SORT_BY_SCREEN_TIME;

    public AppUsageViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<PackageUsageInfo>> getPackageUsageInfoList() {
        return mPackageUsageInfoListLiveData;
    }

    public LiveData<PackageUsageInfo> getPackageUsageInfo() {
        return mPackageUsageInfoLiveData;
    }

    public void setCurrentInterval(@UsageUtils.IntervalType int currentInterval) {
        mCurrentInterval = currentInterval;
        loadPackageUsageInfoList();
    }

    @UsageUtils.IntervalType
    public int getCurrentInterval() {
        return mCurrentInterval;
    }

    public void setSortOrder(int sortOrder) {
        mSortOrder = sortOrder;
        ThreadUtils.postOnBackgroundThread(this::sortItems);
    }

    public int getSortOrder() {
        return mSortOrder;
    }

    public long getTotalScreenTime() {
        return mTotalScreenTime;
    }

    public boolean hasMultipleUsers() {
        return mHasMultipleUsers;
    }

    public void loadPackageUsageInfo(PackageUsageInfo usageInfo) {
        ThreadUtils.postOnBackgroundThread(() -> ExUtils.exceptionAsIgnored(() -> {
            PackageUsageInfo packageUsageInfo = AppUsageStatsManager.getInstance().getUsageStatsForPackage(
                    usageInfo.packageName, mCurrentInterval, usageInfo.userId);
            packageUsageInfo.copyOthers(usageInfo);
            mPackageUsageInfoLiveData.postValue(packageUsageInfo);
        }));
    }

    @AnyThread
    public void loadPackageUsageInfoList() {
        ThreadUtils.postOnBackgroundThread(() -> {
            int[] userIds = Users.getUsersIds();
            AppUsageStatsManager usageStatsManager = AppUsageStatsManager.getInstance();
            mPackageUsageInfoList.clear();
            for (int userId : userIds) {
                ExUtils.exceptionAsIgnored(() -> mPackageUsageInfoList.addAll(usageStatsManager
                        .getUsageStats(mCurrentInterval, userId)));
            }
            mTotalScreenTime = 0;
            Set<Integer> users = new HashSet<>(3);
            for (PackageUsageInfo appItem : mPackageUsageInfoList) {
                mTotalScreenTime += appItem.screenTime;
                users.add(appItem.userId);
            }
            mHasMultipleUsers = users.size() > 1;
            sortItems();
        });
    }

    private void sortItems() {
        Collator collator = Collator.getInstance();
        Collections.sort(mPackageUsageInfoList, ((o1, o2) -> {
            switch (mSortOrder) {
                case SortOrder.SORT_BY_APP_LABEL:
                    return collator.compare(o1.appLabel, o2.appLabel);
                case SortOrder.SORT_BY_LAST_USED:
                    return -Long.compare(o1.lastUsageTime, o2.lastUsageTime);
                case SortOrder.SORT_BY_MOBILE_DATA:
                    if (o1.mobileData == null) return o2.mobileData == null ? 0 : -1;
                    return -o1.mobileData.compareTo(o2.mobileData);
                case SortOrder.SORT_BY_PACKAGE_NAME:
                    return o1.packageName.compareToIgnoreCase(o2.packageName);
                case SortOrder.SORT_BY_SCREEN_TIME:
                    return -Long.compare(o1.screenTime, o2.screenTime);
                case SortOrder.SORT_BY_TIMES_OPENED:
                    return -Integer.compare(o1.timesOpened, o2.timesOpened);
                case SortOrder.SORT_BY_WIFI_DATA:
                    if (o1.wifiData == null) return o2.wifiData == null ? 0 : -1;
                    return -o1.wifiData.compareTo(o2.wifiData);
            }
            return 0;
        }));
        mPackageUsageInfoListLiveData.postValue(mPackageUsageInfoList);
    }
}