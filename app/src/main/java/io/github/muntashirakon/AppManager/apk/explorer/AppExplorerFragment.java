// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.explorer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.widget.MultiSelectionView;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;

public class AppExplorerFragment extends Fragment implements SearchView.OnQueryTextListener, SwipeRefreshLayout.OnRefreshListener {
    public static final String ARG_NAME = "name";
    public static final String ARG_DEPTH = "depth";

    @NonNull
    public static AppExplorerFragment getNewInstance(String name, int depth) {
        AppExplorerFragment fragment = new AppExplorerFragment();
        Bundle args = new Bundle();
        args.putString(ARG_NAME, name);
        args.putInt(ARG_DEPTH, depth);
        fragment.setArguments(args);
        return fragment;
    }

    private AppExplorerViewModel model;
    @Nullable
    private AppExplorerAdapter adapter;
    @Nullable
    private SwipeRefreshLayout swipeRefresh;
    @Nullable
    private MultiSelectionView multiSelectionView;
    private AppExplorerActivity activity;
    private String name;
    private int depth;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        model = new ViewModelProvider(requireActivity()).get(AppExplorerViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_fm, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        name = requireArguments().getString(ARG_NAME);
        depth = requireArguments().getInt(ARG_DEPTH);
        activity = (AppExplorerActivity) requireActivity();
        // Set title and subtitle
        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(model.getName());
            actionBar.setSubtitle("/" + (name == null ? "" : name));
        }
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(this);
        swipeRefresh.setColorSchemeColors(UIUtils.getAccentColor(activity));
        swipeRefresh.setProgressBackgroundColorSchemeColor(UIUtils.getPrimaryColor(activity));
        swipeRefresh.post(() -> swipeRefresh.setRefreshing(true));
        RecyclerView recyclerView = view.findViewById(R.id.list_item);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        new FastScrollerBuilder(recyclerView).useMd2Style().build();
        adapter = new AppExplorerAdapter(activity);
        recyclerView.setAdapter(adapter);
        multiSelectionView = view.findViewById(R.id.selection_view);
        multiSelectionView.hide();
        // Set observer
        model.observeFiles().observe(getViewLifecycleOwner(), fmItems -> {
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            adapter.setFmList(fmItems);
        });
        model.loadFiles(name, depth);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        // TODO: 11/7/21
        return false;
    }

    @Override
    public void onRefresh() {
        if (model != null) model.reload(name, depth);
    }
}
