// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class DebloaterViewModel extends AndroidViewModel {
    private boolean filterInstalledApps = true;
    private DebloatObject[] mDebloatObjects;

    private final Map<String, int[]> mSelectedPackages = new HashMap<>();
    private final MutableLiveData<List<DebloatObject>> mDebloatObjectLiveData = new MutableLiveData<>();
    private final ExecutorService mExecutor = MultithreadedExecutor.getNewInstance();
    private final Gson mGson = new Gson();

    public DebloaterViewModel(@NonNull Application application) {
        super(application);
    }

    public void setFilterInstalledApps(boolean filterInstalledApps) {
        this.filterInstalledApps = filterInstalledApps;
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
                // Could be a backup only item
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
            List<DebloatObject> debloatObjects;
            if (filterInstalledApps) {
                debloatObjects = new ArrayList<>();
                for (DebloatObject debloatObject : mDebloatObjects) {
                    if (debloatObject.isInstalled()) {
                        debloatObjects.add(debloatObject);
                    }
                }
            } else {
                debloatObjects = Arrays.asList(mDebloatObjects);
            }
            mDebloatObjectLiveData.postValue(debloatObjects);
        });
    }

    @WorkerThread
    private void loadDebloatObjects() {
        if (mDebloatObjects != null) {
            return;
        }
        String jsonContent = FileUtils.getContentFromAssets(getApplication(), "debloat.json");
        mDebloatObjects = mGson.fromJson(jsonContent, DebloatObject[].class);
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
                if (debloatObject.getPackageInfo() == null) {
                    try {
                        PackageInfo pi = PackageManagerCompat.getPackageInfo(debloatObject.packageName, PackageUtils.flagMatchUninstalled, app.userId);
                        ApplicationInfo ai = pi.applicationInfo;
                        if ((ai.flags & ApplicationInfo.FLAG_INSTALLED) != 0) {
                            // Reset installed
                            debloatObject.setInstalled(true);
                        }
                        debloatObject.setPackageInfo(pi);
                    } catch (RemoteException | PackageManager.NameNotFoundException ignore) {
                    }
                }
            }
        }
    }
}
