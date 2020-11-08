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

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.ProgressIndicator;

import org.json.JSONException;

import java.io.IOException;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;

public class AppsProfileActivity extends BaseActivity
        implements BottomNavigationView.OnNavigationItemSelectedListener, ViewPager.OnPageChangeListener {
    public static final String EXTRA_PROFILE_NAME = "prof";
    public static final String EXTRA_NEW_PROFILE = "new";

    private ViewPager viewPager;
    private BottomNavigationView bottomNavigationView;
    private MenuItem prevMenuItem;
    private Fragment[] fragments = new Fragment[2];
    ProfileViewModel model;
    FloatingActionButton fab;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apps_profile);
        setSupportActionBar(findViewById(R.id.toolbar));
        ProgressIndicator progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);
        progressIndicator.hide();
        fab = findViewById(R.id.floatingActionButton);
        if (getIntent() == null) {
            finish();
            return;
        }
        String profileName = getIntent().getStringExtra(EXTRA_PROFILE_NAME);
        if (profileName == null) {
            finish();
            return;
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(profileName);
        }
        boolean newProfile = getIntent().getBooleanExtra(EXTRA_NEW_PROFILE, false);
        model = new ViewModelProvider(this).get(ProfileViewModel.class);
        model.setProfileName(profileName, newProfile);
        viewPager = findViewById(R.id.pager);
        viewPager.addOnPageChangeListener(this);
        viewPager.setAdapter(new ProfileFragmentPagerAdapter(getSupportFragmentManager()));
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.fragment_profile_apps_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_apply:
                // TODO(8/11/20): Apply profile
                Toast.makeText(this, "Not yet implemented", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.action_save:
                new Thread(() -> {
                    try {
                        model.save();
                        runOnUiThread(() -> Toast.makeText(this, R.string.saved_successfully, Toast.LENGTH_SHORT).show());
                    } catch (IOException | JSONException e) {
                        Log.e("AppsProfileActivity", "Error: " + e);
                        runOnUiThread(() -> Toast.makeText(this, R.string.saving_failed, Toast.LENGTH_SHORT).show());
                    }
                }).start();
                return true;
            case R.id.action_discard:
                new Thread(() -> model.discard()).start();
                return true;
            case R.id.action_delete:
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
                return true;
            case R.id.action_duplicate:
                // TODO(8/11/20): Duplicate profile
                Toast.makeText(this, "Not yet implemented", Toast.LENGTH_SHORT).show();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        viewPager.removeOnPageChangeListener(this);
        super.onDestroy();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_apps:
                viewPager.setCurrentItem(0);
                return true;
            case R.id.action_conf:
                viewPager.setCurrentItem(1);
                return true;
        }
        return false;
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
