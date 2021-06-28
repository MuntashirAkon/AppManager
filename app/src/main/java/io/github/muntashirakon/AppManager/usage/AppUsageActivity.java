// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textview.MaterialTextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.types.RecyclerViewWithEmptyView;
import io.github.muntashirakon.AppManager.usage.UsageUtils.IntervalType;
import io.github.muntashirakon.AppManager.utils.BetterActivityResult;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

import static io.github.muntashirakon.AppManager.usage.UsageUtils.USAGE_LAST_BOOT;
import static io.github.muntashirakon.AppManager.usage.UsageUtils.USAGE_TODAY;
import static io.github.muntashirakon.AppManager.usage.UsageUtils.USAGE_WEEKLY;
import static io.github.muntashirakon.AppManager.usage.UsageUtils.USAGE_YESTERDAY;

public class AppUsageActivity extends BaseActivity implements SwipeRefreshLayout.OnRefreshListener {
    @IntDef(value = {
            SORT_BY_APP_LABEL,
            SORT_BY_LAST_USED,
            SORT_BY_MOBILE_DATA,
            SORT_BY_PACKAGE_NAME,
            SORT_BY_SCREEN_TIME,
            SORT_BY_TIMES_OPENED,
            SORT_BY_WIFI_DATA
    })
    private @interface SortOrder {
    }

    private static final int SORT_BY_APP_LABEL = 0;
    private static final int SORT_BY_LAST_USED = 1;
    private static final int SORT_BY_MOBILE_DATA = 2;
    private static final int SORT_BY_PACKAGE_NAME = 3;
    private static final int SORT_BY_SCREEN_TIME = 4;
    private static final int SORT_BY_TIMES_OPENED = 5;
    private static final int SORT_BY_WIFI_DATA = 6;

    private static final int[] sSortMenuItemIdsMap = {
            R.id.action_sort_by_app_label, R.id.action_sort_by_last_used,
            R.id.action_sort_by_mobile_data, R.id.action_sort_by_package_name,
            R.id.action_sort_by_screen_time, R.id.action_sort_by_times_opened,
            R.id.action_sort_by_wifi_data};

    private LinearProgressIndicator mProgressIndicator;
    private SwipeRefreshLayout mSwipeRefresh;
    private AppUsageAdapter mAppUsageAdapter;
    private static long totalScreenTime;
    @IntervalType
    private int currentInterval = USAGE_TODAY;
    private final ImageLoader imageLoader = new ImageLoader();
    private final BetterActivityResult<String, Boolean> requestPerm = BetterActivityResult
            .registerForActivityResult(this, new ActivityResultContracts.RequestPermission());

    @SuppressLint("WrongConstant")
    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_app_usage);
        setSupportActionBar(findViewById(R.id.toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(getString(R.string.app_usage));
        }

        mProgressIndicator = findViewById(R.id.progress_linear);

        // Get usage stats
        mAppUsageAdapter = new AppUsageAdapter(this);
        RecyclerViewWithEmptyView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setEmptyView(findViewById(android.R.id.empty));
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(mAppUsageAdapter);

        mSwipeRefresh = findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setColorSchemeColors(UIUtils.getAccentColor(this));
        mSwipeRefresh.setProgressBackgroundColorSchemeColor(UIUtils.getPrimaryColor(this));
        mSwipeRefresh.setOnRefreshListener(this);
        mSwipeRefresh.setOnChildScrollUpCallback((parent, child) -> recyclerView.canScrollVertically(-1));

        Spinner intervalSpinner = findViewById(R.id.spinner_interval);
        // Make spinner the first item to focus on
        intervalSpinner.requestFocus();
        SpinnerAdapter intervalSpinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.usage_interval_dropdown_list, R.layout.item_checked_text_view);
        intervalSpinner.setAdapter(intervalSpinnerAdapter);
        intervalSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentInterval = position;
                getAppUsage();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkPermissions();
    }

    @Override
    protected void onDestroy() {
        imageLoader.close();
        super.onDestroy();
    }

    @Override
    public void onRefresh() {
        mSwipeRefresh.setRefreshing(false);
        checkPermissions();
    }

    private void checkPermissions() {
        // Check permission
        if (!PermissionUtils.hasUsageStatsPermission(this)) promptForUsageStatsPermission();
        else getAppUsage();
        // Grant optional READ_PHONE_STATE permission
        if (!PermissionUtils.hasPermission(this, Manifest.permission.READ_PHONE_STATE) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            requestPerm.launch(Manifest.permission.READ_PHONE_STATE, granted -> {
                if (granted) recreate();
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
        if (mAppUsageAdapter != null) {
            menu.findItem(sSortMenuItemIdsMap[mAppUsageAdapter.mSortBy]).setChecked(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.action_sort_by_app_label) {
            setSortBy(SORT_BY_APP_LABEL);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_last_used) {
            setSortBy(SORT_BY_LAST_USED);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_mobile_data) {
            setSortBy(SORT_BY_MOBILE_DATA);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_package_name) {
            setSortBy(SORT_BY_PACKAGE_NAME);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_screen_time) {
            setSortBy(SORT_BY_SCREEN_TIME);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_times_opened) {
            setSortBy(SORT_BY_TIMES_OPENED);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_wifi_data) {
            setSortBy(SORT_BY_WIFI_DATA);
            item.setChecked(true);
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    private void setSortBy(@SortOrder int sort) {
        if (mAppUsageAdapter != null) {
            mAppUsageAdapter.setSortBy(sort);
            mAppUsageAdapter.sortPackageUSList();
            mAppUsageAdapter.notifyDataSetChanged();
        }
    }

    private void getAppUsage() {
        mProgressIndicator.show();
        new Thread(() -> {
            // TODO: 28/6/21 Replace with ViewModel
            int _try = 5; // try to get usage stat 5 times
            List<PackageUsageInfo> packageUsageInfoList = new ArrayList<>();
            do {
                try {
                    packageUsageInfoList.addAll(AppUsageStatsManager.getInstance(this).getUsageStats(currentInterval));
                } catch (RemoteException e) {
                    Log.e("AppUsage", e);
                }
            } while (0 != --_try && packageUsageInfoList.size() == 0);
            totalScreenTime = 0;
            for (PackageUsageInfo appItem : packageUsageInfoList)
                totalScreenTime += appItem.screenTime;
            runOnUiThread(() -> {
                mAppUsageAdapter.setDefaultList(packageUsageInfoList);
                setUsageSummary();
                mProgressIndicator.hide();
            });
        }).start();
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

    private void setUsageSummary() {
        TextView timeUsed = findViewById(R.id.time_used);
        TextView timeRange = findViewById(R.id.time_range);
        timeUsed.setText(Utils.getFormattedDuration(this, totalScreenTime));
        switch (currentInterval) {
            case USAGE_TODAY:
                timeRange.setText(R.string.usage_today);
                break;
            case USAGE_YESTERDAY:
                timeRange.setText(R.string.usage_yesterday);
                break;
            case USAGE_WEEKLY:
                timeRange.setText(R.string.usage_7_days);
                break;
            case USAGE_LAST_BOOT:
                break;
        }
    }

    static class AppUsageAdapter extends RecyclerView.Adapter<AppUsageAdapter.ViewHolder> {
        @GuardedBy("mAdapterList")
        private final List<PackageUsageInfo> mAdapterList = new ArrayList<>();
        private final AppUsageActivity mActivity;
        private final PackageManager mPackageManager;
        @SortOrder
        private int mSortBy;

        private static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView appIcon;
            MaterialTextView appLabel;
            MaterialTextView packageName;
            MaterialTextView lastUsageDate;
            MaterialTextView mobileDataUsage;
            MaterialTextView wifiDataUsage;
            MaterialTextView screenTime;
            MaterialTextView percentUsage;
            LinearProgressIndicator usageIndicator;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                appIcon = itemView.findViewById(R.id.icon);
                appLabel = itemView.findViewById(R.id.label);
                packageName = itemView.findViewById(R.id.package_name);
                lastUsageDate = itemView.findViewById(R.id.date);
                mobileDataUsage = itemView.findViewById(R.id.data_usage);
                wifiDataUsage = itemView.findViewById(R.id.wifi_usage);
                screenTime = itemView.findViewById(R.id.screen_time);
                percentUsage = itemView.findViewById(R.id.percent_usage);
                usageIndicator = itemView.findViewById(R.id.progress_linear);
            }
        }

        AppUsageAdapter(@NonNull AppUsageActivity activity) {
            mActivity = activity;
            mPackageManager = mActivity.getPackageManager();
            mSortBy = SORT_BY_SCREEN_TIME;
        }

        void setDefaultList(List<PackageUsageInfo> list) {
            synchronized (mAdapterList) {
                mAdapterList.clear();
                mAdapterList.addAll(list);
            }
            sortPackageUSList();
            notifyDataSetChanged();
        }

        public void setSortBy(int sortBy) {
            this.mSortBy = sortBy;
        }

        private void sortPackageUSList() {
            synchronized (mAdapterList) {
                Collections.sort(mAdapterList, ((o1, o2) -> {
                    switch (mSortBy) {
                        case SORT_BY_APP_LABEL:
                            return Collator.getInstance().compare(o1.appLabel, o2.appLabel);
                        case SORT_BY_LAST_USED:
                            return -o1.lastUsageTime.compareTo(o2.lastUsageTime);
                        case SORT_BY_MOBILE_DATA:
                            Long o1MData = o1.mobileData.first + o1.mobileData.second;
                            Long o2MData = o2.mobileData.first + o2.mobileData.second;
                            return -o1MData.compareTo(o2MData);
                        case SORT_BY_PACKAGE_NAME:
                            return o1.packageName.compareToIgnoreCase(o2.packageName);
                        case SORT_BY_SCREEN_TIME:
                            return -o1.screenTime.compareTo(o2.screenTime);
                        case SORT_BY_TIMES_OPENED:
                            return -o1.timesOpened.compareTo(o2.timesOpened);
                        case SORT_BY_WIFI_DATA:
                            Long o1WData = o1.wifiData.first + o1.wifiData.second;
                            Long o2WData = o2.wifiData.first + o2.wifiData.second;
                            return -o1WData.compareTo(o2WData);
                    }
                    return 0;
                }));
            }
        }

        @Override
        public int getItemCount() {
            synchronized (mAdapterList) {
                return mAdapterList.size();
            }
        }

        @Override
        public long getItemId(int position) {
            synchronized (mAdapterList) {
                return Objects.hashCode(mAdapterList.get(position));
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_usage, parent, false);
            return new ViewHolder(view);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final PackageUsageInfo packageUS;
            synchronized (mAdapterList) {
                packageUS = mAdapterList.get(position);
            }
            final int percentUsage = (int) (packageUS.screenTime * 100f / totalScreenTime);
            // Set label (or package name on failure)
            try {
                ApplicationInfo applicationInfo = mPackageManager.getApplicationInfo(packageUS.packageName, 0);
                holder.appLabel.setText(mPackageManager.getApplicationLabel(applicationInfo));
                // Set icon
                mActivity.imageLoader.displayImage(applicationInfo.packageName, applicationInfo, holder.appIcon);
            } catch (PackageManager.NameNotFoundException e) {
                holder.appLabel.setText(packageUS.packageName);
                holder.appIcon.setImageDrawable(mPackageManager.getDefaultActivityIcon());
            }
            // Set package name
            holder.packageName.setText(packageUS.packageName);
            // Set usage
            long lastTimeUsed = packageUS.lastUsageTime;
            if (lastTimeUsed > 1) {
                holder.lastUsageDate.setText(DateUtils.formatDateTime(lastTimeUsed));
            }
            String screenTimesWithTimesOpened;
            // Set times opened
            screenTimesWithTimesOpened = mActivity.getResources().getQuantityString(R.plurals.no_of_times_opened, packageUS.timesOpened, packageUS.timesOpened);
            // Set screen time
            screenTimesWithTimesOpened += ", " + Utils.getFormattedDuration(mActivity, packageUS.screenTime);
            holder.screenTime.setText(screenTimesWithTimesOpened);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Set data usage
                final AppUsageStatsManager.DataUsage mobileData = packageUS.mobileData;
                if (mobileData.first != 0 || mobileData.second != 0) {
                    holder.mobileDataUsage.setText("M: \u2191 " + Formatter.formatFileSize(mActivity, mobileData.first)
                            + " \u2193 " + Formatter.formatFileSize(mActivity, mobileData.second));
                } else holder.mobileDataUsage.setText("");
                final AppUsageStatsManager.DataUsage wifiData = packageUS.wifiData;
                if (wifiData.first != 0 || wifiData.second != 0) {
                    holder.wifiDataUsage.setText("W: \u2191 " + Formatter.formatFileSize(mActivity, wifiData.first)
                            + " \u2193 " + Formatter.formatFileSize(mActivity, wifiData.second));
                } else holder.wifiDataUsage.setText("");

            }
            // Set usage percentage
            holder.percentUsage.setText(String.format(Locale.ROOT, "%d%%", percentUsage));
            holder.usageIndicator.show();
            holder.usageIndicator.setProgress(percentUsage);
            // On Click Listener
            holder.itemView.setOnClickListener(v -> {
                try {
                    PackageUsageInfo packageUS1 = AppUsageStatsManager.getInstance(mActivity)
                            .getUsageStatsForPackage(packageUS.packageName, mActivity.currentInterval);
                    packageUS1.copyOthers(packageUS);
                    AppUsageDetailsDialogFragment appUsageDetailsDialogFragment = new AppUsageDetailsDialogFragment();
                    Bundle args = new Bundle();
                    args.putParcelable(AppUsageDetailsDialogFragment.ARG_PACKAGE_US, packageUS1);
                    appUsageDetailsDialogFragment.setArguments(args);
                    appUsageDetailsDialogFragment.show(mActivity.getSupportFragmentManager(), AppUsageDetailsDialogFragment.TAG);
                } catch (RemoteException e) {
                    Log.e("AppUsage", e);
                }
            });
        }
    }
}
