// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import android.app.Application;
import android.os.UserHandleHidden;

import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public class FinderViewModel extends AndroidViewModel {
    public static final String TAG = FinderViewModel.class.getSimpleName();

    private final MutableLiveData<Long> mLastUpdateTimeLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<FilterItem.FilteredItemInfo<FilterableAppInfo>>> mFilteredAppListLiveData = new MutableLiveData<>();
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

    public MutableLiveData<List<FilterItem.FilteredItemInfo<FilterableAppInfo>>> getFilteredAppListLiveData() {
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
        mFilterableAppInfoList = FilteringUtils.loadFilterableAppInfo(userIds);
    }
}
