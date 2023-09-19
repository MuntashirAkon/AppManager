// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.life.BuildExpiryChecker;
import io.github.muntashirakon.AppManager.self.life.FundingCampaignChecker;

public class SettingsActivity extends BaseActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    public static final String TAG = SettingsActivity.class.getSimpleName();

    private static final String SCHEME = "app-manager";
    private static final String HOST = "settings";

    private static final String SAVED_KEYS = "saved_keys";

    @NonNull
    public static Intent getIntent(@NonNull Context context, @Nullable String... paths) {
        Intent intent = new Intent(context, SettingsActivity.class);
        if (paths != null) {
            intent.setData(SettingsActivity.getSettingUri(paths));
        }
        return intent;
    }

    @NonNull
    private static Uri getSettingUri(@NonNull String... pathSegments) {
        Uri.Builder builder = new Uri.Builder()
                .scheme(SCHEME)
                .authority(HOST);
        for (String pathSegment : pathSegments) {
            builder.appendPath(pathSegment);
        }
        return builder.build();
    }

    public LinearProgressIndicator progressIndicator;
    @NonNull
    private List<String> keys = Collections.emptyList();
    @NonNull
    private ArrayList<String> savedKeys = new ArrayList<>();
    private int level = 0;

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_settings);
        setSupportActionBar(findViewById(R.id.toolbar));
        progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);
        progressIndicator.hide();

        View buildExpiringNotice = findViewById(R.id.app_manager_expiring_notice);
        buildExpiringNotice.setVisibility(BuildExpiryChecker.buildExpired() == null ? View.VISIBLE : View.GONE);
        View fundingCampaignNotice = findViewById(R.id.funding_campaign_notice);
        fundingCampaignNotice.setVisibility(FundingCampaignChecker.campaignRunning() ? View.VISIBLE : View.GONE);

        if (savedInstanceState != null) {
            clearBackStack();
            savedKeys = savedInstanceState.getStringArrayList(SAVED_KEYS);
        }
        setKeysFromIntent(getIntent());

        getSupportFragmentManager().addFragmentOnAttachListener((fragmentManager, fragment) -> {
            if (!(fragment instanceof MainPreferences)) {
                ++level;
            }
        });
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            level = getSupportFragmentManager().getBackStackEntryCount();
            Log.d(TAG, "Backstack changed. Level: " + level);
            // Update saved level: Delete everything from mLevel to the last item)
            int size = savedKeys.size();
            if (level <= size - 1) {
                savedKeys.subList(level, size).clear();
            }
        });

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_layout, MainPreferences.getInstance(getKey(level)))
                .commit();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (setKeysFromIntent(intent)) {
            // Clear old items
            savedKeys.clear();
            clearBackStack();
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_layout);
            if (fragment instanceof MainPreferences) {
                ((MainPreferences) fragment).setPrefKey(getKey(level = 0));
                Log.d(TAG, "Selected pref: " + fragment.getClass().getName());
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, @NonNull Preference pref) {
        if (pref.getFragment() == null) {
            return false;
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        Bundle args = pref.getExtras();
        Fragment fragment = fragmentManager.getFragmentFactory().instantiate(getClassLoader(), pref.getFragment());
        if (fragment instanceof PreferenceFragment) {
            // Inject subKey to the arguments
            String subKey = getKey(level + 1);
            if (subKey != null && Objects.equals(pref.getKey(), getKey(level))) {
                args.putString(PreferenceFragment.PREF_KEY, subKey);
            }
            // Save current key
            saveKey(level, pref.getKey());
        }
        fragment.setArguments(args);
        // The line below is kept because this is how it is handled in AndroidX library
        fragment.setTargetFragment(caller, 0);
        fragmentManager.beginTransaction()
                .replace(R.id.main_layout, fragment)
                .addToBackStack(null)
                .commit();
        return true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putStringArrayList(SAVED_KEYS, savedKeys);
        super.onSaveInstanceState(outState);
    }

    @Nullable
    private String getKey(int level) {
        if (!savedKeys.isEmpty() && savedKeys.size() > level) {
            String key = savedKeys.get(level);
            if (key != null) {
                return key;
            }
        }
        if (keys.size() > level) {
            return keys.get(level);
        }
        return null;
    }

    private void saveKey(int level, @Nullable String key) {
        Log.d(TAG, "Save level: " + level + ", Key: " + key);
        int size = savedKeys.size();
        if (level >= size) {
            // Create levels
            int count = level - size + 1;
            for (int i = 0; i < count; ++i) {
                savedKeys.add(null);
            }
        }
        // Add this level
        savedKeys.set(level, key);
    }

    private boolean setKeysFromIntent(@NonNull Intent intent) {
        Uri uri = intent.getData();
        if (uri != null && SCHEME.equals(uri.getScheme()) && HOST.equals(uri.getHost()) && uri.getPath() != null) {
            keys = Objects.requireNonNull(uri.getPathSegments());
            return true;
        }
        return false;
    }
}