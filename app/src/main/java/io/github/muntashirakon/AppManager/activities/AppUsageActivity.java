package io.github.muntashirakon.AppManager.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
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

import static io.github.muntashirakon.AppManager.usage.Utils.USAGE_MONTHLY;
import static io.github.muntashirakon.AppManager.usage.Utils.USAGE_WEEKLY;
import static io.github.muntashirakon.AppManager.usage.Utils.USAGE_YEARLY;
import static io.github.muntashirakon.AppManager.usage.Utils.USAGE_TODAY;
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
            // FIXME
//            Tuple<Long, Long> interval = io.github.muntashirakon.AppManager.usage.Utils.getTimeInterval(current_interval);
//            UsageStatsManager manager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
//            UsageEvents usageEvents = manager.queryEvents(interval.getFirst(), interval.getSecond());
//            UsageEvents.Event event = new UsageEvents.Event();
//            while (usageEvents.hasNextEvent()) {
//                usageEvents.getNextEvent(event);
//                Log.d("TestAUA", "Event: " + event.getPackageName() + "\t" + event.getTimeStamp() + " (" + event.getEventType() + ")");
//            }
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
            case USAGE_MONTHLY:
                timeRange.setText(R.string.usage_30_days);
                break;
            case USAGE_YEARLY:
                timeRange.setText(R.string.usage_365_days);
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
        static DateFormat sSimpleDateFormat = new SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.getDefault());

        private LayoutInflater mLayoutInflater;
        private List<AppUsageStatsManager.PackageUS> mAdapterList;
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

            AppUsageStatsManager.PackageUS appItem = mAdapterList.get(position);
            // Set label (or package name on failure)
            try {
                ApplicationInfo applicationInfo = mPackageManager.getApplicationInfo(appItem.packageName, 0);
                holder.label.setText(mPackageManager.getApplicationLabel(applicationInfo));
                // Set icon
                holder.iconLoader = new IconAsyncTask(holder.icon, applicationInfo);
                holder.iconLoader.execute();
            } catch (PackageManager.NameNotFoundException e) {
                holder.label.setText(appItem.packageName);
                holder.icon.setImageDrawable(mPackageManager.getDefaultActivityIcon());
            }
            // Set usage
            long lastTimeUsed = appItem.lastUsageTime;
            String string;
            string = formattedTime(mActivity, appItem.screenTime);
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
