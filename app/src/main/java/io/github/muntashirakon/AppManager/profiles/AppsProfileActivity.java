// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.util.UiUtils;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getPrimaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

public class AppsProfileActivity extends BaseActivity implements NavigationBarView.OnItemSelectedListener,
        ViewPager.OnPageChangeListener {
    public static final String EXTRA_PROFILE_NAME = "prof";
    public static final String EXTRA_NEW_PROFILE_NAME = "new_prof";
    public static final String EXTRA_NEW_PROFILE = "new";
    public static final String EXTRA_IS_PRESET = "preset";
    public static final String EXTRA_SHORTCUT_TYPE = "shortcut";
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
    protected boolean displaySplashScreen() {
        return false;
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
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
        String profileState = getIntent().getStringExtra(EXTRA_STATE);
        if (shortcutType == null) {
            if (profileState != null) {
                // If profile state is set, it also means that it is a simple shortcut
                shortcutType = ST_SIMPLE;
            } else shortcutType = ST_NONE;
        }
        boolean newProfile = getIntent().getBooleanExtra(EXTRA_NEW_PROFILE, false);
        boolean isPreset = getIntent().getBooleanExtra(EXTRA_IS_PRESET, false);
        @Nullable String newProfileName;
        if (newProfile) {
            newProfileName = getIntent().getStringExtra(EXTRA_NEW_PROFILE_NAME);
        } else newProfileName = null;
        @Nullable String profileName = getIntent().getStringExtra(EXTRA_PROFILE_NAME);
        if (profileName == null && newProfileName == null) {
            finish();
            return;
        }
        switch (shortcutType) {
            case ST_SIMPLE:
                Intent intent = new Intent(this, ProfileApplierService.class);
                intent.putExtra(ProfileApplierService.EXTRA_PROFILE_NAME, profileName);
                if (profileState != null) {
                    intent.putExtra(ProfileApplierService.EXTRA_PROFILE_STATE, profileState);
                }
                ContextCompat.startForegroundService(this, intent);
                finish();
                return;
            case ST_ADVANCED:
                final String[] statesL = new String[]{
                        getString(R.string.on),
                        getString(R.string.off)
                };
                @ProfileMetaManager.ProfileState final List<String> states = Arrays.asList(ProfileMetaManager.STATE_ON, ProfileMetaManager.STATE_OFF);
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.profile_state)
                        .setSingleChoiceItems(statesL, -1, (dialog, which) -> {
                            Intent aIntent = new Intent(this, ProfileApplierService.class);
                            aIntent.putExtra(ProfileApplierService.EXTRA_PROFILE_NAME, profileName);
                            aIntent.putExtra(ProfileApplierService.EXTRA_PROFILE_STATE, states.get(which));
                            ContextCompat.startForegroundService(this, aIntent);
                            dialog.dismiss();
                        })
                        .setOnDismissListener(dialog -> finish())
                        .show();
                return;
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(newProfile ? newProfileName : profileName);
        }
        model = new ViewModelProvider(this).get(ProfileViewModel.class);
        model.setProfileName(profileName == null ? newProfileName : profileName, newProfile);
        if (newProfileName != null) {
            // Requested a new profile, clone profile
            model.loadAndCloneProfile(newProfileName, isPreset, profileName);
        } else model.loadProfile();
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
                itemNames.add(new SpannableStringBuilder(getPrimaryText(this, itemPair.first)).append("\n")
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
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.profile_state)
                    .setSingleChoiceItems(statesL, -1, (dialog, which) -> {
                        Intent aIntent = new Intent(this, ProfileApplierService.class);
                        aIntent.putExtra(ProfileApplierService.EXTRA_PROFILE_NAME, model.getProfileName());
                        aIntent.putExtra(ProfileApplierService.EXTRA_PROFILE_STATE, states.get(which));
                        ContextCompat.startForegroundService(this, aIntent);
                        dialog.dismiss();
                    })
                    .setOnDismissListener(dialog -> finish())
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
                        model.cloneProfile(profName.toString(), false, "");
                    })
                    .show();
        } else if (id == R.id.action_shortcut) {
            final String[] shortcutTypesL = new String[]{
                    getString(R.string.simple),
                    getString(R.string.advanced)
            };
            final String[] shortcutTypes = new String[]{ST_SIMPLE, ST_ADVANCED};
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.profile_state)
                    .setSingleChoiceItems(shortcutTypesL, -1, (dialog, which) -> {
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
