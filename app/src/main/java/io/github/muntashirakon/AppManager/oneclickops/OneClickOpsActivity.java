// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOpModeNames;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOpNames;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.SpannableStringBuilder;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.behavior.DexOptDialog;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.AppManager.utils.ListItemCreator;
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder;

public class OneClickOpsActivity extends BaseActivity {
    public static final String EXTRA_OP = "op";

    LinearProgressIndicator progressIndicator;
    PowerManager.WakeLock wakeLock;

    private OneClickOpsViewModel mViewModel;
    private ListItemCreator mItemCreator;
    private final BroadcastReceiver mBatchOpsBroadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            progressIndicator.hide();
        }
    };

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        int op = getIntent().getIntExtra(EXTRA_OP, BatchOpsManager.OP_NONE);
        if (op == BatchOpsManager.OP_CLEAR_CACHE) {
            Intent intent = new Intent(this, BatchOpsService.class);
            intent.putExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_CLEAR_CACHE);
            intent.putExtra(BatchOpsService.EXTRA_HEADER, getString(R.string.one_click_ops));
            ContextCompat.startForegroundService(this, intent);
            finishAndRemoveTask();
            return;
        }
        setContentView(R.layout.activity_one_click_ops);
        setSupportActionBar(findViewById(R.id.toolbar));
        mViewModel = new ViewModelProvider(this).get(OneClickOpsViewModel.class);
        mItemCreator = new ListItemCreator(this, R.id.container);
        progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);
        wakeLock = CpuUtils.getPartialWakeLock("1-click_ops");
        setItems();
        // Watch LiveData
        mViewModel.watchTrackerCount().observe(this, this::blockTrackers);
        mViewModel.watchComponentCount().observe(this, listPair ->
                blockComponents(listPair.first, listPair.second));
        mViewModel.watchAppOpsCount().observe(this, listPairPair ->
                setAppOps(listPairPair.first, listPairPair.second.first, listPairPair.second.second));
        mViewModel.getClearDataCandidates().observe(this, this::clearData);
        mViewModel.watchTrimCachesResult().observe(this, isSuccessful -> {
            CpuUtils.releaseWakeLock(wakeLock);
            progressIndicator.hide();
            UIUtils.displayShortToast(isSuccessful ? R.string.done : R.string.failed);
        });
        mViewModel.getAppsInstalledByAmForDexOpt().observe(this, packages -> {
            CpuUtils.releaseWakeLock(wakeLock);
            progressIndicator.hide();
            DexOptDialog dialog = DexOptDialog.getInstance(packages);
            dialog.show(getSupportFragmentManager(), DexOptDialog.TAG);
        });
    }

    private void setItems() {
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.block_unblock_trackers),
                        getString(R.string.block_unblock_trackers_description))
                .setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.block_unblock_trackers)
                        .setMessage(R.string.apply_to_system_apps_question)
                        .setPositiveButton(R.string.no, (dialog, which) -> {
                            progressIndicator.show();
                            if (!wakeLock.isHeld()) {
                                wakeLock.acquire();
                            }
                            mViewModel.blockTrackers(false);
                        })
                        .setNegativeButton(R.string.yes, (dialog, which) -> {
                            progressIndicator.show();
                            if (!wakeLock.isHeld()) {
                                wakeLock.acquire();
                            }
                            mViewModel.blockTrackers(true);
                        })
                        .show());
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.block_unblock_components_dots),
                        getString(R.string.block_unblock_components_description))
                .setOnClickListener(v -> new TextInputDialogBuilder(this, R.string.input_signatures)
                        .setHelperText(R.string.input_signatures_description)
                        .setCheckboxLabel(R.string.apply_to_system_apps)
                        .setTitle(R.string.block_unblock_components_dots)
                        .setPositiveButton(R.string.search, (dialog, which, signatureNames, systemApps) -> {
                            if (signatureNames == null) return;
                            progressIndicator.show();
                            if (!wakeLock.isHeld()) {
                                wakeLock.acquire();
                            }
                            String[] signatures = signatureNames.toString().split("\\s+");
                            mViewModel.blockComponents(systemApps, signatures);
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show());
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
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.clear_data_from_uninstalled_apps),
                        getString(R.string.clear_data_from_uninstalled_apps_description))
                .setOnClickListener(v -> {
                    if (!SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.CLEAR_APP_USER_DATA)) {
                        UIUtils.displayShortToast(R.string.only_works_in_root_or_adb_mode);
                        return;
                    }
                    if (!wakeLock.isHeld()) {
                        wakeLock.acquire();
                    }
                    mViewModel.clearData();
                });
//        mItemCreator.addItemWithTitleSubtitle(getString(R.string.clear_app_cache),
//                        getString(R.string.clear_app_cache_description))
//                .setOnClickListener(v -> clearAppCache());
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.trim_caches_in_all_apps),
                        getString(R.string.trim_caches_in_all_apps_description))
                .setOnClickListener(v -> {
                    if (!SelfPermissions.checkSelfPermission(Manifest.permission.CLEAR_APP_CACHE)
                            && !SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.CLEAR_APP_CACHE)) {
                        UIUtils.displayShortToast(R.string.only_works_in_root_or_adb_mode);
                        return;
                    }
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.trim_caches_in_all_apps)
                            .setMessage(R.string.are_you_sure)
                            .setNegativeButton(R.string.no, null)
                            .setPositiveButton(R.string.yes, (dialog, which) -> {
                                progressIndicator.show();
                                if (!wakeLock.isHeld()) {
                                    wakeLock.acquire();
                                }
                                mViewModel.trimCaches();
                            })
                            .show();
                });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mItemCreator.addItemWithTitleSubtitle(getString(R.string.title_perform_runtime_optimization_to_apps),
                            getString(R.string.summary_perform_runtime_optimization_to_apps))
                    .setOnClickListener(v -> {
                        if (SelfPermissions.isSystemOrRootOrShell()) {
                            DexOptDialog dialog = DexOptDialog.getInstance(null);
                            dialog.show(getSupportFragmentManager(), DexOptDialog.TAG);
                            return;
                        }
                        progressIndicator.show();
                        if (!wakeLock.isHeld()) {
                            wakeLock.acquire();
                        }
                        mViewModel.listAppsInstalledByAmForDexOpt();
                    });
        }
        progressIndicator.hide();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextCompat.registerReceiver(this, mBatchOpsBroadCastReceiver,
                new IntentFilter(BatchOpsService.ACTION_BATCH_OPS_COMPLETED), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBatchOpsBroadCastReceiver);
        if (progressIndicator != null) {
            progressIndicator.hide();
        }
    }

    private void blockTrackers(@Nullable List<ItemCount> trackerCounts) {
        CpuUtils.releaseWakeLock(wakeLock);
        progressIndicator.hide();
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
            trackerPackagesWithTrackerCount.add(new SpannableStringBuilder(tracker.packageLabel)
                    .append("\n").append(getSmallerText(getResources().getQuantityString(R.plurals.no_of_trackers,
                            tracker.count, tracker.count))));
        }
        new SearchableMultiChoiceDialogBuilder<>(this, trackerPackages, trackerPackagesWithTrackerCount)
                .addSelections(trackerPackages)
                .setTitle(R.string.filtered_packages)
                .setPositiveButton(R.string.block, (dialog, which, selectedPackages) -> {
                    progressIndicator.show();
                    Intent intent = new Intent(this, BatchOpsService.class);
                    intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, selectedPackages);
                    intent.putExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_BLOCK_TRACKERS);
                    intent.putExtra(BatchOpsService.EXTRA_HEADER, getString(R.string.one_click_ops));
                    ContextCompat.startForegroundService(this, intent);
                })
                .setNeutralButton(R.string.unblock, (dialog, which, selectedPackages) -> {
                    progressIndicator.show();
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
        CpuUtils.releaseWakeLock(wakeLock);
        progressIndicator.hide();
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
            builder = new SpannableStringBuilder(component.packageLabel)
                    .append("\n").append(getSmallerText(getResources().getQuantityString(R.plurals.no_of_components,
                            component.count, component.count)));
            selectedPackages.add(component.packageName);
            packageNamesWithComponentCount.add(builder);
        }
        new SearchableMultiChoiceDialogBuilder<>(this, selectedPackages, packageNamesWithComponentCount)
                .addSelections(selectedPackages)
                .setTitle(R.string.filtered_packages)
                .setPositiveButton(R.string.block, (dialog1, which1, selectedItems) -> {
                    progressIndicator.show();
                    Intent intent = new Intent(this, BatchOpsService.class);
                    intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, selectedItems);
                    intent.putExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_BLOCK_COMPONENTS);
                    intent.putExtra(BatchOpsService.EXTRA_HEADER, getString(R.string.one_click_ops));
                    Bundle args = new Bundle();
                    args.putStringArray(BatchOpsManager.ARG_SIGNATURES, signatures);
                    intent.putExtra(BatchOpsService.EXTRA_OP_EXTRA_ARGS, args);
                    ContextCompat.startForegroundService(this, intent);
                })
                .setNeutralButton(R.string.unblock, (dialog1, which1, selectedItems) -> {
                    progressIndicator.show();
                    Intent intent = new Intent(this, BatchOpsService.class);
                    intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, selectedItems);
                    intent.putExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_UNBLOCK_COMPONENTS);
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
        if (!SelfPermissions.canModifyAppOpMode()) {
            UIUtils.displayShortToast(R.string.only_works_in_root_or_adb_mode);
            return;
        }
        List<Integer> modes = AppOpsManagerCompat.getModeConstants();
        List<Integer> appOps = AppOpsManagerCompat.getAllOps();
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
                    progressIndicator.show();
                    if (!wakeLock.isHeld()) {
                        wakeLock.acquire();
                    }
                    mViewModel.setAppOps(appOpList, mode, systemApps);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setAppOps(@Nullable List<AppOpCount> appOpCounts, @NonNull int[] appOpList, int mode) {
        CpuUtils.releaseWakeLock(wakeLock);
        progressIndicator.hide();
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
            builder1 = new SpannableStringBuilder(appOp.packageLabel)
                    .append("\n").append(getSmallerText("(" + appOp.count + ") " + TextUtilsCompat.joinSpannable(", ",
                            appOpToNames(appOp.appOps))));
            selectedPackages.add(appOp.packageName);
            packagesWithAppOpCount.add(builder1);
        }
        new SearchableMultiChoiceDialogBuilder<>(this, selectedPackages, packagesWithAppOpCount)
                .addSelections(selectedPackages)
                .setTitle(R.string.filtered_packages)
                .setPositiveButton(R.string.apply, (dialog1, which1, selectedItems) -> {
                    progressIndicator.show();
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
                .setNegativeButton(R.string.cancel, (dialog1, which1, selectedItems) -> progressIndicator.hide())
                .show();
    }

    private void clearData(@NonNull List<String> candidatePackages) {
        CpuUtils.releaseWakeLock(wakeLock);
        String[] packages = candidatePackages.toArray(new String[0]);
        new SearchableMultiChoiceDialogBuilder<>(this, packages, packages)
                .setTitle(R.string.filtered_packages)
                .setPositiveButton(R.string.apply, (dialog1, which1, selectedItems) -> {
                    progressIndicator.show();
                    Intent intent = new Intent(this, BatchOpsService.class);
                    intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, selectedItems);
                    intent.putExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_UNINSTALL);
                    intent.putExtra(BatchOpsService.EXTRA_HEADER, getString(R.string.one_click_ops));
                    ContextCompat.startForegroundService(this, intent);
                })
                .setNegativeButton(R.string.cancel, (dialog1, which1, selectedItems) -> progressIndicator.hide())
                .show();
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

    @Override
    protected void onDestroy() {
        CpuUtils.releaseWakeLock(wakeLock);
        super.onDestroy();
    }

    @NonNull
    private List<String> appOpToNames(@NonNull Collection<Integer> appOps) {
        List<String> appOpNames = new ArrayList<>(appOps.size());
        for (int appOp : appOps) {
            appOpNames.add(AppOpsManagerCompat.opToName(appOp));
        }
        return appOpNames;
    }
}