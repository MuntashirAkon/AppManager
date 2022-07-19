// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

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

import java.util.Objects;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;

public class SettingsActivity extends BaseActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    public static final String EXTRA_KEY = "key";
    public static final String EXTRA_SUB_KEY = "sub_key";

    public LinearProgressIndicator progressIndicator;
    private FragmentManager fragmentManager;
    @Nullable
    private String key;
    @Nullable
    private String subKey;

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_settings);
        setSupportActionBar(findViewById(R.id.toolbar));
        progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);
        progressIndicator.hide();

        key = getIntent().getStringExtra(EXTRA_KEY);
        subKey = getIntent().getStringExtra(EXTRA_SUB_KEY);

        fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.main_layout, MainPreferences.getInstance(key))
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (fragmentManager.getBackStackEntryCount() <= 1) {
                finish();
            } else fragmentManager.popBackStack();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, @NonNull Preference pref) {
        if (pref.getFragment() != null) {
            FragmentManager fragmentManager = getSupportFragmentManager();
            Bundle args = pref.getExtras();
            Fragment fragment = fragmentManager.getFragmentFactory()
                    .instantiate(this.getClassLoader(), pref.getFragment());
            if (subKey != null && fragment instanceof PreferenceFragment && Objects.equals(pref.getKey(), key)) {
                args.putString(PreferenceFragment.PREF_KEY, subKey);
                subKey = null;
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
}