// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.app.Application;
import android.os.FileObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.profiles.struct.AppsProfile;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public class ProfilesViewModel extends AndroidViewModel {
    private final MutableLiveData<HashMap<AppsProfile, CharSequence>> mProfilesLiveData = new MutableLiveData<>();
    private Future<?> mProfileResult;
    private FileObserver mFileObserver;

    public ProfilesViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        if (mFileObserver != null) {
            mFileObserver.stopWatching();
        }
        super.onCleared();
    }

    public LiveData<HashMap<AppsProfile, CharSequence>> getProfilesLiveData() {
        return mProfilesLiveData;
    }

    public void loadProfiles() {
        if (mProfileResult != null) {
            mProfileResult.cancel(true);
        }
        mProfileResult = ThreadUtils.postOnBackgroundThread(() -> {
            synchronized (mProfilesLiveData) {
                try {
                    HashMap<AppsProfile, CharSequence> profiles = ProfileManager.getProfileSummaries(getApplication());
                    setUpObserverAndStart();
                    mProfilesLiveData.postValue(profiles);
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void setUpObserverAndStart() {
        if (mFileObserver != null) {
            mFileObserver.startWatching();
            return;
        }
        File profilePath = ProfileManager.getProfilesDir().getFile();
        if (profilePath != null && !profilePath.exists()) {
            // Do not set up observer yet
            return;
        }
        int mask = FileObserver.CREATE
                | FileObserver.DELETE
                | FileObserver.DELETE_SELF
                | FileObserver.MOVED_TO
                | FileObserver.MODIFY
                | FileObserver.MOVED_FROM;
        mFileObserver = new FileObserver(profilePath, mask) {
            @Override
            public void onEvent(int event, @Nullable String path) {
                loadProfiles();
            }
        };
        mFileObserver.startWatching();
    }
}
