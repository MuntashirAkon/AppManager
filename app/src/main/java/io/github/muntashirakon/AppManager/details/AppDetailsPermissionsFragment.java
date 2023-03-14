// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static io.github.muntashirakon.AppManager.details.AppDetailsFragment.APP_OPS;
import static io.github.muntashirakon.AppManager.details.AppDetailsFragment.PERMISSIONS;
import static io.github.muntashirakon.AppManager.details.AppDetailsFragment.SORT_BY_APP_OP_VALUES;
import static io.github.muntashirakon.AppManager.details.AppDetailsFragment.SORT_BY_DANGEROUS_PERMS;
import static io.github.muntashirakon.AppManager.details.AppDetailsFragment.SORT_BY_DENIED_APP_OPS;
import static io.github.muntashirakon.AppManager.details.AppDetailsFragment.SORT_BY_DENIED_PERMS;
import static io.github.muntashirakon.AppManager.details.AppDetailsFragment.SORT_BY_NAME;
import static io.github.muntashirakon.AppManager.details.AppDetailsFragment.USES_PERMISSIONS;
import static io.github.muntashirakon.AppManager.details.AppDetailsFragment.sSortMenuItemIdsMap;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOpModeNames;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOpNames;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsAppOpItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsDefinedPermissionItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsPermissionItem;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.self.pref.TipsPrefs;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.dialog.SearchableItemsDialogBuilder;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder;
import io.github.muntashirakon.util.ProgressIndicatorCompat;
import io.github.muntashirakon.widget.MaterialAlertView;
import io.github.muntashirakon.widget.RecyclerView;
import io.github.muntashirakon.widget.SwipeRefreshLayout;

public class AppDetailsPermissionsFragment extends Fragment implements AdvancedSearchView.OnQueryTextListener,
        SwipeRefreshLayout.OnRefreshListener {
    public static final String ARG_TYPE = "type";

    @IntDef(value = {
            APP_OPS,
            USES_PERMISSIONS,
            PERMISSIONS,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PermissionProperty {
    }

    private String mPackageName;
    private PackageManager mPackageManager;
    private AppDetailsActivity mActivity;
    private AppDetailsRecyclerAdapter mAdapter;
    private SwipeRefreshLayout mSwipeRefresh;
    private LinearProgressIndicator mProgressIndicator;
    private MaterialAlertView mAlertView;
    private boolean mIsExternalApk;
    @PermissionProperty
    private int mNeededProperty;
    @Nullable
    private AppDetailsViewModel mMainModel;

    private final ExecutorService mExecutor = Executors.newFixedThreadPool(3);
    private final ImageLoader mImageLoader = new ImageLoader(mExecutor);

    private int mColorQueryStringHighlight;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mActivity = (AppDetailsActivity) requireActivity();
        mNeededProperty = requireArguments().getInt(ARG_TYPE);
        mMainModel = new ViewModelProvider(mActivity).get(AppDetailsViewModel.class);
        mPackageManager = mActivity.getPackageManager();
        mColorQueryStringHighlight = ColorCodes.getQueryStringHighlightColor(mActivity);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.pager_app_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Swipe refresh
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setOnRefreshListener(this);
        RecyclerView recyclerView = view.findViewById(R.id.scrollView);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(mActivity, LinearLayoutManager.VERTICAL, false));
        final TextView emptyView = view.findViewById(android.R.id.empty);
        emptyView.setText(getNotFoundString(mNeededProperty));
        recyclerView.setEmptyView(emptyView);
        mAdapter = new AppDetailsRecyclerAdapter();
        recyclerView.setAdapter(mAdapter);
        mProgressIndicator = view.findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        showProgressIndicator(true);
        mAlertView = view.findViewById(R.id.alert_text);
        mAlertView.setEndIconMode(MaterialAlertView.END_ICON_CUSTOM);
        mAlertView.setEndIconDrawable(R.drawable.mtrl_ic_cancel);
        mAlertView.setEndIconContentDescription(R.string.close);
        mAlertView.setEndIconOnClickListener(v -> {
            mAlertView.hide();
            // Check tips
            if (mNeededProperty == APP_OPS) {
                TipsPrefs.getInstance().setDisplayInAppOpsTab(false);
            }
            if (mNeededProperty == USES_PERMISSIONS) {
                TipsPrefs.getInstance().setDisplayInUsesPermissionsTab(false);
            }
            if (mNeededProperty == PERMISSIONS) {
                TipsPrefs.getInstance().setDisplayInPermissionsTab(false);
            }
        });
        int helpStringRes = getHelpString(mNeededProperty);
        if (helpStringRes != 0) mAlertView.setText(helpStringRes);
        if (helpStringRes == 0) {
            mAlertView.setVisibility(View.GONE);
        } else {
            mAlertView.postDelayed(() -> mAlertView.hide(), 15_000);
        }
        mSwipeRefresh.setOnChildScrollUpCallback((parent, child) -> recyclerView.canScrollVertically(-1));
        if (mMainModel == null) return;
        mPackageName = mMainModel.getPackageName();
        mMainModel.get(mNeededProperty).observe(getViewLifecycleOwner(), appDetailsItems -> {
            if (appDetailsItems != null && mAdapter != null && mMainModel.isPackageExist()) {
                mPackageName = mMainModel.getPackageName();
                mIsExternalApk = mMainModel.isExternalApk();
                mAdapter.setDefaultList(appDetailsItems);
            } else showProgressIndicator(false);
        });
        mMainModel.getRuleApplicationStatus().observe(getViewLifecycleOwner(), status -> {
            mAlertView.setAlertType(MaterialAlertView.ALERT_TYPE_WARN);
            if (status == AppDetailsViewModel.RULE_NOT_APPLIED) {
                mAlertView.show();
            } else mAlertView.hide();
        });
    }

    @Override
    public void onDetach() {
        mImageLoader.close();
        mExecutor.shutdownNow();
        super.onDetach();
    }

    @Override
    public void onRefresh() {
        refreshDetails();
        mSwipeRefresh.setRefreshing(false);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        switch (mNeededProperty) {
            case APP_OPS:
                inflater.inflate(R.menu.fragment_app_details_app_ops_actions, menu);
                break;
            case USES_PERMISSIONS:
                if (mMainModel != null && !mMainModel.isExternalApk()) {
                    inflater.inflate(R.menu.fragment_app_details_permissions_actions, menu);
                    break;
                } // else fallthrough
            case PERMISSIONS:
                inflater.inflate(R.menu.fragment_app_details_refresh_actions, menu);
                break;
        }
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        if (mMainModel == null || mMainModel.isExternalApk()) {
            return;
        }
        MenuItem sortItem = menu.findItem(sSortMenuItemIdsMap[mMainModel.getSortOrder(mNeededProperty)]);
        if (sortItem != null) {
            sortItem.setChecked(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh_details) {
            refreshDetails();
        } else if (id == R.id.action_reset_to_default) {  // App ops
            mExecutor.submit(() -> {
                if (mMainModel == null || !mMainModel.resetAppOps()) {
                    ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.failed_to_reset_app_ops));
                } else ThreadUtils.postOnMainThread(() -> showProgressIndicator(true));
            });
        } else if (id == R.id.action_deny_dangerous_app_ops) {  // App ops
            showProgressIndicator(true);
            mExecutor.submit(() -> {
                boolean isSuccessful = true;
                try {
                    if (mMainModel == null || !mMainModel.ignoreDangerousAppOps()) {
                        isSuccessful = false;
                    }
                } catch (RuntimeException e) {
                    isSuccessful = false;
                }
                if (isSuccessful) {
                    ThreadUtils.postOnMainThread(this::refreshDetails);
                } else {
                    ThreadUtils.postOnMainThread(() -> Toast.makeText(mActivity,
                            R.string.failed_to_deny_dangerous_app_ops, Toast.LENGTH_SHORT).show());
                }
            });
        } else if (id == R.id.action_toggle_default_app_ops) {  // App ops
            showProgressIndicator(true);
            // Turn filter on/off
            boolean curr = Prefs.AppDetailsPage.displayDefaultAppOps();
            Prefs.AppDetailsPage.setDisplayDefaultAppOps(!curr);
            refreshDetails();
        } else if (id == R.id.action_custom_app_op) {
            List<Integer> modes = AppOpsManagerCompat.getModeConstants();
            List<Integer> appOps = AppOpsManagerCompat.getAllOps();
            List<CharSequence> modeNames = Arrays.asList(getAppOpModeNames(modes));
            List<CharSequence> appOpNames = Arrays.asList(getAppOpNames(appOps));
            TextInputDropdownDialogBuilder builder = new TextInputDropdownDialogBuilder(mActivity, R.string.set_custom_app_op);
            builder.setTitle(R.string.set_custom_app_op)
                    .setDropdownItems(appOpNames, -1, true)
                    .setAuxiliaryInput(R.string.mode, null, null, modeNames, true)
                    .setPositiveButton(R.string.apply, (dialog, which, inputText, isChecked) -> {
                        // Get mode
                        int mode;
                        try {
                            mode = Utils.getIntegerFromString(builder.getAuxiliaryInput(), modeNames, modes);
                        } catch (IllegalArgumentException e) {
                            return;
                        }
                        // Get op
                        int op;
                        try {
                            op = Utils.getIntegerFromString(inputText, appOpNames, appOps);
                        } catch (IllegalArgumentException e) {
                            return;
                        }
                        mExecutor.submit(() -> {
                            if (mMainModel != null && mMainModel.setAppOp(op, mode)) {
                                ThreadUtils.postOnMainThread(this::refreshDetails);
                            } else {
                                ThreadUtils.postOnMainThread(() -> Toast.makeText(mActivity,
                                        R.string.failed_to_enable_op, Toast.LENGTH_LONG).show());
                            }
                        });
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else if (id == R.id.action_deny_dangerous_permissions) {  // permissions
            showProgressIndicator(true);
            mExecutor.submit(() -> {
                if (mMainModel == null || !mMainModel.revokeDangerousPermissions()) {
                    ThreadUtils.postOnMainThread(() -> Toast.makeText(mActivity,
                            R.string.failed_to_deny_dangerous_perms, Toast.LENGTH_SHORT).show());
                }
                ThreadUtils.postOnMainThread(this::refreshDetails);
            });
            // Sorting
        } else if (id == R.id.action_sort_by_name) {  // All
            setSortBy(SORT_BY_NAME);
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
            mActivity.searchView.setVisibility(View.VISIBLE);
            mActivity.searchView.setOnQueryTextListener(this);
            if (mMainModel != null) {
                mMainModel.filterAndSortItems(mNeededProperty);
            }
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
    public boolean onQueryTextChange(String searchQuery, int type) {
        if (mMainModel != null) {
            mMainModel.setSearchQuery(searchQuery, type, mNeededProperty);
        }
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query, int type) {
        return false;
    }

    private int getNotFoundString(@PermissionProperty int index) {
        switch (index) {
            case APP_OPS:
                if (mIsExternalApk) {
                    return R.string.external_apk_no_app_op;
                } else if (Ops.isPrivileged() || PermissionUtils.hasAppOpsPermission()) {
                    return R.string.no_app_ops;
                } else return R.string.no_app_ops_permission;
            case USES_PERMISSIONS:
            case PERMISSIONS:
            default:
                return R.string.require_no_permission;
        }
    }

    private int getHelpString(@PermissionProperty int index) {
        switch (index) {
            default:
                return 0;
            case APP_OPS:
                if (!TipsPrefs.getInstance().displayInAppOpsTab()) {
                    return 0;
                }
                if (Ops.isPrivileged() || PermissionUtils.hasAppOpsPermission()) {
                    return R.string.help_app_ops_tab;
                } else return 0;
            case USES_PERMISSIONS:
                if (!TipsPrefs.getInstance().displayInUsesPermissionsTab()) {
                    return 0;
                }
                if (Ops.isPrivileged() || PermissionUtils.hasAppOpsPermission()) {
                    return R.string.help_uses_permissions_tab;
                } else return 0;
            case PERMISSIONS:
                if (!TipsPrefs.getInstance().displayInPermissionsTab()) {
                    return 0;
                }
                return R.string.help_permissions_tab;
        }
    }

    private void setSortBy(@AppDetailsFragment.SortOrder int sortBy) {
        showProgressIndicator(true);
        if (mMainModel == null) return;
        mMainModel.setSortOrder(sortBy, mNeededProperty);
    }

    private void refreshDetails() {
        if (mMainModel == null || mIsExternalApk) return;
        showProgressIndicator(true);
        mExecutor.submit(() -> {
            if (mMainModel != null) {
                mMainModel.setIsPackageChanged();
            }
        });
    }

    private void showProgressIndicator(boolean show) {
        ProgressIndicatorCompat.setVisibility(mProgressIndicator, show);
    }

    @UiThread
    private class AppDetailsRecyclerAdapter extends RecyclerView.Adapter<AppDetailsRecyclerAdapter.ViewHolder> {
        @NonNull
        private final List<AppDetailsItem<?>> mAdapterList;
        @PermissionProperty
        private int mRequestedProperty;
        @Nullable
        private String mConstraint;
        private final int mCardColor0;
        private final int mCardColor1;
        private final int mDefaultIndicatorColor;

        AppDetailsRecyclerAdapter() {
            mAdapterList = new ArrayList<>();
            mCardColor0 = ColorCodes.getListItemColor0(mActivity);
            mCardColor1 = ColorCodes.getListItemColor1(mActivity);
            mDefaultIndicatorColor = ColorCodes.getListItemDefaultIndicatorColor(mActivity);
        }

        @UiThread
        void setDefaultList(@NonNull List<AppDetailsItem<?>> list) {
            mRequestedProperty = mNeededProperty;
            mConstraint = mMainModel == null ? null : mMainModel.getSearchQuery();
            showProgressIndicator(false);
            synchronized (mAdapterList) {
                mAdapterList.clear();
                mAdapterList.addAll(list);
                notifyDataSetChanged();
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
            MaterialSwitch toggleSwitch;
            MaterialDivider divider;
            Chip chipType;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                switch (mRequestedProperty) {
                    case PERMISSIONS:
                        imageView = itemView.findViewById(R.id.icon);
                        textView1 = itemView.findViewById(R.id.label);
                        textView2 = itemView.findViewById(R.id.name);
                        textView3 = itemView.findViewById(R.id.taskAffinity);
                        textView4 = itemView.findViewById(R.id.orientation);
                        textView5 = itemView.findViewById(R.id.launchMode);
                        divider = itemView.findViewById(R.id.divider);
                        chipType = itemView.findViewById(R.id.type);
                        itemView.findViewById(R.id.softInput).setVisibility(View.GONE);
                        itemView.findViewById(R.id.launch).setVisibility(View.GONE);
                        itemView.findViewById(R.id.edit_shortcut_btn).setVisibility(View.GONE);
                        itemView.findViewById(R.id.toggle_button).setVisibility(View.GONE);
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
                        divider = itemView.findViewById(R.id.divider);
                        break;
                    case USES_PERMISSIONS:
                        textView1 = itemView.findViewById(R.id.perm_name);
                        textView2 = itemView.findViewById(R.id.perm_description);
                        textView3 = itemView.findViewById(R.id.perm_protection_level);
                        textView4 = itemView.findViewById(R.id.perm_package_name);
                        textView5 = itemView.findViewById(R.id.perm_group);
                        toggleSwitch = itemView.findViewById(R.id.perm_toggle_btn);
                        divider = itemView.findViewById(R.id.divider);
                        break;
                    default:
                        break;
                }
            }
        }

        @NonNull
        @Override
        public AppDetailsRecyclerAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            @SuppressLint("InflateParams") final View view;
            switch (mRequestedProperty) {
                case PERMISSIONS:
                default:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_primary, parent, false);
                    break;
                case APP_OPS:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_appop, parent, false);
                    break;
                case USES_PERMISSIONS:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_perm, parent, false);
                    break;
            }
            return new AppDetailsRecyclerAdapter.ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AppDetailsRecyclerAdapter.ViewHolder holder, int position) {
            Context context = holder.itemView.getContext();
            switch (mRequestedProperty) {
                case APP_OPS:
                    getAppOpsView(context, holder, position);
                    break;
                case USES_PERMISSIONS:
                    getUsesPermissionsView(context, holder, position);
                    break;
                case PERMISSIONS:
                    getPermissionsView(context, holder, position);
                    break;
                default:
                    break;
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

        private void getAppOpsView(@NonNull Context context, @NonNull AppDetailsRecyclerAdapter.ViewHolder holder, int index) {
            AppDetailsAppOpItem item;
            synchronized (mAdapterList) {
                item = (AppDetailsAppOpItem) mAdapterList.get(index);
            }
            final String opStr = item.name;
            PermissionInfo permissionInfo = item.permissionInfo;
            // Set op name
            SpannableStringBuilder opName = new SpannableStringBuilder(item.getOp() + " - ");
            if (item.name.equals(String.valueOf(item.getOp()))) {
                opName.append(getString(R.string.unknown_op));
            } else if (mConstraint != null && opStr.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                opName.append(UIUtils.getHighlightedText(opStr, mConstraint, mColorQueryStringHighlight));
            } else opName.append(opStr);
            holder.textView1.setText(opName);
            // Set op mode, running and duration
            StringBuilder opRunningInfo = new StringBuilder()
                    .append(context.getString(R.string.mode))
                    .append(LangUtils.getSeparatorString())
                    .append(AppOpsManagerCompat.modeToName(item.getMode()));
            if (item.isRunning()) {
                opRunningInfo.append(", ").append(context.getString(R.string.running));
            }
            if (item.getDuration() != 0) {
                opRunningInfo.append(", ").append(context.getString(R.string.duration))
                        .append(LangUtils.getSeparatorString())
                        .append(DateUtils.getFormattedDuration(context, item.getDuration(), true));
            }
            holder.textView7.setText(opRunningInfo);
            // Set accept-time and/or reject-time
            long currentTime = System.currentTimeMillis();
            boolean hasAcceptTime = item.getTime() != 0 && item.getTime() != -1;
            boolean hasRejectTime = item.getRejectTime() != 0 && item.getRejectTime() != -1;
            if (hasAcceptTime || hasRejectTime) {
                StringBuilder opTime = new StringBuilder();
                if (hasAcceptTime) {
                    opTime.append(context.getString(R.string.accept_time))
                            .append(LangUtils.getSeparatorString())
                            .append(DateUtils.getFormattedDuration(context, currentTime - item.getTime()))
                            .append(" ").append(context.getString(R.string.ago));
                }
                if (hasRejectTime) {
                    opTime.append(opTime.length() == 0 ? "" : "\n")
                            .append(context.getString(R.string.reject_time))
                            .append(LangUtils.getSeparatorString())
                            .append(DateUtils.getFormattedDuration(context, currentTime - item.getRejectTime()))
                            .append(" ").append(context.getString(R.string.ago));
                }
                holder.textView8.setVisibility(View.VISIBLE);
                holder.textView8.setText(opTime);
            } else holder.textView8.setVisibility(View.GONE);
            // Set others
            if (permissionInfo != null) {
                // Set permission name
                holder.textView6.setVisibility(View.VISIBLE);
                holder.textView6.setText(String.format(Locale.ROOT, "%s%s%s",
                        context.getString(R.string.permission_name),
                        LangUtils.getSeparatorString(),
                        permissionInfo.name));
                // Description
                CharSequence description = permissionInfo.loadDescription(mPackageManager);
                if (description != null) {
                    holder.textView2.setVisibility(View.VISIBLE);
                    holder.textView2.setText(description);
                } else holder.textView2.setVisibility(View.GONE);
                // Protection level
                String protectionLevel = Utils.getProtectionLevelString(permissionInfo);
                protectionLevel += '|' + (Objects.requireNonNull(item.permission).isGranted() ? "granted" : "revoked");
                holder.textView3.setVisibility(View.VISIBLE);
                holder.textView3.setText(String.format(Locale.ROOT, "⚑ %s", protectionLevel));
                // Set package name
                if (permissionInfo.packageName != null) {
                    holder.textView4.setVisibility(View.VISIBLE);
                    holder.textView4.setText(String.format(Locale.ROOT, "%s%s%s",
                            context.getString(R.string.package_name), LangUtils.getSeparatorString(),
                            permissionInfo.packageName));
                } else holder.textView4.setVisibility(View.GONE);
                // Set group name
                if (permissionInfo.group != null) {
                    holder.textView5.setVisibility(View.VISIBLE);
                    holder.textView5.setText(String.format(Locale.ROOT, "%s%s%s",
                            context.getString(R.string.group), LangUtils.getSeparatorString(), permissionInfo.group));
                } else {
                    holder.textView5.setVisibility(View.GONE);
                }
            } else {
                holder.textView2.setVisibility(View.GONE);
                holder.textView3.setVisibility(View.GONE);
                holder.textView4.setVisibility(View.GONE);
                holder.textView5.setVisibility(View.GONE);
                holder.textView6.setVisibility(View.GONE);
            }
            // Set background
            if (item.isDangerous) {
                holder.divider.setDividerColor(ColorCodes.getPermissionDangerousIndicatorColor(context));
            } else {
                holder.divider.setDividerColor(mDefaultIndicatorColor);
            }
            if (!Ops.isPrivileged()) {
                // No root or ADB, hide toggle buttons
                holder.toggleSwitch.setVisibility(View.GONE);
                return;
            }
            // Op Switch
            holder.toggleSwitch.setVisibility(View.VISIBLE);
            // op granted
            holder.toggleSwitch.setChecked(item.isAllowed());
            holder.itemView.setOnClickListener(v -> {
                boolean isAllowed = !item.isAllowed();
                mExecutor.submit(() -> {
                    if (mMainModel != null && mMainModel.setAppOpMode(item)) {
                        ThreadUtils.postOnMainThread(() -> notifyItemChanged(index));
                    } else {
                        ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(isAllowed
                                ? R.string.failed_to_enable_op : R.string.failed_to_disable_op));
                    }
                });
            });
            holder.itemView.setOnLongClickListener(v -> {
                List<Integer> modes = AppOpsManagerCompat.getModeConstants();
                new SearchableSingleChoiceDialogBuilder<>(mActivity, modes, getAppOpModeNames(modes))
                        .setTitle(R.string.set_app_op_mode)
                        .setSelection(item.getMode())
                        .setOnSingleChoiceClickListener((dialog, which, item1, isChecked) -> {
                            int opMode = modes.get(which);
                            mExecutor.submit(() -> {
                                if (mMainModel != null && mMainModel.setAppOpMode(item, opMode)) {
                                    ThreadUtils.postOnMainThread(() -> notifyItemChanged(index));
                                } else {
                                    ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(
                                            R.string.failed_to_change_app_op_mode));
                                }
                            });
                            dialog.dismiss();
                        })
                        .show();
                return true;
            });
            ((MaterialCardView) holder.itemView).setCardBackgroundColor(mCardColor1);
        }

        private void getUsesPermissionsView(@NonNull Context context, @NonNull AppDetailsRecyclerAdapter.ViewHolder holder, int index) {
            AppDetailsPermissionItem permissionItem;
            synchronized (mAdapterList) {
                permissionItem = (AppDetailsPermissionItem) mAdapterList.get(index);
            }
            @NonNull PermissionInfo permissionInfo = permissionItem.vanillaItem;
            final String permName = permissionInfo.name;
            // Set permission name
            if (mConstraint != null && permName.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.textView1.setText(UIUtils.getHighlightedText(permName, mConstraint, mColorQueryStringHighlight));
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
            protectionLevel += '|' + (permissionItem.permission.isGranted() ? "granted" : "revoked");
            holder.textView3.setText(String.format(Locale.ROOT, "⚑ %s", protectionLevel));
            // Set background color
            if (permissionItem.isDangerous) {
                holder.divider.setDividerColor(ColorCodes.getPermissionDangerousIndicatorColor(context));
            } else {
                holder.divider.setDividerColor(mDefaultIndicatorColor);
            }
            // Set package name
            if (permissionInfo.packageName != null) {
                holder.textView4.setVisibility(View.VISIBLE);
                holder.textView4.setText(String.format("%s%s%s", context.getString(R.string.package_name),
                        LangUtils.getSeparatorString(), permissionInfo.packageName));
            } else holder.textView4.setVisibility(View.GONE);
            // Set group name
            if (permissionInfo.group != null) {
                holder.textView5.setVisibility(View.VISIBLE);
                holder.textView5.setText(String.format("%s%s%s", context.getString(R.string.group),
                        LangUtils.getSeparatorString(), permissionInfo.group));
            } else holder.textView5.setVisibility(View.GONE);
            // Permission Switch
            boolean canGrantOrRevokePermission = permissionItem.modifiable && !mIsExternalApk;
            if (canGrantOrRevokePermission) {
                holder.toggleSwitch.setVisibility(View.VISIBLE);
                holder.toggleSwitch.setChecked(permissionItem.isGranted());
                holder.itemView.setOnClickListener(v -> mExecutor.submit(() -> {
                    try {
                        if (Objects.requireNonNull(mMainModel).togglePermission(permissionItem)) {
                            ThreadUtils.postOnMainThread(() -> notifyItemChanged(index));
                        } else throw new Exception("Couldn't grant permission: " + permName);
                    } catch (Exception e) {
                        e.printStackTrace();
                        ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(permissionItem.isGranted()
                                ? R.string.failed_to_grant_permission
                                : R.string.failed_to_revoke_permission));
                    }
                }));
            } else {
                holder.toggleSwitch.setVisibility(View.GONE);
                holder.itemView.setOnClickListener(null);
                holder.itemView.setClickable(false);
            }
            int flags = permissionItem.permission.getFlags();
            holder.itemView.setOnLongClickListener(flags == 0 ? null : v -> {
                // TODO: 12/1/22 Use ViewModel
                SparseArray<String> permissionFlags = PermissionCompat.getPermissionFlagsWithString(flags);
                String[] flagStrings = new String[permissionFlags.size()];
                for (int i = 0; i < flagStrings.length; ++i) {
                    flagStrings[i] = permissionFlags.valueAt(i);
                }
                new SearchableItemsDialogBuilder<>(mActivity, flagStrings)
                        .setTitle(R.string.permission_flags)
                        .setNegativeButton(R.string.close, null)
                        .show();
                return true;
            });
            holder.itemView.setLongClickable(flags != 0);
            ((MaterialCardView) holder.itemView).setCardBackgroundColor(mCardColor1);
        }

        private void getPermissionsView(@NonNull Context context, @NonNull AppDetailsRecyclerAdapter.ViewHolder holder, int index) {
            AppDetailsDefinedPermissionItem permissionItem;
            synchronized (mAdapterList) {
                permissionItem = (AppDetailsDefinedPermissionItem) mAdapterList.get(index);
            }
            PermissionInfo permissionInfo = permissionItem.vanillaItem;
            // Internal or external
            holder.chipType.setText(permissionItem.isExternal ? R.string.external : R.string.internal);
            // Label
            holder.textView1.setText(permissionInfo.loadLabel(mPackageManager));
            // Name
            if (mConstraint != null && permissionInfo.name.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.textView2.setText(UIUtils.getHighlightedText(permissionInfo.name, mConstraint, mColorQueryStringHighlight));
            } else {
                holder.textView2.setText(permissionInfo.name.startsWith(mPackageName) ?
                        permissionInfo.name.replaceFirst(mPackageName, "") : permissionInfo.name);
            }
            // Icon
            mImageLoader.displayImage(mPackageName + "_" + permissionInfo.name, permissionInfo, holder.imageView);
            // Description
            CharSequence description = permissionInfo.loadDescription(mPackageManager);
            if (description != null) {
                holder.textView3.setVisibility(View.VISIBLE);
                holder.textView3.setText(description);
            } else {
                holder.textView3.setVisibility(View.GONE);
            }
            // LaunchMode
            holder.textView4.setText(String.format(Locale.ROOT, "%s: %s",
                    getString(R.string.group), permissionInfo.group + permAppOp(permissionInfo.name)));
            // Protection level
            String protectionLevel = Utils.getProtectionLevelString(permissionInfo);
            holder.textView5.setText(String.format(Locale.ROOT, "⚑ %s", protectionLevel));
            // Set border color
            if (protectionLevel.contains("dangerous")) {
                holder.divider.setDividerColor(ColorCodes.getPermissionDangerousIndicatorColor(context));
            } else {
                holder.divider.setDividerColor(mDefaultIndicatorColor);
            }
            ((MaterialCardView) holder.itemView).setCardBackgroundColor(mCardColor1);
        }

        @NonNull
        private String permAppOp(String s) {
            String opStr = AppOpsManagerCompat.permissionToOp(s);
            return opStr != null ? "\nAppOp: " + opStr : "";
        }
    }
}
