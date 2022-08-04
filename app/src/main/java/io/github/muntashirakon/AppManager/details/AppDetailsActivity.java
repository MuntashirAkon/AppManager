// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandleHidden;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import java.util.Objects;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.info.AppInfoFragment;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.main.MainActivity;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.util.ParcelUtils;

public class AppDetailsActivity extends BaseActivity {
    public static final String ALIAS_APP_INFO = "io.github.muntashirakon.AppManager.details.AppInfoActivity";

    private static final String EXTRA_PACKAGE_NAME = "pkg";
    private static final String EXTRA_USER_HANDLE = "user";
    private static final String EXTRA_BACK_TO_MAIN = "main";

    @NonNull
    public static Intent getIntent(@NonNull Context context, @NonNull String packageName, @UserIdInt int userId) {
        Intent intent = new Intent(context, AppDetailsActivity.class);
        intent.putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME, packageName);
        intent.putExtra(AppDetailsActivity.EXTRA_USER_HANDLE, userId);
        return intent;
    }

    @NonNull
    public static Intent getIntent(@NonNull Context context, @NonNull String packageName, @UserIdInt int userId,
                                   boolean backToMainPage) {
        Intent intent = new Intent(context, AppDetailsActivity.class);
        intent.putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME, packageName);
        intent.putExtra(AppDetailsActivity.EXTRA_USER_HANDLE, userId);
        intent.putExtra(AppDetailsActivity.EXTRA_BACK_TO_MAIN, backToMainPage);
        return intent;
    }

    @NonNull
    public static Intent getIntent(@NonNull Context context, @NonNull Path apkPath, boolean backToMainPage) {
        Intent intent = new Intent(context, AppDetailsActivity.class);
        intent.setDataAndType(apkPath.getUri(), "application/vnd.android.package-archive");
        intent.putExtra(AppDetailsActivity.EXTRA_BACK_TO_MAIN, backToMainPage);
        return intent;
    }

    public AppDetailsViewModel model;
    public AdvancedSearchView searchView;

    private ViewPager mViewPager;
    private TypedArray mTabTitleIds;
    private Fragment[] mTabFragments;

    private boolean mBackToMainPage;
    @Nullable
    private String mPackageName;
    @Nullable
    private Uri mApkUri;
    @Nullable
    private String mApkType;
    private int mUserHandle;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_app_details);
        setSupportActionBar(findViewById(R.id.toolbar));
        model = new ViewModelProvider(this).get(AppDetailsViewModel.class);
        // Restore instance state
        SavedState ss = savedInstanceState != null ? savedInstanceState.getParcelable("ss") : null;
        if (ss != null) {
            mBackToMainPage = ss.backToMainPage;
            mPackageName = ss.packageName;
            mApkUri = ss.apkUri;
            mApkType = ss.apkType;
            mUserHandle = ss.userHandle;
        } else {
            Intent intent = getIntent();
            mBackToMainPage = intent.getBooleanExtra(EXTRA_BACK_TO_MAIN, mBackToMainPage);
            mPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
            mApkUri = IntentCompat.getDataUri(intent);
            mApkType = intent.getType();
            mUserHandle = intent.getIntExtra(EXTRA_USER_HANDLE, UserHandleHidden.myUserId());
        }
        model.setUserHandle(mUserHandle);
        // Initialize tabs
        mTabTitleIds = getResources().obtainTypedArray(R.array.TAB_TITLES);
        mTabFragments = new Fragment[mTabTitleIds.length()];
        if (mPackageName == null && mApkUri == null) {
            UIUtils.displayLongToast(R.string.empty_package_name);
            finish();
            return;
        }
        // Set search
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            searchView = UIUtils.setupAdvancedSearchView(actionBar, null);
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        mViewPager = findViewById(R.id.pager);

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(mViewPager);
        final AlertDialog progressDialog = UIUtils.getProgressDialog(this, getText(R.string.loading));
        if (mPackageName == null) {
            // Display progress dialog only for external apk files
            progressDialog.show();
        }
        // Set tabs
        mViewPager.setAdapter(new AppDetailsFragmentPagerAdapter(fragmentManager));
        // Load package info
        (mPackageName != null
                ? model.setPackage(mPackageName)
                : model.setPackage(Objects.requireNonNull(mApkUri), mApkType)
        ).observe(this, packageInfo -> {
            progressDialog.dismiss();
            if (packageInfo == null) {
                UIUtils.displayShortToast(R.string.failed_to_fetch_package_info);
                if (!isDestroyed()) {
                    finish();
                }
                return;
            }
            // Set title
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            // Set title as the package label
            setTitle(applicationInfo.loadLabel(getPackageManager()));
            // Set subtitle as the username if more than one user exists
            model.getUserInfo().observe(this, userInfo -> getSupportActionBar()
                    .setSubtitle(getString(R.string.user_profile_with_id, userInfo.name, userInfo.id)));
        });
        // Check for the existence of package
        model.getIsPackageExistLiveData().observe(this, isPackageExist -> {
            if (!isPackageExist) {
                if (!model.getIsExternalApk()) {
                    UIUtils.displayShortToast(R.string.app_not_installed);
                }
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

    static class SavedState implements Parcelable {
        private boolean backToMainPage;
        @Nullable
        private String packageName;
        @Nullable
        private Uri apkUri;
        @Nullable
        private String apkType;
        private int userHandle;

        protected SavedState() {
        }

        public SavedState(Parcel source, ClassLoader loader) {
            backToMainPage = ParcelUtils.readBoolean(source);
            packageName = source.readString();
            apkUri = source.readParcelable(loader);
            apkType = source.readString();
            userHandle = source.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            ParcelUtils.writeBoolean(backToMainPage, dest);
            dest.writeString(packageName);
            dest.writeParcelable(apkUri, flags);
            dest.writeString(apkType);
            dest.writeInt(userHandle);
        }

        public static final Creator<SavedState> CREATOR = new ClassLoaderCreator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new SavedState(in, loader);
            }

            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in, null);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (mApkUri != null || mPackageName != null) {
            SavedState ss = new SavedState();
            ss.backToMainPage = mBackToMainPage;
            ss.packageName = mPackageName;
            ss.apkUri = mApkUri;
            ss.apkType = mApkType;
            ss.userHandle = mUserHandle;
            outState.putParcelable("ss", ss);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final int id = item.getItemId();
        if (id == android.R.id.home) {
            if (mBackToMainPage) {
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
            }
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadTabs() {
        @AppDetailsFragment.Property int id = mViewPager.getCurrentItem();
        Log.d("ADA - " + mTabTitleIds.getText(id), "isPackageChanged called");
        for (int i = 0; i < mTabTitleIds.length(); ++i) model.load(i);
    }

    // For tab layout
    private class AppDetailsFragmentPagerAdapter extends FragmentPagerAdapter {
        AppDetailsFragmentPagerAdapter(@NonNull FragmentManager fragmentManager) {
            super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            if (mTabFragments[position] == null) {
                if (position == 0) mTabFragments[position] = new AppInfoFragment();
                else mTabFragments[position] = new AppDetailsFragment(position);
            }
            return mTabFragments[position];
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
