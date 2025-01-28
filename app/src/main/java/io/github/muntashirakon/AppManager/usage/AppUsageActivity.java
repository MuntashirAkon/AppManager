// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.utils.BetterActivityResult;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.adapters.SelectedArrayAdapter;
import io.github.muntashirakon.view.ProgressIndicatorCompat;
import io.github.muntashirakon.widget.MaterialSpinner;
import io.github.muntashirakon.widget.RecyclerView;
import io.github.muntashirakon.widget.SwipeRefreshLayout;

public class AppUsageActivity extends BaseActivity implements SwipeRefreshLayout.OnRefreshListener {
    private static final int[] sSortMenuItemIdsMap = {
            R.id.action_sort_by_app_label, R.id.action_sort_by_last_used,
            R.id.action_sort_by_mobile_data, R.id.action_sort_by_package_name,
            R.id.action_sort_by_screen_time, R.id.action_sort_by_times_opened,
            R.id.action_sort_by_wifi_data};

    AppUsageViewModel viewModel;
    LinearProgressIndicator progressIndicator;
    private SwipeRefreshLayout mSwipeRefresh;
    private AppUsageAdapter mAppUsageAdapter;
    private final BetterActivityResult<String, Boolean> mRequestPerm = BetterActivityResult
            .registerForActivityResult(this, new ActivityResultContracts.RequestPermission());

    @SuppressLint("WrongConstant")
    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        if (!FeatureController.isUsageAccessEnabled()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_app_usage);
        setSupportActionBar(findViewById(R.id.toolbar));
        viewModel = new ViewModelProvider(this).get(AppUsageViewModel.class);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(getString(R.string.app_usage));
        }

        progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);

        // Interval
        MaterialSpinner spinner = findViewById(R.id.spinner);
        spinner.requestFocus();
        ArrayAdapter<CharSequence> intervalSpinnerAdapter = SelectedArrayAdapter.createFromResource(this,
                R.array.usage_interval_dropdown_list, io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item_small);
        spinner.setAdapter(intervalSpinnerAdapter);
        spinner.setSelection(viewModel.getCurrentInterval());
        spinner.setOnItemClickListener((parent, view, position, id) -> {
            ProgressIndicatorCompat.setVisibility(progressIndicator, true);
            viewModel.setCurrentInterval(position);
        });

        // Get usage stats
        mAppUsageAdapter = new AppUsageAdapter(this);
        RecyclerView recyclerView = findViewById(R.id.scrollView);
        recyclerView.setEmptyView(findViewById(android.R.id.empty));
        recyclerView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(this));
        recyclerView.setAdapter(mAppUsageAdapter);

        mSwipeRefresh = findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setOnRefreshListener(this);
        mSwipeRefresh.setOnChildScrollUpCallback((parent, child) -> recyclerView.canScrollVertically(-1));

        viewModel.getPackageUsageInfoList().observe(this, packageUsageInfoList -> {
            ProgressIndicatorCompat.setVisibility(progressIndicator, false);
            mAppUsageAdapter.setDefaultList(packageUsageInfoList);
        });
        viewModel.getPackageUsageInfo().observe(this, packageUsageInfo -> {
            AppUsageDetailsDialog fragment = AppUsageDetailsDialog.getInstance(packageUsageInfo);
            fragment.show(getSupportFragmentManager(), AppUsageDetailsDialog.TAG);
        });
        checkPermissions();
    }

    @Override
    public void onRefresh() {
        mSwipeRefresh.setRefreshing(false);
        checkPermissions();
    }

    private void checkPermissions() {
        // Check permission
        if (!SelfPermissions.checkUsageStatsPermission()) {
            promptForUsageStatsPermission();
        } else {
            ProgressIndicatorCompat.setVisibility(progressIndicator, true);
            viewModel.loadPackageUsageInfoList();
        }
        if (AppUsageStatsManager.requireReadPhoneStatePermission()) {
            // Grant READ_PHONE_STATE permission
            mRequestPerm.launch(Manifest.permission.READ_PHONE_STATE, granted -> {
                if (granted) {
                    ActivityCompat.recreate(this);
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_app_usage_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (viewModel != null) {
            menu.findItem(sSortMenuItemIdsMap[viewModel.getSortOrder()]).setChecked(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.action_sort_by_app_label) {
            setSortBy(SortOrder.SORT_BY_APP_LABEL);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_last_used) {
            setSortBy(SortOrder.SORT_BY_LAST_USED);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_mobile_data) {
            setSortBy(SortOrder.SORT_BY_MOBILE_DATA);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_package_name) {
            setSortBy(SortOrder.SORT_BY_PACKAGE_NAME);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_screen_time) {
            setSortBy(SortOrder.SORT_BY_SCREEN_TIME);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_times_opened) {
            setSortBy(SortOrder.SORT_BY_TIMES_OPENED);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_wifi_data) {
            setSortBy(SortOrder.SORT_BY_WIFI_DATA);
            item.setChecked(true);
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    private void setSortBy(@SortOrder int sort) {
        if (viewModel != null) {
            viewModel.setSortOrder(sort);
        }
    }

    private void promptForUsageStatsPermission() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.grant_usage_access)
                .setMessage(R.string.grant_usage_acess_message)
                .setPositiveButton(R.string.go, (dialog, which) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                    } catch (ActivityNotFoundException e) {
                        // Usage access isn't available
                        new MaterialAlertDialogBuilder(this)
                                .setCancelable(false)
                                .setTitle(R.string.grant_usage_access)
                                .setMessage(R.string.usage_access_not_supported)
                                .setPositiveButton(R.string.go_back, (dialog1, which1) -> {
                                    FeatureController.getInstance().modifyState(FeatureController
                                            .FEAT_USAGE_ACCESS, false);
                                    finish();
                                })
                                .show();
                    }
                })
                .setNegativeButton(getString(R.string.go_back), (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }
}
