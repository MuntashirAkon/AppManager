// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.SpannableStringBuilder;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.collection.ArraySet;
import androidx.core.content.ContextCompat;

import com.android.internal.util.TextUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.types.TextInputDialogBuilder;
import io.github.muntashirakon.AppManager.types.TextInputDropdownDialogBuilder;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ListItemCreator;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.UiThreadHandler;
import io.github.muntashirakon.AppManager.utils.Utils;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagDisabledComponents;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOpModeNames;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOpModes;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOpNames;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOps;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

public class OneClickOpsActivity extends BaseActivity {
    LinearProgressIndicator mProgressIndicator;

    private ListItemCreator mItemCreator;
    private final BroadcastReceiver mBatchOpsBroadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mProgressIndicator.hide();
        }
    };
    private final ExecutorService executor = Executors.newFixedThreadPool(1);

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_one_click_ops);
        setSupportActionBar(findViewById(R.id.toolbar));
        mItemCreator = new ListItemCreator(this, R.id.container);
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        setItems();
    }

    private void setItems() {
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.block_unblock_trackers),
                getString(R.string.block_unblock_trackers_description))
                .setOnClickListener(v -> {
                    if (!AppPref.isRootEnabled()) {
                        Toast.makeText(this, R.string.only_works_in_root_mode, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.block_unblock_trackers)
                            .setMessage(R.string.apply_to_system_apps_question)
                            .setPositiveButton(R.string.no, (dialog, which) -> blockTrackers(false))
                            .setNegativeButton(R.string.yes, ((dialog, which) -> blockTrackers(true)))
                            .show();
                });
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.block_components_dots),
                getString(R.string.block_components_description))
                .setOnClickListener(v -> blockComponents());
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.set_mode_for_app_ops_dots),
                getString(R.string.deny_app_ops_description))
                .setOnClickListener(v -> blockAppOps());
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

    @SuppressLint("WrongConstant")
    private void blockTrackers(final boolean systemApps) {
        mProgressIndicator.show();
        executor.submit(() -> {
            final List<ItemCount> trackerCounts = new ArrayList<>();
            ItemCount trackerCount;
            try {
                for (PackageInfo packageInfo : PackageManagerCompat.getInstalledPackages(
                        PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | flagDisabledComponents
                                | PackageManager.GET_PROVIDERS | PackageManager.GET_SERVICES, Users.myUserId())) {
                    if (Thread.currentThread().isInterrupted()) return;
                    ApplicationInfo applicationInfo = packageInfo.applicationInfo;
                    if (!systemApps && (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                        continue;
                    trackerCount = ComponentUtils.getTrackerCountForApp(packageInfo);
                    if (trackerCount.count > 0) trackerCounts.add(trackerCount);
                }
            } catch (RemoteException e) {
                Log.e("OCOA", e);
                UiThreadHandler.run(() -> UIUtils.displayShortToast(R.string.failed_to_fetch_package_info));
                return;
            }
            if (!trackerCounts.isEmpty()) {
                final ArrayList<String> trackerPackages = new ArrayList<>();
                final List<CharSequence> trackerPackagesWithTrackerCount = new ArrayList<>(trackerCounts.size());
                for (ItemCount count : trackerCounts) {
                    if (Thread.currentThread().isInterrupted()) return;
                    trackerPackages.add(count.packageName);
                    trackerPackagesWithTrackerCount.add(new SpannableStringBuilder(count.packageLabel)
                            .append("\n").append(getSecondaryText(this, getSmallerText(getResources()
                                    .getQuantityString(R.plurals.no_of_trackers, count.count,
                                            count.count)))));
                }
                if (Thread.currentThread().isInterrupted()) return;
                runOnUiThread(() -> {
                    mProgressIndicator.hide();
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
                            .setNegativeButton(R.string.cancel, (dialog, which, selectedPackages) -> mProgressIndicator.hide())
                            .show();
                });
            } else {
                if (Thread.currentThread().isInterrupted()) return;
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.no_tracker_found, Toast.LENGTH_SHORT).show();
                    mProgressIndicator.hide();
                });
            }
        });
    }

    private void blockComponents() {
        if (!AppPref.isRootEnabled()) {
            Toast.makeText(this, R.string.only_works_in_root_mode, Toast.LENGTH_SHORT).show();
            return;
        }
        new TextInputDialogBuilder(this, R.string.input_signatures)
                .setHelperText(R.string.input_signatures_description)
                .setCheckboxLabel(R.string.apply_to_system_apps)
                .setTitle(R.string.block_components_dots)
                .setPositiveButton(R.string.search, (dialog, which, signatureNames, isChecked) -> {
                    final boolean systemApps = isChecked;
                    if (signatureNames == null) return;
                    mProgressIndicator.show();
                    executor.submit(() -> {
                        String[] signatures = signatureNames.toString().split("\\s+");
                        if (signatures.length == 0) return;
                        final List<ItemCount> componentCounts = new ArrayList<>();
                        for (ApplicationInfo applicationInfo : getPackageManager().getInstalledApplications(0)) {
                            if (Thread.currentThread().isInterrupted()) return;
                            if (!systemApps && (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                                continue;
                            ItemCount componentCount = new ItemCount();
                            componentCount.packageName = applicationInfo.packageName;
                            componentCount.packageLabel = applicationInfo.loadLabel(getPackageManager()).toString();
                            componentCount.count = PackageUtils.getFilteredComponents(applicationInfo.packageName, Users.myUserId(), signatures).size();
                            if (componentCount.count > 0) componentCounts.add(componentCount);
                        }
                        if (!componentCounts.isEmpty()) {
                            ItemCount componentCount;
                            SpannableStringBuilder builder;
                            final ArrayList<String> selectedPackages = new ArrayList<>();
                            List<CharSequence> packageNamesWithComponentCount = new ArrayList<>();
                            for (ItemCount count : componentCounts) {
                                if (Thread.currentThread().isInterrupted()) return;
                                componentCount = count;
                                builder = new SpannableStringBuilder(componentCount.packageLabel)
                                        .append("\n").append(getSecondaryText(this,
                                                getSmallerText(getResources().getQuantityString(
                                                        R.plurals.no_of_components, componentCount.count,
                                                        componentCount.count))));
                                selectedPackages.add(componentCount.packageName);
                                packageNamesWithComponentCount.add(builder);
                            }
                            if (Thread.currentThread().isInterrupted()) return;
                            runOnUiThread(() -> {
                                mProgressIndicator.hide();
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
                                        .setNegativeButton(R.string.cancel, (dialog1, which1, selectedItems) -> mProgressIndicator.hide())
                                        .show();
                            });
                        } else {
                            if (Thread.currentThread().isInterrupted()) return;
                            runOnUiThread(() -> {
                                Toast.makeText(this, R.string.no_matching_package_found, Toast.LENGTH_SHORT).show();
                                mProgressIndicator.hide();
                            });
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void blockAppOps() {
        if (!AppPref.isRootOrAdbEnabled()) {
            Toast.makeText(this, R.string.only_works_in_root_or_adb_mode, Toast.LENGTH_SHORT).show();
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
                    mProgressIndicator.show();
                    executor.submit(() -> {
                        // Get mode
                        int mode;
                        int[] appOpList;
                        try {
                            mode = Utils.getIntegerFromString(builder.getAuxiliaryInput(), modeNames, modes);
                            String[] appOpsStr = appOpNameList.toString().split("\\s+");
                            if (appOpsStr.length == 0) return;
                            // User can unknowingly insert duplicate values for app ops
                            Set<Integer> appOpSet = new ArraySet<>(appOpsStr.length);
                            for (String appOp : appOpsStr) {
                                if (Thread.currentThread().isInterrupted()) return;
                                appOpSet.add(Utils.getIntegerFromString(appOp, appOpNames, appOps));
                            }
                            appOpList = ArrayUtils.convertToIntArray(appOpSet);
                        } catch (IllegalArgumentException e) {
                            if (Thread.currentThread().isInterrupted()) return;
                            runOnUiThread(() -> {
                                Toast.makeText(this, R.string.failed_to_parse_some_numbers, Toast.LENGTH_SHORT).show();
                                mProgressIndicator.hide();
                            });
                            return;
                        }
                        final List<AppOpCount> appOpCounts = new ArrayList<>();
                        for (ApplicationInfo applicationInfo :
                                getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA)) {
                            if (Thread.currentThread().isInterrupted()) return;
                            if (!systemApps && (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                                continue;
                            AppOpCount appOpCount = new AppOpCount();
                            appOpCount.packageName = applicationInfo.packageName;
                            appOpCount.packageLabel = applicationInfo.loadLabel(getPackageManager()).toString();
                            appOpCount.appOps = PackageUtils.getFilteredAppOps(applicationInfo.packageName, Users.myUserId(), appOpList, mode);
                            appOpCount.count = appOpCount.appOps.size();
                            if (appOpCount.count > 0) appOpCounts.add(appOpCount);
                        }
                        if (!appOpCounts.isEmpty()) {
                            AppOpCount appOpCount;
                            SpannableStringBuilder builder1;
                            final ArrayList<String> selectedPackages = new ArrayList<>();
                            List<CharSequence> packagesWithAppOpCount = new ArrayList<>();
                            for (AppOpCount opCount : appOpCounts) {
                                if (Thread.currentThread().isInterrupted()) return;
                                appOpCount = opCount;
                                builder1 = new SpannableStringBuilder(appOpCount.packageLabel)
                                        .append("\n").append(getSecondaryText(this,
                                                getSmallerText("(" + appOpCount.count + ") "
                                                        + TextUtils.joinSpannable(", ",
                                                        appOpToNames(appOpCount.appOps)))));
                                selectedPackages.add(appOpCount.packageName);
                                packagesWithAppOpCount.add(builder1);
                            }
                            if (Thread.currentThread().isInterrupted()) return;
                            runOnUiThread(() -> {
                                mProgressIndicator.hide();
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
                            });
                        } else {
                            if (Thread.currentThread().isInterrupted()) return;
                            runOnUiThread(() -> {
                                Toast.makeText(this, R.string.no_matching_package_found, Toast.LENGTH_SHORT).show();
                                mProgressIndicator.hide();
                            });
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
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

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @NonNull
    private List<String> appOpToNames(@NonNull Collection<Integer> appOps) {
        List<String> appOpNames = new ArrayList<>(appOps.size());
        for (int appOp : appOps) {
            appOpNames.add(AppOpsManager.opToName(appOp));
        }
        return appOpNames;
    }
}