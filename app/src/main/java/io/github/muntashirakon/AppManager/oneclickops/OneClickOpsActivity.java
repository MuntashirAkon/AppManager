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

package io.github.muntashirakon.AppManager.oneclickops;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.ProgressIndicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.types.TextInputDialogBuilder;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ListItemCreator;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class OneClickOpsActivity extends BaseActivity {
    private ListItemCreator mItemCreator;
    private ProgressIndicator mProgressIndicator;
    private final BroadcastReceiver mBatchOpsBroadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mProgressIndicator.hide();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
                .setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.block_unblock_trackers)
                        .setMessage(R.string.apply_to_system_apps_question)
                        .setPositiveButton(R.string.no, (dialog, which) -> blockTrackers(false))
                        .setNegativeButton(R.string.yes, ((dialog, which) -> blockTrackers(true)))
                        .show());
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.block_components_dots),
                getString(R.string.block_components_description))
                .setOnClickListener(v -> blockComponents());
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.deny_app_ops_dots),
                getString(R.string.deny_app_ops_description))
                .setOnClickListener(v -> blockAppOps());
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.clear_data_from_uninstalled_apps),
                getString(R.string.clear_data_from_uninstalled_apps_description))
                .setOnClickListener(v -> clearData());
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.clear_app_cache),
                getString(R.string.clear_app_cache_description))
                .setOnClickListener(v -> clearAppCache());
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

    private void blockTrackers(final boolean systemApps) {
        if (!AppPref.isRootEnabled()) {
            Toast.makeText(this, R.string.only_works_in_root_mode, Toast.LENGTH_SHORT).show();
            return;
        }
        mProgressIndicator.show();
        new Thread(() -> {
            final List<ItemCount> trackerCounts = new ArrayList<>();
            ItemCount trackerCount;
            for (ApplicationInfo applicationInfo : getPackageManager().getInstalledApplications(0)) {
                if (!systemApps && (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                    continue;
                trackerCount = ComponentUtils.getTrackerCountForApp(applicationInfo);
                if (trackerCount.count > 0) trackerCounts.add(trackerCount);
            }
            if (!trackerCounts.isEmpty()) {
                final ArrayList<String> selectedPackages = new ArrayList<>();
                final String[] trackerPackagesWithTrackerCount = new String[trackerCounts.size()];
                for (int i = 0; i < trackerCounts.size(); ++i) {
                    trackerCount = trackerCounts.get(i);
                    selectedPackages.add(trackerCount.packageName);
                    trackerPackagesWithTrackerCount[i] = "(" + trackerCount.count + ") " + trackerCount.packageLabel;
                }
                final String[] trackerPackages = selectedPackages.toArray(new String[0]);
                final boolean[] checkedItems = new boolean[trackerPackages.length];
                Arrays.fill(checkedItems, true);
                runOnUiThread(() -> {
                    mProgressIndicator.hide();
                    new MaterialAlertDialogBuilder(this)
                            .setMultiChoiceItems(trackerPackagesWithTrackerCount, checkedItems, (dialog, which, isChecked) -> {
                                if (!isChecked) selectedPackages.remove(trackerPackages[which]);
                                else selectedPackages.add(trackerPackages[which]);
                            })
                            .setTitle(R.string.found_trackers)
                            .setPositiveButton(R.string.block, (dialog, which) -> {
                                mProgressIndicator.show();
                                Intent intent = new Intent(this, BatchOpsService.class);
                                intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, selectedPackages);
                                intent.putExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_BLOCK_TRACKERS);
                                intent.putExtra(BatchOpsService.EXTRA_HEADER, getString(R.string.one_click_ops));
                                ContextCompat.startForegroundService(this, intent);
                            })
                            .setNeutralButton(R.string.unblock, (dialog, which) -> {
                                mProgressIndicator.show();
                                Intent intent = new Intent(this, BatchOpsService.class);
                                intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, selectedPackages);
                                intent.putExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_UNBLOCK_TRACKERS);
                                intent.putExtra(BatchOpsService.EXTRA_HEADER, getString(R.string.one_click_ops));
                                ContextCompat.startForegroundService(this, intent);
                            })
                            .setNegativeButton(R.string.cancel, (dialog, which) -> mProgressIndicator.hide())
                            .show();
                });
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.no_tracker_found, Toast.LENGTH_SHORT).show();
                    mProgressIndicator.hide();
                });
            }
        }).start();
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
                    new Thread(() -> {
                        String[] signatures = signatureNames.toString().split("\\s+");
                        if (signatures.length == 0) return;
                        final List<ItemCount> componentCounts = new ArrayList<>();
                        for (ApplicationInfo applicationInfo : getPackageManager().getInstalledApplications(0)) {
                            if (!systemApps && (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                                continue;
                            ItemCount componentCount = new ItemCount();
                            componentCount.packageName = applicationInfo.packageName;
                            componentCount.packageLabel = applicationInfo.loadLabel(getPackageManager()).toString();
                            componentCount.count = PackageUtils.getFilteredComponents(applicationInfo.packageName, signatures).size();
                            if (componentCount.count > 0) componentCounts.add(componentCount);
                        }
                        if (!componentCounts.isEmpty()) {
                            ItemCount componentCount;
                            final ArrayList<String> selectedPackages = new ArrayList<>();
                            List<CharSequence> packageNamesWithComponentCount = new ArrayList<>();
                            for (int i = 0; i < componentCounts.size(); ++i) {
                                componentCount = componentCounts.get(i);
                                selectedPackages.add(componentCount.packageName);
                                packageNamesWithComponentCount.add("(" + componentCount.count + ") " + componentCount.packageLabel);
                            }
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
                            runOnUiThread(() -> {
                                Toast.makeText(this, R.string.no_matching_package_found, Toast.LENGTH_SHORT).show();
                                mProgressIndicator.hide();
                            });
                        }
                    }).start();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void blockAppOps() {
        if (!AppPref.isRootOrAdbEnabled()) {
            Toast.makeText(this, R.string.only_works_in_root_or_adb_mode, Toast.LENGTH_SHORT).show();
            return;
        }
        new TextInputDialogBuilder(this, R.string.input_app_ops)
                .setTitle(R.string.deny_app_ops_dots)
                .setCheckboxLabel(R.string.apply_to_system_apps)
                .setHelperText(R.string.input_app_ops_description)
                .setPositiveButton(R.string.search, (dialog, which, appOpNames, isChecked) -> {
                    final boolean systemApps = isChecked;
                    if (appOpNames == null) return;
                    mProgressIndicator.show();
                    new Thread(() -> {
                        String[] appOpsStr = appOpNames.toString().split("\\s+");
                        if (appOpsStr.length == 0) return;
                        int[] appOps = new int[appOpsStr.length];
                        try {
                            for (int i = 0; i < appOpsStr.length; ++i)
                                appOps[i] = Integer.parseInt(appOpsStr[i]);
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                Toast.makeText(this, R.string.failed_to_parse_some_numbers, Toast.LENGTH_SHORT).show();
                                mProgressIndicator.hide();
                            });
                            return;
                        }
                        final List<ItemCount> appOpCounts = new ArrayList<>();
                        for (ApplicationInfo applicationInfo :
                                getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA)) {
                            if (!systemApps && (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                                continue;
                            ItemCount appOpCount = new ItemCount();
                            appOpCount.packageName = applicationInfo.packageName;
                            appOpCount.packageLabel = applicationInfo.loadLabel(getPackageManager()).toString();
                            appOpCount.count = PackageUtils.getFilteredAppOps(applicationInfo.packageName, appOps).size();
                            if (appOpCount.count > 0) appOpCounts.add(appOpCount);
                        }
                        if (!appOpCounts.isEmpty()) {
                            ItemCount appOpCount;
                            final ArrayList<String> selectedPackages = new ArrayList<>();
                            List<CharSequence> packagesWithAppOpCount = new ArrayList<>();
                            for (int i = 0; i < appOpCounts.size(); ++i) {
                                appOpCount = appOpCounts.get(i);
                                selectedPackages.add(appOpCount.packageName);
                                packagesWithAppOpCount.add("(" + appOpCount.count + ") " + appOpCount.packageLabel);
                            }
                            runOnUiThread(() -> {
                                mProgressIndicator.hide();
                                new SearchableMultiChoiceDialogBuilder<>(this, selectedPackages, packagesWithAppOpCount)
                                        .setSelections(selectedPackages)
                                        .setTitle(R.string.filtered_packages)
                                        .setPositiveButton(R.string.apply, (dialog1, which1, selectedItems) -> {
                                            mProgressIndicator.show();
                                            Intent intent = new Intent(this, BatchOpsService.class);
                                            intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, selectedItems);
                                            intent.putExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_IGNORE_APP_OPS);
                                            intent.putExtra(BatchOpsService.EXTRA_HEADER, getString(R.string.one_click_ops));
                                            Bundle args = new Bundle();
                                            args.putIntArray(BatchOpsManager.ARG_APP_OPS, appOps);
                                            intent.putExtra(BatchOpsService.EXTRA_OP_EXTRA_ARGS, args);
                                            ContextCompat.startForegroundService(this, intent);
                                        })
                                        .setNegativeButton(R.string.cancel, (dialog1, which1, selectedItems) -> mProgressIndicator.hide())
                                        .show();
                            });
                        } else {
                            runOnUiThread(() -> {
                                Toast.makeText(this, R.string.no_matching_package_found, Toast.LENGTH_SHORT).show();
                                mProgressIndicator.hide();
                            });
                        }
                    }).start();
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
}