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
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfUriManager;
import io.github.muntashirakon.util.UiUtils;

public class SettingsActivity extends BaseActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    public static final String TAG = SettingsActivity.class.getSimpleName();

    private static final String SAVED_KEYS = "saved_keys";

    @NonNull
    public static Intent getIntent(@NonNull Context context, @Nullable String... paths) {
        Intent intent = new Intent(context, SettingsActivity.class);
        if (paths != null) {
            intent.setData(getSettingUri(paths));
        }
        return intent;
    }

    @NonNull
    private static Uri getSettingUri(@NonNull String... pathSegments) {
        Uri.Builder builder = new Uri.Builder()
                .scheme(SelfUriManager.APP_MANAGER_SCHEME)
                .authority(SelfUriManager.SETTINGS_HOST);
        for (String pathSegment : pathSegments) {
            builder.appendPath(pathSegment);
        }
        return builder.build();
    }

    public LinearProgressIndicator progressIndicator;
    @NonNull
    private List<String> mKeys = Collections.emptyList();
    @NonNull
    private ArrayList<String> mSavedKeys = new ArrayList<>();
    private int mLevel = 0;
    private boolean mDualPaneMode;
    @Nullable
    private MaterialToolbar mSecondaryToolbar;

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        int mainPrefSize = UiUtils.dpToPx(this, 450);
        int windowWidth = getResources().getDisplayMetrics().widthPixels;
        mDualPaneMode = windowWidth >= 2 * mainPrefSize;
        setContentView(mDualPaneMode ? R.layout.activity_settings_dual_pane : R.layout.activity_settings);
        setSupportActionBar(findViewById(R.id.toolbar));
        mSecondaryToolbar = findViewById(R.id.toolbar2);
        FragmentContainerView secondaryContainer = findViewById(R.id.secondary_layout);
        progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);
        progressIndicator.hide();
        // Apply necessary padding: ignore start
        if (mSecondaryToolbar != null) {
            UiUtils.applyWindowInsetsAsPadding(mSecondaryToolbar, true, false, false, true);
        }
        if (secondaryContainer != null) {
            UiUtils.applyWindowInsetsAsPadding(secondaryContainer, false, true, false, true);
        }

        if (savedInstanceState != null) {
            clearBackStack();
            ArrayList<String> savedKeys = savedInstanceState.getStringArrayList(SAVED_KEYS);
            if (savedKeys != null) {
                mSavedKeys = savedKeys;
            }
        }
        setKeysFromIntent(getIntent());

        getSupportFragmentManager().addFragmentOnAttachListener((fragmentManager, fragment) -> {
            if (!(fragment instanceof MainPreferences)) {
                ++mLevel;
            }
        });
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            mLevel = getSupportFragmentManager().getBackStackEntryCount();
            Log.d(TAG, "Backstack changed. Level: %d", mLevel);
            // Update saved level: Delete everything from mLevel to the last item)
            int size = mSavedKeys.size();
            if (mLevel <= size - 1) {
                mSavedKeys.subList(mLevel, size).clear();
            }
        });

        String defaultPref = getKey(mLevel);
        if (defaultPref == null && mDualPaneMode) {
            defaultPref = "appearance_prefs";
        }
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.animator.enter_from_left,
                        R.animator.enter_from_right,
                        R.animator.exit_from_right,
                        R.animator.exit_from_left
                )
                .replace(R.id.main_layout, MainPreferences.getInstance(defaultPref))
                .commit();
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        if (setKeysFromIntent(intent)) {
            // Clear old items
            mSavedKeys.clear();
            clearBackStack();
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_layout);
            if (fragment instanceof MainPreferences) {
                ((MainPreferences) fragment).setPrefKey(getKey(mLevel = 0));
                Log.d(TAG, "Selected pref: %s", fragment.getClass().getName());
            }
        }
    }

    @Override
    public void setTitle(int titleId) {
        if (mDualPaneMode) {
            Objects.requireNonNull(mSecondaryToolbar).setTitle(titleId);
        } else super.setTitle(titleId);
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
            // Inject dual pane mode
            args.putBoolean(PreferenceFragment.PREF_SECONDARY, mDualPaneMode);
            // Inject subKey to the arguments
            String subKey = getKey(mLevel + 1);
            if (subKey != null && Objects.equals(pref.getKey(), getKey(mLevel))) {
                args.putString(PreferenceFragment.PREF_KEY, subKey);
            }
            // Save current key
            saveKey(mLevel, pref.getKey());
        }
        fragment.setArguments(args);
        // The line below is kept because this is how it is handled in AndroidX library
        fragment.setTargetFragment(caller, 0);
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        if (!mDualPaneMode) {
            transaction.setCustomAnimations(
                    R.animator.enter_from_left,
                    R.animator.enter_from_right,
                    R.animator.exit_from_right,
                    R.animator.exit_from_left
            ).addToBackStack(null);
        }
        transaction
                .replace(mDualPaneMode ? R.id.secondary_layout : R.id.main_layout, fragment)
                .commit();
        return true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putStringArrayList(SAVED_KEYS, mSavedKeys);
        super.onSaveInstanceState(outState);
    }

    @Nullable
    private String getKey(int level) {
        if (!mSavedKeys.isEmpty() && mSavedKeys.size() > level) {
            String key = mSavedKeys.get(level);
            if (key != null) {
                return key;
            }
        }
        if (mKeys.size() > level) {
            return mKeys.get(level);
        }
        return null;
    }

    private void saveKey(int level, @Nullable String key) {
        Log.d(TAG, "Save level: %d, Key: %s", level, key);
        int size = mSavedKeys.size();
        if (level >= size) {
            // Create levels
            int count = level - size + 1;
            for (int i = 0; i < count; ++i) {
                mSavedKeys.add(null);
            }
        }
        // Add this level
        mSavedKeys.set(level, key);
    }

    private boolean setKeysFromIntent(@NonNull Intent intent) {
        Uri uri = intent.getData();
        if (uri != null && SelfUriManager.APP_MANAGER_SCHEME.equals(uri.getScheme())
                && SelfUriManager.SETTINGS_HOST.equals(uri.getHost()) && uri.getPath() != null) {
            mKeys = Objects.requireNonNull(uri.getPathSegments());
            return true;
        }
        return false;
    }
}