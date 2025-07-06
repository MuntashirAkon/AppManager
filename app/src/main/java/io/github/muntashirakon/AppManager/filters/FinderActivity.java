// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.filters.options.FilterOption;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.view.ProgressIndicatorCompat;
import io.github.muntashirakon.view.TextInputLayoutCompat;
import io.github.muntashirakon.widget.MultiSelectionView;
import io.github.muntashirakon.widget.RecyclerView;

public class FinderActivity extends BaseActivity implements EditFilterOptionFragment.OnClickDialogButtonInterface {
    private static final Map<String, Integer> HIGHLIGHT_MAP = new HashMap<String, Integer>() {{
        put("&", Color.RED);
        put("|", Color.RED);
        put("(", Color.RED);
        put(")", Color.RED);
        put("true", Color.BLUE);
        put("false", Color.BLUE);
    }};

    private static class ExprTester extends AbsExpressionEvaluator {
        private final FilterItem mFilterItem;

        public ExprTester(FilterItem filterItem) {
            mFilterItem = filterItem;
        }

        @Override
        protected boolean evalId(@NonNull String id) {
            // Extract ID
            int idx = id.lastIndexOf('_');
            int intId;
            if (idx >= 0 && id.length() > (idx + 1)) {
                String part2 = id.substring(idx + 1);
                if (TextUtils.isDigitsOnly(part2)) {
                    intId = Integer.parseInt(part2);
                } else intId = 0;
            } else intId = 0;
            FilterOption option = mFilterItem.getFilterOptionForId(intId);
            if (option == null) {
                lastError = "Invalid ID '" + id + "'";
            }
            return option != null;
        }
    }

    private FinderViewModel mViewModel;
    private LinearProgressIndicator mProgress;
    private RecyclerView mRecyclerView;
    private FinderAdapter mAdapter;
    private FloatingActionButton mFilterBtn;
    private MultiSelectionView mMultiSelectionView;
    private FinderFilterAdapter mFinderFilterAdapter;
    private TextInputLayout mFinderFilterEditorLayout;
    private TextInputEditText mFinderFilterEditor;
    private final TextWatcher mFinderFilterEditorWatcher = new TextWatcher() {
        private ExprTester mExprTester;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (mExprTester == null) {
                mExprTester = new ExprTester(mViewModel.getFilterItem());
            }
            String text = s.toString();
            for (Map.Entry<String, Integer> entry : HIGHLIGHT_MAP.entrySet()) {
                String keyword = entry.getKey();
                int color = entry.getValue();
                int index = text.indexOf(keyword);
                while (index >= 0) {
                    s.setSpan(new ForegroundColorSpan(color), index, index + keyword.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    index = text.indexOf(keyword, index + keyword.length());
                }
            }
            if (!mExprTester.evaluate(s.toString())) {
                CharSequence error = mExprTester.getLastError();
                mFinderFilterEditorLayout.setError(error);
            } else {
                mFinderFilterEditorLayout.setError(null);
            }
        }
    };

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
        View view = View.inflate(this, R.layout.dialog_edit_filter_item, null);
        RecyclerView recyclerView = view.findViewById(android.R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(mFinderFilterAdapter);
        mFinderFilterEditor = view.findViewById(R.id.editor);
        mFinderFilterEditor.addTextChangedListener(mFinderFilterEditorWatcher);
        mFinderFilterEditorLayout = TextInputLayoutCompat.fromTextInputEditText(mFinderFilterEditor);
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
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.apply, (dialog, which) -> {
                    if (mFinderFilterEditorLayout.getError() == null) {
                        mViewModel.getFilterItem().setExpr(mFinderFilterEditor.getText().toString());
                    }
                    mViewModel.loadFilteredAppList(false);
                })
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
        mFinderFilterEditor.setText(mViewModel.getFilterItem().getExpr());
    }

    @Override
    public void onUpdateItem(int position, @NonNull EditFilterOptionFragment.WrappedFilterOption item) {
        mFinderFilterAdapter.update(position, item.filterOption);
        mFinderFilterEditor.setText(mViewModel.getFilterItem().getExpr());
    }

    @Override
    public void onDeleteItem(int position) {
        mFinderFilterAdapter.remove(position);
        mFinderFilterEditor.setText(mViewModel.getFilterItem().getExpr());
    }
}
