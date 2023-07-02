// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.view.ProgressIndicatorCompat;
import io.github.muntashirakon.widget.MaterialAlertView;
import io.github.muntashirakon.widget.RecyclerView;
import io.github.muntashirakon.widget.SwipeRefreshLayout;

public abstract class AppDetailsFragment extends Fragment implements AdvancedSearchView.OnQueryTextListener,
        SwipeRefreshLayout.OnRefreshListener {
    @IntDef(value = {
            APP_INFO,
            ACTIVITIES,
            SERVICES,
            RECEIVERS,
            PROVIDERS,
            APP_OPS,
            USES_PERMISSIONS,
            PERMISSIONS,
            FEATURES,
            CONFIGURATIONS,
            SIGNATURES,
            SHARED_LIBRARIES
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Property {
    }

    public static final int APP_INFO = 0;
    public static final int ACTIVITIES = 1;
    public static final int SERVICES = 2;
    public static final int RECEIVERS = 3;
    public static final int PROVIDERS = 4;
    public static final int APP_OPS = 5;
    public static final int USES_PERMISSIONS = 6;
    public static final int PERMISSIONS = 7;
    public static final int FEATURES = 8;
    public static final int CONFIGURATIONS = 9;
    public static final int SIGNATURES = 10;
    public static final int SHARED_LIBRARIES = 11;

    @IntDef(value = {
            SORT_BY_NAME,
            SORT_BY_BLOCKED,
            SORT_BY_TRACKERS,
            SORT_BY_APP_OP_VALUES,
            SORT_BY_DENIED_APP_OPS,
            SORT_BY_DANGEROUS_PERMS,
            SORT_BY_DENIED_PERMS
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SortOrder {
    }

    public static final int SORT_BY_NAME = 0;
    public static final int SORT_BY_BLOCKED = 1;
    public static final int SORT_BY_TRACKERS = 2;
    public static final int SORT_BY_APP_OP_VALUES = 3;
    public static final int SORT_BY_DENIED_APP_OPS = 4;
    public static final int SORT_BY_DANGEROUS_PERMS = 5;
    public static final int SORT_BY_DENIED_PERMS = 6;

    public static final int[] sSortMenuItemIdsMap = {
            R.id.action_sort_by_name, R.id.action_sort_by_blocked_components,
            R.id.action_sort_by_tracker_components, R.id.action_sort_by_app_ops_values,
            R.id.action_sort_by_denied_app_ops, R.id.action_sort_by_dangerous_permissions,
            R.id.action_sort_by_denied_permissions};

    public static final String ARG_TYPE = "type";

    protected PackageManager packageManager;
    protected AppDetailsActivity activity;
    protected MaterialAlertView alertView;

    protected SwipeRefreshLayout swipeRefresh;
    protected LinearProgressIndicator progressIndicator;
    protected RecyclerView recyclerView;
    protected TextView emptyView;
    @Nullable
    protected AppDetailsViewModel viewModel;

    protected int colorQueryStringHighlight;

    @CallSuper
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        activity = (AppDetailsActivity) requireActivity();
        viewModel = new ViewModelProvider(activity).get(AppDetailsViewModel.class);
        packageManager = activity.getPackageManager();
        colorQueryStringHighlight = ColorCodes.getQueryStringHighlightColor(activity);
    }

    @Nullable
    @Override
    public final View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.pager_app_details, container, false);
    }

    @CallSuper
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Swipe refresh
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(this);
        recyclerView = view.findViewById(R.id.scrollView);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false));
        emptyView = view.findViewById(android.R.id.empty);
        recyclerView.setEmptyView(emptyView);
        progressIndicator = view.findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);
        ProgressIndicatorCompat.setVisibility(progressIndicator, true);
        alertView = view.findViewById(R.id.alert_text);
        alertView.setEndIconMode(MaterialAlertView.END_ICON_CUSTOM);
        alertView.setEndIconDrawable(com.google.android.material.R.drawable.mtrl_ic_cancel);
        alertView.setEndIconContentDescription(R.string.close);
        swipeRefresh.setOnChildScrollUpCallback((parent, child) -> recyclerView.canScrollVertically(-1));
    }

    @CallSuper
    @Override
    public void onResume() {
        super.onResume();
        swipeRefresh.setEnabled(true);
    }

    @CallSuper
    @Override
    public void onPause() {
        super.onPause();
        swipeRefresh.setEnabled(false);
    }

    @CallSuper
    @Override
    public void onDestroyView() {
        swipeRefresh.setRefreshing(false);
        swipeRefresh.clearAnimation();
        super.onDestroyView();
    }

    @Override
    public boolean onQueryTextSubmit(String query, int type) {
        return false;
    }
}
