package io.github.muntashirakon.AppManager.activities;

import android.annotation.SuppressLint;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.ProgressIndicator;

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
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.storage.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.types.IconLoaderThread;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.Utils;

public class RunningAppsActivity extends AppCompatActivity implements SearchView.OnQueryTextListener {
    // "^(?<label>[^\\t\\s]+)[\\t\\s]+(?<pid>\\d+)[\\t\\s]+(?<ppid>\\d+)[\\t\\s]+(?<rss>\\d+)[\\t\\s]+(?<vsz>\\d+)[\\t\\s]+(?<user>[^\\t\\s]+)[\\t\\s]+(?<uid>\\d+)[\\t\\s]+(?<state>\\w)(?<stateplus>[\\w\\+<])?[\\t\\s]+(?<name>[^\\t\\s]+)$"
    private static final Pattern PROCESS_MATCHER = Pattern.compile("^([^\\t\\s]+)[\\t\\s]+(\\d+)" +
            "[\\t\\s]+(\\d+)[\\t\\s]+(\\d+)[\\t\\s]+(\\d+)[\\t\\s]+([^\\t\\s]+)[\\t\\s]+(\\d+)" +
            "[\\t\\s]+(\\w)([\\w+<])?[\\t\\s]+([^\\t\\s]+)$");

    private static String mConstraint;

    private RunningAppsAdapter mAdapter;
    private static PackageManager mPackageManager;
    private ProgressIndicator mProgressIndicator;
    private static boolean enableKillForSystem = false;

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
        setSupportActionBar(findViewById(R.id.toolbar));
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
        mProgressIndicator = findViewById(R.id.progress_linear);
        ListView mListView = findViewById(android.R.id.list);
        mListView.setTextFilterEnabled(true);
        mListView.setDividerHeight(0);
        mListView.setEmptyView(findViewById(android.R.id.empty));
        mAdapter = new RunningAppsAdapter(this);
        mListView.setAdapter(mAdapter);
        mConstraint = null;
        enableKillForSystem = (boolean) AppPref.get(AppPref.PrefKey.PREF_ENABLE_KILL_FOR_SYSTEM_BOOL);
        refresh();
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_running_apps_actions, menu);
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_toggle_kill:
                enableKillForSystem = !enableKillForSystem;
                AppPref.getInstance().setPref(AppPref.PrefKey.PREF_ENABLE_KILL_FOR_SYSTEM_BOOL, enableKillForSystem);
                refresh();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        mPackageManager = null;
        super.onDestroy();
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
        private boolean isAdbMode = false;

        private int mColorTransparent;
        private int mColorSemiTransparent;
        private int mColorRed;

        RunningAppsAdapter(@NonNull RunningAppsActivity activity) {
            mActivity = activity;
            mLayoutInflater = activity.getLayoutInflater();

            mColorTransparent = Color.TRANSPARENT;
            mColorSemiTransparent = ContextCompat.getColor(activity, R.color.semi_transparent);
            mColorRed = ContextCompat.getColor(activity, R.color.red);
        }

        void setDefaultList(List<ProcessItem> list) {
            mDefaultList = list;
            mAdapterList = list;
            isAdbMode = AppPref.isAdbEnabled();
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
            IconLoaderThread iconLoader;
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
                if (holder.iconLoader != null) holder.iconLoader.interrupt();
            }
            ProcessItem processItem = mAdapterList.get(position);
            ApplicationInfo applicationInfo = processItem.applicationInfo;
            String processName = processItem.name;
            // Load icon
            holder.iconLoader = new IconLoaderThread(holder.icon, applicationInfo);
            holder.iconLoader.start();
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
            holder.processIds.setText(mActivity.getString(R.string.pid_and_ppid, processItem.pid, processItem.ppid));
            // Set memory usage
            holder.memoryUsage.setText(mActivity.getString(R.string.memory_virtual_memory, Formatter.formatFileSize(mActivity, processItem.rss), Formatter.formatFileSize(mActivity, processItem.vsz)));
            // Set user info
            holder.userInfo.setText(mActivity.getString(R.string.user_and_uid, processItem.user, processItem.uid));
            // Buttons
            if (applicationInfo != null) {
                holder.forceStopBtn.setVisibility(View.VISIBLE);
                holder.forceStopBtn.setOnClickListener(v -> new Thread(() -> {
                    if (Runner.runCommand(String.format("am force-stop %s", applicationInfo.packageName)).isSuccessful()) {
                        mActivity.runOnUiThread(() -> mActivity.refresh());
                    } else {
                        mActivity.runOnUiThread(() -> Toast.makeText(mActivity, mActivity.getString(R.string.failed_to_stop, processName), Toast.LENGTH_LONG).show());
                    }
                }).start());
                new Thread(() -> {
                    String mode = AppOpsManager.modeToName(AppOpsManager.MODE_DEFAULT);
                    try {
                        mode = new AppOpsService(mActivity).checkOperation(AppOpsManager.OP_RUN_IN_BACKGROUND, applicationInfo.uid, applicationInfo.packageName);
                    } catch (Exception ignore) {}
                    String finalMode = mode;
                    mActivity.runOnUiThread(() -> {
                        if (!finalMode.equals(AppOpsManager.modeToName(AppOpsManager.MODE_IGNORED))) {
                            holder.disableBackgroundRunBtn.setVisibility(View.VISIBLE);
                            holder.disableBackgroundRunBtn.setOnClickListener(v -> new Thread(() -> {
                                try {
                                    new AppOpsService(mActivity).setMode(AppOpsManager.OP_RUN_IN_BACKGROUND,
                                            applicationInfo.uid, applicationInfo.packageName, AppOpsManager.MODE_IGNORED);
                                    try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(mActivity, applicationInfo.packageName)) {
                                        cb.setAppOp(String.valueOf(AppOpsManager.OP_RUN_IN_BACKGROUND), AppOpsManager.MODE_IGNORED);
                                    }
                                    mActivity.runOnUiThread(() -> mActivity.refresh());
                                } catch (Exception e) {
                                    mActivity.runOnUiThread(() -> Toast.makeText(mActivity, mActivity.getString(R.string.failed_to_disable_op), Toast.LENGTH_LONG).show());
                                }
                            }).start());
                        } else holder.disableBackgroundRunBtn.setVisibility(View.GONE);
                    });
                }).start();
            } else {
                holder.forceStopBtn.setVisibility(View.GONE);
                holder.disableBackgroundRunBtn.setVisibility(View.GONE);
            }
            if ((processItem.pid >= 10000 || enableKillForSystem) && !isAdbMode) {
                holder.killBtn.setVisibility(View.VISIBLE);
                holder.killBtn.setOnClickListener(v -> new Thread(() -> {
                    if (Runner.runCommand(String.format(Locale.ROOT, "kill -9 %d", processItem.pid)).isSuccessful()) {
                        mActivity.runOnUiThread(() -> mActivity.refresh());
                    } else {
                        mActivity.runOnUiThread(() -> Toast.makeText(mActivity, mActivity.getString(R.string.failed_to_stop, processName), Toast.LENGTH_LONG).show());
                    }
                }).start());
            } else holder.killBtn.setVisibility(View.GONE);
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
    }

    class ProcessRefreshingThread extends Thread {
        @Override
        public void run() {
            List<ApplicationInfo> applicationInfoList = mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA);
            Runner.runCommand("ps -dwZ -o PID,PPID,RSS,VSZ,USER,UID,STAT,NAME | grep -v :kernel:");
            if (Runner.getLastResult().isSuccessful()) {
                List<String> processInfoLines = Runner.getLastResult().getOutputAsList(1);
                HashMap<String, ProcessItem> processList = new HashMap<>();
                for (String processInfoLine: processInfoLines) {
                    try {
                        ProcessItem processItem = parseProcess(processInfoLine);
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
                    mProgressIndicator.hide();
                });
            }
        }
    }
}
