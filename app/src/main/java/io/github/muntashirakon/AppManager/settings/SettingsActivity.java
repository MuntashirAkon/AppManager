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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.life.BuildExpiryChecker;
import io.github.muntashirakon.AppManager.self.life.FundingCampaignChecker;

public class SettingsActivity extends BaseActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    private static final String SCHEME = "app-manager";
    private static final String HOST = "settings";

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
    private List<String> mKeys = Collections.emptyList();
    private int mLevel = 0;

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

        Uri uri = getIntent().getData();
        if (uri != null && SCHEME.equals(uri.getScheme()) && HOST.equals(uri.getHost()) && uri.getPath() != null) {
            mKeys = Objects.requireNonNull(uri.getPathSegments());
        }

        getSupportFragmentManager().addFragmentOnAttachListener((fragmentManager, fragment) -> {
            if (!(fragment instanceof MainPreferences)) {
                ++mLevel;
            }
        });
        getSupportFragmentManager().addOnBackStackChangedListener(() -> mLevel = getSupportFragmentManager().getBackStackEntryCount());

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_layout, MainPreferences.getInstance(getKey(mLevel)))
                .commit();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri uri = intent.getData();
        if (uri != null && SCHEME.equals(uri.getScheme()) && HOST.equals(uri.getHost()) && uri.getPath() != null) {
            mKeys = Objects.requireNonNull(uri.getPathSegments());
            clearBackStack();
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_layout);
            if (fragment instanceof MainPreferences) {
                ((MainPreferences) fragment).setPrefKey(getKey(mLevel = 0));
            }
            Log.e(TAG, "Pref selected: %s", fragment.getClass().getName());
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
        if (pref.getFragment() != null) {
            FragmentManager fragmentManager = getSupportFragmentManager();
            Bundle args = pref.getExtras();
            Fragment fragment = fragmentManager.getFragmentFactory().instantiate(getClassLoader(), pref.getFragment());
            String subKey = getKey(mLevel + 1);
            if (subKey != null && fragment instanceof PreferenceFragment && Objects.equals(pref.getKey(), getKey(mLevel))) {
                args.putString(PreferenceFragment.PREF_KEY, subKey);
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
        return false;
    }

    @Nullable
    private String getKey(int level) {
        if (mKeys.size() > level) {
            return mKeys.get(level);
        }
        return null;
    }
}