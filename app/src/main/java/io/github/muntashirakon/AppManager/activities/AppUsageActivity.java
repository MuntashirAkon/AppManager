package io.github.muntashirakon.AppManager.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.ProgressIndicator;
import com.google.android.material.textview.MaterialTextView;

import java.text.Collator;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.fragments.AppUsageDetailsDialogFragment;
import io.github.muntashirakon.AppManager.types.IconLoaderThread;
import io.github.muntashirakon.AppManager.types.ScrollSafeSwipeRefreshLayout;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.usage.Utils.IntervalType;
import io.github.muntashirakon.AppManager.utils.Tuple;
import io.github.muntashirakon.AppManager.utils.Utils;

import static io.github.muntashirakon.AppManager.usage.Utils.USAGE_LAST_BOOT;
import static io.github.muntashirakon.AppManager.usage.Utils.USAGE_TODAY;
import static io.github.muntashirakon.AppManager.usage.Utils.USAGE_WEEKLY;
import static io.github.muntashirakon.AppManager.usage.Utils.USAGE_YESTERDAY;

public class AppUsageActivity extends AppCompatActivity implements ListView.OnItemClickListener,
        ScrollSafeSwipeRefreshLayout.OnRefreshListener {
    @IntDef(value = {
            SORT_BY_APP_LABEL,
            SORT_BY_LAST_USED,
            SORT_BY_MOBILE_DATA,
            SORT_BY_PACKAGE_NAME,
            SORT_BY_SCREEN_TIME,
            SORT_BY_TIMES_OPENED,
            SORT_BY_WIFI_DATA
    })
    private @interface SortOrder {}
    private static final int SORT_BY_APP_LABEL    = 0;
    private static final int SORT_BY_LAST_USED    = 1;
    private static final int SORT_BY_MOBILE_DATA  = 2;
    private static final int SORT_BY_PACKAGE_NAME = 3;
    private static final int SORT_BY_SCREEN_TIME  = 4;
    private static final int SORT_BY_TIMES_OPENED = 5;
    private static final int SORT_BY_WIFI_DATA    = 6;

    private static final int[] sSortMenuItemIdsMap = {
            R.id.action_sort_by_app_label, R.id.action_sort_by_last_used,
            R.id.action_sort_by_mobile_data, R.id.action_sort_by_package_name,
            R.id.action_sort_by_screen_time, R.id.action_sort_by_times_opened,
            R.id.action_sort_by_wifi_data};

    private ProgressIndicator mProgressIndicator;
    private ScrollSafeSwipeRefreshLayout mSwipeRefresh;
    private AppUsageAdapter mAppUsageAdapter;
    List<AppUsageStatsManager.PackageUS> mPackageUSList;
    private static long totalScreenTime;
    private static PackageManager mPackageManager;
    private @IntervalType int current_interval = USAGE_TODAY;
    private @SortOrder int mSortBy;

    @SuppressLint("WrongConstant")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_usage);
        setSupportActionBar(findViewById(R.id.toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(getString(R.string.app_usage));
        }

        mProgressIndicator = findViewById(R.id.progress_linear);

        // Get usage stats
        mAppUsageAdapter = new AppUsageAdapter(this);
        ListView listView = findViewById(android.R.id.list);
        listView.setDividerHeight(0);
        listView.setEmptyView(findViewById(android.R.id.empty));
        listView.setAdapter(mAppUsageAdapter);
        listView.setOnItemClickListener(this);

        mPackageManager = getPackageManager();

        mSwipeRefresh = findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setColorSchemeColors(Utils.getThemeColor(this, android.R.attr.colorAccent));
        mSwipeRefresh.setProgressBackgroundColorSchemeColor(Utils.getThemeColor(this, android.R.attr.colorPrimary));
        mSwipeRefresh.setOnRefreshListener(this);
        mSwipeRefresh.setOnChildScrollUpCallback((parent, child) -> listView.canScrollVertically(-1));

        @SuppressLint("InflateParams")
        View header = getLayoutInflater().inflate(R.layout.header_app_usage, null);
        listView.addHeaderView(header);

        Spinner intervalSpinner = findViewById(R.id.spinner_interval);
        SpinnerAdapter intervalSpinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.usage_interval_dropdown_list, android.R.layout.simple_spinner_dropdown_item);
        intervalSpinner.setAdapter(intervalSpinnerAdapter);
        intervalSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                current_interval = position;
                getAppUsage();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        mSortBy = SORT_BY_SCREEN_TIME;
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check permission
        if (!Utils.checkUsageStatsPermission(this)) promptForUsageStatsPermission();
        else getAppUsage();
    }

    @Override
    protected void onDestroy() {
        mPackageManager = null;
        super.onDestroy();
    }

    @Override
    public void onRefresh() {
        mSwipeRefresh.setRefreshing(false);
        // Check permission
        if (!Utils.checkUsageStatsPermission(this)) promptForUsageStatsPermission();
        else getAppUsage();
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_app_usage_actions, menu);
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(sSortMenuItemIdsMap[mSortBy]).setChecked(true);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_sort_by_app_label:
                setSortBy(SORT_BY_APP_LABEL);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_last_used:
                setSortBy(SORT_BY_LAST_USED);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_mobile_data:
                setSortBy(SORT_BY_MOBILE_DATA);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_package_name:
                setSortBy(SORT_BY_PACKAGE_NAME);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_screen_time:
                setSortBy(SORT_BY_SCREEN_TIME);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_times_opened:
                setSortBy(SORT_BY_TIMES_OPENED);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_wifi_data:
                setSortBy(SORT_BY_WIFI_DATA);
                item.setChecked(true);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        AppUsageStatsManager.PackageUS packageUS = mAppUsageAdapter.getItem(position-1);
        AppUsageStatsManager.PackageUS packageUS1 = AppUsageStatsManager.getInstance(this).getUsageStatsForPackage(packageUS.packageName, current_interval);
        packageUS1.copyOthers(packageUS);
        AppUsageDetailsDialogFragment appUsageDetailsDialogFragment = new AppUsageDetailsDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(AppUsageDetailsDialogFragment.ARG_PACKAGE_US, packageUS1);
        appUsageDetailsDialogFragment.setArguments(args);
        appUsageDetailsDialogFragment.show(getSupportFragmentManager(), AppUsageDetailsDialogFragment.TAG);
    }

    private void setSortBy(@SortOrder int sort) {
        mSortBy = sort;
        sortPackageUSList();
        if (mAppUsageAdapter != null)
            mAppUsageAdapter.notifyDataSetChanged();
    }

    private void sortPackageUSList() {
        if (mPackageUSList == null) return;
        Collections.sort(mPackageUSList, ((o1, o2) -> {
            switch (mSortBy) {
                case SORT_BY_APP_LABEL:
                    return Collator.getInstance().compare(o1.appLabel, o2.appLabel);
                case SORT_BY_LAST_USED:
                    return -o1.lastUsageTime.compareTo(o2.lastUsageTime);
                case SORT_BY_MOBILE_DATA:
                    Long o1MData = o1.mobileData.getFirst() + o1.mobileData.getSecond();
                    Long o2MData = o2.mobileData.getFirst() + o2.mobileData.getSecond();
                    return -o1MData.compareTo(o2MData);
                case SORT_BY_PACKAGE_NAME:
                    return o1.packageName.compareToIgnoreCase(o2.packageName);
                case SORT_BY_SCREEN_TIME:
                    return -o1.screenTime.compareTo(o2.screenTime);
                case SORT_BY_TIMES_OPENED:
                    return -o1.timesOpened.compareTo(o2.timesOpened);
                case SORT_BY_WIFI_DATA:
                    Long o1WData = o1.wifiData.getFirst() + o1.wifiData.getSecond();
                    Long o2WData = o2.wifiData.getFirst() + o2.wifiData.getSecond();
                    return -o1WData.compareTo(o2WData);
            }
            return 0;
        }));
    }

    private void getAppUsage() {
        mProgressIndicator.show();
        new Thread(() -> {
            int _try = 5; // try to get usage stat 5 times
            do {
                mPackageUSList = AppUsageStatsManager.getInstance(this).getUsageStats(0, current_interval);
            } while (0 != --_try && mPackageUSList.size() == 0);
            totalScreenTime = 0;
            for (AppUsageStatsManager.PackageUS appItem : mPackageUSList)
                totalScreenTime += appItem.screenTime;
            sortPackageUSList();
            runOnUiThread(() -> {
                mAppUsageAdapter.setDefaultList(mPackageUSList);
                setUsageSummary();
                mProgressIndicator.hide();
            });
        }).start();
    }

    private void promptForUsageStatsPermission() {
        new MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialog)
                .setTitle(R.string.grant_usage_access)
                .setMessage(R.string.grant_usage_acess_message)
                .setPositiveButton(R.string.go, (dialog, which) -> startActivity(new Intent(
                        Settings.ACTION_USAGE_ACCESS_SETTINGS)))
                .setNegativeButton(getString(R.string.go_back), (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void setUsageSummary() {
        TextView timeUsed = findViewById(R.id.time_used);
        TextView timeRange = findViewById(R.id.time_range);
        timeUsed.setText(Utils.getFormattedDuration(this, totalScreenTime));
        switch (current_interval) {
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

    static class AppUsageAdapter extends BaseAdapter {
        static DateFormat sSimpleDateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());

        private LayoutInflater mLayoutInflater;
        private List<AppUsageStatsManager.PackageUS> mAdapterList;
        private Activity mActivity;

        static class ViewHolder {
            ImageView appIcon;
            MaterialTextView appLabel;
            MaterialTextView packageName;
            MaterialTextView lastUsageDate;
            MaterialTextView mobileDataUsage;
            MaterialTextView wifiDataUsage;
            MaterialTextView screenTime;
            MaterialTextView percentUsage;
            ProgressIndicator usageIndicator;
            IconLoaderThread iconLoader;
        }

        AppUsageAdapter(@NonNull Activity activity) {
            mLayoutInflater = activity.getLayoutInflater();
            mActivity = activity;
        }

        void setDefaultList(List<AppUsageStatsManager.PackageUS> list) {
            mAdapterList = list;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mAdapterList == null ? 0 : mAdapterList.size();
        }

        @Override
        public AppUsageStatsManager.PackageUS getItem(int position) {
            return mAdapterList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @SuppressLint("SetTextI18n")
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.item_app_usage, parent, false);
                holder = new ViewHolder();
                holder.appIcon = convertView.findViewById(R.id.icon);
                holder.appLabel = convertView.findViewById(R.id.label);
                holder.packageName = convertView.findViewById(R.id.package_name);
                holder.lastUsageDate = convertView.findViewById(R.id.date);
                holder.mobileDataUsage = convertView.findViewById(R.id.data_usage);
                holder.wifiDataUsage = convertView.findViewById(R.id.wifi_usage);
                holder.screenTime = convertView.findViewById(R.id.screen_time);
                holder.percentUsage = convertView.findViewById(R.id.percent_usage);
                holder.usageIndicator = convertView.findViewById(R.id.progress_linear);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
                if(holder.iconLoader != null) holder.iconLoader.interrupt();
            }
            final AppUsageStatsManager.PackageUS packageUS = mAdapterList.get(position);
            final int percentUsage = (int) (packageUS.screenTime * 100f / totalScreenTime);
            // Set label (or package name on failure)
            try {
                ApplicationInfo applicationInfo = mPackageManager.getApplicationInfo(packageUS.packageName, 0);
                holder.appLabel.setText(mPackageManager.getApplicationLabel(applicationInfo));
                // Set icon
                holder.iconLoader = new IconLoaderThread(holder.appIcon, applicationInfo);
                holder.iconLoader.start();
            } catch (PackageManager.NameNotFoundException e) {
                holder.appLabel.setText(packageUS.packageName);
                holder.appIcon.setImageDrawable(mPackageManager.getDefaultActivityIcon());
            }
            // Set package name
            holder.packageName.setText(packageUS.packageName);
            // Set usage
            long lastTimeUsed = packageUS.lastUsageTime;
            if (lastTimeUsed > 1) {
                holder.lastUsageDate.setText(sSimpleDateFormat.format(new Date(lastTimeUsed)));
            }
            String screenTimesWithTimesOpened;
            // Set times opened
            screenTimesWithTimesOpened = mActivity.getResources().getQuantityString(R.plurals.no_of_times_opened, packageUS.timesOpened, packageUS.timesOpened);
            // Set screen time
            screenTimesWithTimesOpened += ", " + Utils.getFormattedDuration(mActivity, packageUS.screenTime);
            holder.screenTime.setText(screenTimesWithTimesOpened);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Set data usage
                final Tuple<Long, Long> mobileData = packageUS.mobileData;
                if (mobileData.getFirst() != 0 || mobileData.getSecond() != 0) {
                    holder.mobileDataUsage.setText("M: \u2191 " + Formatter.formatFileSize(mActivity, mobileData.getFirst())
                            + " \u2193 " + Formatter.formatFileSize(mActivity, mobileData.getSecond()));
                } else holder.mobileDataUsage.setText("");
                final Tuple<Long, Long> wifiData = packageUS.wifiData;
                if (wifiData.getFirst() != 0 || wifiData.getSecond() != 0) {
                    holder.wifiDataUsage.setText("W: \u2191 " + Formatter.formatFileSize(mActivity, wifiData.getFirst())
                            + " \u2193 " + Formatter.formatFileSize(mActivity, wifiData.getSecond()));
                } else holder.wifiDataUsage.setText("");

            }
            // Set usage percentage
            holder.percentUsage.setText(String.format(Locale.ROOT, "%d%%", percentUsage));
            holder.usageIndicator.setProgress(percentUsage);
            return convertView;
        }
    }
}
