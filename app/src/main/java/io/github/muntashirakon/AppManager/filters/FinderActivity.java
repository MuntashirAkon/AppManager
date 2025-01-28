// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Optional;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.filters.options.FilterOption;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.view.ProgressIndicatorCompat;
import io.github.muntashirakon.widget.MultiSelectionView;
import io.github.muntashirakon.widget.RecyclerView;

public class FinderActivity extends BaseActivity implements EditFilterOptionFragment.OnClickDialogButtonInterface {
    private FinderViewModel mViewModel;
    private LinearProgressIndicator mProgress;
    private RecyclerView mRecyclerView;
    private FinderAdapter mAdapter;
    private FloatingActionButton mFilterBtn;
    private MultiSelectionView mMultiSelectionView;
    private FinderFilterAdapter mFinderFilterAdapter;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_finder);
        setSupportActionBar(findViewById(R.id.toolbar));
        mViewModel = new ViewModelProvider(this).get(FinderViewModel.class);
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
            mAdapter.setDefaultList(list);
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

    private void showFiltersDialog() {
        mFinderFilterAdapter = new FinderFilterAdapter(mViewModel.getFilterItem());
        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(mFinderFilterAdapter);
        DialogTitleBuilder builder = new DialogTitleBuilder(this)
                .setTitle(R.string.filter)
                .setEndIcon(R.drawable.ic_add, v -> {
                    EditFilterOptionFragment dialogFragment = new EditFilterOptionFragment();
                    Bundle args = new Bundle();
                    dialogFragment.setArguments(args);
                    dialogFragment.setOnClickDialogButtonInterface(this);
                    dialogFragment.show(getSupportFragmentManager(), EditFilterOptionFragment.TAG);
                })
                .setEndIconContentDescription(R.string.add_filter_ellipsis);
        new MaterialAlertDialogBuilder(this)
                .setCustomTitle(builder.build())
                .setView(recyclerView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.apply, (dialog, which) -> mViewModel.loadFilteredAppList(false))
                .show();
        mFinderFilterAdapter.setOnItemClickListener((v, position, filterOption) -> displayEditor(position, filterOption));
    }

    private void displayEditor(int position, @NonNull FilterOption filterOption) {
        EditFilterOptionFragment.WrappedFilterOption wrappedFilterOption = new EditFilterOptionFragment.WrappedFilterOption();
        wrappedFilterOption.filterOption = filterOption;
        EditFilterOptionFragment dialogFragment = new EditFilterOptionFragment();
        Bundle args = new Bundle();
        args.putParcelable(EditFilterOptionFragment.ARG_OPTION, wrappedFilterOption);
        args.putInt(EditFilterOptionFragment.ARG_POSITION, position);
        dialogFragment.setArguments(args);
        dialogFragment.setOnClickDialogButtonInterface(this);
        dialogFragment.show(getSupportFragmentManager(), EditFilterOptionFragment.TAG);
    }

    @Override
    public void onAddItem(@NonNull EditFilterOptionFragment.WrappedFilterOption item) {
        mFinderFilterAdapter.add(item.filterOption);
    }

    @Override
    public void onUpdateItem(int position, @NonNull EditFilterOptionFragment.WrappedFilterOption item) {
        mFinderFilterAdapter.update(position, item.filterOption);
    }

    @Override
    public void onDeleteItem(int position) {
        mFinderFilterAdapter.remove(position);
    }
}
