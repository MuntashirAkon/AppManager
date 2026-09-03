// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONException;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public class ProfilesViewModel extends AndroidViewModel {
    private final MutableLiveData<HashMap<BaseProfile, CharSequence>> mProfilesLiveData = new MutableLiveData<>();
    private final BroadcastReceiver mProfilesChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadProfiles();
        }
    };
    private Future<?> mProfileResult;

    public ProfilesViewModel(@NonNull Application application) {
        super(application);
        ContextCompat.registerReceiver(application, mProfilesChangedReceiver,
                new IntentFilter(ProfileManager.ACTION_PROFILES_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onCleared() {
        getApplication().unregisterReceiver(mProfilesChangedReceiver);
        super.onCleared();
    }

    public LiveData<HashMap<BaseProfile, CharSequence>> getProfilesLiveData() {
        return mProfilesLiveData;
    }

    public void loadProfiles() {
        if (mProfileResult != null) {
            mProfileResult.cancel(true);
        }
        mProfileResult = ThreadUtils.postOnBackgroundThread(() -> {
            synchronized (mProfilesLiveData) {
                try {
                    HashMap<BaseProfile, CharSequence> profiles = ProfileManager.getProfileSummaries(getApplication());
                    mProfilesLiveData.postValue(profiles);
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
