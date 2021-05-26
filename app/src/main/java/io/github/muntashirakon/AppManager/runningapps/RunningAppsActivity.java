// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textview.MaterialTextView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;

public class RunningAppsActivity extends BaseActivity implements
        SearchView.OnQueryTextListener, SwipeRefreshLayout.OnRefreshListener {

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

    static String mConstraint;
    static boolean enableKillForSystem = false;
    private static final int[] sortOrderIds = new int[]{
            R.id.action_sort_by_pid,
            R.id.action_sort_by_process_name,
            R.id.action_sort_by_apps_first,
            R.id.action_sort_by_memory_usage,
    };

    private RunningAppsAdapter mAdapter;
    private LinearProgressIndicator mProgressIndicator;
    private SwipeRefreshLayout mSwipeRefresh;
    private MaterialTextView mCounterView;

    RunningAppsViewModel mModel;
    final ImageLoader imageLoader = new ImageLoader();

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_running_apps);
        setSupportActionBar(findViewById(R.id.toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            UIUtils.setupSearchView(this, actionBar, this);
        }
        mModel = new ViewModelProvider(this).get(RunningAppsViewModel.class);
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mCounterView = findViewById(R.id.bottom_appbar_counter);
        BottomAppBar bottomAppBar = findViewById(R.id.bottom_appbar);
        bottomAppBar.setNavigationOnClickListener(v -> mModel.clearSelections());
        mSwipeRefresh = findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setOnRefreshListener(this);
        RecyclerView recyclerView = findViewById(R.id.list_item);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        new FastScrollerBuilder(recyclerView).useMd2Style().build();
        mAdapter = new RunningAppsAdapter(this);
        recyclerView.setAdapter(mAdapter);
        mConstraint = null;
        enableKillForSystem = (boolean) AppPref.get(AppPref.PrefKey.PREF_ENABLE_KILL_FOR_SYSTEM_BOOL);

        // Set observers
        mModel.observeKillProcess().observe(this, processInfo -> {
            if (processInfo.second /* is success */) {
                refresh();
            } else {
                UIUtils.displayLongToast(R.string.failed_to_stop, processInfo.first.name /* process name */);
            }
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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_running_apps_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
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
        } else if (id == R.id.action_toggle_kill) {
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
                mAdapter.setDefaultList();
                mProgressIndicator.hide();
            });
            mModel.getSelection().observe(this, count -> mCounterView.setText(getResources()
                    .getQuantityString(R.plurals.items_selected, count, count)));
        }
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
        imageLoader.close();
        super.onDestroy();
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        mModel.setQuery(newText);
        return true;
    }

    void refresh() {
        mProgressIndicator.show();
        mModel.loadProcesses();
    }
}
