// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PathPermission;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.PatternMatcher;
import android.os.UserHandleHidden;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.PopupMenu;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsActivityItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsComponentItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsServiceItem;
import io.github.muntashirakon.AppManager.intercept.ActivityInterceptor;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.shortcut.CreateShortcutDialogFragment;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.view.ProgressIndicatorCompat;
import io.github.muntashirakon.widget.MaterialAlertView;
import io.github.muntashirakon.widget.RecyclerView;

public class AppDetailsComponentsFragment extends AppDetailsFragment {
    @IntDef(value = {
            ACTIVITIES,
            SERVICES,
            RECEIVERS,
            PROVIDERS,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ComponentProperty {
    }

    private String mPackageName;
    private AppDetailsRecyclerAdapter mAdapter;
    private MenuItem mBlockingToggler;
    private boolean mIsExternalApk;
    @ComponentProperty
    private int mNeededProperty;
    private int mSortOrder;
    private String mSearchQuery;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mNeededProperty = requireArguments().getInt(ARG_TYPE);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        emptyView.setText(getNotFoundString(mNeededProperty));
        mAdapter = new AppDetailsRecyclerAdapter();
        recyclerView.setAdapter(mAdapter);
        alertView.setEndIconOnClickListener(v -> alertView.hide());
        int helpStringRes = R.string.rules_not_applied;
        alertView.setText(helpStringRes);
        alertView.setVisibility(View.GONE);
        if (viewModel == null) return;
        mSortOrder = viewModel.getSortOrder(mNeededProperty);
        mSearchQuery = viewModel.getSearchQuery();
        mPackageName = viewModel.getPackageName();
        viewModel.get(mNeededProperty).observe(getViewLifecycleOwner(), appDetailsItems -> {
            if (appDetailsItems != null && mAdapter != null && viewModel.isPackageExist()) {
                mPackageName = viewModel.getPackageName();
                mIsExternalApk = viewModel.isExternalApk();
                mAdapter.setDefaultList(appDetailsItems);
            } else ProgressIndicatorCompat.setVisibility(progressIndicator, false);
        });
        viewModel.getRuleApplicationStatus().observe(getViewLifecycleOwner(), status -> {
            alertView.setAlertType(MaterialAlertView.ALERT_TYPE_WARN);
            if (status == AppDetailsViewModel.RULE_NOT_APPLIED) {
                alertView.show();
            } else alertView.hide();
            updateBlockMenuItem(status);
        });
    }

    @Override
    public void onRefresh() {
        refreshDetails();
        swipeRefresh.setRefreshing(false);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        if (viewModel != null && !viewModel.isExternalApk() && SelfPermissions.canModifyAppComponentStates(
                viewModel.getUserId(), viewModel.getPackageName(), viewModel.isTestOnlyApp())) {
            inflater.inflate(R.menu.fragment_app_details_components_actions, menu);
            mBlockingToggler = menu.findItem(R.id.action_toggle_blocking);
        } else inflater.inflate(R.menu.fragment_app_details_refresh_actions, menu);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        if (viewModel == null || viewModel.isExternalApk()) {
            return;
        }
        MenuItem sortItem = menu.findItem(AppDetailsFragment.sSortMenuItemIdsMap[viewModel.getSortOrder(mNeededProperty)]);
        if (sortItem != null) {
            sortItem.setChecked(true);
        }
        Integer status = viewModel.getRuleApplicationStatus().getValue();
        if (status != null) {
            updateBlockMenuItem(status);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh_details) {
            refreshDetails();
        } else if (id == R.id.action_toggle_blocking) {  // Components
            if (viewModel != null) {
                viewModel.applyRules();
            }
        } else if (id == R.id.action_block_unblock_trackers) {  // Components
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.block_unblock_trackers)
                    .setMessage(R.string.choose_what_to_do)
                    .setPositiveButton(R.string.block, (dialog, which) -> blockUnblockTrackers(true))
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.unblock, (dialog, which) -> blockUnblockTrackers(false))
                    .show();
        } else if (id == R.id.action_sort_by_name) {  // All
            setSortBy(AppDetailsFragment.SORT_BY_NAME);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_blocked_components) {  // Components
            setSortBy(AppDetailsFragment.SORT_BY_BLOCKED);
            item.setChecked(true);
        } else if (id == R.id.action_sort_by_tracker_components) {  // Components
            setSortBy(AppDetailsFragment.SORT_BY_TRACKERS);
            item.setChecked(true);
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (viewModel != null) {
            mSortOrder = viewModel.getSortOrder(mNeededProperty);
            mSearchQuery = viewModel.getSearchQuery();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (activity.searchView != null) {
            if (!activity.searchView.isShown()) {
                activity.searchView.setVisibility(View.VISIBLE);
            }
            activity.searchView.setOnQueryTextListener(this);
            if (viewModel != null) {
                int sortOrder = viewModel.getSortOrder(mNeededProperty);
                String searchQuery = viewModel.getSearchQuery();
                if (sortOrder != mSortOrder || !Objects.equals(searchQuery, mSearchQuery)) {
                    viewModel.filterAndSortItems(mNeededProperty);
                }
            }
        }
    }

    @Override
    public boolean onQueryTextChange(String searchQuery, int type) {
        if (viewModel != null) {
            viewModel.setSearchQuery(searchQuery, type, mNeededProperty);
        }
        return true;
    }

    private void updateBlockMenuItem(int status) {
        if (mBlockingToggler != null) {
            switch (status) {
                case AppDetailsViewModel.RULE_APPLIED:
                    mBlockingToggler.setVisible(!Prefs.Blocking.globalBlockingEnabled());
                    mBlockingToggler.setTitle(R.string.menu_remove_rules);
                    break;
                case AppDetailsViewModel.RULE_NOT_APPLIED:
                    mBlockingToggler.setVisible(!Prefs.Blocking.globalBlockingEnabled());
                    mBlockingToggler.setTitle(R.string.menu_apply_rules);
                    break;
                case AppDetailsViewModel.RULE_NO_RULE:
                    mBlockingToggler.setVisible(false);
            }
        }
    }

    private void blockUnblockTrackers(boolean block) {
        if (viewModel == null) return;
        // TODO: 19/3/23 Do it via ViewModel
        List<UserPackagePair> userPackagePairs = Collections.singletonList(new UserPackagePair(mPackageName,
                UserHandleHidden.myUserId()));
        ThreadUtils.postOnBackgroundThread(() -> {
            List<UserPackagePair> failedPkgList = block ? ComponentUtils.blockTrackingComponents(userPackagePairs)
                    : ComponentUtils.unblockTrackingComponents(userPackagePairs);
            if (!failedPkgList.isEmpty()) {
                ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(block ? R.string.failed_to_block_trackers
                        : R.string.failed_to_unblock_trackers));
            } else {
                ThreadUtils.postOnMainThread(() -> {
                    UIUtils.displayShortToast(block ? R.string.trackers_blocked_successfully
                            : R.string.trackers_unblocked_successfully);
                    if (!isDetached()) {
                        refreshDetails();
                    }
                });
            }
            viewModel.setRuleApplicationStatus();
        });
    }

    private int getNotFoundString(@ComponentProperty int index) {
        switch (index) {
            case SERVICES:
                return R.string.no_service;
            case RECEIVERS:
                return R.string.no_receivers;
            case PROVIDERS:
                return R.string.no_providers;
            case ACTIVITIES:
            default:
                return R.string.no_activities;
        }
    }

    private void setSortBy(@SortOrder int sortBy) {
        ProgressIndicatorCompat.setVisibility(progressIndicator, true);
        if (viewModel == null) return;
        viewModel.setSortOrder(sortBy, mNeededProperty);
    }

    @MainThread
    private void refreshDetails() {
        if (viewModel == null || mIsExternalApk) return;
        ProgressIndicatorCompat.setVisibility(progressIndicator, true);
        viewModel.triggerPackageChange();
    }

    private void applyRules(@NonNull AppDetailsComponentItem componentItem, @NonNull RuleType type,
                            @NonNull @ComponentRule.ComponentStatus String componentStatus) {
        if (viewModel != null) {
            viewModel.updateRulesForComponent(componentItem, type, componentStatus);
        }
    }

    @UiThread
    private class AppDetailsRecyclerAdapter extends RecyclerView.Adapter<AppDetailsRecyclerAdapter.ViewHolder> {
        @NonNull
        private final List<AppDetailsItem<?>> mAdapterList;
        @ComponentProperty
        private int mRequestedProperty;
        @Nullable
        private String mConstraint;
        private int mUserId;
        private boolean mCanModifyComponentStates;
        private boolean mCanStartAnyActivity;
        private final int mBlockedIndicatorColor;
        private final int mBlockedExternallyIndicatorColor;
        private final int mTrackerIndicatorColor;
        private final int mRunningIndicatorColor;

        AppDetailsRecyclerAdapter() {
            mAdapterList = new ArrayList<>();
            mBlockedIndicatorColor = ColorCodes.getComponentBlockedIndicatorColor(activity);
            mBlockedExternallyIndicatorColor = ColorCodes.getComponentExternallyBlockedIndicatorColor(activity);
            mTrackerIndicatorColor = ColorCodes.getComponentTrackerIndicatorColor(activity);
            mRunningIndicatorColor = ColorCodes.getComponentRunningIndicatorColor(activity);
        }

        @UiThread
        void setDefaultList(@NonNull List<AppDetailsItem<?>> list) {
            ThreadUtils.postOnBackgroundThread(() -> {
                mRequestedProperty = mNeededProperty;
                mCanStartAnyActivity = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.START_ANY_ACTIVITY);
                if (viewModel != null) {
                    mCanModifyComponentStates = !mIsExternalApk && SelfPermissions.canModifyAppComponentStates(mUserId, viewModel.getPackageName(), viewModel.isTestOnlyApp());
                    mConstraint = viewModel.getSearchQuery();
                    mUserId = viewModel.getUserId();
                } else {
                    mCanModifyComponentStates = false;
                    mConstraint = null;
                    mUserId = UserHandleHidden.myUserId();
                }
                ThreadUtils.postOnMainThread(() -> {
                    if (isDetached()) return;
                    ProgressIndicatorCompat.setVisibility(progressIndicator, false);
                    synchronized (mAdapterList) {
                        AdapterUtils.notifyDataSetChanged(this, mAdapterList, list);
                    }
                });
            });
        }

        /**
         * ViewHolder to use recycled views efficiently. Fields names are not expressive because we use
         * the same holder for any kind of view, and view are not all sames.
         */
        class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView itemView;
            TextView labelView;
            TextView nameView;
            TextView textView1;
            TextView textView2;
            TextView textView3;
            TextView textView4;
            TextView processNameView;
            ImageView imageView;
            Button shortcutBtn;
            MaterialButton launchBtn;
            MaterialSwitch toggleSwitch;
            Chip chipType;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                this.itemView = (MaterialCardView) itemView;
                imageView = itemView.findViewById(R.id.icon);
                labelView = itemView.findViewById(R.id.label);
                nameView = itemView.findViewById(R.id.name);
                processNameView = itemView.findViewById(R.id.process_name);

                shortcutBtn = itemView.findViewById(R.id.edit_shortcut_btn);
                toggleSwitch = itemView.findViewById(R.id.toggle_button);
                chipType = itemView.findViewById(R.id.type);
                launchBtn = itemView.findViewById(R.id.launch);

                if (mRequestedProperty == ACTIVITIES) {
                    textView1 = itemView.findViewById(R.id.taskAffinity);
                    textView2 = itemView.findViewById(R.id.launchMode);
                    textView3 = itemView.findViewById(R.id.orientation);
                    textView4 = itemView.findViewById(R.id.softInput);
                } else if (mRequestedProperty == SERVICES) {
                    textView1 = itemView.findViewById(R.id.orientation);
                    itemView.findViewById(R.id.taskAffinity).setVisibility(View.GONE);
                    itemView.findViewById(R.id.launchMode).setVisibility(View.GONE);
                    itemView.findViewById(R.id.softInput).setVisibility(View.GONE);
                    shortcutBtn.setVisibility(View.GONE);
                } else if (mRequestedProperty == RECEIVERS) {
                    textView1 = itemView.findViewById(R.id.taskAffinity);
                    textView2 = itemView.findViewById(R.id.launchMode);
                    textView3 = itemView.findViewById(R.id.orientation);
                    textView4 = itemView.findViewById(R.id.softInput);
                    launchBtn.setVisibility(View.GONE);
                    shortcutBtn.setVisibility(View.GONE);
                } else if (mRequestedProperty == PROVIDERS) {
                    textView1 = itemView.findViewById(R.id.launchMode);
                    textView2 = itemView.findViewById(R.id.orientation);
                    textView3 = itemView.findViewById(R.id.softInput);
                    textView4 = itemView.findViewById(R.id.taskAffinity);
                    launchBtn.setVisibility(View.GONE);
                    shortcutBtn.setVisibility(View.GONE);
                }
            }
        }

        @NonNull
        @Override
        public AppDetailsRecyclerAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_primary, parent, false);
            return new AppDetailsRecyclerAdapter.ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AppDetailsRecyclerAdapter.ViewHolder holder, int position) {
            Context context = holder.itemView.getContext();
            if (mRequestedProperty == SERVICES) {
                getServicesView(context, holder, position);
            } else if (mRequestedProperty == RECEIVERS) {
                getReceiverView(holder, position);
            } else if (mRequestedProperty == PROVIDERS) {
                getProviderView(holder, position);
            } else if (mRequestedProperty == ACTIVITIES) {
                getActivityView(holder, position);
            }
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            synchronized (mAdapterList) {
                return mAdapterList.size();
            }
        }

        private void handleBlock(@NonNull ViewHolder holder, @NonNull AppDetailsComponentItem item, RuleType ruleType) {
            holder.toggleSwitch.setChecked(!item.isBlocked());
            holder.toggleSwitch.setVisibility(View.VISIBLE);
            holder.toggleSwitch.setOnClickListener(buttonView -> {
                String componentStatus = item.isBlocked()
                        ? ComponentRule.COMPONENT_TO_BE_DEFAULTED
                        : Prefs.Blocking.getDefaultBlockingMethod();
                applyRules(item, ruleType, componentStatus);
            });
            holder.toggleSwitch.setOnLongClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(activity, holder.toggleSwitch);
                popupMenu.inflate(R.menu.fragment_app_details_components_selection_actions);
                if (!SelfPermissions.canBlockByIFW()) {
                    Menu menu = popupMenu.getMenu();
                    menu.findItem(R.id.action_ifw_and_disable).setEnabled(false);
                    menu.findItem(R.id.action_ifw).setEnabled(false);
                }
                popupMenu.setOnMenuItemClickListener(item1 -> {
                    int id = item1.getItemId();
                    String componentStatus;
                    if (id == R.id.action_ifw_and_disable) {
                        componentStatus = ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW_DISABLE;
                    } else if (id == R.id.action_ifw) {
                        componentStatus = ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW;
                    } else if (id == R.id.action_disable) {
                        componentStatus = ComponentRule.COMPONENT_TO_BE_DISABLED;
                    } else if (id == R.id.action_enable) {
                        componentStatus = ComponentRule.COMPONENT_TO_BE_ENABLED;
                    } else if (id == R.id.action_default) {
                        componentStatus = ComponentRule.COMPONENT_TO_BE_DEFAULTED;
                    } else {
                        componentStatus = ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW_DISABLE;
                    }
                    applyRules(item, ruleType, componentStatus);
                    return true;
                });
                popupMenu.show();
                return true;
            });
        }

        private void getActivityView(@NonNull ViewHolder holder, int index) {
            final AppDetailsActivityItem componentItem;
            synchronized (mAdapterList) {
                componentItem = (AppDetailsActivityItem) mAdapterList.get(index);
            }
            final ActivityInfo activityInfo = (ActivityInfo) componentItem.mainItem;
            final String activityName = componentItem.name;
            final boolean isDisabled = !mIsExternalApk && componentItem.isDisabled();
            // Background color: regular < tracker < disabled < blocked
            if (!mIsExternalApk && componentItem.isBlocked()) {
                holder.itemView.setStrokeColor(mBlockedIndicatorColor);
            } else if (isDisabled) {
                holder.itemView.setStrokeColor(mBlockedExternallyIndicatorColor);
            } else if (componentItem.isTracker()) {
                holder.itemView.setStrokeColor(mTrackerIndicatorColor);
            } else {
                holder.itemView.setStrokeColor(Color.TRANSPARENT);
            }
            if (componentItem.isTracker()) {
                holder.chipType.setText(R.string.tracker);
                holder.chipType.setVisibility(View.VISIBLE);
            } else holder.chipType.setVisibility(View.GONE);
            // Name
            if (mConstraint != null && activityName.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.nameView.setText(UIUtils.getHighlightedText(activityName, mConstraint, colorQueryStringHighlight));
            } else {
                holder.nameView.setText(activityName.startsWith(mPackageName) ?
                        activityName.replaceFirst(mPackageName, "") : activityName);
            }
            // Icon
            String tag = mPackageName + "_" + activityName;
            holder.imageView.setTag(tag);
            ImageLoader.getInstance().displayImage(tag, activityInfo, holder.imageView);
            // TaskAffinity
            holder.textView1.setText(String.format(Locale.ROOT, "%s: %s",
                    getString(R.string.task_affinity), activityInfo.taskAffinity));
            // LaunchMode
            holder.textView2.setText(String.format(Locale.ROOT, "%s: %s | %s: %s",
                    getString(R.string.launch_mode), getString(Utils.getLaunchMode(activityInfo.launchMode)),
                    getString(R.string.orientation), getString(Utils.getOrientationString(activityInfo.screenOrientation))));
            // Orientation
            holder.textView3.setText(Utils.getActivitiesFlagsString(activityInfo.flags));
            // SoftInput
            holder.textView4.setText(String.format(Locale.ROOT, "%s: %s | %s",
                    getString(R.string.soft_input), Utils.getSoftInputString(activityInfo.softInputMode),
                    (activityInfo.permission == null ? getString(R.string.require_no_permission) : activityInfo.permission)));
            // Label
            holder.labelView.setText(componentItem.label);
            // Process name
            String processName = activityInfo.processName;
            if (processName != null && !processName.equals(mPackageName)) {
                holder.processNameView.setVisibility(View.VISIBLE);
                holder.processNameView.setText(String.format(Locale.ROOT, "%s: %s",
                        getString(R.string.process_name), processName));
            } else holder.processNameView.setVisibility(View.GONE);
            boolean isExported = activityInfo.exported;
            if (componentItem.canLaunch || componentItem.canLaunchAssist) {
                holder.launchBtn.setOnClickListener(v -> {
                    ComponentName cn = new ComponentName(mPackageName, activityName);
                    if (componentItem.canLaunch) {
                        Intent intent = new Intent();
                        intent.setComponent(cn);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            ActivityManagerCompat.startActivity(intent, mUserId);
                        } catch (Throwable e) {
                            UIUtils.displayLongToast(e.getLocalizedMessage());
                        }
                    } else if (componentItem.canLaunchAssist) {
                        ActivityManagerCompat.startActivityViaAssist(ContextUtils.getContext(), cn, () -> {
                            CountDownLatch waitForInteraction = new CountDownLatch(1);
                            ThreadUtils.postOnMainThread(() -> new MaterialAlertDialogBuilder(holder.itemView.getContext())
                                    .setTitle(R.string.launch_activity_dialog_title)
                                    .setMessage(R.string.launch_activity_dialog_message)
                                    .setCancelable(false)
                                    .setOnDismissListener((dialog) -> waitForInteraction.countDown())
                                    .setNegativeButton(R.string.close, null)
                                    .show());
                            try {
                                waitForInteraction.await(10, TimeUnit.MINUTES);
                            } catch (InterruptedException ignore) {
                            }
                        });
                    }
                });
                if (FeatureController.isInterceptorEnabled()) {
                    holder.launchBtn.setOnLongClickListener(v -> {
                        boolean needRoot = mCanStartAnyActivity && (!isExported || !SelfPermissions.checkSelfOrRemotePermission(activityInfo.permission));
                        Intent intent = new Intent(activity, ActivityInterceptor.class);
                        intent.putExtra(ActivityInterceptor.EXTRA_PACKAGE_NAME, mPackageName);
                        intent.putExtra(ActivityInterceptor.EXTRA_CLASS_NAME, activityName);
                        intent.putExtra(ActivityInterceptor.EXTRA_USER_HANDLE, mUserId);
                        intent.putExtra(ActivityInterceptor.EXTRA_ROOT, needRoot);
                        startActivity(intent);
                        return true;
                    });
                }
                holder.shortcutBtn.setOnClickListener(v -> {
                    PackageItemShortcutInfo<ActivityInfo> shortcutInfo = new PackageItemShortcutInfo<>(activityInfo, ActivityInfo.class, mUserId, componentItem.canLaunchAssist);
                    shortcutInfo.setName(componentItem.label);
                    shortcutInfo.setIcon(UIUtils.getBitmapFromDrawable(activityInfo.loadIcon(packageManager)));
                    CreateShortcutDialogFragment dialog = CreateShortcutDialogFragment.getInstance(shortcutInfo);
                    dialog.show(getParentFragmentManager(), CreateShortcutDialogFragment.TAG);
                });
                holder.shortcutBtn.setVisibility(View.VISIBLE);
                holder.launchBtn.setVisibility(View.VISIBLE);
            } else {
                holder.shortcutBtn.setVisibility(View.GONE);
                holder.launchBtn.setVisibility(View.GONE);
            }
            // Blocking
            if (mCanModifyComponentStates) {
                handleBlock(holder, componentItem, RuleType.ACTIVITY);
            } else holder.toggleSwitch.setVisibility(View.GONE);
        }

        private void getServicesView(@NonNull Context context, @NonNull ViewHolder holder, int index) {
            final AppDetailsServiceItem serviceItem;
            synchronized (mAdapterList) {
                serviceItem = (AppDetailsServiceItem) mAdapterList.get(index);
            }
            final ServiceInfo serviceInfo = (ServiceInfo) serviceItem.mainItem;
            final boolean isDisabled = !mIsExternalApk && serviceItem.isDisabled();
            // Background color: regular < tracker < disabled < blocked < running
            if (serviceItem.isRunning()) {
                holder.itemView.setStrokeColor(mRunningIndicatorColor);
            } else if (!mIsExternalApk && serviceItem.isBlocked()) {
                holder.itemView.setStrokeColor(mBlockedIndicatorColor);
            } else if (isDisabled) {
                holder.itemView.setStrokeColor(mBlockedExternallyIndicatorColor);
            } else if (serviceItem.isTracker()) {
                holder.itemView.setStrokeColor(mTrackerIndicatorColor);
            } else {
                holder.itemView.setStrokeColor(Color.TRANSPARENT);
            }
            if (serviceItem.isTracker()) {
                holder.chipType.setText(R.string.tracker);
                holder.chipType.setVisibility(View.VISIBLE);
            } else holder.chipType.setVisibility(View.GONE);
            // Label
            holder.labelView.setText(serviceItem.label);
            // Name
            if (mConstraint != null && serviceInfo.name.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.nameView.setText(UIUtils.getHighlightedText(serviceInfo.name, mConstraint, colorQueryStringHighlight));
            } else {
                holder.nameView.setText(serviceInfo.name.startsWith(mPackageName) ?
                        serviceInfo.name.replaceFirst(mPackageName, "") : serviceInfo.name);
            }
            // Icon
            String tag = mPackageName + "_" + serviceInfo.name;
            holder.imageView.setTag(tag);
            ImageLoader.getInstance().displayImage(tag, serviceInfo, holder.imageView);
            // Flags and Permission
            StringBuilder flagsAndPermission = new StringBuilder(Utils.getServiceFlagsString(serviceInfo.flags));
            if (flagsAndPermission.length() != 0) {
                flagsAndPermission.append("\n");
            }
            flagsAndPermission.append(serviceInfo.permission != null ? serviceInfo.permission : getString(R.string.require_no_permission));
            holder.textView1.setText(flagsAndPermission);
            // Process name
            String processName = serviceInfo.processName;
            if (processName != null && !processName.equals(mPackageName)) {
                holder.processNameView.setVisibility(View.VISIBLE);
                holder.processNameView.setText(String.format(Locale.ROOT, "%s: %s",
                        getString(R.string.process_name), processName));
            } else holder.processNameView.setVisibility(View.GONE);
            if (serviceItem.canLaunch) {
                holder.launchBtn.setOnClickListener(v -> {
                    Intent intent = new Intent();
                    intent.setClassName(mPackageName, serviceInfo.name);
                    try {
                        ActivityManagerCompat.startService(intent, mUserId, true);
                    } catch (Throwable th) {
                        th.printStackTrace();
                        Toast.makeText(context, th.toString(), Toast.LENGTH_LONG).show();
                    }
                });
                holder.launchBtn.setVisibility(View.VISIBLE);
            } else {
                holder.launchBtn.setVisibility(View.GONE);
            }
            // Blocking
            if (mCanModifyComponentStates) {
                handleBlock(holder, serviceItem, RuleType.SERVICE);
            } else holder.toggleSwitch.setVisibility(View.GONE);
        }

        private void getReceiverView(@NonNull ViewHolder holder, int index) {
            final AppDetailsComponentItem componentItem;
            synchronized (mAdapterList) {
                componentItem = (AppDetailsComponentItem) mAdapterList.get(index);
            }
            final ActivityInfo activityInfo = (ActivityInfo) componentItem.mainItem;
            // Background color: regular < tracker < disabled < blocked
            if (!mIsExternalApk && componentItem.isBlocked()) {
                holder.itemView.setStrokeColor(mBlockedIndicatorColor);
            } else if (!mIsExternalApk && componentItem.isDisabled()) {
                holder.itemView.setStrokeColor(mBlockedExternallyIndicatorColor);
            } else if (componentItem.isTracker()) {
                holder.itemView.setStrokeColor(mTrackerIndicatorColor);
            } else {
                holder.itemView.setStrokeColor(Color.TRANSPARENT);
            }
            if (componentItem.isTracker()) {
                holder.chipType.setText(R.string.tracker);
                holder.chipType.setVisibility(View.VISIBLE);
            } else holder.chipType.setVisibility(View.GONE);
            // Label
            holder.labelView.setText(componentItem.label);
            // Name
            if (mConstraint != null && activityInfo.name.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.nameView.setText(UIUtils.getHighlightedText(activityInfo.name, mConstraint, colorQueryStringHighlight));
            } else {
                holder.nameView.setText(activityInfo.name.startsWith(mPackageName) ?
                        activityInfo.name.replaceFirst(mPackageName, "")
                        : activityInfo.name);
            }
            // Icon
            String tag = mPackageName + "_" + activityInfo.name;
            holder.imageView.setTag(tag);
            ImageLoader.getInstance().displayImage(tag, activityInfo, holder.imageView);
            // TaskAffinity
            holder.textView1.setText(String.format(Locale.ROOT, "%s: %s",
                    getString(R.string.task_affinity), activityInfo.taskAffinity));
            // LaunchMode
            holder.textView2.setText(String.format(Locale.ROOT, "%s: %s | %s: %s",
                    getString(R.string.launch_mode), getString(Utils.getLaunchMode(activityInfo.launchMode)),
                    getString(R.string.orientation), getString(Utils.getOrientationString(activityInfo.screenOrientation))));
            // Orientation
            holder.textView3.setText(activityInfo.permission == null ? getString(R.string.require_no_permission) : activityInfo.permission);
            // SoftInput
            holder.textView4.setText(String.format(Locale.ROOT, "%s: %s",
                    getString(R.string.soft_input), Utils.getSoftInputString(activityInfo.softInputMode)));
            // Process name
            String processName = activityInfo.processName;
            if (processName != null && !processName.equals(mPackageName)) {
                holder.processNameView.setVisibility(View.VISIBLE);
                holder.processNameView.setText(String.format(Locale.ROOT, "%s: %s",
                        getString(R.string.process_name), processName));
            } else holder.processNameView.setVisibility(View.GONE);
            // Blocking
            if (mCanModifyComponentStates) {
                handleBlock(holder, componentItem, RuleType.RECEIVER);
            } else holder.toggleSwitch.setVisibility(View.GONE);
        }

        private void getProviderView(@NonNull ViewHolder holder, int index) {
            final AppDetailsComponentItem componentItem;
            synchronized (mAdapterList) {
                componentItem = (AppDetailsComponentItem) mAdapterList.get(index);
            }
            final ProviderInfo providerInfo = (ProviderInfo) componentItem.mainItem;
            final String providerName = providerInfo.name;
            // Background color: regular < tracker < disabled < blocked
            if (!mIsExternalApk && componentItem.isBlocked()) {
                holder.itemView.setStrokeColor(mBlockedIndicatorColor);
            } else if (!mIsExternalApk && componentItem.isDisabled()) {
                holder.itemView.setStrokeColor(mBlockedExternallyIndicatorColor);
            } else if (componentItem.isTracker()) {
                holder.itemView.setStrokeColor(mTrackerIndicatorColor);
            } else {
                holder.itemView.setStrokeColor(Color.TRANSPARENT);
            }
            if (componentItem.isTracker()) {
                holder.chipType.setText(R.string.tracker);
                holder.chipType.setVisibility(View.VISIBLE);
            } else holder.chipType.setVisibility(View.GONE);
            // Label
            holder.labelView.setText(componentItem.label);
            // Icon
            String tag = mPackageName + "_" + providerName;
            holder.imageView.setTag(tag);
            ImageLoader.getInstance().displayImage(tag, providerInfo, holder.imageView);
            // Uri permission
            holder.textView1.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.grant_uri_permission), providerInfo.grantUriPermissions));
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
            holder.textView2.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.path_permissions), finalString)); // +"\n"+providerInfo.readPermission +"\n"+providerInfo.writePermission);
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
            holder.textView3.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.patterns_allowed), finalString1));
            // Authority
            holder.textView4.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.authority), providerInfo.authority));
            // Name
            if (mConstraint != null && providerName.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.nameView.setText(UIUtils.getHighlightedText(providerName, mConstraint, colorQueryStringHighlight));
            } else {
                holder.nameView.setText(providerName.startsWith(mPackageName) ?
                        providerName.replaceFirst(mPackageName, "") : providerName);
            }
            // Process name
            String processName = providerInfo.processName;
            if (processName != null && !processName.equals(mPackageName)) {
                holder.processNameView.setVisibility(View.VISIBLE);
                holder.processNameView.setText(String.format(Locale.ROOT, "%s: %s",
                        getString(R.string.process_name), processName));
            } else holder.processNameView.setVisibility(View.GONE);
            // Blocking
            if (mCanModifyComponentStates) {
                handleBlock(holder, componentItem, RuleType.PROVIDER);
            } else holder.toggleSwitch.setVisibility(View.GONE);
        }
    }
}
