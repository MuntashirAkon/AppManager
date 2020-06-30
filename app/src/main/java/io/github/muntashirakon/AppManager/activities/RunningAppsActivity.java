package io.github.muntashirakon.AppManager.activities;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.jaredrummler.android.shell.CommandResult;
import com.jaredrummler.android.shell.Shell;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.storage.StorageManager;
import io.github.muntashirakon.AppManager.utils.Utils;

public class RunningAppsActivity extends AppCompatActivity implements SearchView.OnQueryTextListener {
    // "^(?<label>[^\\t\\s]+)[\\t\\s]+(?<pid>\\d+)[\\t\\s]+(?<ppid>\\d+)[\\t\\s]+(?<rss>\\d+)[\\t\\s]+(?<vsz>\\d+)[\\t\\s]+(?<user>[^\\t\\s]+)[\\t\\s]+(?<uid>\\d+)[\\t\\s]+(?<state>\\w)(?<stateplus>[\\w\\+<])?[\\t\\s]+(?<name>[^\\t\\s]+)$"
    private static final Pattern PROCESS_MATCHER = Pattern.compile("^([^\\t\\s]+)[\\t\\s]+(\\d+)" +
            "[\\t\\s]+(\\d+)[\\t\\s]+(\\d+)[\\t\\s]+(\\d+)[\\t\\s]+([^\\t\\s]+)[\\t\\s]+(\\d+)" +
            "[\\t\\s]+(\\w)([\\w+<])?[\\t\\s]+([^\\t\\s]+)$");

    private static String mConstraint;

    private RunningAppsAdapter mAdapter;
    private static PackageManager mPackageManager;
    private ProgressBar mProgressBar;

    static class ProcessItem {
        int pid;
        int ppid;
        long rss;
        long vsz;
        String user;
        int uid;
        String state;
        String state_extra;
        String name;
        ApplicationInfo applicationInfo = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_running_apps);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            SearchView searchView = new SearchView(actionBar.getThemedContext());
            searchView.setOnQueryTextListener(this);
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
        mPackageManager = getPackageManager();
        mProgressBar = findViewById(R.id.progress_horizontal);
        ListView mListView = findViewById(android.R.id.list);
        mListView.setTextFilterEnabled(true);
        mListView.setDividerHeight(0);
        mListView.setEmptyView(findViewById(android.R.id.empty));
        mAdapter = new RunningAppsAdapter(this);
        mListView.setAdapter(mAdapter);
        new ProcessRefreshingThread().start();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) finish();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        mConstraint = newText;
        if (mAdapter != null) mAdapter.getFilter().filter(newText);
        return true;
    }

    void refresh() {
        new ProcessRefreshingThread().start();
    }

    @NonNull
    private static ProcessItem parseProcess(String line) throws Exception {
        Matcher matcher = PROCESS_MATCHER.matcher(line);
        if (matcher.find()) {
            ProcessItem processItem = new ProcessItem();
            //noinspection ConstantConditions
            processItem.pid = Integer.parseInt(matcher.group(2));
            //noinspection ConstantConditions
            processItem.ppid = Integer.parseInt(matcher.group(3));
            //noinspection ConstantConditions
            processItem.rss = Integer.parseInt(matcher.group(4));
            //noinspection ConstantConditions
            processItem.vsz = Integer.parseInt(matcher.group(5));
            processItem.user = matcher.group(6);
            //noinspection ConstantConditions
            processItem.uid = Integer.parseInt(matcher.group(7));
            //noinspection ConstantConditions
            processItem.state = Utils.getProcessStateName(matcher.group(8));
            processItem.state_extra = Utils.getProcessStateExtraName(matcher.group(9));
            processItem.name = matcher.group(10);
            return processItem;
        }
        throw new Exception("Failed to parse line");
    }

    static class RunningAppsAdapter extends BaseAdapter implements Filterable {
        private LayoutInflater mLayoutInflater;
        private Filter mFilter;
        private String mConstraint;
        private List<ProcessItem> mDefaultList;
        private List<ProcessItem> mAdapterList;
        private RunningAppsActivity mActivity;

        private int mColorTransparent;
        private int mColorSemiTransparent;
        private int mColorRed;

        RunningAppsAdapter(@NonNull RunningAppsActivity activity) {
            mActivity = activity;
            mLayoutInflater = activity.getLayoutInflater();

            mColorTransparent = Color.TRANSPARENT;
            mColorSemiTransparent = ContextCompat.getColor(activity, R.color.SEMI_TRANSPARENT);
            mColorRed = ContextCompat.getColor(activity, R.color.red);
        }

        void setDefaultList(List<ProcessItem> list) {
            mDefaultList = list;
            mAdapterList = list;
            if(RunningAppsActivity.mConstraint != null
                    && !RunningAppsActivity.mConstraint.equals("")) {
                getFilter().filter(RunningAppsActivity.mConstraint);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mAdapterList == null ? 0 : mAdapterList.size();
        }

        @Override
        public ProcessItem getItem(int position) {
            return mAdapterList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return mDefaultList.indexOf(mAdapterList.get(position));
        }

        static class ViewHolder {
            ImageView icon;
            TextView processName;
            TextView packageName;
            TextView processIds;
            TextView memoryUsage;
            TextView userInfo;
            MaterialButton killBtn;
            MaterialButton forceStopBtn;
            MaterialButton disableBackgroundRunBtn;
            IconAsyncTask iconLoader;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.item_running_app, parent, false);
                holder = new ViewHolder();
                holder.icon = convertView.findViewById(R.id.icon);
                holder.processName = convertView.findViewById(R.id.process_name);
                holder.packageName = convertView.findViewById(R.id.package_name);
                holder.processIds = convertView.findViewById(R.id.process_ids);
                holder.memoryUsage = convertView.findViewById(R.id.memory_usage);
                holder.userInfo = convertView.findViewById(R.id.user_info);
                holder.killBtn = convertView.findViewById(R.id.kill_btn);
                holder.forceStopBtn = convertView.findViewById(R.id.force_stop_btn);
                holder.disableBackgroundRunBtn = convertView.findViewById(R.id.disable_background_run_btn);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
                if (holder.iconLoader != null) holder.iconLoader.cancel(true);
            }
            ProcessItem processItem = mAdapterList.get(position);
            ApplicationInfo applicationInfo = processItem.applicationInfo;
            String processName = processItem.name;
            // Load icon
            holder.iconLoader = new IconAsyncTask(holder.icon, applicationInfo);
            holder.iconLoader.execute();
            // Set process name
            if (mConstraint != null && processName.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.processName.setText(Utils.getHighlightedText(processName, mConstraint, mColorRed));
            } else {
                holder.processName.setText(processName);
            }
            // Set package name
            if (applicationInfo != null) {
                holder.packageName.setVisibility(View.VISIBLE);
                holder.packageName.setText(applicationInfo.packageName);
            } else holder.packageName.setVisibility(View.GONE);
            // Set process IDs
            holder.processIds.setText(String.format(mActivity.getString(R.string.pid_and_ppid), processItem.pid, processItem.ppid));
            // Set memory usage
            holder.memoryUsage.setText(String.format(mActivity.getString(R.string.memory_virtual_memory), Formatter.formatFileSize(mActivity, processItem.rss), Formatter.formatFileSize(mActivity, processItem.vsz)));
            // Set user info
            holder.userInfo.setText(String.format(mActivity.getString(R.string.user_and_uid), processItem.user, processItem.uid));
            // Buttons
            if (applicationInfo != null) {
                holder.forceStopBtn.setVisibility(View.VISIBLE);
                holder.forceStopBtn.setOnClickListener(v -> {
                    if (Shell.SU.run(String.format("am force-stop %s", applicationInfo.packageName)).isSuccessful()) {
                        mActivity.refresh();
                    } else {
                        Toast.makeText(mActivity, String.format(mActivity.getString(R.string.failed_to_stop), processName), Toast.LENGTH_LONG).show();
                    }
                });
                int mode = AppOpsManager.MODE_DEFAULT;
                try {
                    mode = new AppOpsService().checkOperation(AppOpsManager.OP_RUN_IN_BACKGROUND, applicationInfo.uid, applicationInfo.packageName);
                } catch (Exception ignore) {}
                if (mode != AppOpsManager.MODE_IGNORED) {
                    holder.disableBackgroundRunBtn.setVisibility(View.VISIBLE);
                    holder.disableBackgroundRunBtn.setOnClickListener(v -> {
                        try {
                            new AppOpsService().setMode(AppOpsManager.OP_RUN_IN_BACKGROUND, applicationInfo.uid, applicationInfo.packageName, AppOpsManager.MODE_IGNORED);
                            StorageManager.getInstance(mActivity, applicationInfo.packageName).setAppOp(String.valueOf(AppOpsManager.OP_RUN_IN_BACKGROUND), AppOpsManager.MODE_IGNORED);
                            mActivity.refresh();
                        } catch (Exception e) {
                            Toast.makeText(mActivity, mActivity.getString(R.string.failed_to_disable_op), Toast.LENGTH_LONG).show();
                        }
                    });
                } else holder.disableBackgroundRunBtn.setVisibility(View.GONE);
            } else {
                holder.forceStopBtn.setVisibility(View.GONE);
                holder.disableBackgroundRunBtn.setVisibility(View.GONE);
            }
            holder.killBtn.setOnClickListener(v -> {
                if (Shell.SU.run(String.format(Locale.ROOT, "kill -9 %d", processItem.pid)).isSuccessful()) {
                    mActivity.refresh();
                } else {
                    Toast.makeText(mActivity, String.format(mActivity.getString(R.string.failed_to_stop), processName), Toast.LENGTH_LONG).show();
                }
            });
            convertView.setBackgroundColor(position % 2 == 0 ? mColorSemiTransparent : mColorTransparent);
            return convertView;
        }

        @Override
        public Filter getFilter() {
            if (mFilter == null)
                mFilter = new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence charSequence) {
                        String constraint = charSequence.toString().toLowerCase(Locale.ROOT);
                        mConstraint = constraint;
                        FilterResults filterResults = new FilterResults();
                        if (constraint.length() == 0) {
                            filterResults.count = 0;
                            filterResults.values = null;
                            return filterResults;
                        }

                        List<ProcessItem> list = new ArrayList<>(mDefaultList.size());
                        for (ProcessItem item : mDefaultList) {
                            if (item.name.toLowerCase(Locale.ROOT).contains(constraint))
                                list.add(item);
                        }

                        filterResults.count = list.size();
                        filterResults.values = list;
                        return filterResults;
                    }

                    @Override
                    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                        if (filterResults.values == null) {
                            mAdapterList = mDefaultList;
                        } else {
                            //noinspection unchecked
                            mAdapterList = (List<ProcessItem>) filterResults.values;
                        }
                        notifyDataSetChanged();
                    }
                };
            return mFilter;
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
                if (imageView.get()!=null)
                    imageView.get().setVisibility(View.INVISIBLE);
            }

            @Override
            protected Drawable doInBackground(Void... voids) {
                if (!isCancelled()) {
                    if (info != null)
                        return info.loadIcon(mPackageManager);
                    else return mPackageManager.getDefaultActivityIcon();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Drawable drawable) {
                super.onPostExecute(drawable);
                if (imageView.get()!=null){
                    imageView.get().setImageDrawable(drawable);
                    imageView.get().setVisibility(View.VISIBLE);

                }
            }
        }
    }

    class ProcessRefreshingThread extends Thread {
        @Override
        public void run() {
            List<ApplicationInfo> applicationInfoList = mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA);
            CommandResult result = Shell.SU.run("ps -dwZ -o PID,PPID,RSS,VSZ,USER,UID,STAT,NAME | grep -v :kernel:");
            if (result.isSuccessful()) {
                List<String> processInfoLines = result.stdout;
                HashMap<String, ProcessItem> processList = new HashMap<>();
                for (int i = 1; i<processInfoLines.size(); ++i) {
                    try {
                        ProcessItem processItem = parseProcess(processInfoLines.get(i));
                        processList.put(processItem.name, processItem);
                    } catch (Exception ignore) {}
                }
                processList.remove("ps");  // Remove the `ps` command from the list
                Set<String> processNames = processList.keySet();
                for (ApplicationInfo applicationInfo : applicationInfoList) {
                    if (processNames.contains(applicationInfo.packageName)) {
                        //noinspection ConstantConditions
                        processList.get(applicationInfo.packageName).applicationInfo = applicationInfo;
                        //noinspection ConstantConditions
                        processList.get(applicationInfo.packageName).name = applicationInfo.loadLabel(mPackageManager).toString();
                    }
                }
                List<ProcessItem> processItemList = new ArrayList<>(processList.values());
                runOnUiThread(() -> {
                    mAdapter.setDefaultList(processItemList);
                    mProgressBar.setVisibility(View.GONE);
                });
            }
        }
    }
}
