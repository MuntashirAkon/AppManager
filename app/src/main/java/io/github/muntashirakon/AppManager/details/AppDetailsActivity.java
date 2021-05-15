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

package io.github.muntashirakon.AppManager.details;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.UserInfo;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import java.util.List;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.info.AppInfoFragment;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class AppDetailsActivity extends BaseActivity {
    public static final String EXTRA_PACKAGE_NAME = "pkg";
    public static final String EXTRA_USER_HANDLE = "user";

    public AppDetailsViewModel model;
    public SearchView searchView;
    public ViewPager viewPager;

    private TypedArray mTabTitleIds;
    private Fragment[] fragments;

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_app_details);
        setSupportActionBar(findViewById(R.id.toolbar));
        model = new ViewModelProvider(this).get(AppDetailsViewModel.class);
        Intent intent = getIntent();
        // Check for package name
        final String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        final Uri apkUri = intent.getData();
        final String apkType = intent.getType();
        final int userHandle = intent.getIntExtra(EXTRA_USER_HANDLE, Users.getCurrentUserHandle());
        model.setUserHandle(userHandle);
        // Initialize tabs
        mTabTitleIds = getResources().obtainTypedArray(R.array.TAB_TITLES);
        fragments = new Fragment[mTabTitleIds.length()];
        if (packageName == null && apkUri == null) {
            UIUtils.displayLongToast(R.string.empty_package_name);
            finish();
            return;
        }
        // Set search
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            searchView = UIUtils.setupSearchView(this, actionBar, null);
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        viewPager = findViewById(R.id.pager);

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(viewPager);
        final AlertDialog progressDialog = UIUtils.getProgressDialog(this, getText(R.string.loading));
        if (packageName == null) {
            // Display progress dialog only for external apk files
            progressDialog.show();
        }
        // Set tabs
        viewPager.setAdapter(new AppDetailsFragmentPagerAdapter(fragmentManager));
        // Get package info
        (packageName != null ? model.setPackage(packageName) : model.setPackage(apkUri, apkType))
                .observe(this, packageInfo -> {
                    progressDialog.dismiss();
                    if (packageInfo == null) {
                        UIUtils.displayShortToast(R.string.failed_to_fetch_package_info);
                        if (!isDestroyed()) {
                            finish();
                        }
                        return;
                    }
                    // Load tabs for the first time
                    for (int i = 0; i < mTabTitleIds.length(); ++i) model.load(i);
                    // Set title
                    ApplicationInfo applicationInfo = model.getPackageInfo().applicationInfo;
                    // Set title as the package label
                    setTitle(applicationInfo.loadLabel(getPackageManager()));
                    new Thread(() -> {
                        // FIXME: 14/5/21 Replace with ViewModel
                        final List<UserInfo> userInfoList;
                        if (!model.getIsExternalApk() && AppPref.isRootOrAdbEnabled()) {
                            userInfoList = Users.getUsers();
                        } else userInfoList = null;
                        runOnUiThread(() -> {
                            if (isDestroyed()) return;
                            // Set subtitle as the user name if more than one user exists
                            if (userInfoList != null && userInfoList.size() > 1) {
                                for (UserInfo userInfo : userInfoList) {
                                    if (userInfo.id == userHandle) {
                                        getSupportActionBar().setSubtitle(getString(R.string.user_profile_with_id,
                                                userInfo.name, userInfo.id));
                                        break;
                                    }
                                }
                            }
                        });
                    }).start();
                });
        // Check for the existence of package
        model.getIsPackageExistLiveData().observe(this, isPackageExist -> {
            if (!isPackageExist) {
                UIUtils.displayShortToast(R.string.app_not_installed);
                finish();
            }
        });
        // Check for package changes
        model.getIsPackageChanged().observe(this, isPackageChanged -> {
            if (isPackageChanged && model.isPackageExist()) {
                loadTabs();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadTabs() {
        @AppDetailsFragment.Property int id = viewPager.getCurrentItem();
        Log.e("ADA - " + mTabTitleIds.getText(id), "isPackageChanged called");
        if (model.getIsExternalApk()) model.load(AppDetailsFragment.APP_INFO);
        else for (int i = 0; i < mTabTitleIds.length(); ++i) model.load(i);
    }

    // For tab layout
    private class AppDetailsFragmentPagerAdapter extends FragmentPagerAdapter {
        AppDetailsFragmentPagerAdapter(@NonNull FragmentManager fragmentManager) {
            super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            if (fragments[position] == null) {
                if (position == 0) fragments[position] = new AppInfoFragment();
                else fragments[position] = new AppDetailsFragment(position);
            }
            return fragments[position];
        }

        @Override
        public int getCount() {
            return mTabTitleIds.length();
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return mTabTitleIds.getText(position);
        }
    }
}
