// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.profiles.ProfileApplierActivity;

public class AuthFeatureDemultiplexer extends BaseActivity {
    public static final String EXTRA_AUTH = "auth";
    public static final String EXTRA_FEATURE = "feature";

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        Intent intent = getIntent();
        if (!intent.hasExtra(EXTRA_AUTH) || !intent.hasExtra(EXTRA_FEATURE)) {
            // It does not have the required extras, ignore the request
            finishAndRemoveTask();
            return;
        }
        handleRequest(intent);
    }

    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    private void handleRequest(@NonNull Intent intent) {
        String auth = intent.getStringExtra(EXTRA_AUTH);
        String feature = intent.getStringExtra(EXTRA_FEATURE);

        intent.removeExtra(EXTRA_AUTH);
        intent.removeExtra(EXTRA_FEATURE);

        if (!AuthManager.getKey().equals(auth)) {
            // Invalid authorization key
            // TODO: 16/3/22 Display a nice error message
            finishAndRemoveTask();
            return;
        }

        switch (feature) {
            case "profile":
                launchProfile(intent);
                break;
        }
        finish();
    }

    public void launchProfile(@NonNull Intent intent) {
        String profileId = intent.getStringExtra(ProfileApplierActivity.EXTRA_PROFILE_ID);
        String state = intent.getStringExtra(ProfileApplierActivity.EXTRA_STATE);
        startActivity(ProfileApplierActivity.getShortcutIntent(getApplicationContext(), profileId, null, state));
    }
}
