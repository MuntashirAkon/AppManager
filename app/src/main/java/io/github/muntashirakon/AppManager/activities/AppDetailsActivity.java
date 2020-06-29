package io.github.muntashirakon.AppManager.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.fragments.AppDetailsFragment;
import io.github.muntashirakon.AppManager.utils.Utils;

public class AppDetailsActivity extends AppCompatActivity {
    public static final String EXTRA_PACKAGE_NAME = "pkg";

    public static String mConstraint;
    private String mPackageName;
    private TypedArray mTabTitleIds;
    AppDetailsFragmentStateAdapter appDetailsFragmentStateAdapter;
    ViewPager2 viewPager2;
    AppDetailsFragment[] fragments;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_details);
        mConstraint = null;
        mPackageName = getIntent().getStringExtra(AppInfoActivity.EXTRA_PACKAGE_NAME);
        if (mPackageName == null) {
            Toast.makeText(this, getString(R.string.empty_package_name), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // Set title
        try {
            setTitle(getPackageManager().getApplicationInfo(mPackageName, 0).loadLabel(getPackageManager()).toString());
        } catch (PackageManager.NameNotFoundException ignored) {
            finish();
            return;
        }
        // Initialize tabs
        mTabTitleIds = getResources().obtainTypedArray(R.array.TAB_TITLES);
        FragmentManager fragmentManager = getSupportFragmentManager();
        appDetailsFragmentStateAdapter = new AppDetailsFragmentStateAdapter(fragmentManager, getLifecycle());
        viewPager2 = findViewById(R.id.pager);
        viewPager2.setAdapter(appDetailsFragmentStateAdapter);
        fragments = new AppDetailsFragment[mTabTitleIds.length()];
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            final SearchView searchView = new SearchView(actionBar.getThemedContext());
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
            viewPager2.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(@AppDetailsFragment.Property int position) {
                    super.onPageSelected(position);
                    switch (position) {
                        case AppDetailsFragment.ACTIVITIES:
                        case AppDetailsFragment.SERVICES:
                        case AppDetailsFragment.RECEIVERS:
                        case AppDetailsFragment.PROVIDERS:
                        case AppDetailsFragment.APP_OPS:
                        case AppDetailsFragment.USES_PERMISSIONS:
                        case AppDetailsFragment.PERMISSIONS:
                            searchView.setVisibility(View.VISIBLE);
                            if (fragments[position] != null) {
                                searchView.setOnQueryTextListener(fragments[position]);
                                fragments[position].resetFilter();
                            }
                            break;
                        case AppDetailsFragment.FEATURES:
                        case AppDetailsFragment.CONFIGURATION:
                        case AppDetailsFragment.SIGNATURES:
                        case AppDetailsFragment.SHARED_LIBRARY_FILES:
                        case AppDetailsFragment.NONE:
                        default:
                            searchView.setVisibility(View.GONE);
                    }
                }
            });
        }
        TabLayout tabLayout = findViewById(R.id.tab_layout);

        new TabLayoutMediator(tabLayout, viewPager2, true,
                (tab, position) -> tab.setText(mTabTitleIds.getText(position))).attach();
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_app_details_actions, menu);
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
        } else if (id == R.id.action_app_info) {
            Intent appInfoIntent = new Intent(this, AppInfoActivity.class);
            appInfoIntent.putExtra(AppInfoActivity.EXTRA_PACKAGE_NAME, mPackageName);
            startActivity(appInfoIntent);
        }
        return super.onOptionsItemSelected(item);
    }

    // For tab layout
    private class AppDetailsFragmentStateAdapter extends FragmentStateAdapter {
        AppDetailsFragmentStateAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
            super(fragmentManager, lifecycle);
        }

        @NonNull
        @Override
        public Fragment createFragment(@AppDetailsFragment.Property int position) {
            return (fragments[position] = new AppDetailsFragment(position));
        }

        @Override
        public int getItemCount() {
            return mTabTitleIds.length();
        }
    }
}
