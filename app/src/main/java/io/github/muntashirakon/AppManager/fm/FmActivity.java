// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.ProxyFile;
import io.github.muntashirakon.widget.MultiSelectionView;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;

public class FmActivity extends BaseActivity implements SearchView.OnQueryTextListener, SwipeRefreshLayout.OnRefreshListener {
    FmViewModel model;
    @Nullable
    private FmAdapter adapter;
    @Nullable
    private LinearProgressIndicator progressIndicator;
    @Nullable
    private SwipeRefreshLayout swipeRefresh;
    @Nullable
    private MultiSelectionView multiSelectionView;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_fm);
        setSupportActionBar(findViewById(R.id.toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            UIUtils.setupSearchView(actionBar, this);
        }
        model = new ViewModelProvider(this).get(FmViewModel.class);
        progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(this);
        RecyclerView recyclerView = findViewById(R.id.list_item);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        new FastScrollerBuilder(recyclerView).useMd2Style().build();
        adapter = new FmAdapter(this);
        recyclerView.setAdapter(adapter);
        multiSelectionView = findViewById(R.id.selection_view);
        multiSelectionView.hide();
        // Set observer
        model.observeFiles().observe(this, fmItems -> {
            progressIndicator.hide();
            adapter.setFmList(fmItems);
        });
        model.loadFiles(new ProxyFile(AppPref.getString(AppPref.PrefKey.PREF_BACKUP_VOLUME_STR)));
    }

    @Override
    public void onRefresh() {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        // TODO: 3/7/21
        return false;
    }
}
