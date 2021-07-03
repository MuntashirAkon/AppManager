// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOpNames;

public class ProfileViewModel extends AndroidViewModel {
    private final Object profileLock = new Object();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final MutableLiveData<Pair<Integer, Boolean>> toast = new MutableLiveData<>();
    private final MutableLiveData<ArrayList<Pair<CharSequence, ApplicationInfo>>> installedApps = new MutableLiveData<>();
    private final MutableLiveData<Boolean> profileLoaded = new MutableLiveData<>();
    @GuardedBy("profileLock")
    private String profileName;
    private boolean isNew;

    public ProfileViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }

    public LiveData<Pair<Integer, Boolean>> observeToast() {
        return toast;
    }

    public LiveData<ArrayList<Pair<CharSequence, ApplicationInfo>>> observeInstalledApps() {
        return installedApps;
    }

    public LiveData<Boolean> observeProfileLoaded() {
        return profileLoaded;
    }

    @AnyThread
    public void loadInstalledApps() {
        executor.submit(() -> {
            PackageManager pm = getApplication().getPackageManager();
            try {
                ArrayList<Pair<CharSequence, ApplicationInfo>> itemPairs;
                List<PackageInfo> packageInfoList = PackageManagerCompat.getInstalledPackages(
                        PackageManager.GET_META_DATA, Users.myUserId());
                itemPairs = new ArrayList<>(packageInfoList.size());
                for (PackageInfo info : packageInfoList) {
                    itemPairs.add(new Pair<>(pm.getApplicationLabel(info.applicationInfo), info.applicationInfo));
                }
                Collections.sort(itemPairs, (o1, o2) -> o1.first.toString().compareToIgnoreCase(o2.first.toString()));
                installedApps.postValue(itemPairs);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @GuardedBy("profileLock")
    public void setProfileName(String profileName, boolean isNew) {
        synchronized (profileLock) {
            this.profileName = profileName;
            this.isNew = isNew;
        }
    }

    public String getProfileName() {
        return profileName;
    }

    @GuardedBy("profileLock")
    private ProfileMetaManager.Profile profile;
    @GuardedBy("profileLock")
    private ProfileMetaManager profileMetaManager;

    @AnyThread
    public void loadProfile() {
        executor.submit(() -> {
            loadProfileInternal();
            profileLoaded.postValue(profileMetaManager == null);
        });
    }

    @WorkerThread
    @GuardedBy("profileLock")
    private void loadProfileInternal() {
        synchronized (profileLock) {
            profileMetaManager = new ProfileMetaManager(profileName);
            profile = profileMetaManager.profile;
            if (profile == null) profile = profileMetaManager.newProfile(new String[]{});
        }
    }

    @WorkerThread
    @GuardedBy("profileLock")
    public void cloneProfileInternal(String profileName, boolean isPreset, String oldProfileName) {
        setProfileName(profileName, true);
        synchronized (profileLock) {
            if (isPreset) {
                try {
                    profileMetaManager = ProfileMetaManager.fromPreset(profileName, oldProfileName);
                } catch (JSONException e) {
                    // Fallback to default
                    profileMetaManager = new ProfileMetaManager(profileName);
                }
            } else {
                ProfileMetaManager.Profile profile1 = profile;
                profileMetaManager = null;
                profileMetaManager = new ProfileMetaManager(profileName);
                profileMetaManager.profile = profile1;
            }
            profile = profileMetaManager.profile;
            if (profile == null) profile = profileMetaManager.newProfile(new String[]{});
        }
    }

    @AnyThread
    public void cloneProfile(String profileName, boolean isPreset, String oldProfileName) {
        executor.submit(() -> {
            if (profileMetaManager == null) loadProfileInternal();
            cloneProfileInternal(profileName, isPreset, oldProfileName);
            toast.postValue(new Pair<>(R.string.done, false));
            profileLoaded.postValue(profileMetaManager == null);
        });
    }

    @AnyThread
    public void loadAndCloneProfile(String profileName, boolean isPreset, String oldProfileName) {
        executor.submit(() -> {
            if (profileMetaManager == null) loadProfileInternal();
            cloneProfileInternal(profileName, isPreset, oldProfileName);
            profileLoaded.postValue(profileMetaManager == null);
        });
    }

    private MutableLiveData<ArrayList<String>> packagesLiveData;

    @AnyThread
    @GuardedBy("profileLock")
    public void setPackages(@NonNull List<String> packages) {
        synchronized (profileLock) {
            profile.packages = packages.toArray(new String[0]);
            Log.e("Packages", packages.toString());
            loadPackages();
        }
    }

    @GuardedBy("profileLock")
    public void deletePackage(@NonNull String packageName) {
        synchronized (profileLock) {
            profile.packages = Objects.requireNonNull(ArrayUtils.removeString(profile.packages, packageName));
            loadPackages();
        }
    }

    @AnyThread
    @GuardedBy("profileLock")
    public void save() {
        executor.submit(() -> {
            synchronized (profileLock) {
                profileMetaManager.profile = profile;
                try {
                    profileMetaManager.writeProfile();
                    toast.postValue(new Pair<>(R.string.saved_successfully, false));
                } catch (IOException | JSONException | RemoteException e) {
                    e.printStackTrace();
                    toast.postValue(new Pair<>(R.string.saving_failed, false));
                }
            }
        });
    }

    @AnyThread
    public void discard() {
        executor.submit(() -> {
            synchronized (profileLock) {
                loadProfileInternal();
                loadPackages();
            }
        });
    }

    @AnyThread
    public void delete() {
        executor.submit(() -> {
            synchronized (profileLock) {
                if (profileMetaManager.deleteProfile()) {
                    toast.postValue(new Pair<>(R.string.deleted_successfully, true));
                } else toast.postValue(new Pair<>(R.string.deletion_failed, false));
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
        executor.submit(() -> {
            synchronized (profileLock) {
                if (profileMetaManager == null) loadProfileInternal();
                packagesLiveData.postValue(new ArrayList<>(Arrays.asList(profile.packages)));
            }
        });
    }

    public void putBoolean(@NonNull String key, boolean value) {
        switch (key) {
            case "disable":
                profile.disable = value;
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
            case "disable":
                return profile.disable;
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
        List<Integer> appOpList = PackageUtils.getAppOps();
        List<CharSequence> appOpNameList = Arrays.asList(getAppOpNames(appOpList));
        for (CharSequence appOpStr : appOpsStr) {
            if (appOpStr.equals("*")) {
                // Wildcard found, ignore all app ops in favour of global wildcard
                profile.appOps = new int[]{AppOpsManager.OP_NONE};
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
