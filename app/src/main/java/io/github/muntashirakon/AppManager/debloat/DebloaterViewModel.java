// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.app.Application;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;

public class DebloaterViewModel extends AndroidViewModel {
    @DebloaterListOptions.Filter
    private int mFilterFlags;
    private String mQueryString = null;
    @AdvancedSearchView.SearchType
    private int mQueryType;
    @NonNull
    private final List<DebloatObject> mDebloatObjects = new ArrayList<>();

    private final Map<String, int[]> mSelectedPackages = new HashMap<>();
    private final MutableLiveData<List<DebloatObject>> mDebloatObjectListLiveData = new MutableLiveData<>();
    private final ExecutorService mExecutor = MultithreadedExecutor.getNewInstance();

    public DebloaterViewModel(@NonNull Application application) {
        super(application);
        mFilterFlags = AppPref.getInt(AppPref.PrefKey.PREF_DEBLOATER_FILTER_FLAGS_INT);
    }

    public boolean hasFilterFlag(@DebloaterListOptions.Filter int flag) {
        return (mFilterFlags & flag) != 0;
    }

    public void addFilterFlag(@DebloaterListOptions.Filter int flag) {
        mFilterFlags |= flag;
        AppPref.set(AppPref.PrefKey.PREF_DEBLOATER_FILTER_FLAGS_INT, mFilterFlags);
        loadPackages();
    }

    public void removeFilterFlag(@DebloaterListOptions.Filter int flag) {
        mFilterFlags &= ~flag;
        AppPref.set(AppPref.PrefKey.PREF_DEBLOATER_FILTER_FLAGS_INT, mFilterFlags);
        loadPackages();
    }

    public void setQuery(String queryString, @AdvancedSearchView.SearchType int searchType) {
        mQueryString = queryString;
        mQueryType = searchType;
        loadPackages();
    }

    public LiveData<List<DebloatObject>> getDebloatObjectListLiveData() {
        return mDebloatObjectListLiveData;
    }

    public int getTotalItemCount() {
        return mDebloatObjects.size();
    }

    public int getSelectedItemCount() {
        return mSelectedPackages.size();
    }

    public void select(@NonNull DebloatObject debloatObject) {
        mSelectedPackages.put(debloatObject.packageName, debloatObject.getUsers());
    }

    public void deselect(@NonNull DebloatObject debloatObject) {
        mSelectedPackages.remove(debloatObject.packageName);
    }

    public void deselectAll() {
        mSelectedPackages.clear();
    }

    public boolean isSelected(@NonNull DebloatObject debloatObject) {
        return mSelectedPackages.containsKey(debloatObject.packageName);
    }

    public Map<String, int[]> getSelectedPackages() {
        return mSelectedPackages;
    }

    @NonNull
    public ArrayList<UserPackagePair> getSelectedPackagesWithUsers() {
        ArrayList<UserPackagePair> userPackagePairs = new ArrayList<>();
        int myUserId = UserHandleHidden.myUserId();
        int[] userIds = Users.getUsersIds();
        for (String packageName : mSelectedPackages.keySet()) {
            int[] userHandles = mSelectedPackages.get(packageName);
            if (userHandles == null || userHandles.length == 0) {
                // Assign current user in it
                userPackagePairs.add(new UserPackagePair(packageName, myUserId));
            } else {
                for (int userHandle : userHandles) {
                    if (!ArrayUtils.contains(userIds, userHandle)) continue;
                    userPackagePairs.add(new UserPackagePair(packageName, userHandle));
                }
            }
        }
        return userPackagePairs;
    }

    @AnyThread
    public void loadPackages() {
        mExecutor.submit(() -> {
            loadDebloatObjects();
            List<DebloatObject> debloatObjects = new ArrayList<>();
            if (mFilterFlags != DebloaterListOptions.FILTER_NO_FILTER) {
                for (DebloatObject debloatObject : mDebloatObjects) {
                    // List
                    if ((mFilterFlags & DebloaterListOptions.FILTER_LIST_AOSP) == 0 && debloatObject.type.equals("aosp")) {
                        continue;
                    }
                    if ((mFilterFlags & DebloaterListOptions.FILTER_LIST_CARRIER) == 0 && debloatObject.type.equals("carrier")) {
                        continue;
                    }
                    if ((mFilterFlags & DebloaterListOptions.FILTER_LIST_GOOGLE) == 0 && debloatObject.type.equals("google")) {
                        continue;
                    }
                    if ((mFilterFlags & DebloaterListOptions.FILTER_LIST_MISC) == 0 && debloatObject.type.equals("misc")) {
                        continue;
                    }
                    if ((mFilterFlags & DebloaterListOptions.FILTER_LIST_OEM) == 0 && debloatObject.type.equals("oem")) {
                        continue;
                    }
                    if ((mFilterFlags & DebloaterListOptions.FILTER_LIST_PENDING) == 0 && debloatObject.type.equals("pending")) {
                        continue;
                    }
                    // Removal
                    int removalType = debloatObject.getRemoval();
                    if ((mFilterFlags & DebloaterListOptions.FILTER_REMOVAL_SAFE) == 0 && removalType == DebloatObject.REMOVAL_SAFE) {
                        continue;
                    }
                    if ((mFilterFlags & DebloaterListOptions.FILTER_REMOVAL_REPLACE) == 0 && removalType == DebloatObject.REMOVAL_REPLACE) {
                        continue;
                    }
                    if ((mFilterFlags & DebloaterListOptions.FILTER_REMOVAL_CAUTION) == 0 && removalType == DebloatObject.REMOVAL_CAUTION) {
                        continue;
                    }
                    // Filter others
                    if ((mFilterFlags & DebloaterListOptions.FILTER_INSTALLED_APPS) != 0 && !debloatObject.isInstalled()) {
                        continue;
                    }
                    if ((mFilterFlags & DebloaterListOptions.FILTER_UNINSTALLED_APPS) != 0 && debloatObject.isInstalled()) {
                        continue;
                    }
                    if ((mFilterFlags & DebloaterListOptions.FILTER_USER_APPS) != 0 && !debloatObject.isUserApp()) {
                        continue;
                    }
                    if ((mFilterFlags & DebloaterListOptions.FILTER_SYSTEM_APPS) != 0 && !debloatObject.isSystemApp()) {
                        continue;
                    }
                    debloatObjects.add(debloatObject);
                }
            }
            if (TextUtils.isEmpty(mQueryString)) {
                mDebloatObjectListLiveData.postValue(debloatObjects);
                return;
            }
            // Apply searching
            List<DebloatObject> newList = AdvancedSearchView.matches(mQueryString, debloatObjects,
                    (AdvancedSearchView.ChoicesGenerator<DebloatObject>) item -> {
                        CharSequence label = item.getLabel();
                        if (label != null) {
                            return Arrays.asList(item.packageName, label.toString().toLowerCase(Locale.getDefault()));
                        } else {
                            return Collections.singletonList(item.packageName);
                        }
                    },
                    mQueryType);
            mDebloatObjectListLiveData.postValue(newList);
        });
    }

    @WorkerThread
    private void loadDebloatObjects() {
        if (!mDebloatObjects.isEmpty()) {
            return;
        }
        mDebloatObjects.addAll(StaticDataset.getDebloatObjectsWithInstalledInfo(getApplication()));
    }
}
