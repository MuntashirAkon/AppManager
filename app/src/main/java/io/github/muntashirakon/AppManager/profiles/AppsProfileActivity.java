/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.profiles;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.ProgressIndicator;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.LauncherIconCreator;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.types.TextInputDialogBuilder;

public class AppsProfileActivity extends BaseActivity
        implements BottomNavigationView.OnNavigationItemSelectedListener, ViewPager.OnPageChangeListener {
    public static final String EXTRA_PROFILE_NAME = "prof";
    public static final String EXTRA_NEW_PROFILE_NAME = "new_prof";
    public static final String EXTRA_NEW_PROFILE = "new";
    public static final String EXTRA_SHORTCUT_TYPE = "shortcut";

    @StringDef({ST_NONE, ST_SIMPLE, ST_ADVANCED})
    public @interface ShortcutType {
    }

    public static final String ST_NONE = "none";
    public static final String ST_SIMPLE = "simple";
    public static final String ST_ADVANCED = "advanced";

    private ViewPager viewPager;
    private BottomNavigationView bottomNavigationView;
    private MenuItem prevMenuItem;
    private final Fragment[] fragments = new Fragment[2];
    ProfileViewModel model;
    FloatingActionButton fab;
    ProgressIndicator progressIndicator;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apps_profile);
        setSupportActionBar(findViewById(R.id.toolbar));
        progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);
        fab = findViewById(R.id.floatingActionButton);
        if (getIntent() == null) {
            finish();
            return;
        }
        @ShortcutType String shortcutType = getIntent().getStringExtra(EXTRA_SHORTCUT_TYPE);
        if (shortcutType == null) shortcutType = ST_NONE;
        boolean newProfile = getIntent().getBooleanExtra(EXTRA_NEW_PROFILE, false);
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
                intent.putExtra(EXTRA_PROFILE_NAME, profileName);
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
            new Thread(() -> {
                model.loadProfile();
                // Requested a new profile, clone profile
                model.cloneProfile(newProfileName);
                runOnUiThread(() -> progressIndicator.hide());
            }).start();
        } else progressIndicator.hide();
        viewPager = findViewById(R.id.pager);
        viewPager.addOnPageChangeListener(this);
        viewPager.setAdapter(new ProfileFragmentPagerAdapter(getSupportFragmentManager()));
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);
        fab.setOnClickListener(v -> {
            progressIndicator.show();
            new Thread(() -> {
                // List apps
                PackageManager pm = getPackageManager();
                List<PackageInfo> packageInfoList = pm.getInstalledPackages(0);
                ArrayList<String> items = new ArrayList<>(packageInfoList.size());
                ArrayList<CharSequence> itemNames = new ArrayList<>(packageInfoList.size());
                for (PackageInfo info : packageInfoList) {
                    items.add(info.packageName);
                    itemNames.add(pm.getApplicationLabel(info.applicationInfo));
                }
                runOnUiThread(() -> {
                    progressIndicator.hide();
                    new SearchableMultiChoiceDialogBuilder<>(this, items, itemNames)
                            .setSelections(model.getCurrentPackages())
                            .setTitle(R.string.apps)
                            .setPositiveButton(R.string.ok, (dialog, which, selectedItems) ->
                                    new Thread(() -> model.setPackages(selectedItems)).start())
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });
            }).start();
        });
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
            final String[] statesL = new String[]{
                    getString(R.string.on),
                    getString(R.string.off)
            };
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
            new Thread(() -> {
                try {
                    model.save();
                    runOnUiThread(() -> Toast.makeText(this, R.string.saved_successfully, Toast.LENGTH_SHORT).show());
                } catch (IOException | JSONException e) {
                    Log.e("AppsProfileActivity", "Error: " + e);
                    runOnUiThread(() -> Toast.makeText(this, R.string.saving_failed, Toast.LENGTH_SHORT).show());
                }
            }).start();
        } else if (id == R.id.action_discard) {
            new Thread(() -> model.discard()).start();
        } else if (id == R.id.action_delete) {
            new Thread(() -> {
                if (model.delete()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, R.string.deleted_successfully, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, R.string.deletion_failed, Toast.LENGTH_SHORT).show());
                }
            }).start();
        } else if (id == R.id.action_duplicate) {
            new TextInputDialogBuilder(this, R.string.input_profile_name)
                    .setTitle(R.string.new_profile)
                    .setHelperText(R.string.input_profile_name_description)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.go, (dialog, which, profName, isChecked) -> {
                        progressIndicator.show();
                        if (!TextUtils.isEmpty(profName)) {
                            if (getSupportActionBar() != null) {
                                //noinspection ConstantConditions
                                getSupportActionBar().setTitle(profName.toString());
                            }
                            new Thread(() -> {
                                //noinspection ConstantConditions
                                model.cloneProfile(profName.toString());
                                runOnUiThread(() -> {
                                    Toast.makeText(this, R.string.the_operation_was_successful, Toast.LENGTH_SHORT).show();
                                    progressIndicator.hide();
                                });
                            }).start();
                        } else {
                            progressIndicator.hide();
                            Toast.makeText(this, R.string.failed_to_duplicate_profile, Toast.LENGTH_SHORT).show();
                        }
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
                        LauncherIconCreator.createLauncherIcon(this, getPackageName(),
                                model.getProfileName() + " - " + shortcutTypesL[which],
                                ContextCompat.getDrawable(this, R.drawable.ic_launcher_foreground),
                                getResources().getResourceName(R.drawable.ic_launcher_foreground), intent);
                        dialog.dismiss();
                    })
                    .show();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    protected void onDestroy() {
        if (viewPager != null) {
            viewPager.removeOnPageChangeListener(this);
        }
        super.onDestroy();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_apps) {
            viewPager.setCurrentItem(0);
        } else if (itemId == R.id.action_conf) {
            viewPager.setCurrentItem(1);
        } else return false;
        return true;
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        if (prevMenuItem != null) {
            prevMenuItem.setChecked(false);
        } else {
            bottomNavigationView.getMenu().getItem(0).setChecked(false);
        }

        bottomNavigationView.getMenu().getItem(position).setChecked(true);
        prevMenuItem = bottomNavigationView.getMenu().getItem(position);
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
            Fragment fragment = fragments[position];
            if (fragment == null) {
                switch (position) {
                    case 0:
                        return fragments[position] = new AppsFragment();
                    case 1:
                        return fragments[position] = new ConfFragment();
                }
            }
            return Objects.requireNonNull(fragment);
        }

        @Override
        public int getCount() {
            return fragments.length;
        }
    }
}
