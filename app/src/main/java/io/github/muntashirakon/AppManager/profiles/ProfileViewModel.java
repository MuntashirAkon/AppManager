// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_DISABLED_COMPONENTS;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOpNames;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.UserHandleHidden;

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
import java.io.OutputStream;
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
import io.github.muntashirakon.AppManager.profiles.struct.AppsProfile;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.Path;

public class ProfileViewModel extends AndroidViewModel {
    private final Object mProfileLock = new Object();
    private final MutableLiveData<Pair<Integer, Boolean>> mToast = new MutableLiveData<>();
    private final MutableLiveData<ArrayList<Pair<CharSequence, ApplicationInfo>>> mInstalledApps = new MutableLiveData<>();
    private final MutableLiveData<String> mProfileLoaded = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mProfileModifiedLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> mLogs = new MutableLiveData<>();

    private MutableLiveData<ArrayList<AppsFragment.AppsFragmentItem>> packagesLiveData;
    @GuardedBy("profileLock")
    @Nullable
    private AppsProfile mProfile;
    private boolean mIsModified;
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

    public boolean isModified() {
        return mIsModified;
    }

    public void setModified(boolean modified) {
        if (mIsModified != modified) {
            mIsModified = modified;
            if (ThreadUtils.isMainThread()) {
                mProfileModifiedLiveData.setValue(modified);
            } else {
                mProfileModifiedLiveData.postValue(modified);
            }
        }
    }

    public LiveData<Pair<Integer, Boolean>> observeToast() {
        return mToast;
    }

    public LiveData<ArrayList<Pair<CharSequence, ApplicationInfo>>> observeInstalledApps() {
        return mInstalledApps;
    }

    public LiveData<String> observeProfileLoaded() {
        return mProfileLoaded;
    }

    public LiveData<Boolean> getProfileModifiedLiveData() {
        return mProfileModifiedLiveData;
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
                        | MATCH_STATIC_SHARED_AND_SDK_LIBRARIES);
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
                List<String> selectedPackages = mProfile != null ?
                        Arrays.asList(mProfile.packages) : Collections.emptyList();
                Collections.sort(itemPairs, (o1, o2) -> o1.first.toString().compareToIgnoreCase(o2.first.toString()));
                Collections.sort(itemPairs, (o1, o2) -> {
                    boolean o1Selected = selectedPackages.contains(o1.second.packageName);
                    boolean o2Selected = selectedPackages.contains(o2.second.packageName);
                    if (o1Selected && o2Selected) {
                        return 0;
                    }
                    if (o1Selected) {
                        return -1;
                    }
                    if (o2Selected) {
                        return +1;
                    }
                    return 0;
                });
                mInstalledApps.postValue(itemPairs);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public String getProfileName() {
        return mProfile != null ? mProfile.name : null;
    }

    public String getProfileId() {
        return mProfile != null ? mProfile.profileId : null;
    }

    @AnyThread
    public void loadProfile(@NonNull String profileId) {
        if (mLoadProfileResult != null) {
            mLoadProfileResult.cancel(true);
        }
        mLoadProfileResult = ThreadUtils.postOnBackgroundThread(() -> {
            loadProfileInternal(profileId);
            mProfileLoaded.postValue(mProfile != null ? mProfile.name : null);
        });
    }

    @AnyThread
    public void loadLogs() {
        if (mProfile == null) {
            return;
        }
        ThreadUtils.postOnBackgroundThread(() -> mLogs.postValue(ProfileLogger.getAllLogs(mProfile.profileId)));
    }

    @WorkerThread
    @GuardedBy("profileLock")
    private void loadProfileInternal(@NonNull String profileId) {
        synchronized (mProfileLock) {
            Path profilePath = ProfileManager.findProfilePathById(profileId);
            try {
                mProfile = AppsProfile.fromPath(profilePath);
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @WorkerThread
    @GuardedBy("profileLock")
    private void cloneProfileInternal(@NonNull String newProfileName) {
        synchronized (mProfileLock) {
            mProfile = AppsProfile.newProfile(newProfileName, mProfile);
        }
    }

    @AnyThread
    public void cloneProfile(@NonNull String newProfileName) {
        if (mLoadProfileResult != null) {
            mLoadProfileResult.cancel(true);
        }
        mLoadProfileResult = ThreadUtils.postOnBackgroundThread(() -> {
            if (mProfile == null) {
                mToast.postValue(new Pair<>(R.string.failed, false));
                return;
            }
            cloneProfileInternal(newProfileName);
            mToast.postValue(new Pair<>(R.string.done, false));
            setModified(true);
            mProfileLoaded.postValue(mProfile != null ? mProfile.name : null);
        });
    }

    @AnyThread
    public void loadNewProfile(@NonNull String newProfileName, @Nullable String[] initialPackages) {
        if (mLoadProfileResult != null) {
            mLoadProfileResult.cancel(true);
        }
        mLoadProfileResult = ThreadUtils.postOnBackgroundThread(() -> {
            synchronized (mProfileLock) {
                mProfile = AppsProfile.newProfile(newProfileName, null);
            }
            if (initialPackages != null) {
                mProfile.packages = initialPackages;
            }
            setModified(true);
            mProfileLoaded.postValue(mProfile != null ? mProfile.name : null);
        });
    }

    @AnyThread
    public void loadAndCloneProfile(@NonNull String profileId, @NonNull String newProfileName) {
        if (mLoadProfileResult != null) {
            mLoadProfileResult.cancel(true);
        }
        mLoadProfileResult = ThreadUtils.postOnBackgroundThread(() -> {
            if (mProfile == null) {
                loadProfileInternal(profileId);
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
            }
            cloneProfileInternal(newProfileName);
            setModified(true);
            mProfileLoaded.postValue(mProfile != null ? mProfile.name : null);
        });
    }

    @AnyThread
    @GuardedBy("profileLock")
    public void setPackages(@NonNull List<String> packages) {
        if (mProfile == null) return;
        setModified(true);
        synchronized (mProfileLock) {
            mProfile.packages = packages.toArray(new String[0]);
            Log.e("Packages", "%s", packages);
            loadPackages();
        }
    }

    @GuardedBy("profileLock")
    public void deletePackage(@NonNull String packageName) {
        if (mProfile == null) return;
        synchronized (mProfileLock) {
            mProfile.packages = Objects.requireNonNull(ArrayUtils.removeString(mProfile.packages, packageName));
            loadPackages();
        }
    }

    @AnyThread
    @GuardedBy("profileLock")
    public void save(boolean exitOnSave) {
        if (mProfile == null) return; // Should never happen
        ThreadUtils.postOnBackgroundThread(() -> {
            synchronized (mProfileLock) {
                try {
                    Path profilePath = ProfileManager.requireProfilePathById(mProfile.profileId);
                    try (OutputStream os = profilePath.openOutputStream()) {
                        mProfile.write(os);
                        mToast.postValue(new Pair<>(R.string.saved_successfully, exitOnSave));
                        setModified(false);
                    }
                } catch (IOException e) {
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
                if (mProfile != null) {
                    loadProfileInternal(mProfile.profileId);
                }
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
                loadPackages();
                setModified(false);
            }
        });
    }

    @AnyThread
    public void delete() {
        ThreadUtils.postOnBackgroundThread(() -> {
            synchronized (mProfileLock) {
                if (mProfile == null) return;
                if (ProfileManager.deleteProfile(mProfile.profileId)) {
                    mToast.postValue(new Pair<>(R.string.deleted_successfully, true));
                } else mToast.postValue(new Pair<>(R.string.deletion_failed, false));
            }
        });
    }

    @NonNull
    public LiveData<ArrayList<AppsFragment.AppsFragmentItem>> getPackages() {
        if (packagesLiveData == null) {
            packagesLiveData = new MutableLiveData<>();
            loadPackages();
        }
        return packagesLiveData;
    }

    public List<String> getCurrentPackages() {
        if (mProfile == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(mProfile.packages);
    }

    @AnyThread
    public void loadPackages() {
        if (mLoadProfileResult != null) {
            mLoadProfileResult.cancel(true);
        }
        mLoadProfileResult = ThreadUtils.postOnBackgroundThread(() -> {
            synchronized (mProfileLock) {
                if (mProfile == null) return; // Can happen
                ArrayList<AppsFragment.AppsFragmentItem> oldItems = packagesLiveData.getValue();
                ArrayList<AppsFragment.AppsFragmentItem> items = new ArrayList<>(mProfile.packages.length);
                int userId = UserHandleHidden.myUserId();
                PackageManager pm = getApplication().getPackageManager();
                for (String packageName : mProfile.packages) {
                    AppsFragment.AppsFragmentItem item = new AppsFragment.AppsFragmentItem(packageName);
                    // Check for old item for faster loading in case there are hundreds of items
                    if (oldItems != null) {
                        int i = oldItems.indexOf(item);
                        if (i != -1) {
                            AppsFragment.AppsFragmentItem oldItem = oldItems.get(i);
                            if (oldItem.applicationInfo != null) {
                                item.applicationInfo = oldItem.applicationInfo;
                                item.label = oldItem.label;
                            }
                        }
                    }
                    if (item.applicationInfo == null) {
                        try {
                            item.applicationInfo = PackageManagerCompat.getApplicationInfo(packageName,
                                    MATCH_UNINSTALLED_PACKAGES | MATCH_DISABLED_COMPONENTS
                                            | MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId);
                        } catch (RemoteException | PackageManager.NameNotFoundException ignore) {
                        }
                        if (item.applicationInfo != null) {
                            item.label = item.applicationInfo.loadLabel(pm);
                        }
                        if (Objects.equals(item.label, packageName)) {
                            item.label = null;
                        }
                    }
                    items.add(item);
                }
                packagesLiveData.postValue(items);
            }
        });
    }

    public void putBoolean(@NonNull String key, boolean value) {
        if (mProfile == null) return;
        setModified(true);
        switch (key) {
            case "freeze":
                mProfile.freeze = value;
                break;
            case "force_stop":
                mProfile.forceStop = value;
                break;
            case "clear_cache":
                mProfile.clearCache = value;
                break;
            case "clear_data":
                mProfile.clearData = value;
                break;
            case "block_trackers":
                mProfile.blockTrackers = value;
                break;
            case "save_apk":
                mProfile.saveApk = value;
                break;
            case "allow_routine":
                mProfile.allowRoutine = value;
                break;
        }
    }

    public boolean getBoolean(@NonNull String key, boolean defValue) {
        if (mProfile == null) return defValue;
        switch (key) {
            case "freeze":
                return mProfile.freeze;
            case "force_stop":
                return mProfile.forceStop;
            case "clear_cache":
                return mProfile.clearCache;
            case "clear_data":
                return mProfile.clearData;
            case "block_trackers":
                return mProfile.blockTrackers;
            case "save_apk":
                return mProfile.saveApk;
            case "allow_routine":
                return mProfile.allowRoutine;
            default:
                return defValue;
        }
    }

    @Nullable
    public String getComment() {
        if (mProfile == null) return null;
        return mProfile.comment;
    }

    public void setComment(@Nullable String comment) {
        if (mProfile == null) return;
        setModified(true);
        mProfile.comment = comment;
    }

    public void setState(@AppsProfile.ProfileState String state) {
        if (mProfile == null) return;
        setModified(true);
        mProfile.state = state;
    }

    @NonNull
    @AppsProfile.ProfileState
    public String getState() {
        return mProfile == null || mProfile.state == null ? AppsProfile.STATE_OFF : mProfile.state;
    }

    public void setUsers(@Nullable int[] users) {
        if (mProfile == null) return;
        setModified(true);
        mProfile.users = users;
    }

    @WorkerThread
    @NonNull
    public int[] getUsers() {
        return mProfile == null || mProfile.users == null ? Users.getUsersIds() : mProfile.users;
    }

    public void setExportRules(@Nullable Integer flags) {
        if (mProfile == null) return;
        setModified(true);
        mProfile.exportRules = flags;
    }

    @Nullable
    public Integer getExportRules() {
        if (mProfile == null) return null;
        return mProfile.exportRules;
    }

    public void setComponents(@Nullable String[] components) {
        if (mProfile == null) return;
        setModified(true);
        mProfile.components = components;
    }

    @Nullable
    public String[] getComponents() {
        if (mProfile == null) return null;
        return mProfile.components;
    }

    public void setPermissions(@Nullable String[] permissions) {
        if (mProfile == null) return;
        setModified(true);
        if (permissions != null) {
            for (String permission : permissions) {
                if (permission.equals("*")) {
                    // Wildcard found, ignore all permissions in favour of global wildcard
                    mProfile.permissions = new String[]{"*"};
                    return;
                }
            }
        }
        mProfile.permissions = permissions;
    }

    @Nullable
    public String[] getPermissions() {
        if (mProfile == null) return null;
        return mProfile.permissions;
    }

    public void setAppOps(@Nullable String[] appOpsStr) {
        if (mProfile == null) return;
        setModified(true);
        if (appOpsStr == null) {
            mProfile.appOps = null;
            return;
        }
        Set<Integer> selectedAppOps = new HashSet<>(appOpsStr.length);
        List<Integer> appOpList = AppOpsManagerCompat.getAllOps();
        List<CharSequence> appOpNameList = Arrays.asList(getAppOpNames(appOpList));
        for (CharSequence appOpStr : appOpsStr) {
            if (appOpStr.equals("*")) {
                // Wildcard found, ignore all app ops in favour of global wildcard
                mProfile.appOps = new int[]{AppOpsManagerCompat.OP_NONE};
                return;
            }
            try {
                selectedAppOps.add(Utils.getIntegerFromString(appOpStr, appOpNameList, appOpList));
            } catch (IllegalArgumentException ignore) {
            }
        }
        mProfile.appOps = selectedAppOps.isEmpty() ? null : ArrayUtils.convertToIntArray(selectedAppOps);
    }

    @Nullable
    public String[] getAppOps() {
        if (mProfile == null) return null;
        int[] appOps = mProfile.appOps;
        if (appOps == null) return null;
        String[] appOpsStr = new String[appOps.length];
        for (int i = 0; i < appOps.length; ++i) {
            appOpsStr[i] = String.valueOf(appOps[i]);
        }
        return appOpsStr;
    }

    public void setBackupInfo(@Nullable AppsProfile.BackupInfo backupInfo) {
        if (mProfile == null) return;
        setModified(true);
        mProfile.backupData = backupInfo;
    }

    @Nullable
    public AppsProfile.BackupInfo getBackupInfo() {
        if (mProfile == null) return null;
        return mProfile.backupData;
    }
}
