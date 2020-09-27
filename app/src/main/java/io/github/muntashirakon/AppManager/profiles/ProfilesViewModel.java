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

import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class ProfilesViewModel extends AndroidViewModel {

    public ProfilesViewModel(@NonNull Application application) {
        super(application);
    }

    private HashMap<String, String> profiles;
    private MutableLiveData<HashMap<String, String>> profileLiveData;

    public LiveData<HashMap<String, String>> getProfiles() {
        if (profileLiveData == null) {
            profileLiveData = new MutableLiveData<>();
            new Thread(this::loadProfiles).start();
        }
        return profileLiveData;
    }

    @WorkerThread
    public void loadProfiles() {
        profiles = ProfileManager.getProfiles();
        profileLiveData.postValue(profiles);
    }
}
