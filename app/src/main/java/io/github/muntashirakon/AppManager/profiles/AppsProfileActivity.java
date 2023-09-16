// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static io.github.muntashirakon.AppManager.profiles.ProfileApplierActivity.ST_ADVANCED;
import static io.github.muntashirakon.AppManager.profiles.ProfileApplierActivity.ST_SIMPLE;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.shortcut.CreateShortcutDialogFragment;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.util.UiUtils;

public class AppsProfileActivity extends BaseActivity implements NavigationBarView.OnItemSelectedListener {

    @NonNull
    public static Intent getProfileIntent(@NonNull Context context, @NonNull String profileId) {
        Intent intent = new Intent(context, AppsProfileActivity.class);
        intent.putExtra(EXTRA_PROFILE_ID, profileId);
        return intent;
    }

    @NonNull
    public static Intent getNewProfileIntent(@NonNull Context context, @NonNull String profileName) {
        return getNewProfileIntent(context, profileName, null);
    }

    @NonNull
    public static Intent getNewProfileIntent(@NonNull Context context, @NonNull String profileName, @Nullable String[] initialPackages) {
        Intent intent = new Intent(context, AppsProfileActivity.class);
        intent.putExtra(EXTRA_NEW_PROFILE_NAME, profileName);
        if (initialPackages != null) {
            intent.putExtra(EXTRA_NEW_PROFILE_PACKAGES, initialPackages);
        }
        return intent;
    }

    @NonNull
    public static Intent getCloneProfileIntent(@NonNull Context context, @NonNull String oldProfileId,
                                               @NonNull String newProfileName) {
        Intent intent = new Intent(context, AppsProfileActivity.class);
        intent.putExtra(EXTRA_PROFILE_ID, oldProfileId);
        intent.putExtra(EXTRA_NEW_PROFILE_NAME, newProfileName);
        return intent;
    }

    private static final String EXTRA_NEW_PROFILE_NAME = "new_prof";
    private static final String EXTRA_NEW_PROFILE_PACKAGES = "new_prof_pkgs";
    private static final String EXTRA_SHORTCUT_TYPE = "shortcut";
    private static final String EXTRA_PROFILE_ID = "prof";
    private static final String EXTRA_STATE = "state";

    private ViewPager2 mViewPager;
    private NavigationBarView mBottomNavigationView;
    private MenuItem mPrevMenuItem;
    private final Fragment[] mFragments = new Fragment[3];
    private final ViewPager2.OnPageChangeCallback mPageChangeCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            if (mPrevMenuItem != null) {
                mPrevMenuItem.setChecked(false);
            } else {
                mBottomNavigationView.getMenu().getItem(0).setChecked(false);
            }
            mBottomNavigationView.getMenu().getItem(position).setChecked(true);
            mPrevMenuItem = mBottomNavigationView.getMenu().getItem(position);
        }
    };
    ProfileViewModel model;
    FloatingActionButton fab;
    LinearProgressIndicator progressIndicator;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(this).get(ProfileViewModel.class);
        setContentView(R.layout.activity_apps_profile);
        setSupportActionBar(findViewById(R.id.toolbar));
        progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);
        fab = findViewById(R.id.floatingActionButton);
        UiUtils.applyWindowInsetsAsMargin(fab);
        if (getIntent() == null) {
            finish();
            return;
        }
        @Nullable String newProfileName = getIntent().getStringExtra(EXTRA_NEW_PROFILE_NAME);
        @Nullable String[] initialPackages = getIntent().getStringArrayExtra(EXTRA_NEW_PROFILE_PACKAGES);
        @Nullable String profileId = getIntent().getStringExtra(EXTRA_PROFILE_ID);
        if (getIntent().hasExtra(EXTRA_SHORTCUT_TYPE)) {
            // Compatibility mode for shortcut
            @ProfileApplierActivity.ShortcutType String shortcutType = getIntent().getStringExtra(EXTRA_SHORTCUT_TYPE);
            @Nullable String profileState = getIntent().getStringExtra(EXTRA_STATE);
            if (shortcutType != null && profileId != null) {
                ProfileApplierActivity.getShortcutIntent(this, profileId, shortcutType, profileState);
            }
            // Finish regardless of whether the profile applier launched or not
            finish();
            return;
        }
        if (profileId == null && newProfileName == null) {
            // Neither profile name/id is set
            finish();
            return;
        }
        // Load/clone profile
        if (newProfileName != null) {
            // New profile requested
            if (profileId != null) {
                // Clone profile
                model.loadAndCloneProfile(profileId, newProfileName);
            } else {
                // New profile
                model.loadNewProfile(newProfileName, initialPackages);
            }
        } else {
            model.loadProfile(profileId);
        }
        mViewPager = findViewById(R.id.pager);
        mViewPager.setOffscreenPageLimit(2);
        mViewPager.registerOnPageChangeCallback(mPageChangeCallback);
        mViewPager.setAdapter(new ProfileFragmentPagerAdapter(this));
        mBottomNavigationView = findViewById(R.id.bottom_navigation);
        mBottomNavigationView.setOnItemSelectedListener(this);
        fab.setOnClickListener(v -> {
            progressIndicator.show();
            model.loadInstalledApps();
        });
        // Observers
        model.getProfileModifiedLiveData().observe(this, modified -> {
            if (getSupportActionBar() != null) {
                String name = (modified ? "* " : "") + model.getProfileName();
                getSupportActionBar().setTitle(name);
            }
        });
        model.observeToast().observe(this, stringResAndIsFinish -> {
            UIUtils.displayShortToast(stringResAndIsFinish.first);
            if (stringResAndIsFinish.second) finish();
        });
        model.observeInstalledApps().observe(this, itemPairs -> {
            ArrayList<String> items = new ArrayList<>(itemPairs.size());
            ArrayList<CharSequence> itemNames = new ArrayList<>(itemPairs.size());
            for (Pair<CharSequence, ApplicationInfo> itemPair : itemPairs) {
                items.add(itemPair.second.packageName);
                boolean isSystem = (itemPair.second.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                itemNames.add(new SpannableStringBuilder(itemPair.first)
                        .append("\n")
                        .append(getSmallerText(getString(isSystem ? R.string.system : R.string.user))));
            }
            progressIndicator.hide();
            new SearchableMultiChoiceDialogBuilder<>(this, items, itemNames)
                    .addSelections(model.getCurrentPackages())
                    .setTitle(R.string.apps)
                    .setPositiveButton(R.string.ok, (d, i, selectedItems) -> model.setPackages(selectedItems))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
        model.observeProfileLoaded().observe(this, profileName -> {
            setTitle(profileName);
            progressIndicator.hide();
        });
    }

    @Override
    public void onBackPressed() {
        if (model != null && model.isModified()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.exit_confirmation)
                    .setMessage(R.string.profile_modified_are_you_sure)
                    .setPositiveButton(R.string.no, null)
                    .setNegativeButton(R.string.yes, (dialog, which) -> super.onBackPressed())
                    .setNeutralButton(R.string.save_and_exit, (dialog, which) -> {
                        model.save(true);
                        super.onBackPressed();
                    })
                    .show();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.fragment_profile_apps_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
        } else if (id == R.id.action_apply) {
            Intent intent = ProfileApplierActivity.getShortcutIntent(this, model.getProfileName(), null, null);
            startActivity(intent);
        } else if (id == R.id.action_save) {
            model.save(false);
        } else if (id == R.id.action_discard) {
            model.discard();
        } else if (id == R.id.action_delete) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.delete_filename, model.getProfileName()))
                    .setMessage(R.string.are_you_sure)
                    .setPositiveButton(R.string.cancel, null)
                    .setNegativeButton(R.string.ok, (dialog, which) -> model.delete())
                    .show();
        } else if (id == R.id.action_duplicate) {
            new TextInputDialogBuilder(this, R.string.input_profile_name)
                    .setTitle(R.string.new_profile)
                    .setHelperText(R.string.input_profile_name_description)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.go, (dialog, which, profName, isChecked) -> {
                        if (TextUtils.isEmpty(profName)) {
                            Toast.makeText(this, R.string.failed_to_duplicate_profile, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        progressIndicator.show();
                        model.cloneProfile(profName.toString());
                    })
                    .show();
        } else if (id == R.id.action_shortcut) {
            final String[] shortcutTypesL = new String[]{
                    getString(R.string.simple),
                    getString(R.string.advanced)
            };
            final String[] shortcutTypes = new String[]{ST_SIMPLE, ST_ADVANCED};
            new SearchableSingleChoiceDialogBuilder<>(this, shortcutTypes, shortcutTypesL)
                    .setTitle(R.string.create_shortcut)
                    .setOnSingleChoiceClickListener((dialog, which, item1, isChecked) -> {
                        if (!isChecked) {
                            return;
                        }
                        Drawable icon = Objects.requireNonNull(ContextCompat.getDrawable(this, R.drawable.ic_launcher_foreground));
                        ProfileShortcutInfo shortcutInfo = new ProfileShortcutInfo(model.getProfileId(),
                                model.getProfileName(), shortcutTypes[which], shortcutTypesL[which]);
                        shortcutInfo.setIcon(UIUtils.getBitmapFromDrawable(icon));
                        CreateShortcutDialogFragment dialog1 = CreateShortcutDialogFragment.getInstance(shortcutInfo);
                        dialog1.show(getSupportFragmentManager(), CreateShortcutDialogFragment.TAG);
                        dialog.dismiss();
                    })
                    .show();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    protected void onDestroy() {
        if (mViewPager != null) {
            mViewPager.unregisterOnPageChangeCallback(mPageChangeCallback);
        }
        super.onDestroy();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_apps) {
            mViewPager.setCurrentItem(0, true);
        } else if (itemId == R.id.action_conf) {
            mViewPager.setCurrentItem(1, true);
        } else if (itemId == R.id.action_logs) {
            mViewPager.setCurrentItem(2, true);
        } else return false;
        return true;
    }

    // For tab layout
    private class ProfileFragmentPagerAdapter extends FragmentStateAdapter {
        ProfileFragmentPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            Fragment fragment = mFragments[position];
            if (fragment == null) {
                switch (position) {
                    case 0:
                        return mFragments[position] = new AppsFragment();
                    case 1:
                        return mFragments[position] = new ConfFragment();
                    case 2:
                        return mFragments[position] = new LogViewerFragment();
                }
            }
            return Objects.requireNonNull(fragment);
        }

        @Override
        public int getItemCount() {
            return mFragments.length;
        }
    }
}
