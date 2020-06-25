package io.github.muntashirakon.AppManager.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.LayoutInflater;
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

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.usage.Utils.IntervalType;
import io.github.muntashirakon.AppManager.utils.Utils;

import static io.github.muntashirakon.AppManager.usage.Utils.USAGE_LAST_BOOT;
import static io.github.muntashirakon.AppManager.usage.Utils.USAGE_TODAY;
import static io.github.muntashirakon.AppManager.usage.Utils.USAGE_WEEKLY;
import static io.github.muntashirakon.AppManager.usage.Utils.USAGE_YESTERDAY;

public class AppUsageActivity extends AppCompatActivity {
    private AppUsageAdapter mAppUsageAdapter;
    private long totalTimeInMs;
    String[] app_usage_strings;
    private @IntervalType int current_interval = USAGE_TODAY;

    private static final int REQUEST_SETTINGS = 0;

    @SuppressLint("WrongConstant")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_usage);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(getString(R.string.app_usage));
        }

        app_usage_strings = getResources().getStringArray(R.array.usage_interval_dropdown_list);

        // Get usage stats
        mAppUsageAdapter = new AppUsageAdapter(this);
        ListView listView = findViewById(android.R.id.list);
        listView.setDividerHeight(0);
        listView.setEmptyView(findViewById(android.R.id.empty));
        listView.setAdapter(mAppUsageAdapter);

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
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check permission
        if (!Utils.checkUsageStatsPermission(this)) promptForUsageStatsPermission();
        else getAppUsage();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private void getAppUsage() {
        int _try = 5; // try to get usage stat 5 times
        List<AppUsageStatsManager.PackageUS> usageStatsList;
        do {
            usageStatsList = AppUsageStatsManager.getInstance(this).getUsageStats(0, current_interval);
        } while (0 != --_try && usageStatsList.size() == 0);
        mAppUsageAdapter.setDefaultList(usageStatsList);
        totalTimeInMs = 0;
        for(AppUsageStatsManager.PackageUS appItem: usageStatsList) totalTimeInMs += appItem.screenTime;
        setUsageSummary();
    }

    private void promptForUsageStatsPermission() {
        new AlertDialog.Builder(this, R.style.CustomDialog)
                .setTitle(R.string.grant_usage_access)
                .setMessage(R.string.grant_usage_acess_message)
                .setPositiveButton(R.string.go, (dialog, which) -> startActivityForResult(new Intent(
                        Settings.ACTION_USAGE_ACCESS_SETTINGS), REQUEST_SETTINGS))
                .setNegativeButton(getString(R.string.go_back), (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void setUsageSummary() {
        TextView timeUsed = findViewById(R.id.time_used);
        TextView timeRange = findViewById(R.id.time_range);
        timeUsed.setText(formattedTime(this, totalTimeInMs));
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

    public static String formattedTime(Context context, long time) {
        time /= 60000; // minutes
        long month, day, hour, min;
        month = time / 43200; time %= 43200;
        day = time / 1440; time %= 1440;
        hour = time / 60;
        min = time % 60;
        String fTime = "";
        int count = 0;
        if (month != 0){
            fTime += String.format(context.getString(month > 0 ? R.string.usage_months : R.string.usage_month), month);
            ++count;
        }
        if (day != 0) {
            fTime += (count > 0 ? " " : "") + String.format(context.getString(
                    day > 1 ? R.string.usage_days : R.string.usage_day), day);
            ++count;
        }
        if (hour != 0) {
            fTime += (count > 0 ? " " : "") + String.format(context.getString(R.string.usage_hour), hour);
            ++count;
        }
        if (min != 0) {
            fTime += (count > 0 ? " " : "") + String.format(context.getString(R.string.usage_min), min);
        } else {
            if (count == 0) fTime = context.getString(R.string.usage_less_than_a_minute);
        }
        return fTime;
    }

    static class AppUsageAdapter extends BaseAdapter {
        static DateFormat sSimpleDateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());

        private LayoutInflater mLayoutInflater;
        private List<AppUsageStatsManager.PackageUS> mAdapterList;
        private static PackageManager mPackageManager;
        private Activity mActivity;

        static class ViewHolder {
            ImageView appIcon;
            TextView appLabel;
            TextView packageName;
            TextView lastUsageDate;
            TextView timesOpened;
            TextView mobileDataUsage;
            TextView wifiDataUsage;
            TextView screenTime;
            TextView notificationCount;
            IconAsyncTask iconLoader;
        }

        AppUsageAdapter(@NonNull Activity activity) {
            mLayoutInflater = activity.getLayoutInflater();
            mPackageManager = activity.getPackageManager();
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
        public Object getItem(int position) {
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
                holder.timesOpened = convertView.findViewById(R.id.times_opened);
                holder.mobileDataUsage = convertView.findViewById(R.id.data_usage);
                holder.wifiDataUsage = convertView.findViewById(R.id.wifi_usage);
                holder.screenTime = convertView.findViewById(R.id.screen_time);
                holder.notificationCount = convertView.findViewById(R.id.notification_count);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
                if(holder.iconLoader != null) holder.iconLoader.cancel(true);
            }
            AppUsageStatsManager.PackageUS packageUS = mAdapterList.get(position);
            // Set label (or package name on failure)
            try {
                ApplicationInfo applicationInfo = mPackageManager.getApplicationInfo(packageUS.packageName, 0);
                holder.appLabel.setText(mPackageManager.getApplicationLabel(applicationInfo));
                // Set icon
                holder.iconLoader = new IconAsyncTask(holder.appIcon, applicationInfo);
                holder.iconLoader.execute();
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
            // Set times opened
            holder.timesOpened.setText(String.format(packageUS.timesOpened == 1 ?
                    mActivity.getString(R.string.one_time_opened)
                    : mActivity.getString(R.string.no_of_times_opened), packageUS.timesOpened));
            // Set screen time
            holder.screenTime.setText(formattedTime(mActivity, packageUS.screenTime));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Set data usage
                holder.mobileDataUsage.setText("M: \u2191 " + Formatter.formatFileSize(mActivity, packageUS.mobileData.getFirst())
                            + " \u2193 " + Formatter.formatFileSize(mActivity, packageUS.mobileData.getSecond()));
                holder.wifiDataUsage.setText("W: \u2191 " + Formatter.formatFileSize(mActivity, packageUS.wifiData.getFirst())
                        + " \u2193 " + Formatter.formatFileSize(mActivity, packageUS.wifiData.getSecond()));
            }
            // Set notification count
//            holder.notificationCount.setText(String.format(packageUS.notificationReceived == 1 ?
//                    mActivity.getString(R.string.one_notification_received)
//                    : mActivity.getString(R.string.no_of_notification_received),
//                    packageUS.notificationReceived));
            return convertView;
        }

        private static class IconAsyncTask extends AsyncTask<Void, Integer, Drawable> {
            private WeakReference<ImageView> imageView = null;
            ApplicationInfo info;

            private IconAsyncTask(ImageView pImageViewWeakReference, ApplicationInfo info) {
                link(pImageViewWeakReference);
                this.info = info;
            }

            private void link(ImageView pImageViewWeakReference) {
                imageView = new WeakReference<>(pImageViewWeakReference);
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                if (imageView.get() != null)
                    imageView.get().setVisibility(View.INVISIBLE);
            }

            @Override
            protected Drawable doInBackground(Void... voids) {
                if (!isCancelled())
                    return info.loadIcon(mPackageManager);
                return null;
            }

            @Override
            protected void onPostExecute(Drawable drawable) {
                super.onPostExecute(drawable);
                if (imageView.get() != null){
                    imageView.get().setImageDrawable(drawable);
                    imageView.get().setVisibility(View.VISIBLE);

                }
            }
        }
    }
}
