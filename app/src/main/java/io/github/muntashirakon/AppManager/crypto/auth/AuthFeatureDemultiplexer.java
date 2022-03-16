// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.profiles.AppsProfileActivity;

public class AuthFeatureDemultiplexer extends BaseActivity {
    public static final String EXTRA_AUTH = "auth";
    public static final String EXTRA_FEATURE = "feature";

    private final HashMap<String, Class<?>> featureActivityMap = new HashMap<String, Class<?>>() {{
        put("profile", AppsProfileActivity.class);
    }};

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
                intent.setClass(AppManager.getContext(), featureActivityMap.get(feature));
                if (intent.hasExtra(AppsProfileActivity.EXTRA_STATE)) {
                    // Setting state means that it is a simple shortcut
                    intent.putExtra(AppsProfileActivity.EXTRA_SHORTCUT_TYPE, AppsProfileActivity.ST_SIMPLE);
                } else intent.putExtra(AppsProfileActivity.EXTRA_SHORTCUT_TYPE, AppsProfileActivity.ST_ADVANCED);
                startActivity(intent);
                break;
        }
        finish();
    }
}
