// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.ipc.ps.DeviceMemoryInfo;
import io.github.muntashirakon.AppManager.logcat.LogViewerActivity;
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria;
import io.github.muntashirakon.AppManager.scanner.vt.VtFileReport;
import io.github.muntashirakon.AppManager.scanner.vt.VtFileScanMeta;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.reflow.ReflowMenuViewWrapper;
import io.github.muntashirakon.widget.MultiSelectionView;
import io.github.muntashirakon.widget.SwipeRefreshLayout;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;

public class RunningAppsActivity extends BaseActivity implements MultiSelectionView.OnSelectionChangeListener,
        ReflowMenuViewWrapper.OnItemSelectedListener, SearchView.OnQueryTextListener,
        SwipeRefreshLayout.OnRefreshListener {

    @IntDef(value = {
            SORT_BY_PID,
            SORT_BY_PROCESS_NAME,
            SORT_BY_APPS_FIRST,
            SORT_BY_MEMORY_USAGE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SortOrder {
    }

    public static final int SORT_BY_PID = 0;
    public static final int SORT_BY_PROCESS_NAME = 1;
    public static final int SORT_BY_APPS_FIRST = 2;
    public static final int SORT_BY_MEMORY_USAGE = 3;

    @IntDef(value = {
            FILTER_NONE,
            FILTER_APPS,
            FILTER_USER_APPS
    }, flag = true)
    @Retention(RetentionPolicy.SOURCE)
    public @interface Filter {
    }

    public static final int FILTER_NONE = 0;
    public static final int FILTER_APPS = 1;
    public static final int FILTER_USER_APPS = 1 << 1;

    private static final int[] sortOrderIds = new int[]{
            R.id.action_sort_by_pid,
            R.id.action_sort_by_process_name,
            R.id.action_sort_by_apps_first,
            R.id.action_sort_by_memory_usage,
    };

    @Nullable
    RunningAppsViewModel mModel;
    boolean enableKillForSystem = false;
    final ImageLoader imageLoader = new ImageLoader();

    @Nullable
    private RunningAppsAdapter mAdapter;
    @Nullable
    private LinearProgressIndicator mProgressIndicator;
    @Nullable
    private SwipeRefreshLayout mSwipeRefresh;
    @Nullable
    private MultiSelectionView mMultiSelectionView;
    @Nullable
    private Menu mSelectionMenu;
    private TextView mMemoryShortInfoView;
    private TextView mMemoryInfoView;
    private View[] mMemoryInfoChartChildren;
    private LinearLayoutCompat mMemoryInfoChart;
    private TextView mSwapShortInfoView;
    private TextView mSwapInfoView;
    private View[] mSwapInfoChartChildren;
    private LinearLayoutCompat mSwapInfoChart;
    private DeviceMemoryInfo mDeviceMemoryInfo; // TODO: Move to ViewModel
    private boolean mIsAdbMode;

    private final BroadcastReceiver mBatchOpsBroadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refresh();
        }
    };

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_running_apps);
        setSupportActionBar(findViewById(R.id.toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            UIUtils.setupSearchView(actionBar, this);
        }
        mModel = new ViewModelProvider(this).get(RunningAppsViewModel.class);
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mSwipeRefresh = findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setOnRefreshListener(this);
        RecyclerView recyclerView = findViewById(R.id.list_item);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new RunningAppsAdapter(this);
        recyclerView.setAdapter(mAdapter);
        // Recycler view is focused by default
        recyclerView.requestFocus();
        mMultiSelectionView = findViewById(R.id.selection_view);
        mMultiSelectionView.setOnItemSelectedListener(this);
        mMultiSelectionView.setOnSelectionChangeListener(this);
        mMultiSelectionView.setAdapter(mAdapter);
        mMultiSelectionView.updateCounter(true);
        mSelectionMenu = mMultiSelectionView.getMenu();
        mSelectionMenu.findItem(R.id.action_scan_vt).setVisible(false);
        enableKillForSystem = AppPref.getBoolean(AppPref.PrefKey.PREF_ENABLE_KILL_FOR_SYSTEM_BOOL);
        // Memory
        mMemoryShortInfoView = findViewById(R.id.memory_usage);
        mMemoryInfoView = findViewById(R.id.memory_usage_info);
        mMemoryInfoChart = findViewById(R.id.memory_usage_chart);
        int childCount = mMemoryInfoChart.getChildCount();
        mMemoryInfoChartChildren = new View[childCount];
        for (int i = 0; i < childCount; ++i) {
            mMemoryInfoChartChildren[i] = mMemoryInfoChart.getChildAt(i);
        }
        mSwapShortInfoView = findViewById(R.id.swap_usage);
        mSwapInfoView = findViewById(R.id.swap_usage_info);
        mSwapInfoChart = findViewById(R.id.swap_usage_chart);
        childCount = mSwapInfoChart.getChildCount();
        mSwapInfoChartChildren = new View[childCount];
        for (int i = 0; i < childCount; ++i) {
            mSwapInfoChartChildren[i] = mSwapInfoChart.getChildAt(i);
        }
        mDeviceMemoryInfo = new DeviceMemoryInfo();
        new FastScrollerBuilder(findViewById(R.id.scrollView)).useMd2Style().build();

        // Set observers
        mModel.observeKillProcess().observe(this, processInfo -> {
            if (processInfo.second /* is success */) {
                refresh();
            } else {
                UIUtils.displayLongToast(R.string.failed_to_stop, processInfo.first.name /* process name */);
            }
        });
        mModel.observeKillSelectedProcess().observe(this, processInfoList -> {
            if (processInfoList.size() != 0) {
                List<String> processNames = new ArrayList<String>() {{
                    for (ProcessItem processItem : processInfoList) add(processItem.name);
                }};
                UIUtils.displayLongToast(R.string.failed_to_stop, TextUtils.join(", ", processNames));
            }
            refresh();
        });
        mModel.observeForceStop().observe(this, applicationInfoBooleanPair -> {
            if (applicationInfoBooleanPair.second /* is success */) {
                refresh();
            } else {
                UIUtils.displayLongToast(R.string.failed_to_stop, applicationInfoBooleanPair.first
                        .loadLabel(getPackageManager()));
            }
        });
        mModel.observePreventBackgroundRun().observe(this, applicationInfoBooleanPair -> {
            if (applicationInfoBooleanPair.second /* is success */) {
                refresh();
            } else {
                UIUtils.displayLongToast(R.string.failed_to_prevent_background_run, applicationInfoBooleanPair.first
                        .loadLabel(getPackageManager()));
            }
        });
        mModel.observeProcessDetails().observe(this, processItem -> {
            RunningAppDetails fragment = RunningAppDetails.getInstance(processItem);
            fragment.show(getSupportFragmentManager(), RunningAppDetails.TAG);
        });
        mModel.getVtFileScanMeta().observe(this, processItemVtFileScanMetaPair -> {
            ProcessItem processItem = processItemVtFileScanMetaPair.first;
            VtFileScanMeta vtFileScanMeta = processItemVtFileScanMetaPair.second;
            if (vtFileScanMeta == null) {
                // Started uploading
                UIUtils.displayShortToast(R.string.vt_uploading);
                if (AppPref.getBoolean(AppPref.PrefKey.PREF_VIRUS_TOTAL_PROMPT_BEFORE_UPLOADING_BOOL)) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.scan_in_vt)
                            .setMessage(R.string.vt_confirm_uploading_file)
                            .setCancelable(false)
                            .setPositiveButton(R.string.vt_confirm_upload_and_scan, (dialog, which) -> mModel.enableUploading())
                            .setNegativeButton(R.string.no, (dialog, which) -> mModel.disableUploading())
                            .show();
                } else mModel.enableUploading();
            } else {
                UIUtils.displayShortToast(R.string.vt_queued);
            }
            // TODO: 7/1/22 Use a separate fragment
        });
        mModel.getVtFileReport().observe(this, processItemVtFileReportPair -> {
            ProcessItem processItem = processItemVtFileReportPair.first;
            VtFileReport vtFileReport = processItemVtFileReportPair.second;
            if (vtFileReport == null) {
                UIUtils.displayShortToast(R.string.vt_failed);
            } else {
                UIUtils.displayLongToast(getString(R.string.vt_success, vtFileReport.getPositives(), vtFileReport.getTotal()));
            }
            // TODO: 7/1/22 Use a separate fragment
        });
    }

    @Override
    public void onBackPressed() {
        if (mAdapter != null && mMultiSelectionView != null && mAdapter.isInSelectionMode()) {
            mMultiSelectionView.cancel();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_running_apps_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        if (mModel == null) return super.onPrepareOptionsMenu(menu);

        menu.findItem(sortOrderIds[mModel.getSortOrder()]).setChecked(true);
        int filter = mModel.getFilter();
        if ((filter & FILTER_APPS) != 0) {
            menu.findItem(R.id.action_filter_apps).setChecked(true);
        }
        if ((filter & FILTER_USER_APPS) != 0) {
            menu.findItem(R.id.action_filter_user_apps).setChecked(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        if (mModel == null) return true;
        if (id == R.id.action_toggle_kill) {
            enableKillForSystem = !enableKillForSystem;
            AppPref.set(AppPref.PrefKey.PREF_ENABLE_KILL_FOR_SYSTEM_BOOL, enableKillForSystem);
            refresh();
        } else if (id == R.id.action_refresh) {
            refresh();
            // Sort
        } else if (id == R.id.action_sort_by_pid) {
            mModel.setSortOrder(SORT_BY_PID);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_process_name) {
            mModel.setSortOrder(SORT_BY_PROCESS_NAME);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_apps_first) {
            mModel.setSortOrder(SORT_BY_APPS_FIRST);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_memory_usage) {
            mModel.setSortOrder(SORT_BY_MEMORY_USAGE);
            item.setChecked(true);
            // Filter
        } else if (id == R.id.action_filter_apps) {
            if (!item.isChecked()) mModel.addFilter(FILTER_APPS);
            else mModel.removeFilter(FILTER_APPS);
            item.setChecked(!item.isChecked());
        } else if (id == R.id.action_filter_user_apps) {
            if (!item.isChecked()) mModel.addFilter(FILTER_USER_APPS);
            else mModel.removeFilter(FILTER_USER_APPS);
            item.setChecked(!item.isChecked());
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mModel != null) {
            mModel.getProcessLiveData().observe(this, processList -> {
                if (mAdapter != null) {
                    mAdapter.setDefaultList(processList);
                }
                if (mProgressIndicator != null) {
                    mProgressIndicator.hide();
                }
            });
        }
        mIsAdbMode = Ops.isAdb();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        registerReceiver(mBatchOpsBroadCastReceiver, new IntentFilter(BatchOpsService.ACTION_BATCH_OPS_COMPLETED));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBatchOpsBroadCastReceiver);
    }

    @Override
    public void onRefresh() {
        if (mSwipeRefresh != null) {
            mSwipeRefresh.setRefreshing(false);
        }
        refresh();
    }

    @Override
    protected void onDestroy() {
        imageLoader.close();
        super.onDestroy();
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (mModel != null) {
            mModel.setQuery(newText);
        }
        return true;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (mModel == null || mAdapter == null) return true;
        ArrayList<ProcessItem> selectedItems = mAdapter.getSelectedItems();
        int id = item.getItemId();
        if (id == R.id.action_kill) {
            mModel.killSelectedProcesses();
        } else if (id == R.id.action_force_stop) {
            handleBatchOpWithWarning(BatchOpsManager.OP_FORCE_STOP);
        } else if (id == R.id.action_disable_background) {
            handleBatchOpWithWarning(BatchOpsManager.OP_DISABLE_BACKGROUND);
        } else if (id == R.id.action_view_logs) {
            // Should be a singleton list
            if (selectedItems.size() == 1) {
                ProcessItem processItem = selectedItems.get(0);
                Intent logViewerIntent = new Intent(getApplicationContext(), LogViewerActivity.class)
                        .putExtra(LogViewerActivity.EXTRA_FILTER, SearchCriteria.PID_KEYWORD + processItem.pid)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(logViewerIntent);
            }
        }
        return false;
    }

    @Override
    public void onSelectionChange(int selectionCount) {
        if (mSelectionMenu == null || mAdapter == null) return;
        ArrayList<ProcessItem> selectedItems = mAdapter.getSelectedItems();
        MenuItem kill = mSelectionMenu.findItem(R.id.action_kill);
        MenuItem forceStop = mSelectionMenu.findItem(R.id.action_force_stop);
        MenuItem preventBackground = mSelectionMenu.findItem(R.id.action_disable_background);
        MenuItem viewLogs = mSelectionMenu.findItem(R.id.action_view_logs);
        viewLogs.setEnabled(FeatureController.isLogViewerEnabled() && selectedItems.size() == 1);
        int appsCount = 0;
        for (Object item : selectedItems) {
            if (item instanceof AppProcessItem) {
                ++appsCount;
            } else break;
        }
        forceStop.setEnabled(appsCount != 0 && appsCount == selectedItems.size());
        preventBackground.setEnabled(appsCount != 0 && appsCount == selectedItems.size());
        boolean killEnabled = !mIsAdbMode;
        if (killEnabled && !enableKillForSystem) {
            for (ProcessItem item : selectedItems) {
                if (item.uid < 10_000) {
                    killEnabled = false;
                    break;
                }
            }
        }
        kill.setEnabled(selectedItems.size() != 0 && killEnabled);
    }

    private void handleBatchOp(@BatchOpsManager.OpType int op) {
        if (mModel == null) return;
        if (mProgressIndicator != null) {
            mProgressIndicator.show();
        }
        Intent intent = new Intent(this, BatchOpsService.class);
        BatchOpsManager.Result input = new BatchOpsManager.Result(mModel.getSelectedPackagesWithUsers());
        intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, input.getFailedPackages());
        intent.putIntegerArrayListExtra(BatchOpsService.EXTRA_OP_USERS, input.getAssociatedUserHandles());
        intent.putExtra(BatchOpsService.EXTRA_OP, op);
        ContextCompat.startForegroundService(this, intent);
        if (mMultiSelectionView != null) {
            mMultiSelectionView.cancel();
        }
    }

    private void handleBatchOpWithWarning(@BatchOpsManager.OpType int op) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.are_you_sure)
                .setMessage(R.string.this_action_cannot_be_undone)
                .setPositiveButton(R.string.yes, (dialog, which) -> handleBatchOp(op))
                .setNegativeButton(R.string.no, null)
                .show();
    }

    void refresh() {
        if (mProgressIndicator == null || mModel == null) return;
        mProgressIndicator.show();
        mModel.loadProcesses();
        updateMemoryInfo();
    }

    private void updateMemoryInfo() {
        mDeviceMemoryInfo.reload();
        // Memory
        long appMemory = mDeviceMemoryInfo.getApplicationMemory();
        long cachedMemory = mDeviceMemoryInfo.getCachedMemory();
        long buffers = mDeviceMemoryInfo.getBuffers();
        long freeMemory = mDeviceMemoryInfo.getFreeMemory();
        double total = appMemory + cachedMemory + buffers + freeMemory;
        if (total == 0) {
            // Error due to parsing failure, etc.
            mMemoryInfoChart.setVisibility(View.GONE);
            mMemoryShortInfoView.setVisibility(View.GONE);
            mMemoryInfoView.setVisibility(View.GONE);
            return;
        }
        mMemoryInfoChart.post(() -> {
            int width = mMemoryInfoChart.getWidth();
            setLayoutWidth(mMemoryInfoChartChildren[0], (int) (width * appMemory / total));
            setLayoutWidth(mMemoryInfoChartChildren[1], (int) (width * cachedMemory / total));
            setLayoutWidth(mMemoryInfoChartChildren[2], (int) (width * buffers / total));
        });
        mMemoryShortInfoView.setText(UIUtils.getStyledKeyValue(this, R.string.memory, Formatter
                .formatFileSize(this, mDeviceMemoryInfo.getUsedMemory()) + "/" + Formatter
                .formatFileSize(this, mDeviceMemoryInfo.getTotalMemory())));
        // Set color info
        Spannable memInfo = UIUtils.charSequenceToSpannable(getString(R.string.memory_chart_info, Formatter
                        .formatShortFileSize(this, appMemory), Formatter.formatShortFileSize(this, cachedMemory),
                Formatter.formatShortFileSize(this, buffers), Formatter.formatShortFileSize(this, freeMemory)));
        setColors(memInfo, new int[]{R.color.purple_shadow, R.color.android_theme_tag_color_02, R.color.green_mountain,
                R.color.ragin_beige});
        mMemoryInfoView.setText(memInfo);

        // Swap
        long usedSwap = mDeviceMemoryInfo.getUsedSwap();
        long totalSwap = mDeviceMemoryInfo.getTotalSwap();
        if (totalSwap == 0) {
            mSwapInfoChart.setVisibility(View.GONE);
            mSwapShortInfoView.setVisibility(View.GONE);
            mSwapInfoView.setVisibility(View.GONE);
            // No swap
            return;
        }
        mSwapInfoChart.post(() -> {
            int width = mSwapInfoChart.getWidth();
            setLayoutWidth(mSwapInfoChartChildren[0], (int) (width * usedSwap / totalSwap));
        });
        mSwapShortInfoView.setText(UIUtils.getStyledKeyValue(this, R.string.swap, Formatter
                .formatFileSize(this, usedSwap) + "/" + Formatter.formatFileSize(this, totalSwap)));
        // Set color and size info
        Spannable swapInfo = UIUtils.charSequenceToSpannable(getString(R.string.swap_chart_info, Formatter
                .formatShortFileSize(this, usedSwap), Formatter.formatShortFileSize(this, totalSwap - usedSwap)));
        setColors(swapInfo, new int[]{R.color.purple_shadow, R.color.ragin_beige});
        mSwapInfoView.setText(swapInfo);
    }

    private void setColors(@NonNull Spannable text, @ColorRes int[] colors) {
        int idx = 0;
        for (int color : colors) {
            idx = text.toString().indexOf('●', idx);
            if (idx == -1) break;
            text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, color)), idx, idx + 1,
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            ++idx;
        }
    }

    private static void setLayoutWidth(@NonNull View view, int width) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        lp.width = width;
        view.setLayoutParams(lp);
    }
}
