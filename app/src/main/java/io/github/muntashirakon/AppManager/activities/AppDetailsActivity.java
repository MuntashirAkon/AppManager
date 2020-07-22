package io.github.muntashirakon.AppManager.activities;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.fragments.AppDetailsFragment;
import io.github.muntashirakon.AppManager.fragments.AppInfoFragment;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.AppManager.viewmodels.AppDetailsViewModel;

public class AppDetailsActivity extends AppCompatActivity {
    public static final String EXTRA_PACKAGE_NAME = "pkg";

    public AppDetailsViewModel model;
    public SearchView searchView;

    private TypedArray mTabTitleIds;
    private Fragment[] fragments;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_details);
        setSupportActionBar(findViewById(R.id.toolbar));
        final String packageName = getIntent().getStringExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME);
        if (packageName == null) {
            Toast.makeText(this, getString(R.string.empty_package_name), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // Set title
        try {
            setTitle(getPackageManager().getApplicationInfo(packageName, 0).loadLabel(getPackageManager()).toString());
        } catch (PackageManager.NameNotFoundException ignored) {
            finish();
            return;
        }
        // Get model
        model = ViewModelProvider.AndroidViewModelFactory.getInstance(AppManager.getInstance()).create(AppDetailsViewModel.class);
        model.setPackageName(packageName);
        // Initialize tabs
        mTabTitleIds = getResources().obtainTypedArray(R.array.TAB_TITLES);
        FragmentManager fragmentManager = getSupportFragmentManager();
        ViewPager viewPager = findViewById(R.id.pager);
        viewPager.setAdapter(new AppDetailsFragmentPagerAdapter(fragmentManager));
        fragments = new Fragment[mTabTitleIds.length()];
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            searchView = new SearchView(actionBar.getThemedContext());
            actionBar.setDisplayShowCustomEnabled(true);
            searchView.setQueryHint(getString(R.string.search));

            ((ImageView) searchView.findViewById(androidx.appcompat.R.id.search_button))
                    .setColorFilter(Utils.getThemeColor(this, android.R.attr.colorAccent));
            ((ImageView) searchView.findViewById(androidx.appcompat.R.id.search_close_btn))
                    .setColorFilter(Utils.getThemeColor(this, android.R.attr.colorAccent));

            ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.gravity = Gravity.END;
            actionBar.setCustomView(searchView, layoutParams);
        }
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(viewPager);
        // Check for the existence of package
        model.getIsPackageExist().observe(this, isPackageExist -> {
            if (!isPackageExist) {
                Toast.makeText(this, packageName + ": " + getString(R.string.app_not_installed), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
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
