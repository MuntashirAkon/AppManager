// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.IPackageDataObserver;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.android.internal.util.TextUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ListItemCreator;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOpModeNames;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOpModes;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOpNames;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOps;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getPrimaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

public class OneClickOpsActivity extends BaseActivity {
    LinearProgressIndicator mProgressIndicator;

    private OneClickOpsViewModel mViewModel;
    private ListItemCreator mItemCreator;
    private final BroadcastReceiver mBatchOpsBroadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mProgressIndicator.hide();
        }
    };

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_one_click_ops);
        setSupportActionBar(findViewById(R.id.toolbar));
        mViewModel = new ViewModelProvider(this).get(OneClickOpsViewModel.class);
        mItemCreator = new ListItemCreator(this, R.id.container);
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        setItems();
        // Watch LiveData
        mViewModel.watchTrackerCount().observe(this, this::blockTrackers);
        mViewModel.watchComponentCount().observe(this, listPair ->
                blockComponents(listPair.first, listPair.second));
        mViewModel.watchAppOpsCount().observe(this, listPairPair ->
                setAppOps(listPairPair.first, listPairPair.second.first, listPairPair.second.second));
        mViewModel.watchTrimCachesResult().observe(this, isSuccessful -> {
            mProgressIndicator.hide();
            UIUtils.displayShortToast(isSuccessful ? R.string.done : R.string.failed);
        });
    }

    private void setItems() {
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.block_unblock_trackers),
                        getString(R.string.block_unblock_trackers_description))
                .setOnClickListener(v -> {
                    if (!AppPref.isRootEnabled()) {
                        UIUtils.displayShortToast(R.string.only_works_in_root_mode);
                        return;
                    }
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.block_unblock_trackers)
                            .setMessage(R.string.apply_to_system_apps_question)
                            .setPositiveButton(R.string.no, (dialog, which) -> {
                                mProgressIndicator.show();
                                mViewModel.blockTrackers(false);
                            })
                            .setNegativeButton(R.string.yes, (dialog, which) -> {
                                mProgressIndicator.show();
                                mViewModel.blockTrackers(true);
                            })
                            .show();
                });
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.block_components_dots),
                        getString(R.string.block_components_description))
                .setOnClickListener(v -> {
                    if (!AppPref.isRootEnabled()) {
                        UIUtils.displayShortToast(R.string.only_works_in_root_mode);
                        return;
                    }
                    new TextInputDialogBuilder(this, R.string.input_signatures)
                            .setHelperText(R.string.input_signatures_description)
                            .setCheckboxLabel(R.string.apply_to_system_apps)
                            .setTitle(R.string.block_components_dots)
                            .setPositiveButton(R.string.search, (dialog, which, signatureNames, systemApps) -> {
                                if (signatureNames == null) return;
                                mProgressIndicator.show();
                                String[] signatures = signatureNames.toString().split("\\s+");
                                mViewModel.blockComponents(systemApps, signatures);
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.set_mode_for_app_ops_dots),
                        getString(R.string.deny_app_ops_description))
                .setOnClickListener(v -> showAppOpsSelectionDialog());
        mItemCreator.addItemWithTitleSubtitle(getText(R.string.back_up),
                getText(R.string.backup_msg)).setOnClickListener(v ->
                new BackupTasksDialogFragment().show(getSupportFragmentManager(),
                        BackupTasksDialogFragment.TAG));
        mItemCreator.addItemWithTitleSubtitle(getText(R.string.restore),
                getText(R.string.restore_msg)).setOnClickListener(v ->
                new RestoreTasksDialogFragment().show(getSupportFragmentManager(),
                        RestoreTasksDialogFragment.TAG));
        if (BuildConfig.DEBUG) {
            mItemCreator.addItemWithTitleSubtitle(getString(R.string.clear_data_from_uninstalled_apps),
                            getString(R.string.clear_data_from_uninstalled_apps_description))
                    .setOnClickListener(v -> clearData());
            mItemCreator.addItemWithTitleSubtitle(getString(R.string.clear_app_cache),
                            getString(R.string.clear_app_cache_description))
                    .setOnClickListener(v -> clearAppCache());
        }
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.trim_caches_in_all_apps),
                        getString(R.string.trim_caches_in_all_apps_description))
                .setOnClickListener(v -> {
                    if (!AppPref.isRootOrAdbEnabled()) {
                        UIUtils.displayShortToast(R.string.only_works_in_root_or_adb_mode);
                        return;
                    }
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.trim_caches_in_all_apps)
                            .setMessage(R.string.are_you_sure)
                            .setNegativeButton(R.string.no, null)
                            .setPositiveButton(R.string.yes, (dialog, which) -> {
                                mProgressIndicator.show();
                                mViewModel.trimCaches();
                            })
                            .show();
                });
        mProgressIndicator.hide();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mBatchOpsBroadCastReceiver, new IntentFilter(BatchOpsService.ACTION_BATCH_OPS_COMPLETED));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBatchOpsBroadCastReceiver);
        mProgressIndicator.hide();
    }

    private void blockTrackers(@Nullable List<ItemCount> trackerCounts) {
        mProgressIndicator.hide();
        if (trackerCounts == null) {
            UIUtils.displayShortToast(R.string.failed_to_fetch_package_info);
            return;
        }
        if (trackerCounts.isEmpty()) {
            UIUtils.displayShortToast(R.string.no_tracker_found);
            return;
        }
        final ArrayList<String> trackerPackages = new ArrayList<>();
        final List<CharSequence> trackerPackagesWithTrackerCount = new ArrayList<>(trackerCounts.size());
        for (ItemCount tracker : trackerCounts) {
            trackerPackages.add(tracker.packageName);
            trackerPackagesWithTrackerCount.add(new SpannableStringBuilder(getPrimaryText(this, tracker.packageLabel))
                    .append("\n").append(getSmallerText(getResources().getQuantityString(R.plurals.no_of_trackers,
                            tracker.count, tracker.count))));
        }
        new SearchableMultiChoiceDialogBuilder<>(this, trackerPackages, trackerPackagesWithTrackerCount)
                .setSelections(trackerPackages)
                .setTitle(R.string.found_trackers)
                .setPositiveButton(R.string.block, (dialog, which, selectedPackages) -> {
                    mProgressIndicator.show();
                    Intent intent = new Intent(this, BatchOpsService.class);
                    intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, selectedPackages);
                    intent.putExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_BLOCK_TRACKERS);
                    intent.putExtra(BatchOpsService.EXTRA_HEADER, getString(R.string.one_click_ops));
                    ContextCompat.startForegroundService(this, intent);
                })
                .setNeutralButton(R.string.unblock, (dialog, which, selectedPackages) -> {
                    mProgressIndicator.show();
                    Intent intent = new Intent(this, BatchOpsService.class);
                    intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, selectedPackages);
                    intent.putExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_UNBLOCK_TRACKERS);
                    intent.putExtra(BatchOpsService.EXTRA_HEADER, getString(R.string.one_click_ops));
                    ContextCompat.startForegroundService(this, intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void blockComponents(@Nullable List<ItemCount> componentCounts, @NonNull String[] signatures) {
        mProgressIndicator.hide();
        if (componentCounts == null) {
            UIUtils.displayShortToast(R.string.failed_to_fetch_package_info);
            return;
        }
        if (componentCounts.isEmpty()) {
            UIUtils.displayShortToast(R.string.no_matching_package_found);
            return;
        }
        SpannableStringBuilder builder;
        final ArrayList<String> selectedPackages = new ArrayList<>();
        List<CharSequence> packageNamesWithComponentCount = new ArrayList<>();
        for (ItemCount component : componentCounts) {
            builder = new SpannableStringBuilder(getPrimaryText(this, component.packageLabel))
                    .append("\n").append(getSmallerText(getResources().getQuantityString(R.plurals.no_of_components,
                            component.count, component.count)));
            selectedPackages.add(component.packageName);
            packageNamesWithComponentCount.add(builder);
        }
        new SearchableMultiChoiceDialogBuilder<>(this, selectedPackages, packageNamesWithComponentCount)
                .setSelections(selectedPackages)
                .setTitle(R.string.filtered_packages)
                .setPositiveButton(R.string.apply, (dialog1, which1, selectedItems) -> {
                    mProgressIndicator.show();
                    Intent intent = new Intent(this, BatchOpsService.class);
                    intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, selectedItems);
                    intent.putExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_BLOCK_COMPONENTS);
                    intent.putExtra(BatchOpsService.EXTRA_HEADER, getString(R.string.one_click_ops));
                    Bundle args = new Bundle();
                    args.putStringArray(BatchOpsManager.ARG_SIGNATURES, signatures);
                    intent.putExtra(BatchOpsService.EXTRA_OP_EXTRA_ARGS, args);
                    ContextCompat.startForegroundService(this, intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAppOpsSelectionDialog() {
        if (!AppPref.isRootOrAdbEnabled()) {
            UIUtils.displayShortToast(R.string.only_works_in_root_or_adb_mode);
            return;
        }
        List<Integer> modes = getAppOpModes();
        List<Integer> appOps = getAppOps();
        List<CharSequence> modeNames = Arrays.asList(getAppOpModeNames(modes));
        List<CharSequence> appOpNames = Arrays.asList(getAppOpNames(appOps));
        TextInputDropdownDialogBuilder builder = new TextInputDropdownDialogBuilder(this, R.string.input_app_ops);
        builder.setTitle(R.string.set_mode_for_app_ops_dots)
                .setAuxiliaryInput(R.string.mode, null, null, modeNames, true)
                .setCheckboxLabel(R.string.apply_to_system_apps)
                .setHelperText(R.string.input_app_ops_description)
                .setPositiveButton(R.string.search, (dialog, which, appOpNameList, systemApps) -> {
                    if (appOpNameList == null) return;
                    // Get mode
                    int mode;
                    int[] appOpList;
                    try {
                        String[] appOpsStr = appOpNameList.toString().split("\\s+");
                        if (appOpsStr.length == 0) return;
                        mode = Utils.getIntegerFromString(builder.getAuxiliaryInput(), modeNames, modes);
                        // User can unknowingly insert duplicate values for app ops
                        Set<Integer> appOpSet = new ArraySet<>(appOpsStr.length);
                        for (String appOp : appOpsStr) {
                            appOpSet.add(Utils.getIntegerFromString(appOp, appOpNames, appOps));
                        }
                        appOpList = ArrayUtils.convertToIntArray(appOpSet);
                    } catch (IllegalArgumentException e) {
                        UIUtils.displayShortToast(R.string.failed_to_parse_some_numbers);
                        return;
                    }
                    mProgressIndicator.show();
                    mViewModel.setAppOps(appOpList, mode, systemApps);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setAppOps(@Nullable List<AppOpCount> appOpCounts, @NonNull int[] appOpList, int mode) {
        mProgressIndicator.hide();
        if (appOpCounts == null) {
            UIUtils.displayShortToast(R.string.failed_to_fetch_package_info);
            return;
        }
        if (appOpCounts.isEmpty()) {
            UIUtils.displayShortToast(R.string.no_matching_package_found);
            return;
        }
        SpannableStringBuilder builder1;
        final ArrayList<String> selectedPackages = new ArrayList<>();
        List<CharSequence> packagesWithAppOpCount = new ArrayList<>();
        for (AppOpCount appOp : appOpCounts) {
            builder1 = new SpannableStringBuilder(getPrimaryText(this, appOp.packageLabel))
                    .append("\n").append(getSmallerText("(" + appOp.count + ") " + TextUtils.joinSpannable(", ",
                            appOpToNames(appOp.appOps))));
            selectedPackages.add(appOp.packageName);
            packagesWithAppOpCount.add(builder1);
        }
        new SearchableMultiChoiceDialogBuilder<>(this, selectedPackages, packagesWithAppOpCount)
                .setSelections(selectedPackages)
                .setTitle(R.string.filtered_packages)
                .setPositiveButton(R.string.apply, (dialog1, which1, selectedItems) -> {
                    mProgressIndicator.show();
                    Intent intent = new Intent(this, BatchOpsService.class);
                    intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, selectedItems);
                    intent.putExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_SET_APP_OPS);
                    intent.putExtra(BatchOpsService.EXTRA_HEADER, getString(R.string.one_click_ops));
                    Bundle args = new Bundle();
                    args.putIntArray(BatchOpsManager.ARG_APP_OPS, appOpList);
                    args.putInt(BatchOpsManager.ARG_APP_OP_MODE, mode);
                    intent.putExtra(BatchOpsService.EXTRA_OP_EXTRA_ARGS, args);
                    ContextCompat.startForegroundService(this, intent);
                })
                .setNegativeButton(R.string.cancel, (dialog1, which1, selectedItems) -> mProgressIndicator.hide())
                .show();
    }

    private void clearData() {
        // TODO
        Toast.makeText(this, "Not implemented yet.", Toast.LENGTH_SHORT).show();
    }

    private void clearAppCache() {
        // TODO
        Toast.makeText(this, "Not implemented yet.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @NonNull
    private List<String> appOpToNames(@NonNull Collection<Integer> appOps) {
        List<String> appOpNames = new ArrayList<>(appOps.size());
        for (int appOp : appOps) {
            appOpNames.add(AppOpsManager.opToName(appOp));
        }
        return appOpNames;
    }

    static class ClearDataObserver extends IPackageDataObserver.Stub {
        boolean finished;
        boolean result;

        @Override
        public void onRemoveCompleted(String packageName, boolean succeeded) {
            synchronized (this) {
                finished = true;
                result = succeeded;
                notifyAll();
            }
        }
    }
}