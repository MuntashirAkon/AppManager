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

import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
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

public class RunningAppsAdapter extends BaseAdapter implements Filterable {
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
        ApplicationInfo applicationInfo;
        if (processItem instanceof AppProcessItem) {
            applicationInfo = ((AppProcessItem) processItem).packageInfo.applicationInfo;
        } else applicationInfo = null;
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
            if ((processItem.pid >= 10000 || RunningAppsActivity.enableKillForSystem) && !isAdbMode) {
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
