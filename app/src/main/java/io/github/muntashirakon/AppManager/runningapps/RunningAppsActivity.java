// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.logcat.LogViewerActivity;
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.scanner.vt.VtFileReport;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.multiselection.MultiSelectionActionsView;
import io.github.muntashirakon.widget.MultiSelectionView;

public class RunningAppsActivity extends BaseActivity implements MultiSelectionView.OnSelectionChangeListener,
        MultiSelectionActionsView.OnItemSelectedListener, AdvancedSearchView.OnQueryTextListener {

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

    private static final int[] SORT_ORDER_IDS = new int[]{
            R.id.action_sort_by_pid,
            R.id.action_sort_by_process_name,
            R.id.action_sort_by_apps_first,
            R.id.action_sort_by_memory_usage,
    };

    @Nullable
    RunningAppsViewModel model;

    private boolean mEnableKillForSystem = false;
    @Nullable
    private RunningAppsAdapter mAdapter;
    @Nullable
    private LinearProgressIndicator mProgressIndicator;
    @Nullable
    private MultiSelectionView mMultiSelectionView;
    @Nullable
    private Menu mSelectionMenu;
    private Timer mTimer;

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_running_apps);
        setSupportActionBar(findViewById(R.id.toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            UIUtils.setupAdvancedSearchView(actionBar, this);
        }
        model = new ViewModelProvider(this).get(RunningAppsViewModel.class);
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        RecyclerView recyclerView = findViewById(R.id.scrollView);
        recyclerView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(this));
        mAdapter = new RunningAppsAdapter(this);
        mAdapter.setHasStableIds(false);
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
        mEnableKillForSystem = Prefs.RunningApps.enableKillForSystemApps();

        // Set observers
        model.observeKillProcess().observe(this, processInfo -> {
            if (processInfo.second /* is success */) {
                refresh();
            } else {
                UIUtils.displayLongToast(R.string.failed_to_stop, processInfo.first.name /* process name */);
            }
        });
        model.observeKillSelectedProcess().observe(this, processInfoList -> {
            if (!processInfoList.isEmpty()) {
                List<String> processNames = new ArrayList<String>() {{
                    for (ProcessItem processItem : processInfoList) {
                        add(processItem.name);
                    }
                }};
                UIUtils.displayLongToast(R.string.failed_to_stop, TextUtils.join(", ", processNames));
            }
            refresh();
        });
        model.observeForceStop().observe(this, applicationInfoBooleanPair -> {
            if (applicationInfoBooleanPair.second /* is success */) {
                refresh();
            } else {
                UIUtils.displayLongToast(R.string.failed_to_stop, applicationInfoBooleanPair.first
                        .loadLabel(getPackageManager()));
            }
        });
        model.observePreventBackgroundRun().observe(this, applicationInfoBooleanPair -> {
            if (applicationInfoBooleanPair.second /* is success */) {
                refresh();
            } else {
                UIUtils.displayLongToast(R.string.failed_to_prevent_background_run, applicationInfoBooleanPair.first
                        .loadLabel(getPackageManager()));
            }
        });
        model.observeProcessDetails().observe(this, processItem -> {
            RunningAppDetails fragment = RunningAppDetails.getInstance(processItem);
            fragment.show(getSupportFragmentManager(), RunningAppDetails.TAG);
        });
        model.getVtFileUpload().observe(this, processItemVtFilePermalinkPair -> {
            ProcessItem processItem = processItemVtFilePermalinkPair.first;
            String permalink = processItemVtFilePermalinkPair.second;
            if (permalink == null) {
                // Started uploading
                UIUtils.displayShortToast(R.string.vt_uploading);
                if (Prefs.VirusTotal.promptBeforeUpload()) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.scan_in_vt)
                            .setMessage(R.string.vt_confirm_uploading_file)
                            .setCancelable(false)
                            .setPositiveButton(R.string.vt_confirm_upload_and_scan, (dialog, which) -> model.enableUploading())
                            .setNegativeButton(R.string.no, (dialog, which) -> model.disableUploading())
                            .show();
                } else model.enableUploading();
            } else {
                UIUtils.displayShortToast(R.string.vt_queued);
            }
            // TODO: 7/1/22 Use a separate fragment
        });
        model.getVtFileReport().observe(this, processItemVtFileReportPair -> {
            ProcessItem processItem = processItemVtFileReportPair.first;
            VtFileReport vtFileReport = processItemVtFileReportPair.second;
            if (vtFileReport == null) {
                UIUtils.displayShortToast(R.string.vt_failed);
            } else {
                UIUtils.displayLongToast(getString(R.string.vt_success, vtFileReport.getPositives(), vtFileReport.getTotal()));
            }
            // TODO: 7/1/22 Use a separate fragment
        });
        model.getProcessLiveData().observe(this, processList -> {
            if (mProgressIndicator != null) {
                mProgressIndicator.hide();
            }
            if (mAdapter != null) {
                mAdapter.setDefaultList(processList);
            }
        });
        model.getDeviceMemoryInfo().observe(this, deviceMemoryInfo -> {
            if (mAdapter != null) {
                mAdapter.setDeviceMemoryInfo(deviceMemoryInfo);
            }
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
        if (model == null) return super.onPrepareOptionsMenu(menu);

        menu.findItem(SORT_ORDER_IDS[model.getSortOrder()]).setChecked(true);
        int filter = model.getFilter();
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
        if (model == null) return true;
        if (id == R.id.action_toggle_kill) {
            mEnableKillForSystem = !mEnableKillForSystem;
            Prefs.RunningApps.setEnableKillForSystemApps(mEnableKillForSystem);
            refresh();
        } else if (id == R.id.action_sort_by_pid) {
            model.setSortOrder(SORT_BY_PID);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_process_name) {
            model.setSortOrder(SORT_BY_PROCESS_NAME);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_apps_first) {
            model.setSortOrder(SORT_BY_APPS_FIRST);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_memory_usage) {
            model.setSortOrder(SORT_BY_MEMORY_USAGE);
            item.setChecked(true);
            // Filter
        } else if (id == R.id.action_filter_apps) {
            if (!item.isChecked()) model.addFilter(FILTER_APPS);
            else model.removeFilter(FILTER_APPS);
            item.setChecked(!item.isChecked());
        } else if (id == R.id.action_filter_user_apps) {
            if (!item.isChecked()) model.addFilter(FILTER_USER_APPS);
            else model.removeFilter(FILTER_USER_APPS);
            item.setChecked(!item.isChecked());
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTimer = new Timer("running_apps");
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                ThreadUtils.postOnMainThread(() -> {
                    if (model != null) {
                        model.loadProcesses();
                        model.loadMemoryInfo();
                    }
                });
            }
        }, 0, 10_000);
    }

    @Override
    protected void onPause() {
        mTimer.cancel();
        mTimer.purge();
        super.onPause();
    }

    @Override
    public boolean onQueryTextSubmit(String query, int type) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText, int type) {
        if (model != null) {
            model.setQuery(newText, type);
            return true;
        }
        return false;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (model == null || mAdapter == null) return true;
        ArrayList<ProcessItem> selectedItems = mAdapter.getSelectedItems();
        int id = item.getItemId();
        if (id == R.id.action_kill) {
            model.killSelectedProcesses();
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
    public boolean onSelectionChange(int selectionCount) {
        if (mSelectionMenu == null || mAdapter == null) {
            return false;
        }
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
        forceStop.setVisible(SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES));
        preventBackground.setEnabled(appsCount != 0 && appsCount == selectedItems.size());
        boolean killEnabled = Ops.isWorkingUidRoot();
        if (killEnabled && !mEnableKillForSystem) {
            for (ProcessItem item : selectedItems) {
                if (item.uid < Process.FIRST_APPLICATION_UID) {
                    killEnabled = false;
                    break;
                }
            }
        }
        kill.setEnabled(!selectedItems.isEmpty() && killEnabled);
        return true;
    }

    private void handleBatchOp(@BatchOpsManager.OpType int op) {
        if (model == null) return;
        if (mProgressIndicator != null) {
            mProgressIndicator.show();
        }
        Intent intent = new Intent(this, BatchOpsService.class);
        BatchOpsManager.Result input = new BatchOpsManager.Result(model.getSelectedPackagesWithUsers());
        BatchQueueItem item = BatchQueueItem.getBatchOpQueue(op, input.getFailedPackages(), input.getAssociatedUsers(), null);
        intent.putExtra(BatchOpsService.EXTRA_QUEUE_ITEM, item);
        ContextCompat.startForegroundService(this, intent);
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
        if (mProgressIndicator == null || model == null) return;
        mProgressIndicator.show();
        model.loadProcesses();
        model.loadMemoryInfo();
    }
}
