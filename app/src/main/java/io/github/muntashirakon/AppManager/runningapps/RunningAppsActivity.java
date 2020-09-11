/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.runningapps;

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

import com.google.android.material.progressindicator.ProgressIndicator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.types.IconLoaderThread;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.Utils;

public class RunningAppsActivity extends BaseActivity implements
        SearchView.OnQueryTextListener, SwipeRefreshLayout.OnRefreshListener {
    // "^(?<label>[^\\t\\s]+)[\\t\\s]+(?<pid>\\d+)[\\t\\s]+(?<ppid>\\d+)[\\t\\s]+(?<rss>\\d+)[\\t\\s]+(?<vsz>\\d+)[\\t\\s]+(?<user>[^\\t\\s]+)[\\t\\s]+(?<uid>\\d+)[\\t\\s]+(?<state>\\w)(?<stateplus>[\\w\\+<])?[\\t\\s]+(?<name>[^\\t\\s]+)$"
    private static final Pattern PROCESS_MATCHER = Pattern.compile("^([^\\t\\s]+)[\\t\\s]+(\\d+)" +
            "[\\t\\s]+(\\d+)[\\t\\s]+(\\d+)[\\t\\s]+(\\d+)[\\t\\s]+([^\\t\\s]+)[\\t\\s]+(\\d+)" +
            "[\\t\\s]+(\\w)([\\w+<])?[\\t\\s]+([^\\t\\s]+)$");

    private static String mConstraint;

    private RunningAppsAdapter mAdapter;
    private static PackageManager mPackageManager;
    private ProgressIndicator mProgressIndicator;
    private SwipeRefreshLayout mSwipeRefresh;
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
        mSwipeRefresh = findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setOnRefreshListener(this);
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
    public void onRefresh() {
        mSwipeRefresh.setRefreshing(false);
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
        if (mAdapter != null) mAdapter.getFilter().filter(newText.toLowerCase(Locale.ROOT));
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
            if (RunningAppsActivity.mConstraint != null
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
            ImageView more;
            TextView processName;
            TextView packageName;
            TextView processIds;
            TextView memoryUsage;
            TextView userInfo;
            IconLoaderThread iconLoader;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.item_running_app, parent, false);
                holder = new ViewHolder();
                holder.icon = convertView.findViewById(R.id.icon);
                holder.more = convertView.findViewById(R.id.more);
                holder.processName = convertView.findViewById(R.id.process_name);
                holder.packageName = convertView.findViewById(R.id.package_name);
                holder.processIds = convertView.findViewById(R.id.process_ids);
                holder.memoryUsage = convertView.findViewById(R.id.memory_usage);
                holder.userInfo = convertView.findViewById(R.id.user_info);
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
            // Set more
            holder.more.setOnClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(mActivity, holder.more);
                popupMenu.inflate(R.menu.activity_running_apps_popup_actions);
                Menu menu = popupMenu.getMenu();
                // Set kill
                MenuItem killItem = menu.findItem(R.id.action_kill);
                if ((processItem.pid >= 10000 || enableKillForSystem) && !isAdbMode) {
                    killItem.setVisible(true).setOnMenuItemClickListener(item -> {
                        new Thread(() -> {
                            if (Runner.runCommand(new String[]{Runner.TOYBOX, "kill", "-9", String.valueOf(processItem.pid)}).isSuccessful()) {
                                mActivity.runOnUiThread(() -> mActivity.refresh());
                            } else {
                                mActivity.runOnUiThread(() -> Toast.makeText(mActivity, mActivity.getString(R.string.failed_to_stop, processName), Toast.LENGTH_LONG).show());
                            }
                        }).start();
                        return true;
                    });
                } else killItem.setVisible(false);
                // Set others
                MenuItem forceStopItem = menu.findItem(R.id.action_force_stop);
                MenuItem bgItem = menu.findItem(R.id.action_disable_background);
                if (applicationInfo != null) {
                    forceStopItem.setVisible(true).setOnMenuItemClickListener(item -> {
                        new Thread(() -> {
                            if (RunnerUtils.forceStopPackage(applicationInfo.packageName, Users.getUserHandle(applicationInfo.uid)).isSuccessful()) {
                                mActivity.runOnUiThread(() -> mActivity.refresh());
                            } else {
                                mActivity.runOnUiThread(() -> Toast.makeText(mActivity, mActivity.getString(R.string.failed_to_stop, processName), Toast.LENGTH_LONG).show());
                            }
                        }).start();
                        return true;
                    });
                    new Thread(() -> {
                        final AtomicInteger mode = new AtomicInteger(AppOpsManager.MODE_DEFAULT);
                        try {
                            mode.set(new AppOpsService().checkOperation(AppOpsManager.OP_RUN_IN_BACKGROUND, applicationInfo.uid, applicationInfo.packageName));
                        } catch (Exception ignore) {
                        }
                        mActivity.runOnUiThread(() -> {
                            if (mode.get() != AppOpsManager.MODE_IGNORED) {
                                bgItem.setVisible(true).setOnMenuItemClickListener(item -> {
                                    new Thread(() -> {
                                        try {
                                            new AppOpsService().setMode(AppOpsManager.OP_RUN_IN_BACKGROUND,
                                                    applicationInfo.uid, applicationInfo.packageName, AppOpsManager.MODE_IGNORED);
                                            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(applicationInfo.packageName)) {
                                                cb.setAppOp(String.valueOf(AppOpsManager.OP_RUN_IN_BACKGROUND), AppOpsManager.MODE_IGNORED);
                                            }
                                            mActivity.runOnUiThread(() -> mActivity.refresh());
                                        } catch (Exception e) {
                                            mActivity.runOnUiThread(() -> Toast.makeText(mActivity, mActivity.getString(R.string.failed_to_disable_op), Toast.LENGTH_LONG).show());
                                        }
                                    }).start();
                                    return true;
                                });
                            } else bgItem.setVisible(false);
                            // Display popup menu
                            popupMenu.show();
                        });
                    }).start();
                } else {
                    forceStopItem.setVisible(false);
                    bgItem.setVisible(false);
                    // Show popup menu without hesitation
                    popupMenu.show();
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
    }

    class ProcessRefreshingThread extends Thread {
        @Override
        public void run() {
            List<ApplicationInfo> applicationInfoList = mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA);
            Runner.Result result = Runner.runCommand(new String[]{Runner.TOYBOX, "ps", "-dwZ", "-o", "PID,PPID,RSS,VSZ,USER,UID,STAT,NAME"});
            if (result.isSuccessful()) {
                List<String> processInfoLines = result.getOutputAsList(1);
                // FIXME(10/9/20): Process name cannot be a primary key since there can be duplicate
                //  processes. Use process id instead
                HashMap<String, ProcessItem> processList = new HashMap<>();
                for (String processInfoLine : processInfoLines) {
                    if (processInfoLine.contains(":kernel:")) continue;
                    try {
                        ProcessItem processItem = parseProcess(processInfoLine);
                        processList.put(processItem.name, processItem);
                    } catch (Exception ignore) {
                    }
                }
                // Remove toybox commands from the list as they are killed already
                processList.remove("toybox");
                processList.remove("toybox.so");
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
