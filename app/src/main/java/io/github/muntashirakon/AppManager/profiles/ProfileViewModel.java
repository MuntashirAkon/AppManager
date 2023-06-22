// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOpNames;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.util.Pair;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public class ProfileViewModel extends AndroidViewModel {
    private final Object mProfileLock = new Object();
    private final MutableLiveData<Pair<Integer, Boolean>> mToast = new MutableLiveData<>();
    private final MutableLiveData<ArrayList<Pair<CharSequence, ApplicationInfo>>> mInstalledApps = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mProfileLoaded = new MutableLiveData<>();
    private final MutableLiveData<String> mLogs = new MutableLiveData<>();
    @GuardedBy("profileLock")
    private String mProfileName;
    private boolean mIsNew;
    @Nullable
    private Future<?> mLoadProfileResult;
    private Future<?> mLoadAppsResult;

    public ProfileViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        if (mLoadProfileResult != null) {
            mLoadProfileResult.cancel(true);
        }
        if (mLoadAppsResult != null) {
            mLoadAppsResult.cancel(true);
        }
        super.onCleared();
    }

    public LiveData<Pair<Integer, Boolean>> observeToast() {
        return mToast;
    }

    public LiveData<ArrayList<Pair<CharSequence, ApplicationInfo>>> observeInstalledApps() {
        return mInstalledApps;
    }

    public LiveData<Boolean> observeProfileLoaded() {
        return mProfileLoaded;
    }

    public LiveData<String> getLogs() {
        return mLogs;
    }

    @AnyThread
    public void loadInstalledApps() {
        if (mLoadAppsResult != null) {
            mLoadAppsResult.cancel(true);
        }
        mLoadAppsResult = ThreadUtils.postOnBackgroundThread(() -> {
            PackageManager pm = getApplication().getPackageManager();
            try {
                ArrayList<Pair<CharSequence, ApplicationInfo>> itemPairs;
                List<ApplicationInfo> applicationInfoList;
                applicationInfoList = PackageUtils.getAllApplications(MATCH_UNINSTALLED_PACKAGES
                        | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES);
                HashSet<String> applicationInfoHashMap = new HashSet<>();
                itemPairs = new ArrayList<>();
                for (ApplicationInfo info : applicationInfoList) {
                    if (applicationInfoHashMap.contains(info.packageName)) {
                        continue;
                    }
                    applicationInfoHashMap.add(info.packageName);
                    itemPairs.add(new Pair<>(pm.getApplicationLabel(info), info));
                    if (ThreadUtils.isInterrupted()) {
                        return;
                    }
                }
                Collections.sort(itemPairs, (o1, o2) -> o1.first.toString().compareToIgnoreCase(o2.first.toString()));
                mInstalledApps.postValue(itemPairs);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @GuardedBy("profileLock")
    public void setProfileName(String profileName, boolean isNew) {
        synchronized (mProfileLock) {
            mProfileName = profileName;
            mIsNew = isNew;
        }
    }

    public String getProfileName() {
        return mProfileName;
    }

    @GuardedBy("profileLock")
    private ProfileMetaManager.Profile profile;
    @GuardedBy("profileLock")
    private ProfileMetaManager profileMetaManager;

    @AnyThread
    public void loadProfile() {
        if (mLoadProfileResult != null) {
            mLoadProfileResult.cancel(true);
        }
        mLoadProfileResult = ThreadUtils.postOnBackgroundThread(() -> {
            loadProfileInternal();
            mProfileLoaded.postValue(profileMetaManager == null);
        });
    }

    @AnyThread
    public void loadLogs() {
        ThreadUtils.postOnBackgroundThread(() -> mLogs.postValue(ProfileLogger.getAllLogs(mProfileName)));
    }

    @WorkerThread
    @GuardedBy("profileLock")
    private void loadProfileInternal() {
        synchronized (mProfileLock) {
            profileMetaManager = new ProfileMetaManager(mProfileName);
            profile = profileMetaManager.getProfile();
        }
    }

    @WorkerThread
    @GuardedBy("profileLock")
    public void cloneProfileInternal(String profileName) {
        setProfileName(profileName, true);
        synchronized (mProfileLock) {
            profileMetaManager = new ProfileMetaManager(profileName, profile);
            profile = profileMetaManager.getProfile();
        }
    }

    @AnyThread
    public void cloneProfile(String profileName) {
        if (mLoadProfileResult != null) {
            mLoadProfileResult.cancel(true);
        }
        mLoadProfileResult = ThreadUtils.postOnBackgroundThread(() -> {
            if (profileMetaManager == null) {
                loadProfileInternal();
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
            }
            cloneProfileInternal(profileName);
            mToast.postValue(new Pair<>(R.string.done, false));
            mProfileLoaded.postValue(profileMetaManager == null);
        });
    }

    @AnyThread
    public void loadNewProfile(@Nullable String[] initialPackages) {
        if (mLoadProfileResult != null) {
            mLoadProfileResult.cancel(true);
        }
        mLoadProfileResult = ThreadUtils.postOnBackgroundThread(() -> {
            loadProfileInternal();
            if (initialPackages != null) {
                profile.packages = initialPackages;
            }
            mProfileLoaded.postValue(profileMetaManager == null);
        });
    }

    @AnyThread
    public void loadAndCloneProfile(@NonNull String profileName) {
        if (mLoadProfileResult != null) {
            mLoadProfileResult.cancel(true);
        }
        mLoadProfileResult = ThreadUtils.postOnBackgroundThread(() -> {
            if (profileMetaManager == null) {
                loadProfileInternal();
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
            }
            cloneProfileInternal(profileName);
            mProfileLoaded.postValue(profileMetaManager == null);
        });
    }

    private MutableLiveData<ArrayList<String>> packagesLiveData;

    @AnyThread
    @GuardedBy("profileLock")
    public void setPackages(@NonNull List<String> packages) {
        synchronized (mProfileLock) {
            profile.packages = packages.toArray(new String[0]);
            Log.e("Packages", packages.toString());
            loadPackages();
        }
    }

    @GuardedBy("profileLock")
    public void deletePackage(@NonNull String packageName) {
        synchronized (mProfileLock) {
            profile.packages = Objects.requireNonNull(ArrayUtils.removeString(profile.packages, packageName));
            loadPackages();
        }
    }

    @AnyThread
    @GuardedBy("profileLock")
    public void save() {
        ThreadUtils.postOnBackgroundThread(() -> {
            synchronized (mProfileLock) {
                try {
                    profileMetaManager.writeProfile();
                    mToast.postValue(new Pair<>(R.string.saved_successfully, false));
                } catch (IOException | JSONException | RemoteException e) {
                    e.printStackTrace();
                    mToast.postValue(new Pair<>(R.string.saving_failed, false));
                }
            }
        });
    }

    @AnyThread
    public void discard() {
        if (mLoadProfileResult != null) {
            mLoadProfileResult.cancel(true);
        }
        mLoadProfileResult = ThreadUtils.postOnBackgroundThread(() -> {
            synchronized (mProfileLock) {
                loadProfileInternal();
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
                loadPackages();
            }
        });
    }

    @AnyThread
    public void delete() {
        ThreadUtils.postOnBackgroundThread(() -> {
            synchronized (mProfileLock) {
                if (profileMetaManager.deleteProfile()) {
                    mToast.postValue(new Pair<>(R.string.deleted_successfully, true));
                } else mToast.postValue(new Pair<>(R.string.deletion_failed, false));
            }
        });
    }

    @NonNull
    public LiveData<ArrayList<String>> getPackages() {
        if (packagesLiveData == null) {
            packagesLiveData = new MutableLiveData<>();
            loadPackages();
        }
        return packagesLiveData;
    }

    public ArrayList<String> getCurrentPackages() {
        return new ArrayList<>(Arrays.asList(profile.packages));
    }

    @AnyThread
    public void loadPackages() {
        if (mLoadProfileResult != null) {
            mLoadProfileResult.cancel(true);
        }
        mLoadProfileResult = ThreadUtils.postOnBackgroundThread(() -> {
            synchronized (mProfileLock) {
                if (profileMetaManager == null) loadProfileInternal();
                packagesLiveData.postValue(new ArrayList<>(Arrays.asList(profile.packages)));
            }
        });
    }

    public void putBoolean(@NonNull String key, boolean value) {
        switch (key) {
            case "freeze":
                profile.freeze = value;
                break;
            case "force_stop":
                profile.forceStop = value;
                break;
            case "clear_cache":
                profile.clearCache = value;
                break;
            case "clear_data":
                profile.clearData = value;
                break;
            case "block_trackers":
                profile.blockTrackers = value;
                break;
            case "save_apk":
                profile.saveApk = value;
                break;
            case "allow_routine":
                profile.allowRoutine = value;
                break;
        }
    }

    public boolean getBoolean(@NonNull String key, boolean defValue) {
        switch (key) {
            case "freeze":
                return profile.freeze;
            case "force_stop":
                return profile.forceStop;
            case "clear_cache":
                return profile.clearCache;
            case "clear_data":
                return profile.clearData;
            case "block_trackers":
                return profile.blockTrackers;
            case "save_apk":
                return profile.saveApk;
            case "allow_routine":
                return profile.allowRoutine;
            default:
                return defValue;
        }
    }

    @Nullable
    public String getComment() {
        return profile.comment;
    }

    public void setComment(@Nullable String comment) {
        profile.comment = comment;
    }

    public void setState(@ProfileMetaManager.ProfileState String state) {
        profile.state = state;
    }

    @NonNull
    @ProfileMetaManager.ProfileState
    public String getState() {
        return profile.state == null ? ProfileMetaManager.STATE_OFF : profile.state;
    }

    public void setUsers(@Nullable int[] users) {
        profile.users = users;
    }

    @WorkerThread
    @NonNull
    public int[] getUsers() {
        return profile.users == null ? Users.getUsersIds() : profile.users;
    }

    public void setExportRules(@Nullable Integer flags) {
        profile.exportRules = flags;
    }

    @Nullable
    public Integer getExportRules() {
        return profile.exportRules;
    }

    public void setComponents(@Nullable String[] components) {
        profile.components = components;
    }

    @Nullable
    public String[] getComponents() {
        return profile.components;
    }

    public void setPermissions(@Nullable String[] permissions) {
        if (permissions != null) {
            for (String permission : permissions) {
                if (permission.equals("*")) {
                    // Wildcard found, ignore all permissions in favour of global wildcard
                    profile.permissions = new String[]{"*"};
                    return;
                }
            }
        }
        profile.permissions = permissions;
    }

    @Nullable
    public String[] getPermissions() {
        return profile.permissions;
    }

    public void setAppOps(@Nullable String[] appOpsStr) {
        if (appOpsStr == null) {
            profile.appOps = null;
            return;
        }
        Set<Integer> selectedAppOps = new HashSet<>(appOpsStr.length);
        List<Integer> appOpList = AppOpsManagerCompat.getAllOps();
        List<CharSequence> appOpNameList = Arrays.asList(getAppOpNames(appOpList));
        for (CharSequence appOpStr : appOpsStr) {
            if (appOpStr.equals("*")) {
                // Wildcard found, ignore all app ops in favour of global wildcard
                profile.appOps = new int[]{AppOpsManagerCompat.OP_NONE};
                return;
            }
            try {
                selectedAppOps.add(Utils.getIntegerFromString(appOpStr, appOpNameList, appOpList));
            } catch (IllegalArgumentException ignore) {
            }
        }
        profile.appOps = selectedAppOps.size() == 0 ? null : ArrayUtils.convertToIntArray(selectedAppOps);
    }

    @Nullable
    public String[] getAppOps() {
        int[] appOps = profile.appOps;
        if (appOps == null) return null;
        String[] appOpsStr = new String[appOps.length];
        for (int i = 0; i < appOps.length; ++i) {
            appOpsStr[i] = String.valueOf(appOps[i]);
        }
        return appOpsStr;
    }

    public void setBackupInfo(@Nullable ProfileMetaManager.Profile.BackupInfo backupInfo) {
        profile.backupData = backupInfo;
    }

    @Nullable
    public ProfileMetaManager.Profile.BackupInfo getBackupInfo() {
        return profile.backupData;
    }
}
