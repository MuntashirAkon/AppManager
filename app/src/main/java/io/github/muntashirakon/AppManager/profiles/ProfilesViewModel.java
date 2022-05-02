// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.HashMap;

public class ProfilesViewModel extends AndroidViewModel {

    public ProfilesViewModel(@NonNull Application application) {
        super(application);
    }

    private MutableLiveData<HashMap<String, CharSequence>> profileLiveData;

    public LiveData<HashMap<String, CharSequence>> getProfiles() {
        if (profileLiveData == null) {
            profileLiveData = new MutableLiveData<>();
            new Thread(this::loadProfiles).start();
        }
        return profileLiveData;
    }

    @WorkerThread
    public void loadProfiles() {
        HashMap<String, CharSequence> profiles = ProfileManager.getProfileSummaries();
        profileLiveData.postValue(profiles);
    }
}
