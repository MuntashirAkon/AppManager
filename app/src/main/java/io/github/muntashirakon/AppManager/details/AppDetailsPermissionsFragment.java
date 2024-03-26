// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOpModeNames;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOpNames;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PermissionInfo;
import android.graphics.Color;
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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsAppOpItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsDefinedPermissionItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsPermissionItem;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.self.pref.TipsPrefs;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.dialog.SearchableItemsDialogBuilder;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder;
import io.github.muntashirakon.view.ProgressIndicatorCompat;
import io.github.muntashirakon.widget.MaterialAlertView;
import io.github.muntashirakon.widget.RecyclerView;

public class AppDetailsPermissionsFragment extends AppDetailsFragment {
    @IntDef(value = {
            APP_OPS,
            USES_PERMISSIONS,
            PERMISSIONS,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PermissionProperty {
    }

    private String mPackageName;
    private AppDetailsRecyclerAdapter mAdapter;
    private boolean mIsExternalApk;
    @PermissionProperty
    private int mNeededProperty;
    private int mSortOrder;
    private String mSearchQuery;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mNeededProperty = requireArguments().getInt(ARG_TYPE);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        emptyView.setText(getNotFoundString(mNeededProperty));
        mAdapter = new AppDetailsRecyclerAdapter();
        recyclerView.setAdapter(mAdapter);
        alertView.setEndIconOnClickListener(v -> {
            alertView.hide();
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
        if (helpStringRes != 0) alertView.setText(helpStringRes);
        if (helpStringRes == 0) {
            alertView.setVisibility(View.GONE);
        } else {
            alertView.postDelayed(() -> alertView.hide(), 15_000);
        }
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
        });
    }

    @Override
    public void onRefresh() {
        refreshDetails();
        swipeRefresh.setRefreshing(false);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        switch (mNeededProperty) {
            case APP_OPS:
                inflater.inflate(R.menu.fragment_app_details_app_ops_actions, menu);
                break;
            case USES_PERMISSIONS:
                if (viewModel != null && !viewModel.isExternalApk()) {
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
        if (viewModel == null || viewModel.isExternalApk()) {
            return;
        }
        MenuItem sortItem = menu.findItem(sSortMenuItemIdsMap[viewModel.getSortOrder(mNeededProperty)]);
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
            ProgressIndicatorCompat.setVisibility(progressIndicator, true);
            // TODO: 19/3/23 Perform using a ViewModel
            ThreadUtils.postOnBackgroundThread(() -> {
                if (viewModel == null || !viewModel.resetAppOps()) {
                    ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.failed_to_reset_app_ops));
                } else {
                    ThreadUtils.postOnMainThread(() -> {
                        if (!isDetached()) {
                            refreshDetails();
                        }
                    });
                }
            });
        } else if (id == R.id.action_deny_dangerous_app_ops) {  // App ops
            ProgressIndicatorCompat.setVisibility(progressIndicator, true);
            // TODO: 19/3/23 Perform using a ViewModel
            ThreadUtils.postOnBackgroundThread(() -> {
                boolean isSuccessful = ExUtils.requireNonNullElse(() -> viewModel != null
                        && viewModel.ignoreDangerousAppOps(), false);
                if (isSuccessful) {
                    ThreadUtils.postOnMainThread(() -> {
                        if (!isDetached()) {
                            refreshDetails();
                        }
                    });
                } else {
                    ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(
                            R.string.failed_to_deny_dangerous_app_ops));
                }
            });
        } else if (id == R.id.action_toggle_default_app_ops) {  // App ops
            ProgressIndicatorCompat.setVisibility(progressIndicator, true);
            // Turn filter on/off
            boolean curr = Prefs.AppDetailsPage.displayDefaultAppOps();
            Prefs.AppDetailsPage.setDisplayDefaultAppOps(!curr);
            refreshDetails();
        } else if (id == R.id.action_custom_app_op) {
            List<Integer> modes = AppOpsManagerCompat.getModeConstants();
            List<Integer> appOps = AppOpsManagerCompat.getAllOps();
            List<CharSequence> modeNames = Arrays.asList(getAppOpModeNames(modes));
            List<CharSequence> appOpNames = Arrays.asList(getAppOpNames(appOps));
            TextInputDropdownDialogBuilder builder = new TextInputDropdownDialogBuilder(activity, R.string.set_custom_app_op);
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
                        // TODO: 22/5/23 Perform using a ViewModel
                        ThreadUtils.postOnBackgroundThread(() -> {
                            if (viewModel != null && viewModel.setAppOp(op, mode)) {
                                ThreadUtils.postOnMainThread(() -> {
                                    if (!isDetached()) {
                                        refreshDetails();
                                    }
                                });
                            } else {
                                ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(
                                        R.string.failed_to_enable_op));
                            }
                        });
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else if (id == R.id.action_deny_dangerous_permissions) {  // permissions
            ProgressIndicatorCompat.setVisibility(progressIndicator, true);
            // TODO: 22/5/23 Perform using a ViewModel
            ThreadUtils.postOnBackgroundThread(() -> {
                if (viewModel == null || !viewModel.revokeDangerousPermissions()) {
                    ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(
                            R.string.failed_to_deny_dangerous_perms));
                }
                ThreadUtils.postOnMainThread(() -> {
                    if (!isDetached()) {
                        refreshDetails();
                    }
                });
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

    private int getNotFoundString(@PermissionProperty int index) {
        switch (index) {
            case APP_OPS:
                if (mIsExternalApk) {
                    return R.string.external_apk_no_app_op;
                } else if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.GET_APP_OPS_STATS)) {
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
                if (SelfPermissions.canModifyAppOpMode()) {
                    return R.string.help_app_ops_tab;
                } else return 0;
            case USES_PERMISSIONS:
                if (!TipsPrefs.getInstance().displayInUsesPermissionsTab()) {
                    return 0;
                }
                if (SelfPermissions.canModifyPermissions()) {
                    return R.string.help_uses_permissions_tab;
                } else return 0;
            case PERMISSIONS:
                if (!TipsPrefs.getInstance().displayInPermissionsTab()) {
                    return 0;
                }
                return R.string.help_permissions_tab;
        }
    }

    private void setSortBy(@SortOrder int sortBy) {
        ProgressIndicatorCompat.setVisibility(progressIndicator, true);
        if (viewModel == null) return;
        viewModel.setSortOrder(sortBy, mNeededProperty);
    }

    private void refreshDetails() {
        if (viewModel == null || mIsExternalApk) return;
        ProgressIndicatorCompat.setVisibility(progressIndicator, true);
        viewModel.triggerPackageChange();
    }

    @UiThread
    private class AppDetailsRecyclerAdapter extends RecyclerView.Adapter<AppDetailsRecyclerAdapter.ViewHolder> {
        @NonNull
        private final List<AppDetailsItem<?>> mAdapterList;
        @PermissionProperty
        private int mRequestedProperty;
        @Nullable
        private String mConstraint;
        private boolean mCanModifyAppOpMode;

        AppDetailsRecyclerAdapter() {
            mAdapterList = new ArrayList<>();
        }

        @UiThread
        void setDefaultList(@NonNull List<AppDetailsItem<?>> list) {
            ThreadUtils.postOnBackgroundThread(() -> {
                mRequestedProperty = mNeededProperty;
                mConstraint = viewModel == null ? null : viewModel.getSearchQuery();
                mCanModifyAppOpMode = SelfPermissions.canModifyAppOpMode();
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
            Chip chipType;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                this.itemView = (MaterialCardView) itemView;
                switch (mRequestedProperty) {
                    case PERMISSIONS:
                        imageView = itemView.findViewById(R.id.icon);
                        textView1 = itemView.findViewById(R.id.label);
                        textView2 = itemView.findViewById(R.id.name);
                        textView3 = itemView.findViewById(R.id.taskAffinity);
                        textView4 = itemView.findViewById(R.id.orientation);
                        textView5 = itemView.findViewById(R.id.launchMode);
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
                        break;
                    case USES_PERMISSIONS:
                        textView1 = itemView.findViewById(R.id.perm_name);
                        textView2 = itemView.findViewById(R.id.perm_description);
                        textView3 = itemView.findViewById(R.id.perm_protection_level);
                        textView4 = itemView.findViewById(R.id.perm_package_name);
                        textView5 = itemView.findViewById(R.id.perm_group);
                        toggleSwitch = itemView.findViewById(R.id.perm_toggle_btn);
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

        private void getAppOpsView(@NonNull Context context, @NonNull ViewHolder holder, int index) {
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
                opName.append(UIUtils.getHighlightedText(opStr, mConstraint, colorQueryStringHighlight));
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
                CharSequence description = permissionInfo.loadDescription(packageManager);
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
                holder.itemView.setStrokeColor(ColorCodes.getPermissionDangerousIndicatorColor(context));
            } else {
                holder.itemView.setStrokeColor(Color.TRANSPARENT);
            }
            // Op Switch
            holder.toggleSwitch.setVisibility(mCanModifyAppOpMode ? View.VISIBLE : View.GONE);
            // op granted
            holder.toggleSwitch.setChecked(item.isAllowed());
            holder.itemView.setOnClickListener(v -> {
                boolean isAllowed = !item.isAllowed();
                // TODO: 22/5/23 Perform using a ViewModel
                ThreadUtils.postOnBackgroundThread(() -> {
                    if (viewModel != null && viewModel.setAppOpMode(item)) {
                        ThreadUtils.postOnMainThread(() -> notifyItemChanged(index));
                    } else {
                        ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(isAllowed
                                ? R.string.failed_to_enable_op : R.string.failed_to_disable_op));
                    }
                });
            });
            holder.itemView.setOnLongClickListener(v -> {
                List<Integer> modes = AppOpsManagerCompat.getModeConstants();
                new SearchableSingleChoiceDialogBuilder<>(activity, modes, getAppOpModeNames(modes))
                        .setTitle(R.string.set_app_op_mode)
                        .setSelection(item.getMode())
                        .setOnSingleChoiceClickListener((dialog, which, item1, isChecked) -> {
                            int opMode = modes.get(which);
                            // TODO: 22/5/23 Perform using a ViewModel
                            ThreadUtils.postOnBackgroundThread(() -> {
                                if (viewModel != null && viewModel.setAppOpMode(item, opMode)) {
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
        }

        private void getUsesPermissionsView(@NonNull Context context, @NonNull ViewHolder holder, int index) {
            AppDetailsPermissionItem permissionItem;
            synchronized (mAdapterList) {
                permissionItem = (AppDetailsPermissionItem) mAdapterList.get(index);
            }
            @NonNull PermissionInfo permissionInfo = permissionItem.mainItem;
            final String permName = permissionInfo.name;
            // Set permission name
            if (mConstraint != null && permName.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.textView1.setText(UIUtils.getHighlightedText(permName, mConstraint, colorQueryStringHighlight));
            } else holder.textView1.setText(permName);
            // Set others
            // Description
            CharSequence description = permissionInfo.loadDescription(packageManager);
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
                holder.itemView.setStrokeColor(ColorCodes.getPermissionDangerousIndicatorColor(context));
            } else {
                holder.itemView.setStrokeColor(Color.TRANSPARENT);
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
                // TODO: 22/5/23 Perform using a ViewModel
                holder.itemView.setOnClickListener(v -> ThreadUtils.postOnBackgroundThread(() -> {
                    try {
                        if (Objects.requireNonNull(viewModel).togglePermission(permissionItem)) {
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
                new SearchableItemsDialogBuilder<>(activity, flagStrings)
                        .setTitle(R.string.permission_flags)
                        .setNegativeButton(R.string.close, null)
                        .show();
                return true;
            });
            holder.itemView.setLongClickable(flags != 0);
        }

        private void getPermissionsView(@NonNull Context context, @NonNull ViewHolder holder, int index) {
            AppDetailsDefinedPermissionItem permissionItem;
            synchronized (mAdapterList) {
                permissionItem = (AppDetailsDefinedPermissionItem) mAdapterList.get(index);
            }
            PermissionInfo permissionInfo = permissionItem.mainItem;
            // Internal or external
            holder.chipType.setText(permissionItem.isExternal ? R.string.external : R.string.internal);
            // Label
            holder.textView1.setText(permissionInfo.loadLabel(packageManager));
            // Name
            if (mConstraint != null && permissionInfo.name.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.textView2.setText(UIUtils.getHighlightedText(permissionInfo.name, mConstraint, colorQueryStringHighlight));
            } else {
                holder.textView2.setText(permissionInfo.name.startsWith(mPackageName) ?
                        permissionInfo.name.replaceFirst(mPackageName, "") : permissionInfo.name);
            }
            // Icon
            String tag = mPackageName + "_" + permissionInfo.name;
            holder.imageView.setTag(tag);
            ImageLoader.getInstance().displayImage(tag, permissionInfo, holder.imageView);
            // Description
            CharSequence description = permissionInfo.loadDescription(packageManager);
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
                holder.itemView.setStrokeColor(ColorCodes.getPermissionDangerousIndicatorColor(context));
            } else {
                holder.itemView.setStrokeColor(Color.TRANSPARENT);
            }
        }

        @NonNull
        private String permAppOp(String s) {
            String opStr = AppOpsManagerCompat.permissionToOp(s);
            return opStr != null ? "\nAppOp: " + opStr : "";
        }
    }
}
