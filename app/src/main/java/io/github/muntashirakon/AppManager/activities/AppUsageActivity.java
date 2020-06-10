package io.github.muntashirakon.AppManager.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
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
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import io.github.muntashirakon.AppManager.R;

public class AppUsageActivity extends AppCompatActivity {
    private static final String SYS_USAGE_STATS_SERVICE = "usagestats";

    // These constants must be aligned with app_usage_dropdown_list array
    // Same as UsageStatsManager.INTERVAL_*
    private static final int USAGE_DAILY = 0;
    private static final int USAGE_WEEKLY = 1;
    private static final int USAGE_MONTHLY = 2;
    private static final int USAGE_YEARLY = 3;

    private UsageStatsManager mUsageStatsManager;
    private AppUsageAdapter mAppUsageAdapter;
    private int totalTimeInMs;
    String[] app_usage_strings;
    private int current_interval = USAGE_DAILY;

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
        mUsageStatsManager = (UsageStatsManager) getSystemService(SYS_USAGE_STATS_SERVICE);
        mAppUsageAdapter = new AppUsageAdapter(this);

        ListView listView = findViewById(android.R.id.list);
        listView.setDividerHeight(0);
        listView.setAdapter(mAppUsageAdapter);

        @SuppressLint("InflateParams")
        View header = getLayoutInflater().inflate(R.layout.header_app_usage, null);
        listView.addHeaderView(header);

//        Spinner usageSpinner = findViewById(R.id.spinner_usage);
//        SpinnerAdapter usageSpinnerAdapter = ArrayAdapter.createFromResource(this,
//                R.array.usage_types, android.R.layout.simple_spinner_dropdown_item);
//        usageSpinner.setAdapter(usageSpinnerAdapter);
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
        if (!checkUsageStatsPermission()) promptForUsageStatsPermission();
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
        Calendar cal = Calendar.getInstance();
        switch (current_interval) {
            case USAGE_DAILY:
                cal.add(Calendar.HOUR_OF_DAY, -cal.get(Calendar.HOUR_OF_DAY));
                break;
            case USAGE_WEEKLY:
                cal.add(Calendar.DAY_OF_YEAR, -7);
                break;
            case USAGE_MONTHLY:
                cal.add(Calendar.MONTH, -1);
                break;
            case USAGE_YEARLY:
                cal.add(Calendar.YEAR, -1);
                break;
        }

        int _try = 5; // try to get usage stat 5 times
        List<UsageStats> usageStatsList;
        do {
            usageStatsList = mUsageStatsManager.queryUsageStats(current_interval,
                    cal.getTimeInMillis(), System.currentTimeMillis());

            // FIXME
//            UsageEvents usageEvents = mUsageStatsManager.queryEvents(cal.getTimeInMillis(), System.currentTimeMillis());
//            while (usageEvents.hasNextEvent()) {
//                UsageEvents.Event event = new UsageEvents.Event();
//                usageEvents.getNextEvent(event);
//                Log.d(TAG, "Event: " + event.getPackageName() + "\t" + event.getTimeStamp() + " (" + event.getEventType() + ")");
//            }
        } while (0 != --_try && usageStatsList.size() == 0);

        // Filter unused apps
        totalTimeInMs = 0;
        for (int i = usageStatsList.size() - 1; i >= 0; i--) {
            UsageStats usageStats = usageStatsList.get(i);
            totalTimeInMs += usageStats.getTotalTimeInForeground();
            if (usageStats.getTotalTimeInForeground() <= 0)
                usageStatsList.remove(i);
        }

        Collections.sort(usageStatsList, new TimeInForegroundComparatorDesc());
        mAppUsageAdapter.setDefaultList(usageStatsList);
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

    private boolean checkUsageStatsPermission() {
        AppOpsManager appOpsManager = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        assert appOpsManager != null;
        final int mode;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            mode = appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName());
        } else {
            mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName());
        }
        if (mode == AppOpsManager.MODE_DEFAULT
                && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void setUsageSummary() {
        TextView timeUsed = findViewById(R.id.time_used);
        TextView timeRange = findViewById(R.id.time_range);
        timeUsed.setText(formattedTime(this, totalTimeInMs));
        switch (current_interval) {
            case USAGE_DAILY:
                timeRange.setText(R.string.usage_today);
                break;
            case USAGE_WEEKLY:
                timeRange.setText(R.string.usage_7_days);
                break;
            case USAGE_MONTHLY:
                timeRange.setText(R.string.usage_30_days);
                break;
            case USAGE_YEARLY:
                timeRange.setText(R.string.usage_365_days);
                break;
        }
    }

    private static String formattedTime(Activity activity, long time) {
        time /= 60000; // minutes
        long month, day, hour, min;
        month = time / 43200; time %= 43200;
        day = time / 1440; time %= 1440;
        hour = time / 60;
        min = time % 60;
        String fTime = "";
        int count = 0;
        if (month != 0){
            fTime += String.format(activity.getString(month > 0 ? R.string.usage_months : R.string.usage_month), month);
            ++count;
        }
        if (day != 0) {
            fTime += (count > 0 ? " " : "") + String.format(activity.getString(
                    day > 1 ? R.string.usage_days : R.string.usage_day), day);
            ++count;
        }
        if (hour != 0) {
            fTime += (count > 0 ? " " : "") + String.format(activity.getString(R.string.usage_hour), hour);
            ++count;
        }
        if (min != 0) {
            fTime += (count > 0 ? " " : "") + String.format(activity.getString(R.string.usage_min), min);
        } else {
            if (count == 0) fTime = activity.getString(R.string.usage_less_than_a_minute);
        }
        return fTime;
    }

    private static class TimeInForegroundComparatorDesc implements Comparator<UsageStats> {

        @Override
        public int compare(UsageStats left, UsageStats right) {
            return Long.compare(right.getTotalTimeInForeground(), left.getTotalTimeInForeground());
        }
    }

    static class AppUsageAdapter extends BaseAdapter {
        static DateFormat sSimpleDateFormat = new SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.getDefault());

        private LayoutInflater mLayoutInflater;
        private List<UsageStats> mAdapterList;
        private static PackageManager mPackageManager;
        private Activity mActivity;

        static class ViewHolder {
            ImageView icon;
            TextView label;
            TextView usage;
            IconAsyncTask iconLoader;
        }

        AppUsageAdapter(@NonNull Activity activity) {
            mLayoutInflater = activity.getLayoutInflater();
            mPackageManager = activity.getPackageManager();
            mActivity = activity;
        }

        void setDefaultList(List<UsageStats> list) {
//            mDefaultList = list;
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

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.item_icon_title_subtitle, parent, false);
                holder = new ViewHolder();
                holder.icon = convertView.findViewById(R.id.item_icon);
                holder.label = convertView.findViewById(R.id.item_title);
                holder.usage = convertView.findViewById(R.id.item_subtitle);
                convertView.findViewById(R.id.item_open).setVisibility(View.GONE);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
                if(holder.iconLoader != null) holder.iconLoader.cancel(true);
            }

            UsageStats usageStats = mAdapterList.get(position);
            // Set label (or package name on failure)
            try {
                ApplicationInfo applicationInfo = mPackageManager.getApplicationInfo(usageStats.getPackageName(), 0);
                holder.label.setText(mPackageManager.getApplicationLabel(applicationInfo));
                // Set icon
                holder.iconLoader = new IconAsyncTask(holder.icon, applicationInfo);
                holder.iconLoader.execute();
            } catch (PackageManager.NameNotFoundException e) {
                holder.label.setText(usageStats.getPackageName());
                holder.icon.setImageDrawable(mPackageManager.getDefaultActivityIcon());
            }
            // Set usage
            long lastTimeUsed = usageStats.getLastTimeUsed();
            String string;
            string = formattedTime(mActivity, usageStats.getTotalTimeInForeground());
            if (lastTimeUsed > 1)
                string += ", " + mActivity.getString(R.string.usage_last_used)
                        + " " + sSimpleDateFormat.format(new Date(lastTimeUsed));

            holder.usage.setText(string);
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
