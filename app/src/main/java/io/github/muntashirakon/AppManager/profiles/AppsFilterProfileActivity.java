// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class AppsFilterProfileActivity extends AppsBaseProfileActivity {
    @NonNull
    public static Intent getProfileIntent(@NonNull Context context, @NonNull String profileId) {
        Intent intent = new Intent(context, AppsFilterProfileActivity.class);
        intent.putExtra(EXTRA_PROFILE_ID, profileId);
        return intent;
    }

    @NonNull
    public static Intent getNewProfileIntent(@NonNull Context context, @NonNull String profileName) {
        Intent intent = new Intent(context, AppsFilterProfileActivity.class);
        intent.putExtra(EXTRA_NEW_PROFILE_NAME, profileName);
        return intent;
    }

    @NonNull
    public static Intent getCloneProfileIntent(@NonNull Context context, @NonNull String oldProfileId,
                                               @NonNull String newProfileName) {
        Intent intent = new Intent(context, AppsFilterProfileActivity.class);
        intent.putExtra(EXTRA_PROFILE_ID, oldProfileId);
        intent.putExtra(EXTRA_NEW_PROFILE_NAME, newProfileName);
        return intent;
    }


    @Override
    public Fragment getAppsBaseFragment() {
        return new AppsFragment();
    }

    @Override
    public void loadNewProfile(@NonNull String newProfileName, @NonNull Intent intent) {
        model.loadNewAppsFilterProfile(newProfileName);
    }
}
