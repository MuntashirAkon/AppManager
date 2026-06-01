// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Optional;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.view.ProgressIndicatorCompat;
import io.github.muntashirakon.widget.MultiSelectionView;
import io.github.muntashirakon.widget.RecyclerView;

public class FinderActivity extends BaseActivity implements EditFiltersDialogFragment.OnSaveDialogButtonInterface {
    private FinderViewModel mViewModel;
    private LinearProgressIndicator mProgress;
    private RecyclerView mRecyclerView;
    private FinderAdapter mAdapter;
    private FloatingActionButton mFilterBtn;
    private MultiSelectionView mMultiSelectionView;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_finder);
        setSupportActionBar(findViewById(R.id.toolbar));
        mViewModel = new ViewModelProvider(this).get(FinderViewModel.class);
        Optional.ofNullable(getSupportActionBar())
                .ifPresent(actionBar -> actionBar.setDisplayHomeAsUpEnabled(true));
        mProgress = findViewById(R.id.progress_linear);
        mRecyclerView = findViewById(R.id.item_list);
        mFilterBtn = findViewById(R.id.floatingActionButton);
        mMultiSelectionView = findViewById(R.id.selection_view);
        UiUtils.applyWindowInsetsAsMargin(mFilterBtn);
        mAdapter = new FinderAdapter();
        mRecyclerView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(this));
        mRecyclerView.setAdapter(mAdapter);
        mMultiSelectionView.hide();
        mFilterBtn.setOnClickListener(v -> showFiltersDialog());
        // Watch livedata
        mViewModel.getFilteredAppListLiveData().observe(this, list -> {
            ProgressIndicatorCompat.setVisibility(mProgress, false);
            mAdapter.submitList(list);
        });
        mViewModel.getLastUpdateTimeLiveData().observe(this, time -> {
            CharSequence subtitle;
            // TODO: 8/2/24 Set subtitle to "Loaded at: {time}" localised
            if (time < 0) {
                subtitle = getString(R.string.loading);
            } else subtitle = "Loaded at: " + DateUtils.formatDateTime(this, time);
            Optional.ofNullable(getSupportActionBar()).ifPresent(actionBar -> actionBar.setSubtitle(subtitle));
        });
        mViewModel.loadFilteredAppList(true);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showFiltersDialog() {
        EditFiltersDialogFragment dialog = new EditFiltersDialogFragment();
        dialog.setOnSaveDialogButtonInterface(this);
        dialog.show(getSupportFragmentManager(), EditFiltersDialogFragment.TAG);
    }

    @NonNull
    @Override
    public FilterItem getFilterItem() {
        return mViewModel.getFilterItem();
    }

    @Override
    public void onItemAltered(@NonNull FilterItem item) {
        mViewModel.loadFilteredAppList(false);
    }
}
