// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.GET_SIGNING_CERTIFICATES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_DISABLED_COMPONENTS;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.UserHandleHidden;

import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public class FinderViewModel extends AndroidViewModel {
    public static final String TAG = FinderViewModel.class.getSimpleName();

    private final MutableLiveData<Long> mLastUpdateTimeLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<FilterItem.FilteredItemInfo>> mFilteredAppListLiveData = new MutableLiveData<>();
    private Future<?> mAppListLoaderFuture;
    @Nullable
    private List<FilterableAppInfo> mFilterableAppInfoList;
    @NotNull
    private final FilterItem mFilterItem = new FilterItem();

    public FinderViewModel(@NotNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
    }

    public MutableLiveData<Long> getLastUpdateTimeLiveData() {
        return mLastUpdateTimeLiveData;
    }

    public MutableLiveData<List<FilterItem.FilteredItemInfo>> getFilteredAppListLiveData() {
        return mFilteredAppListLiveData;
    }

    public FilterItem getFilterItem() {
        return mFilterItem;
    }

    public void loadFilteredAppList(boolean refresh) {
        if (mAppListLoaderFuture != null) {
            mAppListLoaderFuture.cancel(true);
        }
        mAppListLoaderFuture = ThreadUtils.postOnBackgroundThread(() -> {
            mLastUpdateTimeLiveData.postValue(-1L);
            if (mFilterableAppInfoList == null || refresh) {
                loadAppList();
            }
            if (ThreadUtils.isInterrupted() || mFilterableAppInfoList == null) return;
            mFilteredAppListLiveData.postValue(mFilterItem.getFilteredList(mFilterableAppInfoList));
            mLastUpdateTimeLiveData.postValue(System.currentTimeMillis());
        });
    }

    @WorkerThread
    private void loadAppList() {
        // TODO: 8/2/24 Allow multiple users
        // TODO: 8/2/24 Include backups for uninstalled apps
        int[] userIds = new int[]{UserHandleHidden.myUserId()}; //Users.getUsersIds();
        List<FilterableAppInfo> filterableAppInfoList = new ArrayList<>();
        for (int userId : userIds) {
            if (ThreadUtils.isInterrupted()) return;
            List<PackageInfo> packageInfoList;
            try {
                packageInfoList = PackageManagerCompat.getInstalledPackages(PackageManager.GET_META_DATA
                        | GET_SIGNING_CERTIFICATES | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS
                        | PackageManager.GET_PROVIDERS | PackageManager.GET_SERVICES | MATCH_DISABLED_COMPONENTS
                        | MATCH_UNINSTALLED_PACKAGES | MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId);
            } catch (Exception e) {
                Log.w(TAG, "Could not retrieve package info list for user %d", e, userId);
                continue;
            }
            for (PackageInfo packageInfo : packageInfoList) {
                // Interrupt thread on request
                if (ThreadUtils.isInterrupted()) return;
                filterableAppInfoList.add(new FilterableAppInfo(packageInfo));
            }
        }
        mFilterableAppInfoList = filterableAppInfoList;
    }
}
