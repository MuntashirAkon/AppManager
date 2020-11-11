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

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.github.muntashirakon.AppManager.logs.Log;
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
    public void cloneProfile(String profileName) {
        setProfileName(profileName, true);
        synchronized (profileLock) {
            ProfileMetaManager.Profile profile1 = profile;
            profileMetaManager = null;
            profileMetaManager = new ProfileMetaManager(profileName);
            profileMetaManager.profile = profile1;
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
    public void save() throws IOException, JSONException {
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
}
