/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.details;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.content.pm.PathPermission;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.PatternMatcher;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsComponentItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsPermissionItem;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.server.common.OpEntry;
import io.github.muntashirakon.AppManager.types.IconLoaderThread;
import io.github.muntashirakon.AppManager.types.RecyclerViewWithEmptyView;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public class AppDetailsFragment extends Fragment implements SearchView.OnQueryTextListener,
        SwipeRefreshLayout.OnRefreshListener {
    @IntDef(value = {
            NONE,
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

    public static final int NONE = -1;
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

    private static final int[] sSortMenuItemIdsMap = {
            R.id.action_sort_by_name, R.id.action_sort_by_blocked_components,
            R.id.action_sort_by_tracker_components, R.id.action_sort_by_app_ops_values,
            R.id.action_sort_by_denied_app_ops, R.id.action_sort_by_dangerous_permissions,
            R.id.action_sort_by_denied_permissions};

    private String mPackageName;
    private PackageManager mPackageManager;
    private AppDetailsActivity mActivity;
    private AppDetailsRecyclerAdapter mAdapter;
    private SwipeRefreshLayout mSwipeRefresh;
    private MenuItem blockingToggler;
    private LinearProgressIndicator mProgressIndicator;
    private TextView mRulesNotAppliedMsg;
    private boolean isExternalApk;
    @Property
    private int neededProperty;
    AppDetailsFragmentViewModel model;
    AppDetailsViewModel mainModel;

    private static int mColorGrey1;
    private static int mColorGrey2;
    private static int mColorRed;
    private static int mColorDisabled;
    private static int mColorRunning;
    private static int mColorTracker;

    // Load from saved instance if empty constructor is called.
    private boolean isEmptyFragmentConstructCalled = false;

    public AppDetailsFragment() {
        isEmptyFragmentConstructCalled = true;
    }

    public AppDetailsFragment(@Property int neededProperty) {
        this.neededProperty = neededProperty;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        model = new ViewModelProvider(this).get(AppDetailsFragmentViewModel.class);
        mActivity = (AppDetailsActivity) requireActivity();
        if (isEmptyFragmentConstructCalled) {
            neededProperty = model.getNeededProperty();
        } else model.setNeededProperty(neededProperty);
        mainModel = mActivity.model;
        mPackageManager = mActivity.getPackageManager();
        if (mActivity != null) {
            mColorGrey1 = Color.TRANSPARENT;
            mColorGrey2 = ContextCompat.getColor(mActivity, R.color.semi_transparent);
            mColorRed = ContextCompat.getColor(mActivity, R.color.red);
            mColorDisabled = ContextCompat.getColor(mActivity, R.color.disabled_user);
            mColorRunning = ContextCompat.getColor(mActivity, R.color.running);
            mColorTracker = ContextCompat.getColor(mActivity, R.color.tracker);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.pager_app_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Swipe refresh
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setColorSchemeColors(UIUtils.getAccentColor(mActivity));
        mSwipeRefresh.setProgressBackgroundColorSchemeColor(UIUtils.getPrimaryColor(mActivity));
        mSwipeRefresh.setOnRefreshListener(this);
        RecyclerViewWithEmptyView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(mActivity, LinearLayoutManager.VERTICAL, false));
        final TextView emptyView = view.findViewById(android.R.id.empty);
        emptyView.setText(getNotFoundString(neededProperty));
        recyclerView.setEmptyView(emptyView);
        mProgressIndicator = view.findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        showProgressIndicator(true);
        mRulesNotAppliedMsg = view.findViewById(R.id.alert_text);
        mRulesNotAppliedMsg.setVisibility(View.GONE);
        mRulesNotAppliedMsg.setText(R.string.rules_not_applied);
        mSwipeRefresh.setOnChildScrollUpCallback((parent, child) -> recyclerView.canScrollVertically(-1));
        if (mainModel == null) return;
        if (mPackageName == null) mPackageName = mainModel.getPackageName();
        // Set adapter only after package info is loaded
        new Thread(() -> {
            mPackageName = mainModel.getPackageName();
            if (mPackageName == null) {
                mainModel.setPackageInfo(false);
                mPackageName = mainModel.getPackageName();
            }
            isExternalApk = mainModel.getIsExternalApk();
            mAdapter = new AppDetailsRecyclerAdapter();
            runOnUiThread(() -> recyclerView.setAdapter(mAdapter));
        }).start();
        mainModel.get(neededProperty).observe(getViewLifecycleOwner(), appDetailsItems -> {
            if (mAdapter != null && mainModel.isPackageExist())
                mAdapter.setDefaultList(appDetailsItems);
        });
        mainModel.getRuleApplicationStatus().observe(getViewLifecycleOwner(), status -> {
            if (neededProperty > APP_INFO && neededProperty <= PROVIDERS) {
                mRulesNotAppliedMsg.setVisibility(status != AppDetailsViewModel.RULE_NOT_APPLIED ?
                        View.GONE : View.VISIBLE);
            }
        });
    }

    @Override
    public void onRefresh() {
        refreshDetails();
        mSwipeRefresh.setRefreshing(false);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        switch (neededProperty) {
            case ACTIVITIES:
            case PROVIDERS:
            case RECEIVERS:
            case SERVICES:
                if (!mainModel.getIsExternalApk() && AppPref.isRootEnabled()) {
                    inflater.inflate(R.menu.fragment_app_details_components_actions, menu);
                    blockingToggler = menu.findItem(R.id.action_toggle_blocking);
                    mainModel.getRuleApplicationStatus().observe(mActivity, status -> {
                        switch (status) {
                            case AppDetailsViewModel.RULE_APPLIED:
                                blockingToggler.setVisible(!AppPref.isGlobalBlockingEnabled());
                                blockingToggler.setTitle(R.string.menu_remove_rules);
                                break;
                            case AppDetailsViewModel.RULE_NOT_APPLIED:
                                blockingToggler.setVisible(!AppPref.isGlobalBlockingEnabled());
                                blockingToggler.setTitle(R.string.menu_apply_rules);
                                break;
                            case AppDetailsViewModel.RULE_NO_RULE:
                                blockingToggler.setVisible(false);
                        }
                    });
                } else inflater.inflate(R.menu.fragment_app_details_refresh_actions, menu);
                break;
            case APP_OPS:
                inflater.inflate(R.menu.fragment_app_details_app_ops_actions, menu);
                break;
            case USES_PERMISSIONS:
                if (!mainModel.getIsExternalApk()) {
                    inflater.inflate(R.menu.fragment_app_details_permissions_actions, menu);
                    break;
                }
            case CONFIGURATIONS:
            case FEATURES:
            case NONE:
            case PERMISSIONS:
            case SHARED_LIBRARIES:
            case SIGNATURES:
                inflater.inflate(R.menu.fragment_app_details_refresh_actions, menu);
                break;
            case APP_INFO:
                break;
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        if (isExternalApk) return;
        if (neededProperty == APP_INFO) super.onPrepareOptionsMenu(menu);
        else if (neededProperty <= PROVIDERS) {
            if (AppPref.isRootEnabled())
                menu.findItem(sSortMenuItemIdsMap[mainModel.getSortOrder(neededProperty)]).setChecked(true);
        } else if (neededProperty <= USES_PERMISSIONS)
            menu.findItem(sSortMenuItemIdsMap[mainModel.getSortOrder(neededProperty)]).setChecked(true);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh_details) {
            refreshDetails();
        } else if (id == R.id.action_toggle_blocking) {  // Components
            if (mainModel != null) new Thread(() -> mainModel.applyRules()).start();
        } else if (id == R.id.action_block_trackers) {  // Components
            new Thread(() -> {
                List<UserPackagePair> failedPkgList = ComponentUtils.blockTrackingComponents(Collections.singletonList(new UserPackagePair(mPackageName, Users.getCurrentUserHandle())));
                if (failedPkgList.size() > 0) {
                    runOnUiThread(() -> Toast.makeText(mActivity, R.string.failed_to_disable_trackers, Toast.LENGTH_SHORT).show());
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(mActivity, R.string.trackers_disabled_successfully, Toast.LENGTH_SHORT).show();
                        refreshDetails();
                    });
                }
                runOnUiThread(() -> mainModel.setRuleApplicationStatus());
            }).start();
        } else if (id == R.id.action_reset_to_default) {  // App ops
            new Thread(() -> {
                if (mainModel == null || !mainModel.resetAppOps()) {
                    runOnUiThread(() -> Toast.makeText(mActivity, R.string.failed_to_reset_app_ops, Toast.LENGTH_SHORT).show());
                } else runOnUiThread(() -> showProgressIndicator(true));
            }).start();
        } else if (id == R.id.action_deny_dangerous_app_ops) {  // App ops
            showProgressIndicator(true);
            new Thread(() -> {
                if (mainModel == null || !mainModel.ignoreDangerousAppOps()) {
                    runOnUiThread(() -> Toast.makeText(mActivity, R.string.failed_to_deny_dangerous_app_ops, Toast.LENGTH_SHORT).show());
                }
                runOnUiThread(this::refreshDetails);
            }).start();
        } else if (id == R.id.action_toggle_default_app_ops) {  // App ops
            showProgressIndicator(true);
            // Turn filter on/off
            boolean curr = (boolean) AppPref.get(AppPref.PrefKey.PREF_APP_OP_SHOW_DEFAULT_BOOL);
            AppPref.set(AppPref.PrefKey.PREF_APP_OP_SHOW_DEFAULT_BOOL, !curr);
            refreshDetails();
        } else if (id == R.id.action_deny_dangerous_permissions) {  // permissions
            showProgressIndicator(true);
            new Thread(() -> {
                if (mainModel == null || !mainModel.revokeDangerousPermissions()) {
                    runOnUiThread(() -> Toast.makeText(mActivity, R.string.failed_to_deny_dangerous_perms, Toast.LENGTH_SHORT).show());
                }
                runOnUiThread(this::refreshDetails);
            }).start();
        // Sorting
        } else if (id == R.id.action_sort_by_name) {  // All
            setSortBy(SORT_BY_NAME);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_blocked_components) {  // Components
            setSortBy(SORT_BY_BLOCKED);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_tracker_components) {  // Components
            setSortBy(SORT_BY_TRACKERS);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_app_ops_values) {  // App ops
            setSortBy(SORT_BY_APP_OP_VALUES);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_denied_app_ops) {  // App ops
            setSortBy(SORT_BY_DENIED_APP_OPS);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_dangerous_permissions) {  // App ops
            setSortBy(SORT_BY_DANGEROUS_PERMS);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_denied_permissions) {
            setSortBy(SORT_BY_DENIED_PERMS);
            item.setChecked(true);
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        mSwipeRefresh.setEnabled(true);
        if (mActivity.searchView != null) {
            if (neededProperty > APP_INFO && neededProperty <= PERMISSIONS) {
                mActivity.searchView.setVisibility(View.VISIBLE);
                mActivity.searchView.setOnQueryTextListener(this);
            } else mActivity.searchView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mSwipeRefresh.setEnabled(false);
    }

    @Override
    public void onDestroyView() {
        mSwipeRefresh.setRefreshing(false);
        mSwipeRefresh.clearAnimation();
        super.onDestroyView();
    }

    @Override
    public boolean onQueryTextChange(String searchQuery) {
        if (mainModel != null) {
            mainModel.setSearchQuery(searchQuery.toLowerCase(Locale.ROOT));
            mainModel.load(neededProperty);
        }
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    private void runOnUiThread(Runnable runnable) {
        mActivity.runOnUiThread(runnable);
    }

    private void setSortBy(@SortOrder int sortBy) {
        showProgressIndicator(true);
        mainModel.setSortOrder(sortBy, neededProperty);
        mainModel.load(neededProperty);
    }

    synchronized private void applyRules(String componentName, RulesStorageManager.Type type) {
        if (mainModel != null) {
            new Thread(() -> mainModel.updateRulesForComponent(componentName, type)).start();
        }
    }

    private void refreshDetails() {
        showProgressIndicator(true);
        mainModel.setIsPackageChanged();
    }

    /**
     * Return corresponding section's array
     */
    private int getNotFoundString(@Property int index) {
        switch (index) {
            case SERVICES:
                return R.string.no_service;
            case RECEIVERS:
                return R.string.no_receivers;
            case PROVIDERS:
                return R.string.no_providers;
            case APP_OPS:
                if (isExternalApk) return R.string.external_apk_no_app_op;
                if (AppPref.isRootOrAdbEnabled()) return R.string.no_app_ops;
                else return R.string.only_works_in_root_mode;
            case USES_PERMISSIONS:
            case PERMISSIONS:
                return R.string.require_no_permission;
            case FEATURES:
                return R.string.no_feature;
            case CONFIGURATIONS:
                return R.string.no_configurations;
            case SIGNATURES:
                return R.string.no_signatures;
            case SHARED_LIBRARIES:
                return R.string.no_shared_libs;
            case ACTIVITIES:
            case APP_INFO:
            case NONE:
            default:
                return R.string.no_activities;
        }
    }

    private void showProgressIndicator(boolean show) {
        if (mProgressIndicator == null) return;
        if (show) mProgressIndicator.show();
        else mProgressIndicator.hide();
    }

    public static boolean isComponentDisabled(@NonNull PackageManager pm, @NonNull ComponentInfo componentInfo) {
        String className = componentInfo.name;
        if (componentInfo instanceof ActivityInfo
                && ((ActivityInfo) componentInfo).targetActivity != null) {
            className = ((ActivityInfo) componentInfo).targetActivity;
        }
        ComponentName componentName = new ComponentName(componentInfo.packageName, className);
        int componentEnabledSetting = pm.getComponentEnabledSetting(componentName);

        switch (componentEnabledSetting) {
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
                return true;
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                return false;
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
            default:
                return !componentInfo.isEnabled();
        }
    }

    @NonNull
    private String permAppOp(String s) {
        String opStr = AppOpsManager.permissionToOp(s);
        return opStr != null ? "\nAppOp: " + opStr : "";
    }

    @UiThread
    private class AppDetailsRecyclerAdapter extends RecyclerView.Adapter<AppDetailsRecyclerAdapter.ViewHolder> {
        @NonNull
        private List<AppDetailsItem> mAdapterList;
        @Property
        private int requestedProperty;
        private String mConstraint;
        private Boolean isRootEnabled = true;
        private Boolean isADBEnabled = true;
        private List<String> runningServices;

        AppDetailsRecyclerAdapter() {
            mAdapterList = new ArrayList<>();
        }

        void setDefaultList(@NonNull List<AppDetailsItem> list) {
            isRootEnabled = AppPref.isRootEnabled();
            isADBEnabled = AppPref.isAdbEnabled();
            requestedProperty = neededProperty;
            mConstraint = mainModel.getSearchQuery();
            mAdapterList = list;
            showProgressIndicator(false);
            notifyDataSetChanged();
            new Thread(() -> {
                if (requestedProperty == SERVICES && (isRootEnabled || isADBEnabled) && !isExternalApk) {
                    runningServices = PackageUtils.getRunningServicesForPackage(mPackageName);
                }
            }).start();
        }

        void set(int currentIndex, AppDetailsItem appDetailsItem) {
            mAdapterList.set(currentIndex, appDetailsItem);
            notifyItemChanged(currentIndex);
            // Update the value in the app ops list in view model
            if (neededProperty == APP_OPS) {
                new Thread(() -> mainModel.setAppOp(appDetailsItem)).start();
            }
        }

        /**
         * ViewHolder to use recycled views efficiently. Fields names are not expressive because we use
         * the same holder for any kind of view, and view are not all sames.
         */
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView1;
            TextView textView2;
            TextView textView3;
            TextView textView4;
            TextView textView5;
            TextView textView6;
            TextView textView7;
            TextView textView8;
            ImageView imageView;
            ImageButton blockBtn;
            Button createBtn;
            Button editBtn;
            Button launchBtn;
            SwitchMaterial toggleSwitch;
            IconLoaderThread iconLoader;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                switch (requestedProperty) {
                    case ACTIVITIES:
                        imageView = itemView.findViewById(R.id.icon);
                        textView2 = itemView.findViewById(R.id.name);
                        textView3 = itemView.findViewById(R.id.taskAffinity);
                        textView4 = itemView.findViewById(R.id.launchMode);
                        textView5 = itemView.findViewById(R.id.orientation);
                        textView6 = itemView.findViewById(R.id.softInput);
                        launchBtn = itemView.findViewById(R.id.launch);
                        blockBtn = itemView.findViewById(R.id.block_component);
                        createBtn = itemView.findViewById(R.id.create_shortcut_btn);
                        editBtn = itemView.findViewById(R.id.edit_shortcut_btn);
                        itemView.findViewById(R.id.label).setVisibility(View.GONE);
                        break;
                    case SERVICES:
                        imageView = itemView.findViewById(R.id.icon);
                        textView1 = itemView.findViewById(R.id.label);
                        textView2 = itemView.findViewById(R.id.name);
                        textView3 = itemView.findViewById(R.id.orientation);
                        blockBtn = itemView.findViewById(R.id.block_component);
                        itemView.findViewById(R.id.taskAffinity).setVisibility(View.GONE);
                        itemView.findViewById(R.id.launchMode).setVisibility(View.GONE);
                        itemView.findViewById(R.id.softInput).setVisibility(View.GONE);
                        itemView.findViewById(R.id.launch).setVisibility(View.GONE);
                        itemView.findViewById(R.id.create_shortcut_btn).setVisibility(View.GONE);
                        itemView.findViewById(R.id.edit_shortcut_btn).setVisibility(View.GONE);
                        break;
                    case RECEIVERS:
                        imageView = itemView.findViewById(R.id.icon);
                        textView1 = itemView.findViewById(R.id.label);
                        textView2 = itemView.findViewById(R.id.name);
                        textView3 = itemView.findViewById(R.id.taskAffinity);
                        textView4 = itemView.findViewById(R.id.launchMode);
                        textView5 = itemView.findViewById(R.id.orientation);
                        textView6 = itemView.findViewById(R.id.softInput);
                        blockBtn = itemView.findViewById(R.id.block_component);
                        itemView.findViewById(R.id.launch).setVisibility(View.GONE);
                        itemView.findViewById(R.id.create_shortcut_btn).setVisibility(View.GONE);
                        itemView.findViewById(R.id.edit_shortcut_btn).setVisibility(View.GONE);
                        break;
                    case PROVIDERS:
                        imageView = itemView.findViewById(R.id.icon);
                        textView1 = itemView.findViewById(R.id.label);
                        textView2 = itemView.findViewById(R.id.name);
                        textView3 = itemView.findViewById(R.id.launchMode);
                        textView4 = itemView.findViewById(R.id.orientation);
                        textView5 = itemView.findViewById(R.id.softInput);
                        textView6 = itemView.findViewById(R.id.taskAffinity);
                        blockBtn = itemView.findViewById(R.id.block_component);
                        itemView.findViewById(R.id.launch).setVisibility(View.GONE);
                        itemView.findViewById(R.id.create_shortcut_btn).setVisibility(View.GONE);
                        itemView.findViewById(R.id.edit_shortcut_btn).setVisibility(View.GONE);
                        break;
                    case PERMISSIONS:
                        imageView = itemView.findViewById(R.id.icon);
                        textView1 = itemView.findViewById(R.id.label);
                        textView2 = itemView.findViewById(R.id.name);
                        textView3 = itemView.findViewById(R.id.taskAffinity);
                        textView4 = itemView.findViewById(R.id.orientation);
                        textView5 = itemView.findViewById(R.id.launchMode);
                        itemView.findViewById(R.id.softInput).setVisibility(View.GONE);
                        itemView.findViewById(R.id.launch).setVisibility(View.GONE);
                        itemView.findViewById(R.id.create_shortcut_btn).setVisibility(View.GONE);
                        itemView.findViewById(R.id.edit_shortcut_btn).setVisibility(View.GONE);
                        itemView.findViewById(R.id.block_component).setVisibility(View.GONE);
                        break;
                    case APP_OPS:
                        textView1 = itemView.findViewById(R.id.op_name);
                        textView2 = itemView.findViewById(R.id.perm_description);
                        textView3 = itemView.findViewById(R.id.perm_protection_level);
                        textView4 = itemView.findViewById(R.id.perm_package_name);
                        textView5 = itemView.findViewById(R.id.perm_group);
                        textView6 = itemView.findViewById(R.id.perm_name);
                        textView7 = itemView.findViewById(R.id.op_mode_running_duration);
                        textView8 = itemView.findViewById(R.id.op_accept_reject_time);
                        toggleSwitch = itemView.findViewById(R.id.perm_toggle_btn);
                        break;
                    case USES_PERMISSIONS:
                        textView1 = itemView.findViewById(R.id.perm_name);
                        textView2 = itemView.findViewById(R.id.perm_description);
                        textView3 = itemView.findViewById(R.id.perm_protection_level);
                        textView4 = itemView.findViewById(R.id.perm_package_name);
                        textView5 = itemView.findViewById(R.id.perm_group);
                        toggleSwitch = itemView.findViewById(R.id.perm_toggle_btn);
                        break;
                    case FEATURES:
                        textView1 = itemView.findViewById(R.id.name);
                        textView2 = itemView.findViewById(R.id.flags);
                        textView3 = itemView.findViewById(R.id.gles_ver);
                        break;
                    case CONFIGURATIONS:
                        textView1 = itemView.findViewById(R.id.reqgles);
                        textView2 = itemView.findViewById(R.id.reqfea);
                        textView3 = itemView.findViewById(R.id.reqkey);
                        textView4 = itemView.findViewById(R.id.reqnav);
                        textView5 = itemView.findViewById(R.id.reqtouch);
                        break;
                    case SIGNATURES:
                    case SHARED_LIBRARIES:
                    case NONE:
                    case APP_INFO:
                    default:
                        break;
                }
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            @SuppressLint("InflateParams") final View view;
            switch (requestedProperty) {
                case ACTIVITIES:
                case SERVICES:
                case RECEIVERS:
                case PROVIDERS:
                case PERMISSIONS:
                case NONE:
                case APP_INFO:
                default:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_primary, parent, false);
                    break;
                case APP_OPS:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_appop, parent, false);
                    break;
                case USES_PERMISSIONS:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_perm, parent, false);
                    break;
                case FEATURES:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_secondary, parent, false);
                    break;
                case CONFIGURATIONS:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_tertiary, parent, false);
                    break;
                case SIGNATURES:
                case SHARED_LIBRARIES:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_text_view, parent, false);
                    break;
            }
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            switch (requestedProperty) {
                case SERVICES:
                    getServicesView(holder, position);
                    break;
                case RECEIVERS:
                    getReceiverView(holder, position);
                    break;
                case PROVIDERS:
                    getProviderView(holder, position);
                    break;
                case APP_OPS:
                    getAppOpsView(holder, position);
                    break;
                case USES_PERMISSIONS:
                    getUsesPermissionsView(holder, position);
                    break;
                case PERMISSIONS:
                    getPermissionsView(holder, position);
                    break;
                case FEATURES:
                    getFeaturesView(holder, position);
                    break;
                case CONFIGURATIONS:
                    getConfigurationView(holder, position);
                    break;
                case SIGNATURES:
                    getSignatureView(holder, position);
                    break;
                case SHARED_LIBRARIES:
                    getSharedLibsView(holder, position);
                    break;
                case ACTIVITIES:
                case NONE:
                case APP_INFO:
                default:
                    getActivityView(holder, position);
                    break;
            }
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            return mAdapterList.size();
        }

        private void getActivityView(@NonNull ViewHolder holder, int index) {
            final View view = holder.itemView;
            final AppDetailsComponentItem appDetailsItem = (AppDetailsComponentItem) mAdapterList.get(index);
            final ActivityInfo activityInfo = (ActivityInfo) appDetailsItem.vanillaItem;
            final String activityName = appDetailsItem.name;
            final boolean isDisabled = !isExternalApk && isComponentDisabled(mPackageManager, activityInfo);
            // Background color: regular < tracker < disabled < blocked
            if (!isExternalApk && appDetailsItem.isBlocked) view.setBackgroundColor(mColorRed);
            else if (isDisabled) view.setBackgroundColor(mColorDisabled);
            else if (appDetailsItem.isTracker) view.setBackgroundColor(mColorTracker);
            else view.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            // Name
            if (mConstraint != null && activityName.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.textView2.setText(UIUtils.getHighlightedText(activityName, mConstraint, mColorRed));
            } else {
                holder.textView2.setText(activityName.startsWith(mPackageName) ?
                        activityName.replaceFirst(mPackageName, "") : activityName);
            }
            // Icon
            if (holder.iconLoader != null) holder.iconLoader.interrupt();
            holder.iconLoader = new IconLoaderThread(holder.imageView, activityInfo);
            holder.iconLoader.start();
            // TaskAffinity
            holder.textView3.setText(String.format(Locale.ROOT, "%s: %s",
                    getString(R.string.task_affinity), activityInfo.taskAffinity));
            // LaunchMode
            holder.textView4.setText(String.format(Locale.ROOT, "%s: %s | %s: %s",
                    getString(R.string.launch_mode), getString(Utils.getLaunchMode(activityInfo.launchMode)),
                    getString(R.string.orientation), getString(Utils.getOrientationString(activityInfo.screenOrientation))));
            // Orientation
            holder.textView5.setText(Utils.getActivitiesFlagsString(activityInfo.flags));
            // SoftInput
            holder.textView6.setText(String.format(Locale.ROOT, "%s: %s | %s",
                    getString(R.string.soft_input), Utils.getSoftInputString(activityInfo.softInputMode),
                    (activityInfo.permission == null ? getString(R.string.require_no_permission) : activityInfo.permission)));
            // Label
            Button launch = holder.launchBtn;
            String appLabel = activityInfo.applicationInfo.loadLabel(mPackageManager).toString();
            String activityLabel = activityInfo.loadLabel(mPackageManager).toString();
            launch.setText(activityLabel.equals(appLabel) || TextUtils.isEmpty(activityLabel) ?
                    Utils.camelCaseToSpaceSeparatedString(Utils.getLastComponent(activityInfo.name))
                    : activityLabel);
            boolean isExported = activityInfo.exported;
            launch.setEnabled(isExported && !isDisabled && !appDetailsItem.isBlocked);
            if (isExported && !isDisabled && !appDetailsItem.isBlocked) {
                launch.setOnClickListener(v -> {
                    Intent intent = new Intent();
                    intent.setClassName(mPackageName, activityName);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        if (getActivity() != null)
                            ActivityCompat.recreate(getActivity());
                        Toast.makeText(mActivity, e.toString(), Toast.LENGTH_LONG).show();
                    }
                });
                holder.createBtn.setOnClickListener(v -> {
                    String iconResourceName = null;
                    try {
                        ComponentName activity = new ComponentName(activityInfo.packageName, activityInfo.name);
                        iconResourceName = mPackageManager.getResourcesForActivity(activity)
                                .getResourceName(activityInfo.getIconResource());
                    } catch (PackageManager.NameNotFoundException e) {
                        Toast.makeText(mActivity, e.toString(), Toast.LENGTH_LONG).show();
                    } catch (Resources.NotFoundException ignore) {
                    }
                    LauncherIconCreator.createLauncherIcon(getActivity(), activityInfo,
                            (String) activityInfo.loadLabel(mPackageManager),
                            activityInfo.loadIcon(mPackageManager), iconResourceName);
                });
                holder.editBtn.setOnClickListener(v -> {
                    DialogFragment dialog = new EditShortcutDialogFragment();
                    Bundle args = new Bundle();
                    args.putParcelable(EditShortcutDialogFragment.ARG_ACTIVITY_INFO, activityInfo);
                    dialog.setArguments(args);
                    dialog.show(getParentFragmentManager(), EditShortcutDialogFragment.TAG);
                });
                holder.createBtn.setVisibility(View.VISIBLE);
                holder.editBtn.setVisibility(View.VISIBLE);
            } else {
                holder.createBtn.setVisibility(View.GONE);
                holder.editBtn.setVisibility(View.GONE);
            }
            // Blocking
            if (isRootEnabled && !isExternalApk) {
                if (appDetailsItem.isBlocked) {
                    holder.blockBtn.setImageDrawable(ContextCompat.getDrawable(mActivity, R.drawable.ic_restore_black_24dp));
                } else {
                    holder.blockBtn.setImageDrawable(ContextCompat.getDrawable(mActivity, R.drawable.ic_block_black_24dp));
                }
                holder.blockBtn.setVisibility(View.VISIBLE);
                holder.blockBtn.setOnClickListener(v -> {
                    applyRules(activityName, RulesStorageManager.Type.ACTIVITY);
                    appDetailsItem.isBlocked = !appDetailsItem.isBlocked;
                    set(index, appDetailsItem);
                });
            } else holder.blockBtn.setVisibility(View.GONE);
        }

        private void getServicesView(@NonNull ViewHolder holder, int index) {
            View view = holder.itemView;
            final AppDetailsComponentItem appDetailsItem = (AppDetailsComponentItem) mAdapterList.get(index);
            final ServiceInfo serviceInfo = (ServiceInfo) appDetailsItem.vanillaItem;
            // Background color: regular < tracker < disabled < blocked < running
            if (runningServices != null && runningServices.contains(serviceInfo.name))
                view.setBackgroundColor(mColorRunning);
            else if (!isExternalApk && appDetailsItem.isBlocked) view.setBackgroundColor(mColorRed);
            else if (!isExternalApk && isComponentDisabled(mPackageManager, serviceInfo))
                view.setBackgroundColor(mColorDisabled);
            else if (appDetailsItem.isTracker) view.setBackgroundColor(mColorTracker);
            else view.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            // Label
            holder.textView1.setText(Utils.camelCaseToSpaceSeparatedString(Utils.getLastComponent(serviceInfo.name)));
            // Name
            if (mConstraint != null && serviceInfo.name.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.textView2.setText(UIUtils.getHighlightedText(serviceInfo.name, mConstraint, mColorRed));
            } else {
                holder.textView2.setText(serviceInfo.name.startsWith(mPackageName) ?
                        serviceInfo.name.replaceFirst(mPackageName, "") : serviceInfo.name);
            }
            // Icon
            if (holder.iconLoader != null) holder.iconLoader.interrupt();
            holder.iconLoader = new IconLoaderThread(holder.imageView, serviceInfo);
            holder.iconLoader.start();
            // Flags and Permission
            holder.textView3.setText(String.format(Locale.ROOT, "%s\n%s",
                    Utils.getServiceFlagsString(serviceInfo.flags),
                    (serviceInfo.permission != null ? serviceInfo.permission : "")));
            // Blocking
            if (isRootEnabled && !isExternalApk) {
                if (appDetailsItem.isBlocked) {
                    holder.blockBtn.setImageDrawable(ContextCompat.getDrawable(mActivity, R.drawable.ic_restore_black_24dp));
                } else {
                    holder.blockBtn.setImageDrawable(ContextCompat.getDrawable(mActivity, R.drawable.ic_block_black_24dp));
                }
                holder.blockBtn.setVisibility(View.VISIBLE);
                holder.blockBtn.setOnClickListener(v -> {
                    applyRules(serviceInfo.name, RulesStorageManager.Type.SERVICE);
                    appDetailsItem.isBlocked = !appDetailsItem.isBlocked;
                    set(index, appDetailsItem);
                });
            } else holder.blockBtn.setVisibility(View.GONE);
        }

        private void getReceiverView(@NonNull ViewHolder holder, int index) {
            View view = holder.itemView;
            final AppDetailsComponentItem appDetailsItem = (AppDetailsComponentItem) mAdapterList.get(index);
            final ActivityInfo activityInfo = (ActivityInfo) appDetailsItem.vanillaItem;
            // Background color: regular < tracker < disabled < blocked
            if (!isExternalApk && appDetailsItem.isBlocked) view.setBackgroundColor(mColorRed);
            else if (!isExternalApk && isComponentDisabled(mPackageManager, activityInfo))
                view.setBackgroundColor(mColorDisabled);
            else if (appDetailsItem.isTracker) view.setBackgroundColor(mColorTracker);
            else view.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            // Label
            holder.textView1.setText(Utils.camelCaseToSpaceSeparatedString(Utils.getLastComponent(activityInfo.name)));
            // Name
            if (mConstraint != null && activityInfo.name.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.textView2.setText(UIUtils.getHighlightedText(activityInfo.name, mConstraint, mColorRed));
            } else {
                holder.textView2.setText(activityInfo.name.startsWith(mPackageName) ?
                        activityInfo.name.replaceFirst(mPackageName, "")
                        : activityInfo.name);
            }
            // Icon
            if (holder.iconLoader != null) holder.iconLoader.interrupt();
            holder.iconLoader = new IconLoaderThread(holder.imageView, activityInfo);
            holder.iconLoader.start();
            // TaskAffinity
            holder.textView3.setText(String.format(Locale.ROOT, "%s: %s",
                    getString(R.string.task_affinity), activityInfo.taskAffinity));
            // LaunchMode
            holder.textView4.setText(String.format(Locale.ROOT, "%s: %s | %s: %s",
                    getString(R.string.launch_mode), getString(Utils.getLaunchMode(activityInfo.launchMode)),
                    getString(R.string.orientation), getString(Utils.getOrientationString(activityInfo.screenOrientation))));
            // Orientation
            holder.textView5.setText(activityInfo.permission == null ? getString(R.string.require_no_permission) : activityInfo.permission);
            // SoftInput
            holder.textView6.setText(String.format(Locale.ROOT, "%s: %s",
                    getString(R.string.soft_input), Utils.getSoftInputString(activityInfo.softInputMode)));
            // Blocking
            if (isRootEnabled && !isExternalApk) {
                if (appDetailsItem.isBlocked) {
                    holder.blockBtn.setImageDrawable(ContextCompat.getDrawable(mActivity, R.drawable.ic_restore_black_24dp));
                } else {
                    holder.blockBtn.setImageDrawable(ContextCompat.getDrawable(mActivity, R.drawable.ic_block_black_24dp));
                }
                holder.blockBtn.setVisibility(View.VISIBLE);
                holder.blockBtn.setOnClickListener(v -> {
                    applyRules(activityInfo.name, RulesStorageManager.Type.RECEIVER);
                    appDetailsItem.isBlocked = !appDetailsItem.isBlocked;
                    set(index, appDetailsItem);
                });
            } else holder.blockBtn.setVisibility(View.GONE);
        }

        private void getProviderView(@NonNull ViewHolder holder, int index) {
            View view = holder.itemView;
            final AppDetailsComponentItem appDetailsItem = (AppDetailsComponentItem) mAdapterList.get(index);
            final ProviderInfo providerInfo = (ProviderInfo) appDetailsItem.vanillaItem;
            final String providerName = providerInfo.name;
            // Background color: regular < tracker < disabled < blocked
            if (!isExternalApk && appDetailsItem.isBlocked) view.setBackgroundColor(mColorRed);
            else if (!isExternalApk && isComponentDisabled(mPackageManager, providerInfo))
                view.setBackgroundColor(mColorDisabled);
            else if (appDetailsItem.isTracker) view.setBackgroundColor(mColorTracker);
            else view.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            // Label
            holder.textView1.setText(Utils.camelCaseToSpaceSeparatedString(Utils.getLastComponent(providerName)));
            // Icon
            if (holder.iconLoader != null) holder.iconLoader.interrupt();
            holder.iconLoader = new IconLoaderThread(holder.imageView, providerInfo);
            holder.iconLoader.start();
            // Uri permission
            holder.textView3.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.grant_uri_permission), providerInfo.grantUriPermissions));
            // Path permissions
            PathPermission[] pathPermissions = providerInfo.pathPermissions;
            String finalString;
            if (pathPermissions != null) {
                StringBuilder builder = new StringBuilder();
                String read = getString(R.string.read);
                String write = getString(R.string.write);
                for (PathPermission permission : pathPermissions) {
                    builder.append(read).append(": ").append(permission.getReadPermission());
                    builder.append("/");
                    builder.append(write).append(": ").append(permission.getWritePermission());
                    builder.append(", ");
                }
                Utils.checkStringBuilderEnd(builder);
                finalString = builder.toString();
            } else finalString = "null";
            holder.textView4.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.path_permissions), finalString)); // +"\n"+providerInfo.readPermission +"\n"+providerInfo.writePermission);
            // Pattern matchers
            PatternMatcher[] patternMatchers = providerInfo.uriPermissionPatterns;
            String finalString1;
            if (patternMatchers != null) {
                StringBuilder builder = new StringBuilder();
                for (PatternMatcher patternMatcher : patternMatchers) {
                    builder.append(patternMatcher.toString());
                    builder.append(", ");
                }
                Utils.checkStringBuilderEnd(builder);
                finalString1 = builder.toString();
            } else
                finalString1 = "null";
            holder.textView5.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.patterns_allowed), finalString1));
            // Authority
            holder.textView6.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.authority), providerInfo.authority));
            // Name
            if (mConstraint != null && providerName.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.textView2.setText(UIUtils.getHighlightedText(providerName, mConstraint, mColorRed));
            } else {
                holder.textView2.setText(providerName.startsWith(mPackageName) ?
                        providerName.replaceFirst(mPackageName, "") : providerName);
            }
            // Blocking
            if (isRootEnabled && !isExternalApk) {
                if (appDetailsItem.isBlocked) {
                    holder.blockBtn.setImageDrawable(ContextCompat.getDrawable(mActivity, R.drawable.ic_restore_black_24dp));
                } else {
                    holder.blockBtn.setImageDrawable(ContextCompat.getDrawable(mActivity, R.drawable.ic_block_black_24dp));
                }
                holder.blockBtn.setVisibility(View.VISIBLE);
                holder.blockBtn.setOnClickListener(v -> {
                    applyRules(providerName, RulesStorageManager.Type.PROVIDER);
                    appDetailsItem.isBlocked = !appDetailsItem.isBlocked;
                    set(index, appDetailsItem);
                    notifyItemChanged(index);
                });
            } else holder.blockBtn.setVisibility(View.GONE);
        }

        private void getAppOpsView(@NonNull ViewHolder holder, int index) {
            View view = holder.itemView;
            AppDetailsItem item = mAdapterList.get(index);
            OpEntry opEntry = (OpEntry) item.vanillaItem;
            final String opStr = mAdapterList.get(index).name;
            boolean isDangerousOp = false;
            PermissionInfo permissionInfo = null;
            try {
                String permName = AppOpsManager.opToPermission(opEntry.getOp());
                if (permName != null)
                    permissionInfo = mPackageManager.getPermissionInfo(permName, PackageManager.GET_META_DATA);
            } catch (PackageManager.NameNotFoundException | IllegalArgumentException | IndexOutOfBoundsException ignore) {
            }
            // Set op name
            SpannableStringBuilder opName = new SpannableStringBuilder("(" + opEntry.getOp() + ") ");
            if (item.name.equals(String.valueOf(opEntry.getOp()))) {
                opName.append(getString(R.string.unknown_op));
            } else {
                if (mConstraint != null && opStr.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                    // Highlight searched query
                    opName.append(UIUtils.getHighlightedText(opStr, mConstraint, mColorRed));
                } else opName.append(opStr);
            }
            holder.textView1.setText(opName);
            // Set op mode, running and duration
            String opRunningInfo = mActivity.getString(R.string.mode) + ": " + AppOpsManager.modeToName(opEntry.getMode());
            if (opEntry.isRunning()) opRunningInfo += ", " + mActivity.getString(R.string.running);
            if (opEntry.getDuration() != 0)
                opRunningInfo += ", " + mActivity.getString(R.string.duration) + ": " +
                        Utils.getFormattedDuration(mActivity, opEntry.getDuration(), true);
            holder.textView7.setText(opRunningInfo);
            // Set accept time and/or reject time
            long currentTime = System.currentTimeMillis();
            if (opEntry.getTime() != 0 || opEntry.getRejectTime() != 0) {
                String opTime = "";
                if (opEntry.getTime() != 0)
                    opTime = mActivity.getString(R.string.accept_time) + ": " +
                            Utils.getFormattedDuration(mActivity, currentTime - opEntry.getTime())
                            + " " + mActivity.getString(R.string.ago);
                if (opEntry.getRejectTime() != 0)
                    opTime += (opTime.equals("") ? "" : "\n") + mActivity.getString(R.string.reject_time)
                            + ": " + Utils.getFormattedDuration(mActivity, currentTime - opEntry.getRejectTime())
                            + " " + mActivity.getString(R.string.ago);
                holder.textView8.setVisibility(View.VISIBLE);
                holder.textView8.setText(opTime);
            } else holder.textView8.setVisibility(View.GONE);
            // Set others
            if (permissionInfo != null) {
                // Set permission name
                holder.textView6.setVisibility(View.VISIBLE);
                holder.textView6.setText(String.format(Locale.ROOT, "%s: %s", mActivity.getString(R.string.permission_name), permissionInfo.name));
                // Description
                CharSequence description = permissionInfo.loadDescription(mPackageManager);
                if (description != null) {
                    holder.textView2.setVisibility(View.VISIBLE);
                    holder.textView2.setText(description);
                } else holder.textView2.setVisibility(View.GONE);
                // Protection level
                String protectionLevel = Utils.getProtectionLevelString(permissionInfo);
                holder.textView3.setVisibility(View.VISIBLE);
                holder.textView3.setText(String.format(Locale.ROOT, "\u2691 %s", protectionLevel));
                if (protectionLevel.contains("dangerous")) isDangerousOp = true;
                // Set package name
                if (permissionInfo.packageName != null) {
                    holder.textView4.setVisibility(View.VISIBLE);
                    holder.textView4.setText(String.format(Locale.ROOT, "%s: %s",
                            mActivity.getString(R.string.package_name), permissionInfo.packageName));
                } else holder.textView4.setVisibility(View.GONE);
                // Set group name
                if (permissionInfo.group != null) {
                    holder.textView5.setVisibility(View.VISIBLE);
                    holder.textView5.setText(String.format(Locale.ROOT, "%s: %s",
                            mActivity.getString(R.string.group), permissionInfo.group));
                } else holder.textView5.setVisibility(View.GONE);
            } else {
                holder.textView2.setVisibility(View.GONE);
                holder.textView3.setVisibility(View.GONE);
                holder.textView4.setVisibility(View.GONE);
                holder.textView5.setVisibility(View.GONE);
                holder.textView6.setVisibility(View.GONE);
            }
            // Set background
            if (isDangerousOp)
                view.setBackgroundColor(ContextCompat.getColor(mActivity, R.color.red));
            else view.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            // Op Switch
            holder.toggleSwitch.setVisibility(View.VISIBLE);
            // op granted
            holder.toggleSwitch.setChecked(opEntry.getMode() == AppOpsManager.MODE_ALLOWED);
            holder.toggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (buttonView.isPressed()) {
                    new Thread(() -> {
                        int opMode = isChecked ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_IGNORED;
                        if (mainModel.setAppOp(opEntry.getOp(), opMode)) {
                            OpEntry opEntry1 = new OpEntry(opEntry.getOp(), opMode, opEntry.getTime(),
                                    opEntry.getRejectTime(), opEntry.getDuration(),
                                    opEntry.getProxyUid(), opEntry.getProxyPackageName());
                            AppDetailsItem appDetailsItem = new AppDetailsItem(opEntry1);
                            appDetailsItem.name = item.name;
                            runOnUiThread(() -> set(index, appDetailsItem));
                        } else {
                            runOnUiThread(() -> {
                                Toast.makeText(mActivity, isChecked ? R.string.failed_to_enable_op : R.string.app_op_cannot_be_disabled, Toast.LENGTH_LONG).show();
                                notifyItemChanged(index);
                            });
                        }
                    }).start();
                }
            });
        }

        /**
         * We do not need complex views, Use recycled view if possible
         */
        private void getUsesPermissionsView(@NonNull ViewHolder holder, int index) {
            View view = holder.itemView;
            view.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            AppDetailsPermissionItem permissionItem = (AppDetailsPermissionItem) mAdapterList.get(index);
            @NonNull PermissionInfo permissionInfo = (PermissionInfo) permissionItem.vanillaItem;
            final String permName = permissionInfo.name;
            // Set permission name
            if (mConstraint != null && permName.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.textView1.setText(UIUtils.getHighlightedText(permName, mConstraint, mColorRed));
            } else holder.textView1.setText(permName);
            // Set others
            // Description
            CharSequence description = permissionInfo.loadDescription(mPackageManager);
            if (description != null) {
                holder.textView2.setVisibility(View.VISIBLE);
                holder.textView2.setText(description);
            } else holder.textView2.setVisibility(View.GONE);
            // Protection level
            String protectionLevel = Utils.getProtectionLevelString(permissionInfo);
            protectionLevel += '|' + (permissionItem.isGranted ? "granted" : "revoked");
            holder.textView3.setText(String.format(Locale.ROOT, "\u2691 %s", protectionLevel));
            if (permissionItem.isDangerous)
                view.setBackgroundColor(ContextCompat.getColor(mActivity, R.color.red));
            // Set package name
            if (permissionInfo.packageName != null) {
                holder.textView4.setVisibility(View.VISIBLE);
                holder.textView4.setText(String.format("%s: %s",
                        mActivity.getString(R.string.package_name), permissionInfo.packageName));
            } else holder.textView4.setVisibility(View.GONE);
            // Set group name
            if (permissionInfo.group != null) {
                holder.textView5.setVisibility(View.VISIBLE);
                holder.textView5.setText(String.format("%s: %s",
                        mActivity.getString(R.string.group), permissionInfo.group));
            } else holder.textView5.setVisibility(View.GONE);
            // Permission Switch
            int sdkVersion = mainModel.getPackageInfo().applicationInfo.targetSdkVersion;
            if ((isRootEnabled || isADBEnabled) && !isExternalApk && ((permissionItem.isDangerous
                    && sdkVersion > 23) || protectionLevel.contains("development")
                    || permissionItem.appOp != AppOpsManager.OP_NONE)) {
                holder.toggleSwitch.setVisibility(View.VISIBLE);
                holder.toggleSwitch.setChecked(permissionItem.isGranted);
                holder.toggleSwitch.setOnCheckedChangeListener((buttonView, isGranted) -> {
                    if (buttonView.isPressed()) {
                        new Thread(() -> {
                            if (mainModel.setPermission(permName, isGranted)) {
                                AppDetailsPermissionItem appDetailsItem = new AppDetailsPermissionItem(permissionItem);
                                appDetailsItem.isGranted = isGranted;
                                runOnUiThread(() -> set(index, appDetailsItem));
                                mainModel.setUsesPermission(appDetailsItem.name, isGranted);
                            } else {
                                runOnUiThread(() -> {
                                    Toast.makeText(mActivity, isGranted ? R.string.failed_to_grant_permission : R.string.failed_to_revoke_permission, Toast.LENGTH_SHORT).show();
                                    notifyItemChanged(index);
                                });
                            }
                        }).start();
                    }
                });
            } else holder.toggleSwitch.setVisibility(View.GONE);
        }

        private void getSharedLibsView(@NonNull ViewHolder holder, int index) {
            TextView textView = (TextView) holder.itemView;
            textView.setTextIsSelectable(true);
            textView.setText((String) mAdapterList.get(index).vanillaItem);
            textView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            int medium_size = mActivity.getResources().getDimensionPixelSize(R.dimen.padding_medium);
            int small_size = mActivity.getResources().getDimensionPixelSize(R.dimen.padding_very_small);
            textView.setTextSize(12);
            textView.setPadding(medium_size, small_size, medium_size, small_size);
        }

        private void getPermissionsView(@NonNull ViewHolder holder, int index) {
            View view = holder.itemView;
            final PermissionInfo permissionInfo = (PermissionInfo) mAdapterList.get(index).vanillaItem;
            view.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            // Label
            holder.textView1.setText(permissionInfo.loadLabel(mPackageManager));
            // Name
            if (mConstraint != null && permissionInfo.name.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.textView2.setText(UIUtils.getHighlightedText(permissionInfo.name, mConstraint, mColorRed));
            } else {
                holder.textView2.setText(permissionInfo.name.startsWith(mPackageName) ?
                        permissionInfo.name.replaceFirst(mPackageName, "") : permissionInfo.name);
            }
            // Icon
            if (holder.iconLoader != null) holder.iconLoader.interrupt();
            holder.iconLoader = new IconLoaderThread(holder.imageView, permissionInfo);
            holder.iconLoader.start();
            // Description
            holder.textView3.setText(permissionInfo.loadDescription(mPackageManager));
            // LaunchMode
            holder.textView4.setText(String.format(Locale.ROOT, "%s: %s",
                    getString(R.string.group), permissionInfo.group + permAppOp(permissionInfo.name)));
            // Protection level
            String protectionLevel = Utils.getProtectionLevelString(permissionInfo);
            holder.textView5.setText(String.format(Locale.ROOT, "%s: %s",
                    getString(R.string.protection_level), protectionLevel));
            if (protectionLevel.contains("dangerous"))
                view.setBackgroundColor(ContextCompat.getColor(mActivity, R.color.red));
        }

        private void getFeaturesView(@NonNull ViewHolder holder, int index) {
            View view = holder.itemView;
            final FeatureInfo featureInfo = (FeatureInfo) mAdapterList.get(index).vanillaItem;
            view.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            // Name
            holder.textView1.setText(featureInfo.name);
            // Flags
            holder.textView2.setText(String.format(Locale.ROOT, "%s: %s",
                    getString(R.string.flags), getString(Utils.getFeatureFlags(featureInfo.flags))
                            + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && featureInfo.version != 0 ? " | minV%:" + featureInfo.version : "")));
            // GLES ver
            holder.textView3.setText(String.format(Locale.ROOT, "%s: %s",
                    getString(R.string.gles_ver), (mainModel.isbFi() && !featureInfo.name.equals("OpenGL ES") ? "_"
                            : Utils.getOpenGL(featureInfo.reqGlEsVersion))));
        }

        private void getConfigurationView(@NonNull ViewHolder holder, int index) {
            View view = holder.itemView;
            final ConfigurationInfo configurationInfo = (ConfigurationInfo) mAdapterList.get(index).vanillaItem;
            view.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            // GL ES ver
            holder.textView1.setText(String.format(Locale.ROOT, "%s: %s",
                    getString(R.string.gles_ver), Utils.getOpenGL(configurationInfo.reqGlEsVersion)));
            // Flag & others
            holder.textView2.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.input_features),
                    Utils.getInputFeaturesString(configurationInfo.reqInputFeatures)));
            holder.textView3.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.keyboard_type),
                    getString(Utils.getKeyboardType(configurationInfo.reqKeyboardType))));
            holder.textView4.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.navigation),
                    getString(Utils.getNavigation(configurationInfo.reqNavigation))));
            holder.textView5.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.touchscreen),
                    getString(Utils.getTouchScreen(configurationInfo.reqTouchScreen))));
        }

        private void getSignatureView(@NonNull ViewHolder holder, int index) {
            TextView textView = (TextView) holder.itemView;
            AppDetailsItem item = mAdapterList.get(index);
            final X509Certificate signature = (X509Certificate) mAdapterList.get(index).vanillaItem;
            final SpannableStringBuilder builder = new SpannableStringBuilder();
            if (index == 0) {
                // Display verifier info
                builder.append(PackageUtils.getApkVerifierInfo(mainModel.getApkVerifierResult(), mActivity));
            }
            if (!TextUtils.isEmpty(item.name)) {
                builder.append(UIUtils.getTitleText(mActivity, item.name)).append("\n");
            }
            builder.append(PackageUtils.getSigningCertificateInfo(mActivity, signature));
            textView.setText(builder);
            textView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            textView.setTextIsSelectable(true);
            int medium_size = mActivity.getResources().getDimensionPixelSize(R.dimen.padding_medium);
            int small_size = mActivity.getResources().getDimensionPixelSize(R.dimen.padding_small);
            textView.setTextSize(12);
            textView.setPadding(medium_size, small_size, medium_size, small_size);
        }
    }
}
