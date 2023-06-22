// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;

public class DebloaterViewModel extends AndroidViewModel {
    @DebloaterListOptions.Filter
    private int mFilterFlags;
    private String mQueryString = null;
    @AdvancedSearchView.SearchType
    private int mQueryType;
    private DebloatObject[] mDebloatObjects;

    private final Map<String, int[]> mSelectedPackages = new HashMap<>();
    private final MutableLiveData<List<DebloatObject>> mDebloatObjectLiveData = new MutableLiveData<>();
    private final ExecutorService mExecutor = MultithreadedExecutor.getNewInstance();
    private final Gson mGson = new Gson();

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

    public LiveData<List<DebloatObject>> getDebloatObjectLiveData() {
        return mDebloatObjectLiveData;
    }

    public int getTotalItemCount() {
        return mDebloatObjects != null ? mDebloatObjects.length : 0;
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
                    if ((mFilterFlags & DebloaterListOptions.FILTER_LIST_AOSP) == 0 && debloatObject.type.equals("Aosp")) {
                        continue;
                    }
                    if ((mFilterFlags & DebloaterListOptions.FILTER_LIST_OEM) == 0 && debloatObject.type.equals("Oem")) {
                        continue;
                    }
                    if ((mFilterFlags & DebloaterListOptions.FILTER_LIST_CARRIER) == 0 && debloatObject.type.equals("Carrier")) {
                        continue;
                    }
                    if ((mFilterFlags & DebloaterListOptions.FILTER_LIST_GOOGLE) == 0 && debloatObject.type.equals("Google")) {
                        continue;
                    }
                    if ((mFilterFlags & DebloaterListOptions.FILTER_LIST_MISC) == 0 && debloatObject.type.equals("Misc")) {
                        continue;
                    }
                    if ((mFilterFlags & DebloaterListOptions.FILTER_LIST_PENDING) == 0 && debloatObject.type.equals("Pending")) {
                        continue;
                    }
                    // Removal
                    int removalType = debloatObject.getmRemoval();
                    if ((mFilterFlags & DebloaterListOptions.FILTER_REMOVAL_SAFE) == 0 && removalType == DebloatObject.REMOVAL_SAFE) {
                        continue;
                    }
                    if ((mFilterFlags & DebloaterListOptions.FILTER_REMOVAL_REPLACE) == 0 && removalType == DebloatObject.REMOVAL_REPLACE) {
                        continue;
                    }
                    if ((mFilterFlags & DebloaterListOptions.FILTER_REMOVAL_CAUTION) == 0 && removalType == DebloatObject.REMOVAL_CAUTION) {
                        continue;
                    }
                    if ((mFilterFlags & DebloaterListOptions.FILTER_REMOVAL_UNSAFE) == 0 && removalType == DebloatObject.REMOVAL_UNSAFE) {
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
                mDebloatObjectLiveData.postValue(debloatObjects);
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
            mDebloatObjectLiveData.postValue(newList);
        });
    }

    @WorkerThread
    private void loadDebloatObjects() {
        if (mDebloatObjects != null) {
            return;
        }
        String jsonContent = FileUtils.getContentFromAssets(getApplication(), "debloat.json");
        mDebloatObjects = mGson.fromJson(jsonContent, DebloatObject[].class);
        PackageManager pm = getApplication().getPackageManager();
        // Fetch package info for all users
        AppDb appDb = new AppDb();
        for (DebloatObject debloatObject : mDebloatObjects) {
            List<App> apps = appDb.getAllApplications(debloatObject.packageName);
            for (App app : apps) {
                if (!app.isInstalled) {
                    continue;
                }
                debloatObject.setInstalled(true);
                debloatObject.addUser(app.userId);
                debloatObject.setSystemApp((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                if (debloatObject.getPackageInfo() == null) {
                    try {
                        PackageInfo pi = PackageManagerCompat.getPackageInfo(debloatObject.packageName,
                                MATCH_UNINSTALLED_PACKAGES
                                        | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, app.userId);
                        ApplicationInfo ai = pi.applicationInfo;
                        if ((ai.flags & ApplicationInfo.FLAG_INSTALLED) != 0) {
                            // Reset installed
                            debloatObject.setInstalled(true);
                        }
                        // Reset system app
                        debloatObject.setSystemApp((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                        debloatObject.setPackageInfo(pi);
                        debloatObject.setLabel(ai.loadLabel(pm));
                    } catch (RemoteException | PackageManager.NameNotFoundException ignore) {
                    }
                }
            }
        }
    }
}
