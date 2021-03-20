/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.profiles;

import android.app.Application;
import android.os.RemoteException;

import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;

public class ProfileViewModel extends AndroidViewModel {
    private final Object profileLock = new Object();
    @GuardedBy("profileLock")
    private String profileName;
    private boolean isNew;

    public ProfileViewModel(@NonNull Application application) {
        super(application);
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

    @WorkerThread
    @GuardedBy("profileLock")
    public void loadProfile() {
        synchronized (profileLock) {
            profileMetaManager = new ProfileMetaManager(profileName);
            profile = profileMetaManager.profile;
            if (profile == null) profile = profileMetaManager.newProfile(new String[]{});
        }
    }

    @WorkerThread
    @GuardedBy("profileLock")
    public void cloneProfile(String profileName, boolean isPreset, String oldProfileName) {
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

    private MutableLiveData<ArrayList<String>> packagesLiveData;

    @WorkerThread
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

    @WorkerThread
    @GuardedBy("profileLock")
    public void save() throws IOException, JSONException, RemoteException {
        synchronized (profileLock) {
            profileMetaManager.profile = profile;
            profileMetaManager.writeProfile();
        }
    }

    @WorkerThread
    @GuardedBy("profileLock")
    public void discard() {
        synchronized (profileLock) {
            loadProfile();
            loadPackages();
        }
    }

    @WorkerThread
    @GuardedBy("profileLock")
    public boolean delete() {
        synchronized (profileLock) {
            return profileMetaManager.deleteProfile();
        }
    }

    @NonNull
    public LiveData<ArrayList<String>> getPackages() {
        if (packagesLiveData == null) {
            packagesLiveData = new MutableLiveData<>();
            new Thread(this::loadPackages).start();
        }
        return packagesLiveData;
    }

    public ArrayList<String> getCurrentPackages() {
        return new ArrayList<>(Arrays.asList(profile.packages));
    }

    @WorkerThread
    @GuardedBy("profileLock")
    public void loadPackages() {
        synchronized (profileLock) {
            if (profileMetaManager == null) loadProfile();
            packagesLiveData.postValue(new ArrayList<>(Arrays.asList(profile.packages)));
        }
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
            case "backup_apk":
                profile.backupApk = value;
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
            case "backup_apk":
                return profile.backupApk;
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
        return profile.state;
    }

    public void setUsers(@Nullable int[] users) {
        profile.users = users;
    }

    @WorkerThread
    @NonNull
    public int[] getUsers() {
        return profile.users == null ? Users.getUsersHandles() : profile.users;
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
        int[] appOps = new int[appOpsStr.length];
        for (int i = 0; i < appOps.length; ++i) {
            if (appOpsStr[i].equals("*")) {
                // Wildcard found, ignore all app ops in favour of global wildcard
                profile.appOps = new int[]{AppOpsManager.OP_NONE};
                return;
            }
            appOps[i] = Integer.parseInt(appOpsStr[i]);
        }
        profile.appOps = appOps;
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
