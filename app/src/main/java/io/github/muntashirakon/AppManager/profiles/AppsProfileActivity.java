// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.LauncherIconCreator;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.util.UiUtils;

public class AppsProfileActivity extends BaseActivity implements NavigationBarView.OnItemSelectedListener,
        ViewPager.OnPageChangeListener {

    @NonNull
    public static Intent getProfileIntent(@NonNull Context context, @NonNull String profileName) {
        Intent intent = new Intent(context, AppsProfileActivity.class);
        intent.putExtra(EXTRA_PROFILE_NAME, profileName);
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
    public static Intent getCloneProfileIntent(@NonNull Context context, @NonNull String oldProfileName,
                                               @NonNull String newProfileName) {
        Intent intent = new Intent(context, AppsProfileActivity.class);
        intent.putExtra(EXTRA_PROFILE_NAME, oldProfileName);
        intent.putExtra(EXTRA_NEW_PROFILE_NAME, newProfileName);
        return intent;
    }

    @NonNull
    public static Intent getShortcutIntent(@NonNull Context context, @NonNull String profileName, @Nullable String shortcutType, @Nullable String state) {
        Intent intent = new Intent(context, AppsProfileActivity.class);
        intent.putExtra(EXTRA_PROFILE_NAME, profileName);
        if (shortcutType == null) {
            if (state != null) { // State => It's a simple shortcut
                intent.putExtra(EXTRA_SHORTCUT_TYPE, ST_SIMPLE);
                intent.putExtra(EXTRA_STATE, state);
            } else { // Otherwise it's an advance shortcut
                intent.putExtra(EXTRA_SHORTCUT_TYPE, ST_ADVANCED);
            }
        } else {
            intent.putExtra(EXTRA_SHORTCUT_TYPE, shortcutType);
            if (state != null) {
                intent.putExtra(EXTRA_STATE, state);
            } else if (shortcutType.equals(ST_SIMPLE)) {
                // Shortcut is set to simple but no state set
                intent.putExtra(EXTRA_STATE, ProfileMetaManager.STATE_ON);
            }
        }
        intent.putExtra(EXTRA_SHORTCUT_TYPE, shortcutType);
        return intent;
    }

    private static final String EXTRA_NEW_PROFILE_NAME = "new_prof";
    private static final String EXTRA_NEW_PROFILE_PACKAGES = "new_prof_pkgs";
    private static final String EXTRA_SHORTCUT_TYPE = "shortcut";

    public static final String EXTRA_PROFILE_NAME = "prof";
    public static final String EXTRA_STATE = "state";

    @StringDef({ST_NONE, ST_SIMPLE, ST_ADVANCED})
    public @interface ShortcutType {
    }

    public static final String ST_NONE = "none";
    public static final String ST_SIMPLE = "simple";
    public static final String ST_ADVANCED = "advanced";

    private ViewPager mViewPager;
    private NavigationBarView mBottomNavigationView;
    private MenuItem mPrevMenuItem;
    private final Fragment[] mFragments = new Fragment[3];
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
        @ShortcutType String shortcutType = getIntent().getStringExtra(EXTRA_SHORTCUT_TYPE);
        if (shortcutType == null) {
            shortcutType = ST_NONE;
        }
        @Nullable String profileState = getIntent().getStringExtra(EXTRA_STATE);
        @Nullable String newProfileName = getIntent().getStringExtra(EXTRA_NEW_PROFILE_NAME);
        @Nullable String[] initialPackages = getIntent().getStringArrayExtra(EXTRA_NEW_PROFILE_PACKAGES);
        @Nullable String profileName = getIntent().getStringExtra(EXTRA_PROFILE_NAME);
        if (!shortcutType.equals(ST_NONE)) {
            if (profileName == null) {
                // Profile name not set
                finish();
                return;
            }
            handleShortcut(shortcutType, profileName, profileState);
            return;
        }
        if (profileName == null && newProfileName == null) {
            // Neither profile name is set
            finish();
            return;
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(newProfileName != null ? newProfileName : profileName);
        }
        // Load/clone profile
        if (newProfileName == null) {
            model.setProfileName(profileName, false);
            model.loadProfile();
        } else {
            // New profile requested
            if (profileName != null) {
                // Clone profile
                model.setProfileName(profileName, false);
                model.loadAndCloneProfile(newProfileName);
            } else {
                // New profile
                model.setProfileName(newProfileName, false);
                model.loadNewProfile(initialPackages);
            }
        }
        mViewPager = findViewById(R.id.pager);
        mViewPager.addOnPageChangeListener(this);
        mViewPager.setAdapter(new ProfileFragmentPagerAdapter(getSupportFragmentManager()));
        mBottomNavigationView = findViewById(R.id.bottom_navigation);
        mBottomNavigationView.setOnItemSelectedListener(this);
        fab.setOnClickListener(v -> {
            progressIndicator.show();
            model.loadInstalledApps();
        });
        // Observers
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
        model.observeProfileLoaded().observe(this, loaded -> progressIndicator.hide());
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
            finish();
        } else if (id == R.id.action_apply) {
            final String[] statesL = new String[]{getString(R.string.on), getString(R.string.off)};
            @ProfileMetaManager.ProfileState final List<String> states = Arrays.asList(ProfileMetaManager.STATE_ON, ProfileMetaManager.STATE_OFF);
            new SearchableSingleChoiceDialogBuilder<>(this, states, statesL)
                    .setTitle(R.string.profile_state)
                    .setOnSingleChoiceClickListener((dialog, which, item1, isChecked) -> {
                        if (!isChecked) {
                            return;
                        }
                        Intent aIntent = new Intent(this, ProfileApplierService.class);
                        aIntent.putExtra(ProfileApplierService.EXTRA_PROFILE_NAME, model.getProfileName());
                        aIntent.putExtra(ProfileApplierService.EXTRA_PROFILE_STATE, states.get(which));
                        ContextCompat.startForegroundService(this, aIntent);
                        dialog.dismiss();
                    })
                    .show();
        } else if (id == R.id.action_save) {
            model.save();
        } else if (id == R.id.action_discard) {
            model.discard();
        } else if (id == R.id.action_delete) {
            model.delete();
        } else if (id == R.id.action_duplicate) {
            new TextInputDialogBuilder(this, R.string.input_profile_name)
                    .setTitle(R.string.new_profile)
                    .setHelperText(R.string.input_profile_name_description)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.go, (dialog, which, profName, isChecked) -> {
                        progressIndicator.show();
                        if (TextUtils.isEmpty(profName)) {
                            progressIndicator.hide();
                            Toast.makeText(this, R.string.failed_to_duplicate_profile, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (getSupportActionBar() != null) {
                            //noinspection ConstantConditions
                            getSupportActionBar().setTitle(profName.toString());
                        }
                        //noinspection ConstantConditions
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
                    .setTitle(R.string.profile_state)
                    .setOnSingleChoiceClickListener((dialog, which, item1, isChecked) -> {
                        if (!isChecked) {
                            return;
                        }
                        Intent intent = new Intent(this, AppsProfileActivity.class);
                        intent.putExtra(EXTRA_PROFILE_NAME, model.getProfileName());
                        intent.putExtra(EXTRA_SHORTCUT_TYPE, shortcutTypes[which]);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        LauncherIconCreator.createLauncherIcon(this,
                                model.getProfileName() + " - " + shortcutTypesL[which],
                                ContextCompat.getDrawable(this, R.drawable.ic_launcher_foreground), intent);
                        dialog.dismiss();
                    })
                    .show();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    protected void onDestroy() {
        if (mViewPager != null) {
            mViewPager.removeOnPageChangeListener(this);
        }
        super.onDestroy();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_apps) {
            mViewPager.setCurrentItem(0);
        } else if (itemId == R.id.action_conf) {
            mViewPager.setCurrentItem(1);
        } else if (itemId == R.id.action_logs) {
            mViewPager.setCurrentItem(2);
        } else return false;
        return true;
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

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

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    private void handleShortcut(@NonNull @ShortcutType String shortcutType, @NonNull String profileName, @Nullable String state) {
        switch (shortcutType) {
            case ST_SIMPLE:
                Intent intent = new Intent(this, ProfileApplierService.class);
                intent.putExtra(ProfileApplierService.EXTRA_PROFILE_NAME, profileName);
                // There must be a state
                intent.putExtra(ProfileApplierService.EXTRA_PROFILE_STATE, Objects.requireNonNull(state));
                ContextCompat.startForegroundService(this, intent);
                finish();
                break;
            case ST_ADVANCED:
                final String[] statesL = new String[]{
                        getString(R.string.on),
                        getString(R.string.off)
                };
                @ProfileMetaManager.ProfileState final List<String> states = Arrays.asList(ProfileMetaManager.STATE_ON, ProfileMetaManager.STATE_OFF);
                new SearchableSingleChoiceDialogBuilder<>(this, states, statesL)
                        .setTitle(R.string.profile_state)
                        .setSelection(state)
                        .setOnSingleChoiceClickListener((dialog, which, item, isChecked) -> {
                            if (!isChecked) {
                                return;
                            }
                            Intent aIntent = new Intent(this, ProfileApplierService.class);
                            aIntent.putExtra(ProfileApplierService.EXTRA_PROFILE_NAME, profileName);
                            aIntent.putExtra(ProfileApplierService.EXTRA_PROFILE_STATE, states.get(which));
                            ContextCompat.startForegroundService(this, aIntent);
                            dialog.dismiss();
                        })
                        .show();
                break;
        }
    }

    // For tab layout
    private class ProfileFragmentPagerAdapter extends FragmentPagerAdapter {
        ProfileFragmentPagerAdapter(@NonNull FragmentManager fragmentManager) {
            super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
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
        public int getCount() {
            return mFragments.length;
        }
    }
}
