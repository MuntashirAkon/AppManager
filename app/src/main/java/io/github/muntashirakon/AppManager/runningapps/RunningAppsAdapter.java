// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.logcat.LogViewerActivity;
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.ProxyFile;
import io.github.muntashirakon.widget.MultiSelectionView;

public class RunningAppsAdapter extends MultiSelectionView.Adapter<RunningAppsAdapter.ViewHolder> {
    private final RunningAppsActivity mActivity;
    private final RunningAppsViewModel mModel;
    private final int mColorRed;
    private final ArrayList<ProcessItem> mProcessItems = new ArrayList<>();
    private boolean mIsAdbMode = false;

    RunningAppsAdapter(@NonNull RunningAppsActivity activity) {
        super();
        mActivity = activity;
        mModel = activity.mModel;
        mColorRed = ContextCompat.getColor(activity, R.color.red);
    }

    void setDefaultList(List<ProcessItem> processItems) {
        mIsAdbMode = Ops.isAdb();
        this.mProcessItems.clear();
        this.mProcessItems.addAll(processItems);
        notifyDataSetChanged();
        notifySelectionChange();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_running_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProcessItem processItem = mProcessItems.get(position);
        ApplicationInfo applicationInfo;
        if (processItem instanceof AppProcessItem) {
            applicationInfo = ((AppProcessItem) processItem).packageInfo.applicationInfo;
        } else applicationInfo = null;
        String processName = processItem.name;
        // Load icon
        mActivity.imageLoader.displayImage(processName, applicationInfo, holder.icon);
        // Set process name
        if (mModel.getQuery() != null && processName.toLowerCase(Locale.ROOT).contains(mModel.getQuery())) {
            // Highlight searched query
            holder.processName.setText(UIUtils.getHighlightedText(processName, mModel.getQuery(), mColorRed));
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
        holder.memoryUsage.setText(mActivity.getString(R.string.memory_virtual_memory,
                Formatter.formatFileSize(mActivity, processItem.getMemory()),
                Formatter.formatFileSize(mActivity, processItem.getVirtualMemory())));
        // Set user info
        String userInfo = mActivity.getString(R.string.user_and_uid, processItem.user, processItem.uid);
        String stateInfo;
        if (TextUtils.isEmpty(processItem.state_extra)) {
            stateInfo = mActivity.getString(R.string.process_state, processItem.state);
        } else {
            stateInfo = mActivity.getString(R.string.process_state_with_extra, processItem.state, processItem.state_extra);
        }
        holder.userAndStateInfo.setText(String.format("%s, %s", userInfo, stateInfo));
        holder.selinuxContext.setText(String.format("SELinux%s %s", LangUtils.getSeparatorString(),
                processItem.context));
        // Set more
        holder.more.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(mActivity, holder.more);
            popupMenu.inflate(R.menu.activity_running_apps_popup_actions);
            Menu menu = popupMenu.getMenu();
            // Set kill
            MenuItem killItem = menu.findItem(R.id.action_kill);
            if ((processItem.uid >= 10000 || mActivity.enableKillForSystem) && !mIsAdbMode) {
                killItem.setVisible(true).setOnMenuItemClickListener(item -> {
                    mModel.killProcess(processItem);
                    return true;
                });
            } else killItem.setVisible(false);
            // Set view logs
            MenuItem viewLogsItem = menu.findItem(R.id.action_view_logs);
            if (FeatureController.isLogViewerEnabled()) {
                viewLogsItem.setVisible(true).setOnMenuItemClickListener(item -> {
                    Intent logViewerIntent = new Intent(mActivity.getApplicationContext(), LogViewerActivity.class)
                            .setAction(LogViewerActivity.ACTION_LAUNCH)
                            .putExtra(LogViewerActivity.EXTRA_FILTER, SearchCriteria.PID_KEYWORD + processItem.pid)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mActivity.startActivity(logViewerIntent);
                    return true;
                });
            } else viewLogsItem.setVisible(false);
            // Scan using VT
            MenuItem scanVtIem = menu.findItem(R.id.action_scan_vt);
            String firstCliArg = processItem.getCommandlineArgs()[0];
            if (mModel.isVirusTotalAvailable() && (applicationInfo != null || new ProxyFile(firstCliArg).canRead())) {
                // TODO: 7/1/22 Check other arguments for files, too?
                scanVtIem.setVisible(true).setOnMenuItemClickListener(item -> {
                    mModel.scanWithVt(processItem);
                    return true;
                });
            } else scanVtIem.setVisible(false);
            // Set others
            MenuItem forceStopItem = menu.findItem(R.id.action_force_stop);
            MenuItem bgItem = menu.findItem(R.id.action_disable_background);
            if (applicationInfo != null) {
                forceStopItem.setVisible(true).setOnMenuItemClickListener(item -> {
                    mModel.forceStop(applicationInfo);
                    return true;
                });
                int mode = AppOpsManager.MODE_DEFAULT;
                try {
                    mode = new AppOpsService().checkOperation(AppOpsManager.OP_RUN_IN_BACKGROUND, applicationInfo.uid, applicationInfo.packageName);
                } catch (Exception ignore) {
                }
                if (mode != AppOpsManager.MODE_IGNORED && mode != AppOpsManager.MODE_ERRORED) {
                    bgItem.setVisible(true).setOnMenuItemClickListener(item -> {
                        mModel.preventBackgroundRun(applicationInfo);
                        return true;
                    });
                } else bgItem.setVisible(false);
            } else {
                forceStopItem.setVisible(false);
                bgItem.setVisible(false);
            }
            // Display popup menu
            popupMenu.show();
        });
        // Set background colors
        holder.itemView.setBackgroundResource(position % 2 == 0 ? R.drawable.item_semi_transparent : R.drawable.item_transparent);
        // Set selections
        holder.icon.setOnClickListener(v -> toggleSelection(position));
        holder.itemView.setOnLongClickListener(v -> {
            toggleSelection(position);
            return true;
        });
        // Open process details
        holder.itemView.setOnClickListener(v -> {
            if (isInSelectionMode()) {
                toggleSelection(position);
            } else {
                mModel.requestDisplayProcessDetails(processItem);
            }
        });
        super.onBindViewHolder(holder, position);
    }

    @Override
    public long getItemId(int position) {
        return mProcessItems.get(position).hashCode();
    }

    @Override
    protected void select(int position) {
        mModel.select(mProcessItems.get(position));
    }

    @Override
    protected void deselect(int position) {
        mModel.deselect(mProcessItems.get(position));
    }

    @Override
    protected boolean isSelected(int position) {
        return mModel.isSelected(mProcessItems.get(position));
    }

    @Override
    protected void cancelSelection() {
        mModel.clearSelections();
        notifyDataSetChanged();
    }

    @Override
    public void clearSelections() {
        mModel.clearSelections();
        notifyDataSetChanged();
        super.clearSelections();
    }

    @NonNull
    public ArrayList<ProcessItem> getSelectedItems() {
        return mModel.getSelections();
    }

    @Override
    protected int getSelectedItemCount() {
        return mModel.getSelectionCount();
    }

    @Override
    protected int getTotalItemCount() {
        return mModel.getTotalCount();
    }

    @Override
    public int getItemCount() {
        return mProcessItems.size();
    }

    static class ViewHolder extends MultiSelectionView.ViewHolder {
        ImageView icon;
        MaterialButton more;
        TextView processName;
        TextView packageName;
        TextView processIds;
        TextView memoryUsage;
        TextView userAndStateInfo;
        TextView selinuxContext;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            more = itemView.findViewById(R.id.more);
            processName = itemView.findViewById(R.id.process_name);
            packageName = itemView.findViewById(R.id.package_name);
            processIds = itemView.findViewById(R.id.process_ids);
            memoryUsage = itemView.findViewById(R.id.memory_usage);
            userAndStateInfo = itemView.findViewById(R.id.user_state_info);
            selinuxContext = itemView.findViewById(R.id.selinux_context);
        }
    }
}
