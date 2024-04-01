// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Process;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.PopupMenu;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.logcat.LogViewerActivity;
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.proc.ProcMemoryInfo;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.widget.MultiSelectionView;

public class RunningAppsAdapter extends MultiSelectionView.Adapter<MultiSelectionView.ViewHolder> {
    private static final int VIEW_TYPE_MEMORY_INFO = 1;
    private static final int VIEW_TYPE_PROCESS_INFO = 2;

    private final RunningAppsActivity mActivity;
    private final RunningAppsViewModel mModel;
    private final int mQueryStringHighlightColor;
    private final Object mLock = new Object();
    @NonNull
    private List<ProcessItem> mProcessItems = Collections.emptyList();
    private ProcMemoryInfo mProcMemoryInfo;

    RunningAppsAdapter(@NonNull RunningAppsActivity activity) {
        super();
        mActivity = activity;
        mModel = activity.model;
        mQueryStringHighlightColor = ColorCodes.getQueryStringHighlightColor(activity);
    }

    void setDefaultList(@NonNull List<ProcessItem> processItems) {
        synchronized (mLock) {
            int previousCount = mProcessItems.size() + 1;
            mProcessItems = processItems;
            AdapterUtils.notifyDataSetChanged(this, previousCount, mProcessItems.size() + 1);
        }
        notifySelectionChange();
    }

    public void setDeviceMemoryInfo(ProcMemoryInfo procMemoryInfo) {
        mProcMemoryInfo = procMemoryInfo;
        notifyItemChanged(0);
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) return VIEW_TYPE_MEMORY_INFO;
        return VIEW_TYPE_PROCESS_INFO;
    }

    @NonNull
    @Override
    public MultiSelectionView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_MEMORY_INFO) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.header_running_apps_memory_info, parent, false);
            return new HeaderViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_running_app, parent, false);
        return new BodyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MultiSelectionView.ViewHolder holder, int position) {
        if (position == 0) {
            onBindViewHolder((HeaderViewHolder) holder);
        } else {
            onBindViewHolder((BodyViewHolder) holder, position);
            super.onBindViewHolder(holder, position);
        }
    }

    private void onBindViewHolder(@NonNull HeaderViewHolder holder) {
        if (mProcMemoryInfo == null) {
            return;
        }
        Context context = holder.itemView.getContext();
        // Memory
        long appMemory = mProcMemoryInfo.getApplicationMemory();
        long cachedMemory = mProcMemoryInfo.getCachedMemory();
        long buffers = mProcMemoryInfo.getBuffers();
        long freeMemory = mProcMemoryInfo.getFreeMemory();
        double total = appMemory + cachedMemory + buffers + freeMemory;
        boolean totalIsNonZero = total > 0;
        AdapterUtils.setVisible(holder.mMemoryInfoChart, totalIsNonZero);
        AdapterUtils.setVisible(holder.mMemoryShortInfoView, totalIsNonZero);
        AdapterUtils.setVisible(holder.mMemoryInfoView, totalIsNonZero);
        if (totalIsNonZero) {
            holder.mMemoryInfoChart.post(() -> {
                int width = holder.mMemoryInfoChart.getWidth();
                setLayoutWidth(holder.mMemoryInfoChartChildren[0], (int) (width * appMemory / total));
                setLayoutWidth(holder.mMemoryInfoChartChildren[1], (int) (width * cachedMemory / total));
                setLayoutWidth(holder.mMemoryInfoChartChildren[2], (int) (width * buffers / total));
            });
        }
        holder.mMemoryShortInfoView.setText(UIUtils.getStyledKeyValue(context, R.string.memory, Formatter
                .formatFileSize(context, mProcMemoryInfo.getUsedMemory()) + "/" + Formatter
                .formatFileSize(context, mProcMemoryInfo.getTotalMemory())));
        // Set color info
        Spannable memInfo = UIUtils.charSequenceToSpannable(context.getString(R.string.memory_chart_info, Formatter
                        .formatShortFileSize(context, appMemory), Formatter.formatShortFileSize(context, cachedMemory),
                Formatter.formatShortFileSize(context, buffers), Formatter.formatShortFileSize(context, freeMemory)));
        setColors(holder.itemView, memInfo, new int[]{com.google.android.material.R.attr.colorOnSurface, androidx.appcompat.R.attr.colorPrimary, com.google.android.material.R.attr.colorTertiary,
                com.google.android.material.R.attr.colorSurfaceVariant});
        holder.mMemoryInfoView.setText(memInfo);

        // Swap
        long usedSwap = mProcMemoryInfo.getUsedSwap();
        long totalSwap = mProcMemoryInfo.getTotalSwap();
        boolean totalSwapIsNonZero = totalSwap > 0;
        AdapterUtils.setVisible(holder.mSwapInfoChart, totalSwapIsNonZero);
        AdapterUtils.setVisible(holder.mSwapShortInfoView, totalSwapIsNonZero);
        AdapterUtils.setVisible(holder.mSwapInfoView, totalSwapIsNonZero);
        if (totalSwapIsNonZero) {
            holder.mSwapInfoChart.post(() -> {
                int width = holder.mSwapInfoChart.getWidth();
                setLayoutWidth(holder.mSwapInfoChartChildren[0], (int) (width * usedSwap / totalSwap));
            });
        }
        holder.mSwapShortInfoView.setText(UIUtils.getStyledKeyValue(context, R.string.swap, Formatter
                .formatFileSize(context, usedSwap) + "/" + Formatter.formatFileSize(context, totalSwap)));
        // Set color and size info
        Spannable swapInfo = UIUtils.charSequenceToSpannable(context.getString(R.string.swap_chart_info, Formatter
                .formatShortFileSize(context, usedSwap), Formatter.formatShortFileSize(context, totalSwap - usedSwap)));
        setColors(holder.itemView, swapInfo, new int[]{com.google.android.material.R.attr.colorOnSurface, com.google.android.material.R.attr.colorSurfaceVariant});
        holder.mSwapInfoView.setText(swapInfo);
    }

    private void onBindViewHolder(@NonNull BodyViewHolder holder, int position) {
        ProcessItem processItem;
        synchronized (mLock) {
            processItem = mProcessItems.get(position - 1);
        }
        ApplicationInfo applicationInfo;
        if (processItem instanceof AppProcessItem) {
            applicationInfo = ((AppProcessItem) processItem).packageInfo.applicationInfo;
        } else applicationInfo = null;
        String processName = processItem.name;
        // Load icon
        holder.icon.setTag(processName);
        ImageLoader.getInstance().displayImage(processName, applicationInfo, holder.icon);
        // Set process name
        holder.processName.setText(UIUtils.getHighlightedText(processName, mModel.getQuery(), mQueryStringHighlightColor));
        // Set package name
        AdapterUtils.setVisible(holder.packageName, applicationInfo != null);
        if (applicationInfo != null) {
            holder.packageName.setText(UIUtils.getHighlightedText(applicationInfo.packageName, mModel.getQuery(), mQueryStringHighlightColor));
        }
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
            popupMenu.setForceShowIcon(true);
            popupMenu.inflate(R.menu.activity_running_apps_popup_actions);
            Menu menu = popupMenu.getMenu();
            // Set kill
            MenuItem killItem = menu.findItem(R.id.action_kill);
            if ((processItem.uid >= Process.FIRST_APPLICATION_UID || Prefs.RunningApps.enableKillForSystemApps()) && Ops.isRoot()) {
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
                            .putExtra(LogViewerActivity.EXTRA_FILTER, SearchCriteria.PID_KEYWORD + processItem.pid)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mActivity.startActivity(logViewerIntent);
                    return true;
                });
            } else viewLogsItem.setVisible(false);
            // Scan using VT
            MenuItem scanVtIem = menu.findItem(R.id.action_scan_vt);
            String firstCliArg = processItem.getCommandlineArgs()[0];
            if (mModel.isVirusTotalAvailable() && (applicationInfo != null || Paths.get(firstCliArg).canRead())) {
                // TODO: 7/1/22 Check other arguments for files, too?
                scanVtIem.setVisible(true).setOnMenuItemClickListener(item -> {
                    mModel.scanWithVt(processItem);
                    return true;
                });
            } else scanVtIem.setVisible(false);
            // Set force-stop
            MenuItem forceStopItem = menu.findItem(R.id.action_force_stop);
            if (applicationInfo != null) {
                forceStopItem.setOnMenuItemClickListener(item -> {
                            mModel.forceStop(applicationInfo);
                            return true;
                        })
                        .setEnabled(SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES));
            } else forceStopItem.setEnabled(false);
            MenuItem bgItem = menu.findItem(R.id.action_disable_background);
            if (applicationInfo != null) {
                forceStopItem.setOnMenuItemClickListener(item -> {
                            mModel.forceStop(applicationInfo);
                            return true;
                        })
                        .setVisible(SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES));
                if (mModel.canRunInBackground(applicationInfo)) {
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
        // Set selections
        holder.icon.setOnClickListener(v -> toggleSelection(position));
        holder.itemView.setOnLongClickListener(v -> {
            ProcessItem lastSelectedItem = mModel.getLastSelectedItem();
            int lastSelectedItemPosition = lastSelectedItem == null ? -1 : mProcessItems.indexOf(lastSelectedItem);
            if (lastSelectedItemPosition >= 0) {
                // Select from last selection to this selection
                selectRange(lastSelectedItemPosition + 1, position);
            } else toggleSelection(position);
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
        holder.itemView.setStrokeColor(Color.TRANSPARENT);
    }

    @Override
    public long getItemId(int position) {
        if (position == 0) {
            return mProcMemoryInfo != null ? mProcMemoryInfo.hashCode() : View.NO_ID;
        }
        synchronized (mLock) {
            return mProcessItems.get(position - 1).hashCode();
        }
    }

    @Override
    protected void select(int position) {
        if (position == 0) {
            return;
        }
        synchronized (mLock) {
            mModel.select(mProcessItems.get(position - 1));
        }
    }

    @Override
    protected void deselect(int position) {
        if (position == 0) {
            return;
        }
        synchronized (mLock) {
            mModel.deselect(mProcessItems.get(position - 1));
        }
    }

    @Override
    protected boolean isSelected(int position) {
        if (position == 0) {
            return false;
        }
        synchronized (mLock) {
            return mModel.isSelected(mProcessItems.get(position - 1));
        }
    }

    @Override
    protected void cancelSelection() {
        super.cancelSelection();
        mModel.clearSelections();
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
        synchronized (mLock) {
            return mProcessItems.size() + 1;
        }
    }

    private static void setColors(@NonNull View v, @NonNull Spannable text, @NonNull @AttrRes int[] colors) {
        int idx = 0;
        for (int color : colors) {
            idx = text.toString().indexOf('●', idx);
            if (idx == -1) break;
            text.setSpan(new ForegroundColorSpan(MaterialColors.getColor(v, color)), idx, idx + 1,
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            ++idx;
        }
    }

    private static void setLayoutWidth(@NonNull View view, int width) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        lp.width = width;
        view.setLayoutParams(lp);
    }

    static class HeaderViewHolder extends MultiSelectionView.ViewHolder {
        private final TextView mMemoryShortInfoView;
        private final TextView mMemoryInfoView;
        private final View[] mMemoryInfoChartChildren;
        private final LinearLayoutCompat mMemoryInfoChart;
        private final TextView mSwapShortInfoView;
        private final TextView mSwapInfoView;
        private final View[] mSwapInfoChartChildren;
        private final LinearLayoutCompat mSwapInfoChart;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            mMemoryShortInfoView = itemView.findViewById(R.id.memory_usage);
            mMemoryInfoView = itemView.findViewById(R.id.memory_usage_info);
            mMemoryInfoChart = itemView.findViewById(R.id.memory_usage_chart);
            int childCount = mMemoryInfoChart.getChildCount();
            mMemoryInfoChartChildren = new View[childCount];
            for (int i = 0; i < childCount; ++i) {
                mMemoryInfoChartChildren[i] = mMemoryInfoChart.getChildAt(i);
            }
            mSwapShortInfoView = itemView.findViewById(R.id.swap_usage);
            mSwapInfoView = itemView.findViewById(R.id.swap_usage_info);
            mSwapInfoChart = itemView.findViewById(R.id.swap_usage_chart);
            childCount = mSwapInfoChart.getChildCount();
            mSwapInfoChartChildren = new View[childCount];
            for (int i = 0; i < childCount; ++i) {
                mSwapInfoChartChildren[i] = mSwapInfoChart.getChildAt(i);
            }
        }
    }

    static class BodyViewHolder extends MultiSelectionView.ViewHolder {
        MaterialCardView itemView;
        ImageView icon;
        MaterialButton more;
        TextView processName;
        TextView packageName;
        TextView processIds;
        TextView memoryUsage;
        TextView userAndStateInfo;
        TextView selinuxContext;

        public BodyViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = (MaterialCardView) itemView;
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
